package net.kasax.bobbot.work;

import net.kasax.bobbot.bot.Bot;
import net.kasax.bobbot.bot.BotTask;
import net.kasax.bobbot.bot.task.ObtainTask;
import net.kasax.bobbot.companion.Companion;
import net.kasax.bobbot.companion.Lang;
import net.kasax.bobbot.companion.job.Job;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.phys.Vec3;

import java.util.Set;

/**
 * "Light this up": torches put down round him until no spot a monster could spawn on is left
 * dark (block light 0 on solid ground) - the darkest, nearest first. Torches made from coal and
 * sticks if he has none.
 */
public final class LightJob extends Job {
    private BlockPos centre;
    private int radius = 16, placed;
    private boolean triedTorches;
    private BlockPos spot;
    private int spotTicks;

    public LightJob() {
    }

    public LightJob(BlockPos centre, int radius) {
        this.centre = centre.immutable();
        this.radius = radius;
    }

    @Override
    public String kind() {
        return "light";
    }

    @Override
    public String describe(Lang l) {
        return l.t("light up " + radius + " blocks round " + centre.toShortString(), "im Umkreis von " + radius + " um " + centre.toShortString() + " Licht machen");
    }

    @Override
    public String progress(Lang l) {
        return l.t(placed + " torches", placed + " Fackeln");
    }

    @Override
    public BotTask next(Companion c, Bot bot) {
        var body = bot.body();
        ServerLevel level = (ServerLevel) body.level();
        if (body.getInventory().countItem(Items.TORCH) == 0) {
            if (triedTorches) {
                if (placed == 0) fail("I have no torches (nor coal for them)", "ich habe keine Fackeln (und keine Kohle dafür)");
                return null;
            }
            triedTorches = true;
            return new ObtainTask(Set.of(Items.TORCH), 32);
        }
        BlockPos dark = darkest(level, body.blockPosition());
        if (dark == null) return null;
        return new net.kasax.bobbot.bot.task.SequenceTask("torch at " + dark.toShortString(), java.util.List.of(
                () -> new net.kasax.bobbot.bot.task.GoToTask(dark, 3.5),
                () -> new PlaceTorch(dark)));
    }

    /** A dark spot a monster could stand on, nearest the bot first. */
    private BlockPos darkest(ServerLevel level, BlockPos at) {
        BlockPos best = null;
        double bestD = Double.MAX_VALUE;
        BlockPos.MutableBlockPos m = new BlockPos.MutableBlockPos();
        for (int x = -radius; x <= radius; x += 2) {
            for (int z = -radius; z <= radius; z += 2) {
                for (int y = -6; y <= 6; y++) {
                    m.set(centre.getX() + x, centre.getY() + y, centre.getZ() + z);
                    if (!level.getBlockState(m).isAir() || !level.getBlockState(m.above()).isAir()) continue;
                    BlockPos below = m.below();
                    if (!level.getBlockState(below).isFaceSturdy(level, below, net.minecraft.core.Direction.UP)) continue;
                    if (level.getBrightness(LightLayer.BLOCK, m) > 0) continue;
                    if (!bot0Allowed(level, m)) continue;
                    double d = m.distSqr(at);
                    if (d < bestD) {
                        bestD = d;
                        best = m.immutable();
                    }
                }
            }
        }
        return best;
    }

    private static boolean bot0Allowed(ServerLevel level, BlockPos p) {
        return level.getFluidState(p).isEmpty();
    }

    /** A torch at the spot (on the ground there). */
    private final class PlaceTorch implements BotTask {
        private final BlockPos at;
        private int ticks;

        PlaceTorch(BlockPos at) {
            this.at = at;
        }

        @Override
        public Result tick(Bot bot) {
            if (++ticks > 40) return Result.FAILED;
            if (bot.body().getEyePosition().distanceTo(Vec3.atCenterOf(at)) > 4.3) return Result.FAILED;
            if (Work.torch(bot, at)) {
                placed++;
                return Result.DONE;
            }
            return ticks > 5 ? Result.FAILED : Result.RUNNING;
        }

        @Override
        public String describe() {
            return "put a torch at " + at.toShortString();
        }
    }

    @Override
    public void stepEnded(Companion c, Bot bot, BotTask step, boolean ok) {
        if (!ok && ++spotTicks > 20) fail("I can't get to the dark spots", "ich komme nicht an die dunklen Stellen");
    }

    @Override
    protected void save(CompoundTag t) {
        t.putLong("centre", centre.asLong());
        t.putInt("radius", radius);
    }

    @Override
    protected void load(CompoundTag t) {
        centre = BlockPos.of(t.getLongOr("centre", 0));
        radius = t.getIntOr("radius", 16);
    }
}
