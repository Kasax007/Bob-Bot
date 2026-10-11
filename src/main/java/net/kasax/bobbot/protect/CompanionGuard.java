package net.kasax.bobbot.protect;

import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.longs.LongCollection;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import net.kasax.bobbot.bot.BlockGuard;
import net.kasax.bobbot.companion.Area;
import net.kasax.bobbot.companion.Companion;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.levelgen.structure.BoundingBox;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * What Bob, as somebody's companion, keeps his hands off - whatever the job, whatever the way:
 * <ul>
 * <li>every block a player put down (and what Bob built for one);</li>
 * <li>everything in an area the owner marked as protected;</li>
 * <li>chests, furnaces, beds, signs - anything with contents - unless he set it down himself;</li>
 * <li>with {@code protectStructures} on (the default): made blocks (planks, glass, bricks...),
 * the world's own buildings (villages, temples...), and logs that are not part of a grown tree.</li>
 * </ul>
 * His own pillars, bridges and crafting tables he may take back.
 */
public final class CompanionGuard implements BlockGuard {
    private final Companion c;

    public CompanionGuard(Companion c) {
        this.c = c;
    }

    /** Why a block is left alone (for the owner, who asks). */
    public enum Reason {
        PLACED("a player placed it", "ein Spieler hat ihn gesetzt"),
        AREA("it is in a protected area", "er liegt in einem geschützten Bereich"),
        CONTAINER("it has contents (a chest, a furnace, a sign...)", "er hat einen Inhalt (Kiste, Ofen, Schild...)"),
        MADE("it is a made block, part of something built", "er ist ein hergestellter Block, Teil eines Baus"),
        NOT_GROWN("it is not part of a grown tree", "er gehört zu keinem gewachsenen Baum"),
        STRUCTURE("it belongs to a village or another of the world's buildings", "er gehört zu einem Dorf oder einem anderen Bauwerk der Welt");

        public final String en, de;

        Reason(String en, String de) {
            this.en = en;
            this.de = de;
        }
    }

    /** Why Bob leaves the block at {@code pos} alone, or null if he may break it. */
    public Reason reason(ServerLevel level, BlockPos pos, BlockState state) {
        PlacedBlocks.By by = PlacedBlocks.at(level, pos);
        if (by == PlacedBlocks.By.PLAYER) return Reason.PLACED;
        if (c.protectedAt(level.dimension(), pos)) return Reason.AREA;
        if (by == PlacedBlocks.By.BOB) return null;
        if (state.hasBlockEntity()) return Reason.CONTAINER;
        if (!c.settings.protectStructures) return null;
        if (Built.isCrafted(level.getServer(), state.getBlock())) return Reason.MADE;
        if (state.is(BlockTags.LOGS) && !Trees.grown(level, pos)) return Reason.NOT_GROWN;
        return Structures.protectedAt(level, pos) ? Reason.STRUCTURE : null;
    }

    @Override
    public boolean mayBreak(ServerLevel level, BlockPos pos, BlockState state) {
        return reason(level, pos, state) == null;
    }

    @Override
    public boolean mayPlace(ServerLevel level, BlockPos pos) {
        return !c.protectedAt(level.dimension(), pos);
    }

    @Override
    public Frozen freeze(ServerLevel level, LongCollection chunks) {
        LongOpenHashSet player = new LongOpenHashSet(), bob = new LongOpenHashSet();
        boolean structures = c.settings.protectStructures;
        Long2ObjectOpenHashMap<List<BoundingBox>> boxes = new Long2ObjectOpenHashMap<>();
        for (long key : chunks) {
            int x = ChunkPos.unpack(key).x(), z = ChunkPos.unpack(key).z();
            LevelChunk chunk = level.getChunkSource().getChunkNow(x, z);
            if (chunk == null) continue;
            PlacedBlocks.collect(chunk, player, bob);
            if (structures) {
                List<BoundingBox> b = Structures.boxes(level, x, z);
                if (!b.isEmpty()) boxes.put(key, b);
            }
        }
        List<Area> areas = new ArrayList<>();
        for (Area a : c.areas.values()) if (a.kind() == Area.Kind.PROTECTED && a.dim() == level.dimension()) areas.add(a);
        Set<Block> crafted = structures ? Built.crafted(level.getServer()) : Set.of();
        var dim = level.dimension();
        return new Frozen() {
            @Override
            public boolean mayBreak(BlockPos p, BlockState s) {
                long k = p.asLong();
                if (player.contains(k)) return false;
                for (Area a : areas) if (a.contains(dim, p)) return false;
                if (bob.contains(k)) return true;
                if (s.hasBlockEntity()) return false;
                if (!structures) return true;
                // (Logs: whether this one is a grown tree's is not worked out off the server
                // thread - and a way through a tree is a way round it.)
                if (crafted.contains(s.getBlock()) || s.is(BlockTags.LOGS)) return false;
                List<BoundingBox> b = boxes.get(ChunkPos.pack(p.getX() >> 4, p.getZ() >> 4));
                if (b != null) for (BoundingBox box : b) if (box.isInside(p)) return false;
                return true;
            }

            @Override
            public boolean mayPlace(BlockPos p) {
                for (Area a : areas) if (a.contains(dim, p)) return false;
                return true;
            }
        };
    }
}
