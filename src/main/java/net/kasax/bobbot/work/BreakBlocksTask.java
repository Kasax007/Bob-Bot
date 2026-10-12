package net.kasax.bobbot.work;

import net.kasax.bobbot.bot.Bot;
import net.kasax.bobbot.bot.BotTask;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.state.BlockState;

import java.util.ArrayList;
import java.util.List;
import java.util.function.BiConsumer;
import java.util.function.Predicate;

/**
 * A list of blocks broken one after the other (the nearest next), walking or building up to each:
 * a layer of a hole being dug out, the ripe crops of a field. Blocks no longer wanted, out of
 * reach for good, somebody's, or with liquid beside them are left.
 */
public final class BreakBlocksTask implements BotTask {
    private final String what;
    private final List<BlockPos> left;
    private final Predicate<BlockState> wanted;
    private final boolean dryOnly;
    private final BiConsumer<Bot, BlockPos> after;
    private BlockPos current;
    private int ticks, idle;
    public int broken, skipped;

    public BreakBlocksTask(String what, List<BlockPos> blocks, Predicate<BlockState> wanted, boolean dryOnly, BiConsumer<Bot, BlockPos> after) {
        this.what = what;
        this.left = new ArrayList<>(blocks);
        this.wanted = wanted;
        this.dryOnly = dryOnly;
        this.after = after;
    }

    @Override
    public Result tick(Bot bot) {
        ServerLevel level = (ServerLevel) bot.body().level();
        if (++ticks > 20 * 60 * 5) return Result.DONE;
        if (current == null) {
            BlockPos at = bot.body().blockPosition();
            double best = Double.MAX_VALUE;
            for (BlockPos p : left) {
                double d = p.distSqr(at);
                if (d < best) {
                    best = d;
                    current = p;
                }
            }
            if (current == null) return Result.DONE;
            left.remove(current);
            idle = 0;
            BlockState s = level.getBlockState(current);
            if (s.isAir() || !wanted.test(s) || !bot.actions().mayBreak(current) || dryOnly && Work.liquidBeside(level, current)) {
                if (!s.isAir()) skipped++;
                current = null;
                return Result.RUNNING;
            }
        }
        switch (Work.reach(bot, current, 4.2)) {
            case THERE -> {
                if (bot.actions().breakTick(current)) {
                    broken++;
                    if (after != null) after.accept(bot, current);
                    current = null;
                } else if (++idle > 300) {
                    skipped++;
                    current = null;
                }
            }
            case NO_WAY -> {
                skipped++;
                current = null;
            }
            case ON_THE_WAY -> {
            }
        }
        return Result.RUNNING;
    }

    @Override
    public String describe() {
        return what + " (" + left.size() + " left)";
    }
}
