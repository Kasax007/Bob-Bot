package net.kasax.bobbot.gametest;

import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.kasax.bobbot.build.Blueprint;
import net.kasax.bobbot.build.BuildJob;
import net.kasax.bobbot.build.Site;
import net.kasax.bobbot.companion.Companion;
import net.kasax.bobbot.companion.RoleChest;
import net.kasax.bobbot.protect.PlacedBlocks;
import net.kasax.bobbot.world.Containers;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.GlobalPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.LongArrayTag;
import net.minecraft.nbt.NbtIo;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.StairBlock;
import net.minecraft.world.level.block.WallTorchBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static net.kasax.bobbot.gametest.BotArena.FEET;
import static net.kasax.bobbot.gametest.BotArena.STRUCTURE;

/** Bob building from a blueprint: reading Litematica's files, a hut from the input chest, waiting for what is missing. */
public class BuildTests {

    /** A .litematic written by hand (values packed across longs, a region of negative size) read back right. */
    @GameTest(structure = STRUCTURE, maxTicks = 40, skyAccess = true, padding = 8)
    public void readsLitematic(GameTestHelper h) throws Exception {
        List<BlockState> palette = List.of(Blocks.AIR.defaultBlockState(), Blocks.STONE.defaultBlockState(), Blocks.OAK_PLANKS.defaultBlockState(),
                Blocks.GLASS.defaultBlockState(), Blocks.OAK_STAIRS.defaultBlockState().setValue(StairBlock.FACING, Direction.EAST),
                Blocks.COBBLESTONE.defaultBlockState(), Blocks.DIRT.defaultBlockState());
        int sx = 5, sy = 4, sz = 3, bits = 3;
        int volume = sx * sy * sz;
        long[] data = new long[(volume * bits + 63) / 64];
        int[] values = new int[volume];
        for (int i = 0; i < volume; i++) {
            values[i] = (i * 7 + 3) % palette.size();
            long start = (long) i * bits;
            int a = (int) (start >> 6), b = (int) (((long) (i + 1) * bits - 1) >> 6), off = (int) (start & 63);
            data[a] |= (long) values[i] << off;
            if (a != b) data[b] |= (long) values[i] >>> (64 - off);
        }
        CompoundTag region = new CompoundTag();
        CompoundTag pos = new CompoundTag();
        pos.putInt("x", 4);
        pos.putInt("y", 0);
        pos.putInt("z", 0);
        CompoundTag size = new CompoundTag();
        size.putInt("x", -sx); // (grows from x 4 down to x 0)
        size.putInt("y", sy);
        size.putInt("z", sz);
        region.put("Position", pos);
        region.put("Size", size);
        ListTag pal = new ListTag();
        for (BlockState s : palette) pal.add(NbtUtils.writeBlockState(s));
        region.put("BlockStatePalette", pal);
        region.put("BlockStates", new LongArrayTag(data));
        CompoundTag regions = new CompoundTag();
        regions.put("main", region);
        CompoundTag root = new CompoundTag();
        root.put("Regions", regions);
        root.putInt("MinecraftDataVersion", net.minecraft.SharedConstants.getCurrentVersion().dataVersion().version());
        Path dir = Files.createTempDirectory("bobbot");
        Path file = dir.resolve("probe.litematic");
        NbtIo.writeCompressed(root, file);
        Blueprint bp = Blueprint.read(h.getLevel().getServer(), file);
        h.assertTrue(bp.size.getX() == sx && bp.size.getY() == sy && bp.size.getZ() == sz, "size " + bp.size);
        for (int i = 0; i < volume; i++) {
            int x = i % sx, z = (i / sx) % sz, y = i / (sx * sz);
            BlockState got = bp.at(x, y, z), want = palette.get(values[i]);
            h.assertTrue(got == want, "at " + x + "," + y + "," + z + ": " + got + " instead of " + want);
        }
        h.succeed();
    }

    /** A small hut: floor, walls with a door and a window, stairs, a torch on the wall. */
    static Map<BlockPos, BlockState> hut() {
        Map<BlockPos, BlockState> m = new java.util.LinkedHashMap<>();
        for (int x = 0; x < 5; x++) for (int z = 0; z < 5; z++) m.put(new BlockPos(x, 0, z), Blocks.COBBLESTONE.defaultBlockState());
        for (int y = 1; y <= 3; y++) {
            for (int x = 0; x < 5; x++) {
                for (int z = 0; z < 5; z++) {
                    boolean wall = x == 0 || x == 4 || z == 0 || z == 4;
                    m.put(new BlockPos(x, y, z), wall ? Blocks.OAK_PLANKS.defaultBlockState() : Blocks.AIR.defaultBlockState());
                }
            }
        }
        BlockState door = Blocks.OAK_DOOR.defaultBlockState().setValue(DoorBlock.FACING, Direction.NORTH);
        m.put(new BlockPos(2, 1, 0), door.setValue(DoorBlock.HALF, DoubleBlockHalf.LOWER));
        m.put(new BlockPos(2, 2, 0), door.setValue(DoorBlock.HALF, DoubleBlockHalf.UPPER));
        m.put(new BlockPos(4, 2, 2), Blocks.GLASS.defaultBlockState());
        m.put(new BlockPos(1, 1, 3), Blocks.OAK_STAIRS.defaultBlockState().setValue(StairBlock.FACING, Direction.SOUTH));
        m.put(new BlockPos(3, 2, 3), Blocks.WALL_TORCH.defaultBlockState().setValue(WallTorchBlock.FACING, Direction.NORTH));
        for (int x = 0; x < 5; x++) for (int z = 0; z < 5; z++) m.put(new BlockPos(x, 4, z), Blocks.OAK_SLAB.defaultBlockState());
        return m;
    }

    /** The hut set up somewhere and copied as a blueprint, the spot cleared again. */
    static Blueprint hutBlueprint(GameTestHelper h, BotArena a) {
        BlockPos at = a.abs(2, FEET + 10, 2);
        for (var e : hut().entrySet()) h.getLevel().setBlock(at.offset(e.getKey()), e.getValue(), 2 | 16);
        Blueprint bp = Blueprint.copy(h.getLevel(), "hut", at, at.offset(4, 4, 4));
        for (var e : hut().entrySet()) h.getLevel().setBlock(at.offset(e.getKey()), Blocks.AIR.defaultBlockState(), 2 | 16);
        return bp;
    }

    /** The hut built from the input chest: every block as the blueprint has it, the chest emptied, the blocks the owner's. */
    @GameTest(structure = STRUCTURE, maxTicks = 6000, skyAccess = true, padding = 8)
    public void buildsTheHut(GameTestHelper h) {
        BotArena a = BotArena.flat(h, "builds_the_hut");
        Blueprint bp = hutBlueprint(h, a);
        Site site = new Site(bp, a.abs(18, FEET - 1, 18), Rotation.NONE, Mirror.NONE);
        BlockPos chest = a.abs(10, FEET, 26);
        h.getLevel().setBlock(chest, Blocks.CHEST.defaultBlockState(), 3);
        var box = Containers.at(h.getLevel(), chest);
        for (Map.Entry<Item, Integer> e : site.materials().entrySet()) {
            int n = e.getValue();
            while (n > 0) {
                int k = Math.min(n, e.getKey().getDefaultMaxStackSize());
                Containers.insert(box, new ItemStack(e.getKey(), k));
                n -= k;
            }
        }
        ServerPlayer owner = CompanionTests.owner(h, a, 4, 4);
        Companion c = CompanionTests.bob(h, a, owner, 12, 26);
        c.settings.followWhenIdle = false;
        c.chests.add(new RoleChest(GlobalPos.of(h.getLevel().dimension(), chest), RoleChest.Role.INPUT));
        c.bot.body().getInventory().add(new ItemStack(Items.IRON_PICKAXE));
        c.bot.body().getInventory().add(new ItemStack(Items.IRON_SHOVEL));
        c.now(new BuildJob(site));
        h.succeedWhen(() -> {
            h.assertTrue(c.current == null, "still building");
            int wrong = 0;
            String first = null;
            for (Site.Target t : site.targets) {
                BlockState now = h.getLevel().getBlockState(t.pos());
                boolean ok = now == t.state() || t.state().isAir() && now.isAir();
                if (!ok) {
                    wrong++;
                    if (first == null) first = t.pos().subtract(site.origin).toShortString() + " " + now + " instead of " + t.state();
                }
            }
            h.assertTrue(wrong == 0, wrong + " blocks wrong, e.g. " + first);
            h.assertTrue(Containers.at(h.getLevel(), chest).isEmpty(), "the input chest is not empty");
            h.assertTrue(PlacedBlocks.at(h.getLevel(), site.origin) == PlacedBlocks.By.PLAYER, "the floor is not the owner's");
            CompanionTests.done(h, c, owner);
        });
    }

    /** Short of the glass: everything else built, the glass asked for, and put in once it is there. */
    @GameTest(structure = STRUCTURE, maxTicks = 7200, skyAccess = true, padding = 8)
    public void waitsForMissingMaterial(GameTestHelper h) {
        BotArena a = BotArena.flat(h, "waits_for_missing_material");
        Blueprint bp = hutBlueprint(h, a);
        Site site = new Site(bp, a.abs(18, FEET - 1, 18), Rotation.CLOCKWISE_90, Mirror.NONE);
        BlockPos chest = a.abs(10, FEET, 26);
        h.getLevel().setBlock(chest, Blocks.CHEST.defaultBlockState(), 3);
        var box = Containers.at(h.getLevel(), chest);
        for (Map.Entry<Item, Integer> e : site.materials().entrySet()) {
            if (e.getKey() == Items.GLASS) continue;
            int n = e.getValue();
            while (n > 0) {
                int k = Math.min(n, e.getKey().getDefaultMaxStackSize());
                Containers.insert(box, new ItemStack(e.getKey(), k));
                n -= k;
            }
        }
        ServerPlayer owner = CompanionTests.owner(h, a, 4, 4);
        Companion c = CompanionTests.bob(h, a, owner, 12, 26);
        c.settings.followWhenIdle = false;
        c.chests.add(new RoleChest(GlobalPos.of(h.getLevel().dimension(), chest), RoleChest.Role.INPUT));
        c.bot.body().getInventory().add(new ItemStack(Items.IRON_PICKAXE));
        c.now(new BuildJob(site));
        boolean[] given = new boolean[1];
        h.onEachTick(() -> {
            if (!given[0] && c.log.stream().anyMatch(s -> s.contains("missing") || s.contains("fehlen"))) {
                given[0] = true;
                Containers.insert(Containers.at(h.getLevel(), chest), new ItemStack(Items.GLASS, 1));
            }
        });
        h.succeedWhen(() -> {
            h.assertTrue(given[0], "never said what is missing");
            h.assertTrue(c.current == null, "still building");
            for (Site.Target t : site.targets) {
                if (t.state().is(Blocks.GLASS)) h.assertTrue(h.getLevel().getBlockState(t.pos()).is(Blocks.GLASS), "no glass at " + t.pos().subtract(site.origin).toShortString());
            }
            CompanionTests.done(h, c, owner);
        });
    }
}
