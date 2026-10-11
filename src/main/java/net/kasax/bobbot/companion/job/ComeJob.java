package net.kasax.bobbot.companion.job;

import net.kasax.bobbot.bot.Bot;
import net.kasax.bobbot.bot.BotTask;
import net.kasax.bobbot.companion.Companion;
import net.kasax.bobbot.companion.Lang;
import net.kasax.bobbot.companion.task.StayNearTask;
import net.minecraft.server.level.ServerPlayer;

/** To the owner, once. */
public final class ComeJob extends Job {
    private boolean sent;

    @Override
    public String kind() {
        return "come";
    }

    @Override
    public BotTask next(Companion c, Bot bot) {
        if (sent) return null;
        ServerPlayer owner = c.ownerPlayer(bot.server());
        if (owner == null) return null;
        sent = true;
        return new StayNearTask(owner, 2, c.settings.teleport, true);
    }

    @Override
    public String describe(Lang l) {
        return l.t("come to you", "zu dir kommen");
    }
}
