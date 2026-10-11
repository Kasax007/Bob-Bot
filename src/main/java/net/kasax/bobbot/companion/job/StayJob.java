package net.kasax.bobbot.companion.job;

import net.kasax.bobbot.bot.Bot;
import net.kasax.bobbot.bot.BotTask;
import net.kasax.bobbot.bot.task.GoToTask;
import net.kasax.bobbot.bot.task.WaitTask;
import net.kasax.bobbot.companion.Companion;
import net.kasax.bobbot.companion.Lang;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;

/** Waits at a spot (and goes back to it if pushed or drawn off by a fight) until told otherwise. */
public final class StayJob extends Job {
    private BlockPos at;

    public StayJob() {
    }

    public StayJob(BlockPos at) {
        this.at = at.immutable();
    }

    @Override
    public String kind() {
        return "stay";
    }

    @Override
    public BotTask next(Companion c, Bot bot) {
        if (at == null) at = bot.body().blockPosition();
        if (bot.body().blockPosition().distSqr(at) > 4) return new GoToTask(at, 1);
        return new WaitTask(40);
    }

    @Override
    public boolean endless() {
        return true;
    }

    @Override
    public String describe(Lang l) {
        return l.t("wait here", "hier warten");
    }

    @Override
    protected void save(CompoundTag t) {
        if (at != null) t.putLong("at", at.asLong());
    }

    @Override
    protected void load(CompoundTag t) {
        t.getLong("at").ifPresent(v -> at = BlockPos.of(v));
    }
}
