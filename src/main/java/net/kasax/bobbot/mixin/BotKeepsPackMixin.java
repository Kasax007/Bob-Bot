package net.kasax.bobbot.mixin;

import net.kasax.bobbot.bot.BotPlayer;
import net.kasax.bobbot.companion.Companion;
import net.kasax.bobbot.companion.CompanionManager;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Bob keeps what he carries when he dies (his setting "keepInventory"): nothing is dropped, and no
 * experience either (he keeps that too; dropped as well, it would be there twice).
 */
@Mixin(Player.class)
public abstract class BotKeepsPackMixin {
    @Inject(method = "dropEquipment", at = @At("HEAD"), cancellable = true)
    private void bobbot$keepPack(ServerLevel level, CallbackInfo ci) {
        if (!((Object) this instanceof BotPlayer body)) return;
        Companion c = CompanionManager.of(body);
        if (c != null && c.settings.keepInventory) ci.cancel();
    }

    @Inject(method = "getBaseExperienceReward", at = @At("HEAD"), cancellable = true)
    private void bobbot$keepExperience(ServerLevel level, CallbackInfoReturnable<Integer> cir) {
        if (!((Object) this instanceof BotPlayer body)) return;
        Companion c = CompanionManager.of(body);
        if (c != null && c.settings.keepInventory) cir.setReturnValue(0);
    }
}
