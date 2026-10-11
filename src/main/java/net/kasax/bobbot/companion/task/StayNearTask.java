package net.kasax.bobbot.companion.task;

import net.kasax.bobbot.bot.Bot;
import net.kasax.bobbot.bot.BotNavigator;
import net.kasax.bobbot.bot.BotTask;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.levelgen.Heightmap;

import java.util.Set;
import java.util.UUID;

/**
 * Keeps near a player, as a tamed wolf does: walks after him past {@code distance} blocks, and
 * when left far behind or the player went to another dimension, appears beside him (if allowed).
 * Runs until the player is gone; ends at once when {@code once} and it has arrived.
 */
public final class StayNearTask implements BotTask {
    private final UUID player;
    private final String name;
    private final int distance;
    private final boolean teleport, once;
    private int repath;

    public StayNearTask(ServerPlayer player, int distance, boolean teleport, boolean once) {
        this.player = player.getUUID();
        this.name = player.getGameProfile().name();
        this.distance = distance;
        this.teleport = teleport;
        this.once = once;
    }

    @Override
    public Result tick(Bot bot) {
        ServerPlayer p = bot.server().getPlayerList().getPlayer(player);
        if (p == null || p.isSpectator()) return once ? Result.FAILED : Result.RUNNING;
        var body = bot.body();
        boolean elsewhere = p.level() != body.level();
        double d = elsewhere ? Double.MAX_VALUE : p.distanceTo(body);
        if (teleport && (elsewhere || d > 48) && !p.isFallFlying() && p.onGround()) {
            jumpTo(bot, p);
            return once ? Result.DONE : Result.RUNNING;
        }
        if (elsewhere) return once ? Result.FAILED : Result.RUNNING;
        if (d <= distance + 0.5) {
            bot.navigator().stop();
            body.stopInputs();
            body.lookAt(p.getEyePosition());
            return once ? Result.DONE : Result.RUNNING;
        }
        if (repath-- <= 0 || bot.navigator().status() != BotNavigator.Status.MOVING) {
            repath = 20;
            bot.navigator().goNear(p.blockPosition(), Math.max(1.5, distance - 0.5));
        }
        // (Far: at a run.)
        body.hurry = d > 12;
        bot.navigator().tick();
        return Result.RUNNING;
    }

    /** Beside the player on firm ground (where he stands, if nothing better). */
    static void jumpTo(Bot bot, ServerPlayer p) {
        ServerLevel level = (ServerLevel) p.level();
        BlockPos base = p.blockPosition();
        BlockPos spot = base;
        for (int[] o : new int[][]{{2, 0}, {-2, 0}, {0, 2}, {0, -2}, {1, 1}, {-1, -1}}) {
            BlockPos q = base.offset(o[0], 0, o[1]);
            BlockPos top = level.getHeightmapPos(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, q);
            if (Math.abs(top.getY() - base.getY()) <= 2 && level.getBlockState(top).isAir() && level.getBlockState(top.above()).isAir()
                    && !level.getBlockState(top.below()).getCollisionShape(level, top.below()).isEmpty()
                    && level.getFluidState(top.below()).isEmpty()) {
                spot = top;
                break;
            }
        }
        bot.navigator().stop();
        bot.body().teleportTo(level, spot.getX() + 0.5, spot.getY(), spot.getZ() + 0.5, Set.of(), p.getYRot(), 0f, true);
    }

    @Override
    public String describe() {
        return (once ? "come to " : "follow ") + name;
    }
}
