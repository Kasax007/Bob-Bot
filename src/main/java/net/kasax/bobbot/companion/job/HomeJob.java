package net.kasax.bobbot.companion.job;

import net.kasax.bobbot.bot.Bot;
import net.kasax.bobbot.bot.BotTask;
import net.kasax.bobbot.bot.task.GoToTask;
import net.kasax.bobbot.companion.Companion;
import net.kasax.bobbot.companion.Lang;

/** Back home (the spot set with {@code /bob home set}). */
public final class HomeJob extends Job {
    private boolean sent;

    @Override
    public String kind() {
        return "home";
    }

    @Override
    public BotTask next(Companion c, Bot bot) {
        if (sent || c.home == null) return null;
        sent = true;
        if (c.home.dimension() != bot.body().level().dimension()) {
            fail("home is in another dimension", "mein Zuhause ist in einer anderen Dimension");
            return null;
        }
        return new GoToTask(c.home.pos(), 2);
    }

    @Override
    public String describe(Lang l) {
        return l.t("go home", "nach Hause gehen");
    }
}
