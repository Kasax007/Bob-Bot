package net.kasax.bobbot.companion;

import net.fabricmc.fabric.api.event.lifecycle.v1.ServerEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerBossEvent;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.BossEvent;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.decoration.ArmorStand;

import java.util.HashMap;
import java.util.Map;

/**
 * What Bob is doing, shown: a line over his head for everyone (an invisible marker carrying the
 * text, any client shows it), and for his owner a bar at the top of the screen with the job and
 * how far along it is.
 */
public final class StatusShow {
    private StatusShow() {
    }

    private static final String TAG = "bobbot_status";
    private static final Map<Companion, ArmorStand> TAGS = new HashMap<>();
    private static final Map<Companion, ServerBossEvent> BARS = new HashMap<>();

    public static void register() {
        ServerTickEvents.END_SERVER_TICK.register(StatusShow::tick);
        // (A line left over from a crash: gone when its chunk loads.)
        ServerEntityEvents.ENTITY_LOAD.register((e, level) -> {
            if (e instanceof ArmorStand a && a.entityTags().contains(TAG) && !TAGS.containsValue(a)) a.discard();
        });
    }

    private static void tick(MinecraftServer server) {
        long now = server.overworld().getGameTime();
        for (Companion c : CompanionManager.all()) {
            boolean here = c.bot != null && c.bot.body() != null && c.bot.body().isAlive() && !c.bot.body().isRemoved();
            nameTag(c, here, now);
            if (now % 10 == 0) bar(server, c, here);
        }
        TAGS.entrySet().removeIf(e -> {
            if (CompanionManager.byName(e.getKey().name) == e.getKey()) return false;
            e.getValue().discard();
            return true;
        });
        BARS.entrySet().removeIf(e -> {
            if (CompanionManager.byName(e.getKey().name) == e.getKey()) return false;
            e.getValue().removeAllPlayers();
            return true;
        });
    }

    private static String line(Companion c, Lang l) {
        if (c.paused) return l.t("paused", "pausiert");
        if (c.current == null) return "";
        String p = c.current.progress(l);
        return c.current.describe(l) + (p.isEmpty() ? "" : " · " + p);
    }

    private static void nameTag(Companion c, boolean here, long now) {
        ArmorStand tag = TAGS.get(c);
        String text = here && c.settings.nameTag ? line(c, c.lang(c.bot.server())) : "";
        if (text.isEmpty()) {
            if (tag != null) {
                tag.discard();
                TAGS.remove(c);
            }
            return;
        }
        var body = c.bot.body();
        ServerLevel level = (ServerLevel) body.level();
        if (tag == null || tag.isRemoved() || tag.level() != level) {
            if (tag != null) tag.discard();
            tag = EntityTypes.ARMOR_STAND.create(level, EntitySpawnReason.COMMAND);
            if (tag == null) return;
            tag.setInvisible(true);
            tag.setNoGravity(true);
            ((net.kasax.bobbot.mixin.ArmorStandAccessor) tag).bobbot$setMarker(true);
            tag.setSilent(true);
            tag.setCustomNameVisible(true);
            tag.addTag(TAG);
            tag.snapTo(body.getX(), body.getY() + 2.15, body.getZ(), 0, 0);
            // (Known as his before it is in the world: the clean-up of leftovers must not take it.)
            TAGS.put(c, tag);
            level.addFreshEntity(tag);
        }
        if (now % 20 == 0 || tag.getCustomName() == null || !tag.getCustomName().getString().equals(text)) {
            tag.setCustomName(Component.literal(text).withStyle(ChatFormatting.GRAY));
        }
        tag.teleportTo(body.getX(), body.getY() + 2.15, body.getZ());
    }

    private static void bar(MinecraftServer server, Companion c, boolean here) {
        ServerPlayer owner = c.ownerPlayer(server);
        ServerBossEvent bar = BARS.get(c);
        boolean show = here && owner != null && c.settings.bossBar && c.current != null && !c.current.endless();
        if (!show) {
            if (bar != null) bar.removeAllPlayers();
            return;
        }
        Lang l = Lang.of(owner);
        if (bar == null) {
            bar = new ServerBossEvent(java.util.UUID.randomUUID(), Component.empty(), BossEvent.BossBarColor.YELLOW, BossEvent.BossBarOverlay.PROGRESS);
            BARS.put(c, bar);
        }
        double f = c.current.fraction();
        bar.setName(Component.literal(c.name + ": " + line(c, l)));
        bar.setProgress(f < 0 ? 1f : (float) Math.max(0, Math.min(1, f)));
        bar.setColor(f < 0 ? BossEvent.BossBarColor.BLUE : BossEvent.BossBarColor.YELLOW);
        if (!bar.getPlayers().contains(owner)) {
            bar.removeAllPlayers();
            bar.addPlayer(owner);
        }
    }

    /** Off when Bob leaves the world. */
    public static void gone(Companion c) {
        ArmorStand tag = TAGS.remove(c);
        if (tag != null) tag.discard();
        ServerBossEvent bar = BARS.remove(c);
        if (bar != null) bar.removeAllPlayers();
    }
}
