package net.kasax.bobbot.gametest;

import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.kasax.bobbot.bot.task.GoToTask;
import net.kasax.bobbot.companion.Area;
import net.kasax.bobbot.companion.Companion;
import net.kasax.bobbot.companion.job.GatherJob;
import net.kasax.bobbot.protect.CompanionGuard;
import net.kasax.bobbot.protect.PlacedBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.LeavesBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import static net.kasax.bobbot.gametest.BotArena.FEET;
import static net.kasax.bobbot.gametest.BotArena.STRUCTURE;

/** Bob keeps his hands off what players built: houses, chests, marked areas - and goes through doors. */
public class ProtectionTests {

    /** A grown oak: a trunk and a crown of leaves that grew (not persistent). */
    static void tree(BotArena a, int x, int z, int height) {
        BlockState leaves = Blocks.OAK_LEAVES.defaultBlockState().setValue(LeavesBlock.PERSISTENT, false).setValue(LeavesBlock.DISTANCE, 1);
        for (int dx = -2; dx <= 2; dx++)
            for (int dz = -2; dz <= 2; dz++)
                for (int dy = height - 2; dy <= height; dy++)
                    if (Math.abs(dx) + Math.abs(dz) <= 3) a.fill(x + dx, FEET + dy, z + dz, x + dx, FEET + dy, z + dz, leaves);
        a.fill(x, FEET, z, x, FEET + height - 1, z, Blocks.OAK_LOG);
    }

    /**
     * Told to get logs, with a log cabin (half of it placed by a player, half older than Bob's
     * count, planks between the logs) nearer than the one grown tree: the tree is felled, the
     * cabin stands.
     */
    @GameTest(structure = STRUCTURE, maxTicks = 2400, skyAccess = true, padding = 8)
    public void fellsOnlyGrownTrees(GameTestHelper h) {
        BotArena a = BotArena.flat(h, "fells_only_grown_trees");
        // The cabin: logs at the corners and along the walls, planks between, a roof of planks.
        List<BlockPos> cabin = new ArrayList<>();
        for (int x = 8; x <= 12; x++) {
            for (int z = 16; z <= 22; z++) {
                boolean wall = x == 8 || x == 12 || z == 16 || z == 22;
                if (!wall) continue;
                boolean post = (x == 8 || x == 12) && (z == 16 || z == 22) || z == 19;
                for (int y = 0; y < 3; y++) {
                    BlockPos p = a.abs(x, FEET + y, z);
                    h.getLevel().setBlock(p, (post ? Blocks.OAK_LOG : Blocks.OAK_PLANKS).defaultBlockState(), 3);
                    if (post) cabin.add(p);
                    // (The east half put there by a player since Bob came; the west half older.)
                    if (x >= 10) PlacedBlocks.mark(h.getLevel(), p, PlacedBlocks.By.PLAYER);
                }
            }
        }
        a.fill(8, FEET + 3, 16, 12, FEET + 3, 22, Blocks.OAK_PLANKS);
        // A lone post stood up in the field (nobody's leaves on it): not a tree either.
        for (int y = 0; y < 3; y++) {
            BlockPos p = a.abs(16, FEET + y, 28);
            h.getLevel().setBlock(p, Blocks.OAK_LOG.defaultBlockState(), 3);
            cabin.add(p);
        }
        tree(a, 30, 20, 6);
        ServerPlayer owner = CompanionTests.owner(h, a, 4, 4);
        Companion c = CompanionTests.bob(h, a, owner, 16, 20);
        c.settings.followWhenIdle = false;
        c.bot.body().getInventory().add(new ItemStack(Items.IRON_AXE));
        c.now(new GatherJob(Set.of(Items.OAK_LOG), 4, GatherJob.Then.KEEP));
        h.succeedWhen(() -> {
            for (BlockPos p : cabin) h.assertTrue(h.getLevel().getBlockState(p).is(Blocks.OAK_LOG), "a cabin log was taken at " + p.subtract(a.abs(0, 0, 0)).toShortString());
            h.assertTrue(c.bot.body().getInventory().countItem(Items.OAK_LOG) >= 4, "not 4 logs yet (" + c.bot.body().getInventory().countItem(Items.OAK_LOG) + ")");
            CompanionTests.done(h, c, owner);
        });
    }

    /**
     * A player's wall right across, a door at the far end: Bob goes round through the door,
     * opens it and shuts it behind him - the wall and the door stay as they were.
     */
    @GameTest(structure = STRUCTURE, maxTicks = 1200, skyAccess = true, padding = 8)
    public void throughTheDoorNotTheWall(GameTestHelper h) {
        BotArena a = BotArena.flat(h, "through_the_door");
        List<BlockPos> wall = new ArrayList<>();
        for (int z = 0; z < BotArena.SIZE; z++) {
            for (int y = 0; y < 4; y++) {
                if (z == 30 && y < 2) continue;
                BlockPos p = a.abs(20, FEET + y, z);
                h.getLevel().setBlock(p, Blocks.COBBLESTONE.defaultBlockState(), 3);
                PlacedBlocks.mark(h.getLevel(), p, PlacedBlocks.By.PLAYER);
                wall.add(p);
            }
        }
        BlockPos door = a.abs(20, FEET, 30);
        BlockState lower = Blocks.OAK_DOOR.defaultBlockState().setValue(DoorBlock.FACING, Direction.EAST);
        h.getLevel().setBlock(door, lower.setValue(DoorBlock.HALF, DoubleBlockHalf.LOWER), 3);
        h.getLevel().setBlock(door.above(), lower.setValue(DoorBlock.HALF, DoubleBlockHalf.UPPER), 3);
        PlacedBlocks.mark(h.getLevel(), door, PlacedBlocks.By.PLAYER);
        PlacedBlocks.mark(h.getLevel(), door.above(), PlacedBlocks.By.PLAYER);
        ServerPlayer owner = CompanionTests.owner(h, a, 4, 4);
        Companion c = CompanionTests.bob(h, a, owner, 12, 12);
        c.settings.followWhenIdle = false;
        // (A pickaxe in hand: the wall could be dug - it is not his to dig.)
        c.bot.body().getInventory().add(new ItemStack(Items.IRON_PICKAXE));
        boolean[] opened = new boolean[1];
        h.runAfterDelay(5, () -> c.bot.doNow(new GoToTask(a.abs(28, FEET, 12), 1.0)));
        h.onEachTick(() -> {
            if (h.getLevel().getBlockState(door).getBlock() instanceof DoorBlock && h.getLevel().getBlockState(door).getValue(DoorBlock.OPEN)) opened[0] = true;
        });
        h.succeedWhen(() -> {
            for (BlockPos p : wall) h.assertTrue(h.getLevel().getBlockState(p).is(Blocks.COBBLESTONE), "the wall was dug at " + p.subtract(a.abs(0, 0, 0)).toShortString());
            BlockState d = h.getLevel().getBlockState(door);
            h.assertTrue(d.is(Blocks.OAK_DOOR), "the door is gone");
            h.assertTrue(c.bot.body().blockPosition().getX() > door.getX() + 3, "not through yet");
            h.assertTrue(opened[0], "went through without opening the door?");
            h.assertTrue(!d.getValue(DoorBlock.OPEN), "the door was left open");
            CompanionTests.done(h, c, owner);
        });
    }

    /** What Bob may break and what not, and why. */
    @GameTest(structure = STRUCTURE, maxTicks = 100, skyAccess = true, padding = 8)
    public void guardReasons(GameTestHelper h) {
        BotArena a = BotArena.flat(h, "guard_reasons");
        ServerPlayer owner = CompanionTests.owner(h, a, 4, 4);
        Companion c = CompanionTests.bob(h, a, owner, 6, 6);
        var level = h.getLevel();
        CompanionGuard g = new CompanionGuard(c);
        BlockPos placed = a.abs(10, FEET, 10), chest = a.abs(12, FEET, 10), planks = a.abs(14, FEET, 10), post = a.abs(16, FEET, 10),
                stone = a.abs(18, FEET - 2, 10), table = a.abs(20, FEET, 10), inArea = a.abs(30, FEET - 1, 30);
        level.setBlock(placed, Blocks.COBBLESTONE.defaultBlockState(), 3);
        PlacedBlocks.mark(level, placed, PlacedBlocks.By.PLAYER);
        level.setBlock(chest, Blocks.CHEST.defaultBlockState(), 3);
        level.setBlock(planks, Blocks.OAK_PLANKS.defaultBlockState(), 3);
        level.setBlock(post, Blocks.OAK_LOG.defaultBlockState(), 3);
        tree(a, 26, 14, 5);
        c.areas.put("p", Area.of("p", level.dimension(), a.abs(28, 0, 28), a.abs(32, 30, 32), Area.Kind.PROTECTED));
        // Bob sets down a crafting table for himself (through the hands, as a player would).
        c.bot.body().getInventory().add(new ItemStack(Items.CRAFTING_TABLE));
        h.runAfterDelay(3, () -> {
            BlockPos put = c.bot.actions().placeNearby(Items.CRAFTING_TABLE);
            h.assertTrue(put != null, "no crafting table set down");
            h.assertTrue(PlacedBlocks.at(level, put) == PlacedBlocks.By.BOB, "Bob's own table not marked as his: " + PlacedBlocks.at(level, put));
            h.assertTrue(g.reason(level, put, level.getBlockState(put)) == null, "his own table is not his to take back");
            check(h, g, placed, CompanionGuard.Reason.PLACED);
            check(h, g, chest, CompanionGuard.Reason.CONTAINER);
            check(h, g, planks, CompanionGuard.Reason.MADE);
            check(h, g, post, CompanionGuard.Reason.NOT_GROWN);
            check(h, g, stone, null);
            check(h, g, a.abs(26, FEET + 1, 14), null);
            check(h, g, inArea, CompanionGuard.Reason.AREA);
            // Building for the owner: what goes down is the owner's.
            PlacedBlocks.buildingForOwner(c.bot.id, true);
            c.bot.body().getInventory().add(new ItemStack(Items.STONE_BRICKS, 4));
            BlockPos built = c.bot.actions().placeNearby(Items.STONE_BRICKS);
            PlacedBlocks.buildingForOwner(c.bot.id, false);
            h.assertTrue(built != null && PlacedBlocks.at(level, built) == PlacedBlocks.By.PLAYER, "a block built for the owner is not the owner's");
            // Broken, the mark goes with it.
            level.removeBlock(placed, false);
            h.assertTrue(PlacedBlocks.at(level, placed) == null, "the mark stayed after the block was gone");
            CompanionTests.done(h, c, owner);
        });
    }

    private static void check(GameTestHelper h, CompanionGuard g, BlockPos p, CompanionGuard.Reason want) {
        var got = g.reason(h.getLevel(), p, h.getLevel().getBlockState(p));
        h.assertTrue(got == want, h.getLevel().getBlockState(p).getBlock().getName().getString() + ": " + got + " instead of " + want);
    }
}
