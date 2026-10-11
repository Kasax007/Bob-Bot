package net.kasax.bobbot.mixin;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import net.kasax.bobbot.protect.PlacedBlocks;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.context.BlockPlaceContext;
import org.spongepowered.asm.mixin.Mixin;

/** A block put down by a player (or by Bob) is marked as theirs: see {@link PlacedBlocks}. */
@Mixin(BlockItem.class)
public abstract class BlockItemMixin {
    @WrapMethod(method = "place")
    private InteractionResult bobbot$markPlaced(BlockPlaceContext context, Operation<InteractionResult> original) {
        Player p = context.getPlayer();
        if (p == null || p.level().isClientSide()) return original.call(context);
        return PlacedBlocks.placing(PlacedBlocks.byWhom(p), () -> original.call(context));
    }
}
