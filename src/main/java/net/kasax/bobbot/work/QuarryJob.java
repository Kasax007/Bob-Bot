package net.kasax.bobbot.work;

import net.kasax.bobbot.bot.Bot;
import net.kasax.bobbot.bot.BotTask;
import net.kasax.bobbot.companion.Area;
import net.kasax.bobbot.companion.Companion;
import net.kasax.bobbot.companion.Lang;
import net.kasax.bobbot.companion.job.Job;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.state.BlockState;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * "Dig this out": the marked box emptied layer by layer from the top - a cellar, a quarry, room
 * for a build. What lies beside water or lava, and what is somebody's, is left standing (and
 * reported); rock beyond a stack is thrown away, the rest goes into the drop-off chest.
 */
public final class QuarryJob extends Job {
    private BlockPos min, max;
    private int broken, left;
    private final Set<BlockPos> skipped = new HashSet<>();
    private int layer = Integer.MIN_VALUE;

    public QuarryJob() {
    }

    public QuarryJob(BlockPos a, BlockPos b) {
        this.min = BlockPos.min(a, b);
        this.max = BlockPos.max(a, b);
    }

    public static QuarryJob of(Area a) {
        return new QuarryJob(a.min(), a.max());
    }

    @Override
    public String kind() {
        return "quarry";
    }

    @Override
    public String describe(Lang l) {
        int dx = max.getX() - min.getX() + 1, dy = max.getY() - min.getY() + 1, dz = max.getZ() - min.getZ() + 1;
        return l.t("dig out " + dx + "×" + dy + "×" + dz + " at " + min.toShortString(), dx + "×" + dy + "×" + dz + " bei " + min.toShortString() + " ausheben");
    }

    @Override
    public String progress(Lang l) {
        return l.t(broken + " blocks, layer y " + layer, broken + " Blöcke, Schicht y " + layer);
    }

    @Override
    public boolean delivers() {
        return true;
    }

    @Override
    public BotTask next(Companion c, Bot bot) {
        ServerLevel level = (ServerLevel) bot.body().level();
        if (Work.freeSlots(bot) <= 4) Work.dropJunk(bot);
        if (layer == Integer.MIN_VALUE) layer = max.getY();
        while (layer >= min.getY()) {
            List<BlockPos> todo = new ArrayList<>();
            for (int x = min.getX(); x <= max.getX(); x++) {
                for (int z = min.getZ(); z <= max.getZ(); z++) {
                    BlockPos p = new BlockPos(x, layer, z);
                    if (skipped.contains(p)) continue;
                    BlockState s = level.getBlockState(p);
                    if (s.isAir() || !s.getFluidState().isEmpty() && s.getCollisionShape(level, p).isEmpty()) continue;
                    if (s.getDestroySpeed(level, p) < 0 || !bot.actions().mayBreak(p) || Work.liquidBeside(level, p)) {
                        skipped.add(p);
                        continue;
                    }
                    todo.add(p);
                }
            }
            if (!todo.isEmpty()) {
                BlockPos at = bot.body().blockPosition();
                todo.sort(Comparator.comparingDouble(p -> p.distSqr(at)));
                left = todo.size();
                return new BreakBlocksTask("dig out layer y " + layer, todo.subList(0, Math.min(24, todo.size())), s -> true, true, null);
            }
            layer--;
        }
        return null;
    }

    @Override
    public void stepEnded(Companion c, Bot bot, BotTask step, boolean ok) {
        if (step instanceof BreakBlocksTask b) broken += b.broken;
    }

    @Override
    public void ended(Companion c, Bot bot, boolean done) {
        if (done && !skipped.isEmpty()) {
            c.say(bot.server(), skipped.size() + " blocks are left standing (beside water or lava, or somebody's).",
                    skipped.size() + " Blöcke habe ich stehen lassen (neben Wasser oder Lava, oder sie gehören jemandem).");
        }
    }

    @Override
    protected void save(CompoundTag t) {
        t.putLong("min", min.asLong());
        t.putLong("max", max.asLong());
        t.putInt("broken", broken);
    }

    @Override
    protected void load(CompoundTag t) {
        min = BlockPos.of(t.getLongOr("min", 0));
        max = BlockPos.of(t.getLongOr("max", 0));
        broken = t.getIntOr("broken", 0);
    }
}
