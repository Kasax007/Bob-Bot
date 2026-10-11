package net.kasax.bobbot.util;

import net.minecraft.commands.CommandSourceStack;
import net.minecraft.server.permissions.Permission;
import net.minecraft.server.permissions.Permissions;
import net.minecraft.world.entity.player.Player;

/** The mod's one definition of "this needs op" (26.2+ named permissions; gamemaster = the old level 2). */
public final class Perms {
    private Perms() {
    }

    public static final Permission OP = Permissions.COMMANDS_GAMEMASTER;

    public static boolean isOp(CommandSourceStack source) {
        return source.permissions().hasPermission(OP);
    }

    public static boolean isOp(Player player) {
        return player.permissions().hasPermission(OP);
    }
}
