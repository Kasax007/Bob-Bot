package net.kasax.bobbot.control;

import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerPlayer;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Outlines in the air, for one player: the box he just marked, the area Bob works in. Drawn with
 * particles every half second for a few seconds - works in any client.
 */
public final class Show {
    private Show() {
    }

    private record Outline(UUID player, BlockPos min, BlockPos max, ParticleOptions particle, long until) {
    }

    private static final List<Outline> OUTLINES = new ArrayList<>();
    private static boolean registered;

    private static void register() {
        if (registered) return;
        registered = true;
        ServerTickEvents.END_SERVER_TICK.register(server -> {
            if (OUTLINES.isEmpty()) return;
            long now = server.overworld().getGameTime();
            OUTLINES.removeIf(o -> o.until() < now);
            if (now % 10 != 0) return;
            for (Outline o : OUTLINES) {
                ServerPlayer p = server.getPlayerList().getPlayer(o.player());
                if (p != null) draw(p, o);
            }
        });
    }

    /** The box's edges (only those within 48 blocks of the player; a tall box at his height). */
    public static void box(ServerPlayer p, BlockPos a, BlockPos b, int seconds, boolean protect) {
        register();
        BlockPos min = BlockPos.min(a, b), max = BlockPos.max(a, b);
        OUTLINES.removeIf(o -> o.player().equals(p.getUUID()) && o.min().equals(min) && o.max().equals(max));
        OUTLINES.add(new Outline(p.getUUID(), min, max, protect ? ParticleTypes.WAX_ON : ParticleTypes.HAPPY_VILLAGER,
                p.level().getGameTime() + seconds * 20L));
    }

    private static void draw(ServerPlayer p, Outline o) {
        var level = p.level();
        // (A box from the bottom of the world to the top: drawn round the player's feet and head.)
        int y0 = Math.max(o.min().getY(), p.getBlockY() - 8), y1 = Math.min(o.max().getY() + 1, p.getBlockY() + 10);
        double x0 = o.min().getX(), x1 = o.max().getX() + 1, z0 = o.min().getZ(), z1 = o.max().getZ() + 1;
        int n = 0;
        for (double y : new double[]{y0, y1}) {
            for (double x = x0; x <= x1; x += 1) {
                n += dot(p, level, o.particle(), x, y, z0) + dot(p, level, o.particle(), x, y, z1);
            }
            for (double z = z0; z <= z1; z += 1) {
                n += dot(p, level, o.particle(), x0, y, z) + dot(p, level, o.particle(), x1, y, z);
            }
            if (n > 1500) return;
        }
        for (double x : new double[]{x0, x1}) {
            for (double z : new double[]{z0, z1}) {
                for (double y = y0; y <= y1; y += 1) dot(p, level, o.particle(), x, y, z);
            }
        }
    }

    private static int dot(ServerPlayer p, net.minecraft.server.level.ServerLevel level, ParticleOptions particle, double x, double y, double z) {
        double dx = x - p.getX(), dz = z - p.getZ();
        if (dx * dx + dz * dz > 48 * 48) return 0;
        level.sendParticles(p, particle, true, false, x, y, z, 1, 0, 0, 0, 0);
        return 1;
    }
}
