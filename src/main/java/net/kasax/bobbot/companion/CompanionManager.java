package net.kasax.bobbot.companion;

import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.kasax.bobbot.BobBot;
import net.kasax.bobbot.bot.Bot;
import net.kasax.bobbot.bot.BotManager;
import net.kasax.bobbot.bot.BotPlayer;
import net.minecraft.core.GlobalPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

/**
 * All Bobs of the server: made for a player, called into the world when the owner comes, sent away
 * when he goes (unless there is work), and saved with the world.
 */
public final class CompanionManager {
    private CompanionManager() {
    }

    private static final Map<String, Companion> BY_NAME = new LinkedHashMap<>();
    /** How many Bobs one player may have. */
    public static int maxPerPlayer = 3;

    public static Collection<Companion> all() {
        return BY_NAME.values();
    }

    public static void register() {
        ServerLifecycleEvents.SERVER_STARTED.register(CompanionManager::load);
        ServerLifecycleEvents.BEFORE_SAVE.register((server, flush, force) -> save(server));
        ServerLifecycleEvents.SERVER_STOPPING.register(server -> {
            save(server);
            for (Companion c : all()) c.bot = null;
        });
        ServerLifecycleEvents.SERVER_STOPPED.register(server -> BY_NAME.clear());
        ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> joined(server, handler.player));
        ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> left(server, handler.player));
        ServerTickEvents.END_SERVER_TICK.register(CompanionManager::tick);
    }

    private static void load(MinecraftServer server) {
        BY_NAME.clear();
        var list = BobSavedData.get(server).loaded();
        for (int i = 0; i < list.size(); i++) {
            Companion c = Companion.load(list.getCompoundOrEmpty(i));
            BY_NAME.put(c.name.toLowerCase(Locale.ROOT), c);
        }
        if (!BY_NAME.isEmpty()) BobBot.LOG.info("[Bob] {} companion(s) known", BY_NAME.size());
    }

    private static void save(MinecraftServer server) {
        for (Companion c : all()) {
            if (c.bot != null && c.bot.body() != null && c.bot.body().isAlive())
                c.lastPos = GlobalPos.of(c.bot.body().level().dimension(), c.bot.body().blockPosition());
        }
        BobSavedData.get(server).touch();
    }

    public static Companion byName(String name) {
        return name == null ? null : BY_NAME.get(name.toLowerCase(Locale.ROOT));
    }

    public static List<Companion> ofOwner(UUID owner) {
        List<Companion> out = new ArrayList<>();
        for (Companion c : all()) if (owner.equals(c.owner)) out.add(c);
        return out;
    }

    /** The owner's Bob that "Bob, ..." means: the first one he made. */
    public static Companion primary(UUID owner) {
        for (Companion c : all()) if (owner.equals(c.owner)) return c;
        return null;
    }

    public static Companion of(Bot bot) {
        return bot.brain() instanceof CompanionBrain b ? b.companion() : null;
    }

    public static Companion of(ServerPlayer body) {
        if (!(body instanceof BotPlayer)) return null;
        return byName(body.getGameProfile().name());
    }

    /** Why this name will not do, or null if it is free for this owner. */
    public static String nameProblem(MinecraftServer server, UUID owner, String name) {
        if (!name.matches("[A-Za-z0-9_]{3,16}")) return "3 to 16 letters, digits or _";
        Companion c = byName(name);
        if (c != null && !owner.equals(c.owner)) return "taken";
        ServerPlayer p = server.getPlayerList().getPlayerByName(name);
        if (p != null && !(p instanceof BotPlayer)) return "a player of that name is online";
        if (c == null && ofOwner(owner).size() >= maxPerPlayer) return "you already have " + maxPerPlayer;
        return null;
    }

    /** A new Bob for the player (not yet in the world). */
    public static Companion create(ServerPlayer owner, String name) {
        Companion c = new Companion(name, owner.getUUID(), owner.getGameProfile().name());
        BY_NAME.put(name.toLowerCase(Locale.ROOT), c);
        BobSavedData.get(owner.level().getServer()).touch();
        return c;
    }

    /** Bob into the world at {@code pos} (or where he was last) with his mind. */
    public static void summon(MinecraftServer server, Companion c, ServerLevel level, Vec3 pos) {
        if (c.bot != null && c.bot.body() != null && !c.bot.body().isRemoved()) return;
        Bot bot = BotManager.spawn(server, c.name, level, pos);
        bot.setBrain(new CompanionBrain(c));
        // (Somebody's house, a chest, a village: never his to break, whatever the job.)
        bot.setGuard(new net.kasax.bobbot.protect.CompanionGuard(c));
        // (His every step in the log only while his owner listens in: /bob debug.)
        bot.verbose = c.owner != null && BotManager.DEBUG_LISTENERS.contains(c.owner);
        c.bot = bot;
        BobBot.LOG.info("[Bob] {} (of {}) is here at {}", c.name, c.ownerName, pos);
    }

    /** Bob out of the world (everything he carries stays his - the player data keeps it). */
    public static void dismiss(MinecraftServer server, Companion c) {
        if (c.bot == null) return;
        if (c.bot.body() != null) c.lastPos = GlobalPos.of(c.bot.body().level().dimension(), c.bot.body().blockPosition());
        BotManager.remove(server, c.bot);
        c.bot = null;
        BobSavedData.get(server).touch();
    }

    /** For good: out of the world and forgotten. */
    public static void delete(MinecraftServer server, Companion c) {
        dismiss(server, c);
        BY_NAME.remove(c.name.toLowerCase(Locale.ROOT));
        BobSavedData.get(server).touch();
    }

    private static void joined(MinecraftServer server, ServerPlayer player) {
        if (player instanceof BotPlayer) return;
        for (Companion c : ofOwner(player.getUUID())) {
            c.ownerName = player.getGameProfile().name();
            if (c.bot != null) continue;
            server.execute(() -> comeBack(server, c, player));
        }
    }

    /** Back where he was (or beside the owner if that is gone). */
    static void comeBack(MinecraftServer server, Companion c, ServerPlayer owner) {
        ServerLevel level = owner.level();
        Vec3 pos = owner.position();
        if (c.lastPos != null) {
            ServerLevel l = server.getLevel(c.lastPos.dimension());
            if (l != null) {
                level = l;
                pos = Vec3.atBottomCenterOf(c.lastPos.pos());
            }
        }
        summon(server, c, level, pos);
    }

    private static void left(MinecraftServer server, ServerPlayer player) {
        if (player instanceof BotPlayer) return;
        for (Companion c : ofOwner(player.getUUID())) {
            boolean working = c.current != null && !c.current.endless() || !c.queue.isEmpty();
            if (c.bot != null && !(c.settings.workWhileAway && working)) dismiss(server, c);
        }
    }

    private static void tick(MinecraftServer server) {
        if (BY_NAME.isEmpty()) return;
        long now = server.overworld().getGameTime();
        for (Companion c : all()) {
            if (c.bot == null) continue;
            // (Kicked, or gone some other way: the body is no more.)
            if (server.getPlayerList().getPlayer(c.bot.id) == null) {
                c.bot = null;
                continue;
            }
            // Done with the work while the owner is away: off home for the night.
            if (now % 200 == 0 && c.ownerPlayer(server) == null && c.current == null && c.queue.isEmpty()) dismiss(server, c);
        }
        if (now % 600 == 0) BobSavedData.get(server).touch();
    }
}
