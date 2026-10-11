package net.kasax.bobbot.companion;

import net.kasax.bobbot.BobBot;
import net.kasax.bobbot.bot.Bot;
import net.kasax.bobbot.bot.BotBrain;
import net.kasax.bobbot.bot.BotTask;
import net.kasax.bobbot.bot.task.WaitTask;
import net.kasax.bobbot.companion.job.Job;
import net.kasax.bobbot.companion.task.StayNearTask;
import net.minecraft.server.level.ServerPlayer;

/**
 * Bob's mind as a companion: the job now, step by step, then the next one from the queue; with
 * nothing to do, at the owner's side (or where he is). The engine's reflexes - fight, flight, food,
 * air - keep running underneath whatever the job is.
 */
public final class CompanionBrain implements BotBrain {
    private final Companion c;
    /** The step of the current job that is on the stack. */
    private BotTask step;
    private long announcedAt;

    public CompanionBrain(Companion c) {
        this.c = c;
    }

    public Companion companion() {
        return c;
    }

    @Override
    public void think(Bot bot) {
        var server = bot.server();
        if (c.paused) {
            step = null;
            bot.doNow(new WaitTask(20));
            return;
        }
        if (Deliveries.needed(c, bot)) {
            step = null;
            bot.doNow(Deliveries.trip(c, bot));
            return;
        }
        if (c.current == null) {
            Job next = c.queue.pollFirst();
            if (next != null) {
                c.current = next;
                c.reportedAt = server.overworld().getGameTime();
                next.started(c, bot);
                if (c.settings.talk != Settings.Talk.QUIET && server.overworld().getGameTime() - announcedAt > 40) {
                    Lang l = c.lang(server);
                    c.say(server, "Now: " + next.describe(Lang.EN) + ".", "Jetzt: " + next.describe(Lang.DE) + ".");
                }
            }
        }
        if (c.current != null) {
            Job job = c.current;
            BotTask t;
            try {
                t = job.next(c, bot);
            } catch (RuntimeException e) {
                BobBot.LOG.warn("[Bob] {}: job {} failed", c.name, job.kind(), e);
                job.trouble = "something went wrong (" + e.getClass().getSimpleName() + ")";
                t = null;
            }
            if (t == null) {
                finish(bot, job);
                return;
            }
            step = t;
            bot.doNow(t);
            return;
        }
        idle(bot);
    }

    private void finish(Bot bot, Job job) {
        var server = bot.server();
        boolean done = job.trouble == null;
        job.ended(c, bot, done);
        c.current = null;
        step = null;
        announcedAt = server.overworld().getGameTime();
        if (job.endless() && done) return;
        if (done) {
            if (c.settings.talk != Settings.Talk.QUIET) {
                String p = job.progress(Lang.EN), pd = job.progress(Lang.DE);
                c.say(server, "Done: " + job.describe(Lang.EN) + (p.isEmpty() ? "" : " (" + p + ")") + ".",
                        "Fertig: " + job.describe(Lang.DE) + (pd.isEmpty() ? "" : " (" + pd + ")") + ".");
            }
            // What the job gathered goes into the drop-off chest now, not with the next job.
            if (job.delivers()) Deliveries.requestFinal(c);
        } else {
            c.say(server, "I stopped \"" + job.describe(Lang.EN) + "\": " + job.trouble + ".",
                    "Ich habe \"" + job.describe(Lang.DE) + "\" abgebrochen: " + job.trouble + ".");
        }
    }

    /** Nothing to do: by the owner (if he is about and wants that), else a look round where he is. */
    private void idle(Bot bot) {
        ServerPlayer owner = c.ownerPlayer(bot.server());
        if (c.settings.followWhenIdle && owner != null && !owner.isSpectator()) {
            step = null;
            bot.doNow(new net.kasax.bobbot.companion.job.FollowJob.Stint(
                    new StayNearTask(owner, c.settings.followDistance, c.settings.teleport, false), 100));
            return;
        }
        step = null;
        bot.doNow(new WaitTask(40));
    }

    @Override
    public void finished(Bot bot, BotTask task, boolean success) {
        if (task == step && c.current != null) {
            try {
                c.current.stepEnded(c, bot, task, success);
            } catch (RuntimeException e) {
                BobBot.LOG.warn("[Bob] {}: step end of {} failed", c.name, c.current.kind(), e);
            }
        }
    }

    @Override
    public void tick(Bot bot) {
        // Progress now and then, for those who want it.
        var server = bot.server();
        long now = server.overworld().getGameTime();
        if (c.settings.talk == Settings.Talk.CHATTY && c.current != null && !c.current.endless() && now - c.reportedAt > 1200) {
            c.reportedAt = now;
            String p = c.current.progress(Lang.EN), pd = c.current.progress(Lang.DE);
            if (!p.isEmpty()) c.say(server, "Still at it: " + c.current.describe(Lang.EN) + " (" + p + ").",
                    "Bin noch dran: " + c.current.describe(Lang.DE) + " (" + pd + ").");
        }
    }

    @Override
    public void respawned(Bot bot) {
        var server = bot.server();
        var d = bot.lastDeath;
        String at = d == null ? "" : " (" + d.pos().toShortString() + ")";
        c.say(server, "I died" + at + " - I'm back on my feet.", "Ich bin gestorben" + at + " - ich bin wieder auf den Beinen.");
        step = null;
    }
}
