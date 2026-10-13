package net.kasax.bobbot.gametest;

import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.kasax.bobbot.bot.task.GoToTask;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;

import static net.kasax.bobbot.gametest.BotArena.FEET;
import static net.kasax.bobbot.gametest.BotArena.GROUND;
import static net.kasax.bobbot.gametest.BotArena.STRUCTURE;

/** Leaps: only where falling short does no harm, and from the very edge, a standing start too. */
public class MovementTests {

    /**
     * A gap three wide over a ravine twelve deep (a leap fallen short there costs nine hearts, as
     * in a game played against him): no leap - the way round over the land bridge at its end.
     */
    @GameTest(structure = STRUCTURE, maxTicks = 1600, skyAccess = true, padding = 8)
    public void noLongLeapOverRavine(GameTestHelper h) {
        BotArena a = BotArena.flat(h, "no_long_leap_over_ravine");
        a.fill(18, GROUND - 11, 0, 20, GROUND, 33, Blocks.AIR);
        a.spawn(8, FEET, 20);
        var body = a.bot().body();
        a.run(new GoToTask(a.abs(30, FEET, 20), 1.5), 1600, () -> {
            h.assertTrue(body.getY() > a.abs(0, FEET, 0).getY() - 1.5, "no_long_leap_over_ravine: fell in at " + body.position());
            return a.near(30, FEET, 20, 1.6);
        });
    }

    /** A gap three wide over water (falling short does no harm): leapt, with a run-up, never in the water. */
    @GameTest(structure = STRUCTURE, maxTicks = 600, skyAccess = true, padding = 8)
    public void longLeapOverWater(GameTestHelper h) {
        longLeap(h, "long_leap_over_water", 8);
    }

    /** The same gap from a standing start on its very edge: the jump at the last moment still carries him over. */
    @GameTest(structure = STRUCTURE, maxTicks = 600, skyAccess = true, padding = 8)
    public void longLeapStandingStart(GameTestHelper h) {
        longLeap(h, "long_leap_standing_start", 17);
    }

    private static void longLeap(GameTestHelper h, String name, int fromX) {
        BotArena a = BotArena.flat(h, name);
        a.fill(18, GROUND - 4, 0, 20, GROUND, 39, Blocks.AIR);
        a.fill(18, GROUND - 5, 0, 20, GROUND - 5, 39, Blocks.WATER);
        a.spawn(fromX, FEET, 20);
        var body = a.bot().body();
        a.run(new GoToTask(a.abs(30, FEET, 20), 1.5), 600, () -> {
            h.assertTrue(!body.isInWater() && body.getY() > a.abs(0, FEET, 0).getY() - 1.5, name + ": fell short, at " + body.position());
            return a.near(30, FEET, 20, 1.6);
        });
    }

    /**
     * Up in a crown that hangs out over a pit fifteen deep (a self-play game: the outer leaf broken,
     * forty-one blocks down): along the crown to the trunk, and down that - not a heart lost.
     */
    @GameTest(structure = STRUCTURE, maxTicks = 1200, skyAccess = true, padding = 8)
    public void leafDescentOverPit(GameTestHelper h) {
        BotArena a = BotArena.flat(h, "leaf_descent_over_pit");
        a.fill(20, 1, 10, 30, GROUND, 30, Blocks.AIR);
        a.fill(17, FEET, 20, 17, FEET + 5, 20, Blocks.OAK_LOG);
        a.fill(15, FEET + 6, 18, 23, FEET + 6, 22,
                Blocks.OAK_LEAVES.defaultBlockState().setValue(net.minecraft.world.level.block.LeavesBlock.PERSISTENT, true));
        a.spawn(22, FEET + 7, 20);
        var body = a.bot().body();
        long[] piled = {0};
        a.run(new net.kasax.bobbot.bot.task.WaitTask(1), 1200, () -> {
            h.assertTrue(body.getHealth() >= 19, "leaf_descent_over_pit: fell, " + body.getHealth() + " hp at " + body.position());
            // (The way down started once, not afresh over its own walk every few ticks: a
            // self-play game piled nine hundred of them up and never got down.)
            piled[0] = Math.max(piled[0], a.bot().tasks().stream().filter(t -> t instanceof net.kasax.bobbot.bot.task.LeafDescentTask).count());
            h.assertTrue(piled[0] <= 1, "leaf_descent_over_pit: " + piled[0] + " descents on the stack at once");
            return body.onGround() && body.getY() < a.abs(0, FEET + 1, 0).getY();
        });
    }

    /**
     * The same crown with a vine hanging in it right beside him, a leaf two below the vine (a
     * jungle crown in a self-play game: the spot over the vine, a block off, walked for nine
     * hundred times - a vine holds nobody, no way leads onto it): round it to the trunk, and down.
     */
    @GameTest(structure = STRUCTURE, maxTicks = 1200, skyAccess = true, padding = 8)
    public void leafDescentPastVine(GameTestHelper h) {
        BotArena a = BotArena.flat(h, "leaf_descent_past_vine");
        a.fill(20, 1, 10, 30, GROUND, 30, Blocks.AIR);
        a.fill(17, FEET, 20, 17, FEET + 5, 20, Blocks.OAK_LOG);
        var leaves = Blocks.OAK_LEAVES.defaultBlockState().setValue(net.minecraft.world.level.block.LeavesBlock.PERSISTENT, true);
        a.fill(15, FEET + 6, 18, 23, FEET + 6, 22, leaves);
        a.fill(21, FEET + 6, 20, 21, FEET + 6, 20, Blocks.VINE.defaultBlockState().setValue(net.minecraft.world.level.block.VineBlock.EAST, true));
        a.fill(21, FEET + 4, 20, 21, FEET + 4, 20, leaves);
        a.spawn(22, FEET + 7, 20);
        var body = a.bot().body();
        long[] piled = {0};
        a.run(new net.kasax.bobbot.bot.task.WaitTask(1), 1200, () -> {
            h.assertTrue(body.getHealth() >= 19, "leaf_descent_past_vine: fell, " + body.getHealth() + " hp at " + body.position());
            piled[0] = Math.max(piled[0], a.bot().tasks().stream().filter(t -> t instanceof net.kasax.bobbot.bot.task.LeafDescentTask).count());
            h.assertTrue(piled[0] <= 1, "leaf_descent_past_vine: " + piled[0] + " descents on the stack at once");
            return body.onGround() && body.getY() < a.abs(0, FEET + 1, 0).getY();
        });
    }

    /**
     * At the bottom of a flooded shaft that holds it down: water falling down the shaft onto a
     * whirlpool a block high over a magma block (its pull at the feet stronger than any swimming,
     * the falling water at the head no air) - a second shaft of still water two blocks off,
     * joined at the bottom (a self-play game drowned "digging up" at nothing under a village's
     * water): over to the still shaft, up and out.
     * (In the whirlpool itself there is air, as in the game: the head is in the water above it.)
     */
    @GameTest(structure = STRUCTURE, maxTicks = 900, skyAccess = true, padding = 8)
    public void airPastWhirlpool(GameTestHelper h) {
        BotArena a = BotArena.flat(h, "air_past_whirlpool");
        a.fill(18, GROUND - 10, 18, 24, GROUND, 22, Blocks.STONE);
        a.fill(20, GROUND - 9, 20, 20, GROUND - 9, 20, Blocks.MAGMA_BLOCK);
        a.fill(21, GROUND - 8, 20, 21, GROUND - 7, 20, Blocks.WATER);
        a.fill(22, GROUND - 8, 20, 22, GROUND, 20, Blocks.WATER);
        a.fill(20, GROUND, 20, 20, GROUND, 20, Blocks.WATER);
        a.fill(20, GROUND - 7, 20, 20, GROUND - 1, 20, Blocks.WATER.defaultBlockState().setValue(net.minecraft.world.level.block.LiquidBlock.LEVEL, 8));
        a.fill(20, GROUND - 8, 20, 20, GROUND - 8, 20, Blocks.BUBBLE_COLUMN.defaultBlockState()
                .setValue(net.minecraft.world.level.block.BubbleColumnBlock.DRAG_DOWN, true));
        a.spawn(20, GROUND - 8, 20, new ItemStack(Items.IRON_PICKAXE));
        var body = a.bot().body();
        body.setAirSupply(160);
        body.addEffect(new net.minecraft.world.effect.MobEffectInstance(net.minecraft.world.effect.MobEffects.FIRE_RESISTANCE, 1200));
        boolean[] under = {false}, out = {false};
        a.run(new net.kasax.bobbot.bot.task.WaitTask(1), 900, () -> {
            if (a.bot().body() != body || !body.isAlive()) h.fail("air_past_whirlpool: drowned");
            boolean eyeIn = body.isEyeInFluid(net.minecraft.tags.FluidTags.WATER);
            under[0] |= eyeIn;
            out[0] |= under[0] && !eyeIn && body.getY() > a.abs(0, GROUND - 1, 0).getY();
            return out[0];
        });
    }

    /**
     * A task that runs straight at a drop of four again and again (its own steps, no walk of the
     * navigator's): the guard brakes, and after some seconds the task is given up - no stand at
     * the edge for a minute.
     */
    @GameTest(structure = STRUCTURE, maxTicks = 400, skyAccess = true, padding = 8)
    public void guardGivesUpStuckTask(GameTestHelper h) {
        BotArena a = BotArena.flat(h, "guard_gives_up_stuck_task");
        a.fill(20, GROUND - 3, 0, 39, GROUND, 39, Blocks.AIR);
        a.spawn(12, FEET, 20);
        var body = a.bot().body();
        a.run(blindRun(), 400, () -> {
            h.assertTrue(body.getHealth() >= 20, "guard_gives_up_stuck_task: fell, " + body.getHealth());
            return h.getTick() > 40 && a.bot().current() == null && body.onGround();
        });
    }

    /** A task that runs east at a sprint, blind: no path, only its own keys - for the guard to watch. */
    private static net.kasax.bobbot.bot.BotTask blindRun() {
        return new net.kasax.bobbot.bot.BotTask() {
            @Override
            public Result tick(net.kasax.bobbot.bot.Bot bot) {
                var body = bot.body();
                body.setYRot(-90f);
                body.setYHeadRot(-90f);
                body.forward = 1f;
                body.sprintNow = true;
                return Result.RUNNING;
            }

            @Override
            public String describe() {
                return "running blind";
            }
        };
    }

    /** Under water at the bottom of a flooded shaft, a waterlogged fence across it overhead: the fence dug away, up and out. */
    @GameTest(structure = STRUCTURE, maxTicks = 900, skyAccess = true, padding = 8)
    public void airPastWaterloggedFence(GameTestHelper h) {
        BotArena a = BotArena.flat(h, "air_past_waterlogged_fence");
        a.fill(19, GROUND - 9, 19, 21, GROUND, 21, Blocks.STONE);
        a.fill(20, GROUND - 8, 20, 20, GROUND, 20, Blocks.WATER);
        a.fill(20, GROUND - 5, 20, 20, GROUND - 5, 20,
                Blocks.OAK_FENCE.defaultBlockState().setValue(net.minecraft.world.level.block.FenceBlock.WATERLOGGED, true));
        a.spawn(20, GROUND - 8, 20, new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.IRON_AXE));
        var body = a.bot().body();
        body.setAirSupply(160);
        boolean[] under = {false}, out = {false};
        a.run(new net.kasax.bobbot.bot.task.WaitTask(1), 900, () -> {
            if (a.bot().body() != body || !body.isAlive()) h.fail("air_past_waterlogged_fence: drowned");
            boolean eyeIn = body.isEyeInFluid(net.minecraft.tags.FluidTags.WATER);
            under[0] |= eyeIn;
            out[0] |= under[0] && !eyeIn && body.getY() > a.abs(0, GROUND - 5, 0).getY();
            return out[0];
        });
    }

    /**
     * Buried in a gravel bank (a self-play game dug for flint under one, the head in the gravel
     * that came down, and suffocated digging "free" for seventeen seconds): out alive, a shovel
     * in the pack.
     */
    @GameTest(structure = STRUCTURE, maxTicks = 600, skyAccess = true, padding = 8)
    public void outOfAGravelBank(GameTestHelper h) {
        BotArena a = BotArena.flat(h, "out_of_a_gravel_bank");
        a.fill(17, FEET, 17, 23, FEET + 9, 23, Blocks.GRAVEL);
        a.fill(20, FEET, 20, 20, FEET, 20, Blocks.AIR);
        a.spawn(20, FEET, 20, new ItemStack(Items.STONE_SHOVEL), new ItemStack(Items.STONE_PICKAXE));
        var body = a.bot().body();
        a.run(new net.kasax.bobbot.bot.task.WaitTask(1), 600, () -> {
            if (a.bot().body() != body || !body.isAlive()) h.fail("out_of_a_gravel_bank: suffocated");
            return !body.isInWall() && h.getTick() > 100 && body.getHealth() > 0;
        });
    }

    /** The same with the head at the corner of four columns of gravel (it stood off the middle of a block). */
    @GameTest(structure = STRUCTURE, maxTicks = 600, skyAccess = true, padding = 8)
    public void outOfAGravelBankOffCentre(GameTestHelper h) {
        BotArena a = BotArena.flat(h, "out_of_a_gravel_bank_off_centre");
        a.fill(17, FEET, 17, 24, FEET + 9, 24, Blocks.GRAVEL);
        a.fill(20, FEET, 20, 21, FEET, 21, Blocks.AIR);
        a.spawn(20, FEET, 20, new ItemStack(Items.STONE_SHOVEL), new ItemStack(Items.STONE_PICKAXE));
        var body = a.bot().body();
        var corner = a.abs(21, FEET, 21);
        body.teleportTo(corner.getX(), corner.getY(), corner.getZ());
        a.run(new net.kasax.bobbot.bot.task.WaitTask(1), 600, () -> {
            if (a.bot().body() != body || !body.isAlive()) h.fail("out_of_a_gravel_bank_off_centre: suffocated");
            return !body.isInWall() && h.getTick() > 100 && body.getHealth() > 0;
        });
    }
}
