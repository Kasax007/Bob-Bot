package net.kasax.bobbot.protect;

import com.mojang.serialization.Codec;
import it.unimi.dsi.fastutil.ints.Int2ByteMap;
import it.unimi.dsi.fastutil.ints.Int2ByteOpenHashMap;
import it.unimi.dsi.fastutil.longs.LongSet;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.kasax.bobbot.bot.BotPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.CactusBlock;
import net.minecraft.world.level.block.CocoaBlock;
import net.minecraft.world.level.block.SugarCaneBlock;
import net.minecraft.world.level.block.VegetationBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;
import java.util.stream.IntStream;

/**
 * Every block a player put into the world, and every block Bob put there for himself (a crafting
 * table, a pillar): kept with the chunk it is in, saved with it. What a player built, Bob leaves
 * alone; his own things he may take down again.
 */
public final class PlacedBlocks {
    private PlacedBlocks() {
    }

    public enum By {
        /** Put there by a player - or by Bob building for one: never broken by Bob. */
        PLAYER,
        /** Bob's own working blocks: his to take back. */
        BOB
    }

    /** The marks of one chunk: the block (its place in the chunk) and who put it there. */
    public static final class Marks {
        final Int2ByteOpenHashMap map = new Int2ByteOpenHashMap();

        Marks() {
            map.defaultReturnValue((byte) -1);
        }

        static final Codec<Marks> CODEC = Codec.INT_STREAM.xmap(Marks::of, Marks::stream);

        private static Marks of(IntStream packed) {
            Marks m = new Marks();
            packed.forEach(v -> m.map.put(v >>> 2, (byte) (v & 3)));
            return m;
        }

        private IntStream stream() {
            int[] out = new int[map.size()];
            int i = 0;
            for (Int2ByteMap.Entry e : map.int2ByteEntrySet()) out[i++] = e.getIntKey() << 2 | e.getByteValue();
            return IntStream.of(out);
        }

        public int size() {
            return map.size();
        }
    }

    public static final AttachmentType<Marks> MARKS = AttachmentRegistry.create(
            Identifier.fromNamespaceAndPath("bobbot", "placed_blocks"), b -> b.persistent(Marks.CODEC));

    /** Called once at start (the attachment type is registered when this class loads). */
    public static void register() {
    }

    // ---- who is placing right now -----------------------------------------------------------------

    private static final ThreadLocal<By> PLACING = new ThreadLocal<>();
    /** Bots building for their owner (their placements count as the owner's). */
    private static final Map<UUID, Boolean> BUILDING_FOR_OWNER = new ConcurrentHashMap<>();

    /** Whose a block put down by this player is. */
    public static By byWhom(Player p) {
        if (p instanceof BotPlayer) return BUILDING_FOR_OWNER.containsKey(p.getUUID()) ? By.PLAYER : By.BOB;
        return By.PLAYER;
    }

    /** While on, what this bot places counts as its owner's (built for him: never taken down again). */
    public static void buildingForOwner(UUID bot, boolean on) {
        if (on) BUILDING_FOR_OWNER.put(bot, true);
        else BUILDING_FOR_OWNER.remove(bot);
    }

    /** Runs a placement by {@code by} (a block item used): whatever it puts into the world is marked. */
    public static <T> T placing(By by, Supplier<T> place) {
        By before = PLACING.get();
        PLACING.set(by);
        try {
            return place.get();
        } finally {
            if (before == null) PLACING.remove();
            else PLACING.set(before);
        }
    }

    // ---- the marks ---------------------------------------------------------------------------------

    private static int key(BlockPos p) {
        return (p.getY() & 0xFFF) << 8 | (p.getX() & 15) << 4 | p.getZ() & 15;
    }

    private static BlockPos pos(LevelChunk chunk, int key) {
        int y = key >> 8 << 20 >> 20;
        return new BlockPos(chunk.getPos().getMinBlockX() + (key >> 4 & 15), y, chunk.getPos().getMinBlockZ() + (key & 15));
    }

    /**
     * A block in a chunk changed (from the chunk itself, for every change on the server): marked if
     * someone is placing it, the mark gone when the block is gone.
     */
    public static void changed(LevelChunk chunk, BlockPos pos, BlockState old, BlockState now) {
        By by = PLACING.get();
        if (by != null && placedHere(old, now)) {
            Marks m = chunk.getAttached(MARKS);
            if (m == null) {
                m = new Marks();
                chunk.setAttached(MARKS, m);
            }
            m.map.put(key(pos), (byte) by.ordinal());
            chunk.markUnsaved();
            return;
        }
        // (Gone: broken, burnt, blown up, washed away. A block turned into another - stripped,
        // oxidised, grassed over - is still the one somebody put there.)
        if (now.canBeReplaced() && !old.canBeReplaced()) {
            Marks m = chunk.getAttached(MARKS);
            if (m != null && m.map.remove(key(pos)) != m.map.defaultReturnValue()) {
                if (m.map.isEmpty()) chunk.removeAttached(MARKS);
                chunk.markUnsaved();
            }
        }
    }

    /**
     * A new block where there was room for one (air, water, grass): what a placement does. Not
     * plants - a sapling, a seed, sugar cane are for growing and harvesting, not for keeping.
     */
    private static boolean placedHere(BlockState old, BlockState now) {
        if (now.canBeReplaced() || !old.canBeReplaced()) return false;
        Block b = now.getBlock();
        return !(b instanceof VegetationBlock || b instanceof SugarCaneBlock || b instanceof CactusBlock || b instanceof CocoaBlock
                || b instanceof net.minecraft.world.level.block.BambooStalkBlock || b instanceof net.minecraft.world.level.block.BambooSaplingBlock
                || now.is(BlockTags.SAPLINGS) || now.is(BlockTags.CROPS));
    }

    /** Who put the block at {@code pos} there, or null (nature, a structure, or not loaded). */
    public static By at(ServerLevel level, BlockPos pos) {
        LevelChunk chunk = level.getChunkSource().getChunkNow(pos.getX() >> 4, pos.getZ() >> 4);
        if (chunk == null) return null;
        Marks m = chunk.getAttached(MARKS);
        if (m == null) return null;
        int v = m.map.get(key(pos));
        return v < 0 ? null : By.values()[v];
    }

    /** The marked blocks of a chunk, as packed positions, into the set for their kind. */
    public static void collect(LevelChunk chunk, LongSet player, LongSet bob) {
        Marks m = chunk.getAttached(MARKS);
        if (m == null) return;
        for (Int2ByteMap.Entry e : m.map.int2ByteEntrySet()) {
            long p = pos(chunk, e.getIntKey()).asLong();
            if (e.getByteValue() == By.PLAYER.ordinal()) player.add(p);
            else bob.add(p);
        }
    }

    /** Marks a block by hand (a build Bob made for the owner before he was asked to keep it, a test). */
    public static void mark(ServerLevel level, BlockPos pos, By by) {
        LevelChunk chunk = level.getChunkAt(pos);
        Marks m = chunk.getAttached(MARKS);
        if (m == null) {
            m = new Marks();
            chunk.setAttached(MARKS, m);
        }
        m.map.put(key(pos), (byte) by.ordinal());
        chunk.markUnsaved();
    }
}
