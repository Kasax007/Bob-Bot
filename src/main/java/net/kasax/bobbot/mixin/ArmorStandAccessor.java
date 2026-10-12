package net.kasax.bobbot.mixin;

import net.minecraft.world.entity.decoration.ArmorStand;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

/** A marker armour stand (no box to hit) to carry the line over Bob's head. */
@Mixin(ArmorStand.class)
public interface ArmorStandAccessor {
    @Invoker("setMarker")
    void bobbot$setMarker(boolean marker);
}
