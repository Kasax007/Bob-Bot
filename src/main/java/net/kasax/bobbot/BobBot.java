package net.kasax.bobbot;

import net.fabricmc.api.ModInitializer;
import net.kasax.bobbot.bot.BotManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Bob: a companion who plays like a player and does what he is told. */
public final class BobBot implements ModInitializer {
    public static final String MOD_ID = "bobbot";
    public static final Logger LOG = LoggerFactory.getLogger("Bob");

    @Override
    public void onInitialize() {
        BotManager.register();
        LOG.info("Bob is ready");
    }
}
