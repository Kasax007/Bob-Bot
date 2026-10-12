package net.kasax.bobbot.gametest;

import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.kasax.bobbot.bot.task.GoToTask;
import net.minecraft.gametest.framework.GameTestHelper;
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
        a.run(new net.kasax.bobbot.bot.task.WaitTask(1), 1200, () -> {
            h.assertTrue(body.getHealth() >= 19, "leaf_descent_over_pit: fell, " + body.getHealth() + " hp at " + body.position());
            return body.onGround() && body.getY() < a.abs(0, FEET + 1, 0).getY();
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
}
