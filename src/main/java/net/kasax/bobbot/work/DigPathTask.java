package net.kasax.bobbot.work;

import net.kasax.bobbot.bot.Bot;
import net.kasax.bobbot.bot.BotNavigator;
import net.kasax.bobbot.bot.BotTask;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.state.BlockState;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * A way dug through the rock step by step, as a miner does it: the cells of a step opened (never
 * one with lava or water beside it - there the way ends), the ore that shows in the walls taken
 * with its vein, a torch now and then in the dark, then a step on. Used for tunnels, branches and
 * staircases down.
 */
public final class DigPathTask implements BotTask {
    /** One step: the cells to open (in order) and where to stand after. */
    public record Step(List<BlockPos> open, BlockPos stand) {
    }

    private final String what;
    private final List<Step> steps;
    private final int torchEvery;
    private int index, ticks, idle;
    private BlockPos cell;
    private final Deque<BlockPos> ores = new ArrayDeque<>();
    private final Set<BlockPos> tried = new HashSet<>();
    /** Picking up what a vein dropped in its holes, before going on. */
    private net.kasax.bobbot.bot.task.CollectDropsTask collect;
    private BlockPos lastOre;
    private boolean moving;
    /** Why it ended early ("lava ahead", "a hole"), or null; ore blocks and cells dug. */
    public String stopped;
    public int oresMined, dug;

    public DigPathTask(String what, List<Step> steps, int torchEvery) {
        this.what = what;
        this.steps = steps;
        this.torchEvery = torchEvery;
    }

    /** A straight tunnel two high from {@code from} (the cell stood in) along {@code dir}. */
    public static DigPathTask tunnel(BlockPos from, Direction dir, int length, int torchEvery) {
        List<Step> steps = new ArrayList<>();
        for (int i = 1; i <= length; i++) {
            BlockPos feet = from.relative(dir, i);
            steps.add(new Step(List.of(feet.above(), feet), feet));
        }
        return new DigPathTask("tunnel " + dir.getName() + " " + length, steps, torchEvery);
    }

    /** A staircase down from {@code from} along {@code dir} to feet level {@code toY}. */
    public static DigPathTask stairsDown(BlockPos from, Direction dir, int toY, int torchEvery) {
        List<Step> steps = new ArrayList<>();
        BlockPos feet = from;
        while (feet.getY() > toY) {
            BlockPos ahead = feet.relative(dir);
            BlockPos next = ahead.below();
            steps.add(new Step(List.of(ahead.above(), ahead, next), next));
            feet = next;
        }
        return new DigPathTask("stairs down to y " + toY, steps, torchEvery);
    }

    @Override
    public Result tick(Bot bot) {
        ServerLevel level = (ServerLevel) bot.body().level();
        if (++ticks > 20 * 60 * 15) return Result.DONE;
        // Ore showing in the walls first (a player does not walk past it).
        if (!ores.isEmpty()) {
            mineOre(bot, level);
            if (ores.isEmpty() && lastOre != null) collect = new net.kasax.bobbot.bot.task.CollectDropsTask(lastOre, 3, 80);
            return Result.RUNNING;
        }
        if (collect != null) {
            if (collect.tick(bot) != Result.RUNNING) {
                collect = null;
                lastOre = null;
            }
            return Result.RUNNING;
        }
        if (index >= steps.size()) return Result.DONE;
        Step step = steps.get(index);
        // Open the cells of the step.
        for (BlockPos c : step.open()) {
            BlockState s = level.getBlockState(c);
            if (s.isAir() || s.getCollisionShape(level, c).isEmpty() && s.getFluidState().isEmpty()) continue;
            if (!s.getFluidState().isEmpty() || Work.liquidBeside(level, c)) {
                stopped = Work.lavaBeside(level, c) ? "lava ahead" : "water ahead";
                return Result.DONE;
            }
            if (!bot.actions().mayBreak(c)) {
                stopped = "something of somebody's ahead";
                return Result.DONE;
            }
            if (!bot.actions().inReach(c)) {
                walk(bot, step, index > 0 ? steps.get(index - 1).stand() : bot.body().blockPosition());
                return Result.RUNNING;
            }
            bot.navigator().stop();
            if (!c.equals(cell)) {
                cell = c;
                idle = 0;
            }
            if (bot.actions().breakTick(c)) {
                dug++;
                look(level, c);
            } else if (++idle > 600) {
                stopped = "could not dig through";
                return Result.DONE;
            }
            return Result.RUNNING;
        }
        // Gravel or sand still coming down: wait.
        if (!level.getEntitiesOfClass(net.minecraft.world.entity.item.FallingBlockEntity.class,
                new net.minecraft.world.phys.AABB(step.stand()).inflate(1, 3, 1)).isEmpty()) return Result.RUNNING;
        // Nothing to stand on there (a cave below): a block put down, else the way ends.
        BlockPos floor = step.stand().below();
        if (level.getBlockState(floor).getCollisionShape(level, floor).isEmpty()) {
            if (!level.getFluidState(floor).isEmpty() || !bot.actions().inReach(floor) || !bot.actions().placeThrowaway(floor)) {
                stopped = "a hole in the floor";
                return Result.DONE;
            }
            return Result.RUNNING;
        }
        // On to the next step.
        if (!bot.body().blockPosition().equals(step.stand())) {
            walk(bot, step, step.stand());
            return Result.RUNNING;
        }
        bot.navigator().stop();
        moving = false;
        // A torch where it has got dark (a torch's light is down to 3 some eleven blocks off).
        if (torchEvery > 0 && level.getBrightness(net.minecraft.world.level.LightLayer.BLOCK, step.stand()) < 3
                && level.getBrightness(net.minecraft.world.level.LightLayer.SKY, step.stand()) < 8) Work.torch(bot, step.stand());
        index++;
        return Result.RUNNING;
    }

    private void walk(Bot bot, Step step, BlockPos to) {
        var nav = bot.navigator();
        if (!moving || nav.status() != BotNavigator.Status.MOVING) {
            nav.goTo(to);
            moving = true;
        }
        if (nav.tick() == BotNavigator.Status.FAILED) {
            moving = false;
            if (++idle > 200) stopped = "could not get on";
        }
    }

    /** The cells around a newly opened one: any ore there goes on the list. */
    private void look(ServerLevel level, BlockPos opened) {
        for (Direction d : Direction.values()) {
            BlockPos n = opened.relative(d);
            if (!tried.contains(n) && Work.ore(level.getBlockState(n))) {
                tried.add(n.immutable());
                ores.add(n.immutable());
            }
        }
    }

    /** The first ore on the list, and what of its vein it shows. */
    private void mineOre(Bot bot, ServerLevel level) {
        BlockPos o = ores.peek();
        BlockState s = level.getBlockState(o);
        if (!Work.ore(s) || !bot.actions().mayBreak(o) || Work.liquidBeside(level, o) || !bot.tools().canHarvest(s)) {
            ores.poll();
            return;
        }
        switch (Work.reach(bot, o, 4.2)) {
            case THERE -> {
                if (bot.actions().breakTick(o)) {
                    ores.poll();
                    oresMined++;
                    lastOre = o;
                    look(level, o);
                    // (What it dropped: picked up as it goes - it lies at its feet or close by.)
                }
            }
            case NO_WAY -> ores.poll();
            case ON_THE_WAY -> {
            }
        }
    }

    @Override
    public String describe() {
        return what + " (" + Math.min(index, steps.size()) + "/" + steps.size() + ")";
    }
}
