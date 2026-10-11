package net.kasax.bobbot;

import net.fabricmc.api.ModInitializer;
import net.kasax.bobbot.bot.BotManager;
import net.kasax.bobbot.companion.CompanionManager;
import net.kasax.bobbot.control.BobCommands;
import net.kasax.bobbot.control.ChatControl;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Bob: a companion who plays like a player and does what he is told. */
public final class BobBot implements ModInitializer {
    public static final String MOD_ID = "bobbot";
    public static final Logger LOG = LoggerFactory.getLogger("Bob");

    @Override
    public void onInitialize() {
        // (The companions first: on shutdown they note where each Bob stands before the bots leave.)
        CompanionManager.register();
        BotManager.register();
        BobCommands.register();
        ChatControl.register();
        LOG.info("Bob is ready");
    }
}
