package net.kasax.bobbot.companion.task;

import net.kasax.bobbot.bot.Bot;
import net.kasax.bobbot.bot.BotTask;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;

/**
 * Standing about for a while, the way a player waits: still, now and then looking at whoever is
 * near (his owner first), not staring at a wall.
 */
public final class IdleTask implements BotTask {
    private int left;
    private final ServerPlayer owner;

    public IdleTask(int ticks, ServerPlayer owner) {
        this.left = ticks;
        this.owner = owner;
    }

    @Override
    public Result tick(Bot bot) {
        var body = bot.body();
        body.stopInputs();
        if (left % 20 == 0) {
            Player look = owner != null && owner.level() == body.level() && owner.distanceTo(body) < 12 ? owner
                    : body.level().getNearestPlayer(body.getX(), body.getY(), body.getZ(), 8, p -> p != body && !p.isSpectator());
            if (look != null) body.lookAt(look.getEyePosition());
        }
        return --left > 0 ? Result.RUNNING : Result.DONE;
    }

    @Override
    public String describe() {
        return "idle";
    }
}
