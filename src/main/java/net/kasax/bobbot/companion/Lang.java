package net.kasax.bobbot.companion;

import net.minecraft.server.level.ServerPlayer;

/**
 * Bob answers in the language his owner plays in: German for a German client, English for every
 * other. (The server knows each player's language from the client's settings; no resource pack or
 * client mod needed for that.)
 */
public enum Lang {
    EN, DE;

    public static Lang of(ServerPlayer player) {
        if (player == null) return EN;
        String l = player.clientInformation().language();
        return l != null && l.toLowerCase(java.util.Locale.ROOT).startsWith("de") ? DE : EN;
    }

    /** The English or the German text. */
    public String t(String en, String de) {
        return this == DE ? de : en;
    }
}
