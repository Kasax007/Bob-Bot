package net.kasax.bobbot.companion.job;

import net.kasax.bobbot.bot.Bot;
import net.kasax.bobbot.bot.BotTask;
import net.kasax.bobbot.bot.task.KillTask;
import net.kasax.bobbot.companion.task.IdleTask;
import net.kasax.bobbot.companion.Companion;
import net.kasax.bobbot.companion.Lang;
import net.kasax.bobbot.companion.Settings;
import net.kasax.bobbot.companion.task.StayNearTask;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.monster.Enemy;

/**
 * At the owner's side until told otherwise. Guarding: whatever comes for the owner is fought first
 * (and on aggressive, any monster close to him).
 */
public final class FollowJob extends Job {
    public boolean guard;

    public FollowJob() {
    }

    public FollowJob(boolean guard) {
        this.guard = guard;
    }

    @Override
    public String kind() {
        return "follow";
    }

    @Override
    public BotTask next(Companion c, Bot bot) {
        ServerPlayer owner = c.ownerPlayer(bot.server());
        if (owner == null) return new IdleTask(40, null);
        if (guard && c.settings.combat != Settings.Combat.PASSIVE && owner.level() == bot.body().level()) {
            Mob threat = threat(c, owner);
            if (threat != null) return new KillTask(threat).nearby(24);
        }
        // (A short stint each time, so a threat to the owner is looked at again every few seconds.)
        return new Stint(new StayNearTask(owner, c.settings.followDistance, c.settings.teleport, false), guard ? 60 : 400);
    }

    /** What comes for the owner (or, on aggressive, any monster near him), nearest first. */
    static Mob threat(Companion c, ServerPlayer owner) {
        boolean all = c.settings.combat == Settings.Combat.AGGRESSIVE;
        Mob best = null;
        double bestD = Double.MAX_VALUE;
        for (Mob m : owner.level().getEntitiesOfClass(Mob.class, owner.getBoundingBox().inflate(12),
                m -> m.isAlive() && m instanceof Enemy && (all || m.getTarget() == owner))) {
            double d = m.distanceToSqr(owner);
            if (d < bestD) {
                bestD = d;
                best = m;
            }
        }
        return best;
    }

    @Override
    public boolean endless() {
        return true;
    }

    @Override
    public String describe(Lang l) {
        return guard ? l.t("guard you", "dich beschützen") : l.t("follow you", "dir folgen");
    }

    @Override
    protected void save(CompoundTag t) {
        t.putBoolean("guard", guard);
    }

    @Override
    protected void load(CompoundTag t) {
        guard = t.getBooleanOr("guard", false);
    }

    /** Runs a task for at most so many ticks, then ends (done). */
    public static final class Stint implements BotTask {
        private final BotTask task;
        private int left;

        public Stint(BotTask task, int ticks) {
            this.task = task;
            this.left = ticks;
        }

        @Override
        public Result tick(Bot bot) {
            if (--left <= 0) {
                bot.navigator().stop();
                return Result.DONE;
            }
            return task.tick(bot);
        }

        @Override
        public BotTask inner() {
            return task;
        }

        @Override
        public String describe() {
            return task.describe();
        }
    }
}
