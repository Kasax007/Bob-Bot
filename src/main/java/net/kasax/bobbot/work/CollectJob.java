package net.kasax.bobbot.work;

import net.kasax.bobbot.bot.Bot;
import net.kasax.bobbot.bot.BotTask;
import net.kasax.bobbot.bot.task.CollectDropsTask;
import net.kasax.bobbot.companion.Companion;
import net.kasax.bobbot.companion.Lang;
import net.kasax.bobbot.companion.job.Job;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;

/** "Pick that up": everything lying about round him picked up, into the drop-off chest after. */
public final class CollectJob extends Job {
    private BlockPos centre;
    private int radius = 16;
    private boolean done;

    public CollectJob() {
    }

    public CollectJob(BlockPos centre, int radius) {
        this.centre = centre.immutable();
        this.radius = radius;
    }

    @Override
    public String kind() {
        return "collect";
    }

    @Override
    public String describe(Lang l) {
        return l.t("pick up what lies about", "aufsammeln, was herumliegt");
    }

    @Override
    public boolean delivers() {
        return true;
    }

    @Override
    public BotTask next(Companion c, Bot bot) {
        if (done) return null;
        done = true;
        return new CollectDropsTask(centre, radius, 20 * 90);
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
