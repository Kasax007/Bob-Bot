package net.kasax.bobbot.mixin;

import net.minecraft.world.entity.projectile.FishingHook;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** For Bob's fishing: whether a fish is on the hook right now (the moment to reel in). */
@Mixin(FishingHook.class)
public interface FishingHookAccessor {
    @Accessor("nibble")
    int bobbot$nibble();
}
