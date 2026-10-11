package net.kasax.bobbot.companion.job;

import net.kasax.bobbot.bot.Bot;
import net.kasax.bobbot.bot.BotTask;
import net.kasax.bobbot.companion.Companion;
import net.kasax.bobbot.companion.Lang;
import net.minecraft.nbt.CompoundTag;

/**
 * One thing the owner asked for ("fell trees", "mine at y -58", "build the house"), carried out as
 * a series of engine tasks: when one step has ended the job hands out the next, until it has none
 * left. Jobs are saved with Bob, so a restart does not lose what he was asked.
 */
public abstract class Job {
    /** The registry name, for saving (see {@link Jobs}). */
    public abstract String kind();

    /** The next step, or null when the job is done. Bob is idle when this is asked. */
    public abstract BotTask next(Companion c, Bot bot);

    /** What it is, in a few words ("fell trees", "Bäume fällen"). */
    public abstract String describe(Lang l);

    /** How far along, in a few words ("23/64 logs"); empty if it cannot say. */
    public String progress(Lang l) {
        return "";
    }

    /** A share done, 0..1, or -1 when it cannot tell. */
    public double fraction() {
        return -1;
    }

    /** Called once when the job becomes the current one. */
    public void started(Companion c, Bot bot) {
    }

    /** Called once when the job ends, done or stopped. */
    public void ended(Companion c, Bot bot, boolean done) {
    }

    /** A step handed out by {@link #next} came to an end. */
    public void stepEnded(Companion c, Bot bot, BotTask step, boolean ok) {
    }

    /** What it gathers goes into the drop-off chest (when the pack is full, and at the end). */
    public boolean delivers() {
        return false;
    }

    /** Runs until told to stop (following, guarding, farming round after round). */
    public boolean endless() {
        return false;
    }

    /** Why it stopped unfinished, if it did (for the owner). */
    public String trouble;

    protected void save(CompoundTag t) {
    }

    protected void load(CompoundTag t) {
    }

    public final CompoundTag toTag() {
        CompoundTag t = new CompoundTag();
        t.putString("kind", kind());
        save(t);
        return t;
    }
}
