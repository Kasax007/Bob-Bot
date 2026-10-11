package net.kasax.bobbot.control;

import net.kasax.bobbot.companion.Companion;
import net.minecraft.server.level.ServerPlayer;

/** A line of chat for Bob straight to the parser (for the tests, and for the screen's command line). */
public final class ChatControlAccess {
    private ChatControlAccess() {
    }

    public static void handle(ServerPlayer p, Companion c, String text) {
        ChatControl.handle(p, c, text);
    }
}
