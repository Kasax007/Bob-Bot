package net.kasax.bobbot.companion.job;

import net.kasax.bobbot.bot.Bot;
import net.kasax.bobbot.bot.BotTask;
import net.kasax.bobbot.bot.plan.ObtainPlanner;
import net.kasax.bobbot.bot.task.FishTask;
import net.kasax.bobbot.bot.task.ObtainTask;
import net.kasax.bobbot.companion.Companion;
import net.kasax.bobbot.companion.Lang;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.Items;

import java.util.Set;

/** Fishes until so many catches (a rod made first if he has none). */
public final class FishJob extends Job {
    private int count, caught, fails;

    public FishJob() {
    }

    public FishJob(int count) {
        this.count = count;
    }

    @Override
    public String kind() {
        return "fish";
    }

    @Override
    public BotTask next(Companion c, Bot bot) {
        if (caught >= count || fails > 3) return null;
        if (ObtainPlanner.countAny(bot.body(), Set.of(Items.FISHING_ROD)) == 0) return new ObtainTask(Set.of(Items.FISHING_ROD), 1);
        return new FishTask();
    }

    @Override
    public void stepEnded(Companion c, Bot bot, BotTask step, boolean ok) {
        if (step instanceof FishTask) {
            if (ok) caught++;
            else fails++;
        }
    }

    @Override
    public boolean delivers() {
        return true;
    }

    @Override
    public String describe(Lang l) {
        return l.t("fish (" + count + " catches)", "angeln (" + count + " Fänge)");
    }

    @Override
    public String progress(Lang l) {
        return caught + "/" + count;
    }

    @Override
    public double fraction() {
        return count == 0 ? -1 : (double) caught / count;
    }

    @Override
    protected void save(CompoundTag t) {
        t.putInt("count", count);
        t.putInt("caught", caught);
    }

    @Override
    protected void load(CompoundTag t) {
        count = t.getIntOr("count", 1);
        caught = t.getIntOr("caught", 0);
    }
}
