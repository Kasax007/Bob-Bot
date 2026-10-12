package net.kasax.bobbot.mixin;

import net.kasax.bobbot.bot.BotPlayer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.players.SleepStatus;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

import java.util.List;

/** Bob does not keep the night from passing: when the players sleep, the night is over, Bob or no Bob. */
@Mixin(SleepStatus.class)
public abstract class SleepIgnoresBobMixin {
    @ModifyVariable(method = "update", at = @At("HEAD"), argsOnly = true)
    private List<ServerPlayer> bobbot$withoutBob(List<ServerPlayer> players) {
        return without(players);
    }

    @ModifyVariable(method = "areEnoughDeepSleeping", at = @At("HEAD"), argsOnly = true)
    private List<ServerPlayer> bobbot$deepWithoutBob(List<ServerPlayer> players) {
        return without(players);
    }

    private static List<ServerPlayer> without(List<ServerPlayer> players) {
        boolean any = false;
        for (ServerPlayer p : players) if (p instanceof BotPlayer) any = true;
        if (!any) return players;
        return players.stream().filter(p -> !(p instanceof BotPlayer)).toList();
    }
}
