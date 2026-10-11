package net.kasax.bobbot.companion.task;

import net.kasax.bobbot.bot.Bot;
import net.kasax.bobbot.bot.BotNavigator;
import net.kasax.bobbot.bot.BotTask;
import net.kasax.bobbot.world.Containers;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;

import java.util.function.Predicate;

/**
 * To a chest and everything but what he keeps (his tools, armour, some food and blocks) into it,
 * as a player empties his pockets. What does not fit stays with him.
 */
public final class DepositTask implements BotTask {
    private final BlockPos chest;
    private final Predicate<ItemStack> keep;
    private int ticks, repath, waited;
    /** Stacks put in, and whether the chest ran full. */
    public int stored;
    public boolean full, missing;

    public DepositTask(BlockPos chest, Predicate<ItemStack> keep) {
        this.chest = chest.immutable();
        this.keep = keep;
    }

    @Override
    public Result tick(Bot bot) {
        var body = bot.body();
        ServerLevel level = (ServerLevel) body.level();
        if (++ticks > 3600) return Result.FAILED;
        var container = Containers.at(level, chest);
        if (container == null) {
            missing = true;
            return Result.FAILED;
        }
        if (body.getEyePosition().distanceTo(net.minecraft.world.phys.Vec3.atCenterOf(chest)) > 4.0) {
            if (repath-- <= 0 || bot.navigator().status() != BotNavigator.Status.MOVING) {
                repath = 30;
                bot.navigator().goNear(chest, 2.5);
            }
            if (bot.navigator().tick() == BotNavigator.Status.FAILED && ticks > 600) return Result.FAILED;
            return Result.RUNNING;
        }
        bot.navigator().stop();
        body.lookAt(net.minecraft.world.phys.Vec3.atCenterOf(chest));
        // (A moment with the lid open, as a player takes to empty his pockets.)
        if (waited++ == 0) Containers.open(level, chest, true);
        if (waited < 8) return Result.RUNNING;
        var inv = body.getInventory();
        for (int i = 0; i < inv.getNonEquipmentItems().size(); i++) {
            ItemStack st = inv.getItem(i);
            if (st.isEmpty() || keep.test(st)) continue;
            ItemStack left = Containers.insert(container, st);
            if (left.getCount() < st.getCount()) stored++;
            inv.setItem(i, left);
            if (!left.isEmpty()) full = true;
        }
        Containers.open(level, chest, false);
        return Result.DONE;
    }

    @Override
    public String describe() {
        return "empty the pack into the chest at " + chest.toShortString();
    }
}
