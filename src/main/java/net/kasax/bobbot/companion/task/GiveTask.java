package net.kasax.bobbot.companion.task;

import net.kasax.bobbot.bot.Bot;
import net.kasax.bobbot.bot.BotNavigator;
import net.kasax.bobbot.bot.BotTask;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;

import java.util.UUID;
import java.util.function.Predicate;

/**
 * Brings things to a player: walks up to him and hands over what matches (into his pack; what does
 * not fit is dropped at his feet), at most {@code count} items.
 */
public final class GiveTask implements BotTask {
    private final UUID player;
    private final Predicate<ItemStack> what;
    private final int count;
    private int ticks, repath;
    public int given;

    public GiveTask(ServerPlayer player, Predicate<ItemStack> what, int count) {
        this.player = player.getUUID();
        this.what = what;
        this.count = count;
    }

    @Override
    public Result tick(Bot bot) {
        ServerPlayer p = bot.server().getPlayerList().getPlayer(player);
        if (p == null || ++ticks > 2400) return Result.FAILED;
        var body = bot.body();
        if (p.level() != body.level() || p.distanceTo(body) > 48) StayNearTask.jumpTo(bot, p);
        if (p.distanceTo(body) > 3) {
            if (repath-- <= 0 || bot.navigator().status() != BotNavigator.Status.MOVING) {
                repath = 20;
                bot.navigator().goNear(p.blockPosition(), 2);
            }
            bot.navigator().tick();
            return Result.RUNNING;
        }
        bot.navigator().stop();
        body.lookAt(p.getEyePosition());
        var inv = body.getInventory();
        for (int i = 0; i < inv.getContainerSize() && given < count; i++) {
            ItemStack st = inv.getItem(i);
            if (st.isEmpty() || !what.test(st)) continue;
            int n = Math.min(st.getCount(), count - given);
            ItemStack out = st.split(n);
            given += n;
            if (!p.getInventory().add(out)) {
                var drop = new net.minecraft.world.entity.item.ItemEntity(p.level(), p.getX(), p.getY() + 0.5, p.getZ(), out);
                drop.setPickUpDelay(0);
                p.level().addFreshEntity(drop);
            }
        }
        body.swing(net.minecraft.world.InteractionHand.MAIN_HAND, net.minecraft.world.item.component.SwingAnimation.DEFAULT, true);
        return Result.DONE;
    }

    @Override
    public String describe() {
        return "hand things over";
    }
}
