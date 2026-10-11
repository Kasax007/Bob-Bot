package net.kasax.bobbot.companion;

import net.kasax.bobbot.bot.Bot;
import net.kasax.bobbot.companion.job.Job;
import net.kasax.bobbot.companion.job.Jobs;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * One Bob: whose he is, what he was asked to do (the job now and the queue after it), the chests
 * and areas his owner gave him, and how he goes about things. The body that does the work is the
 * engine's {@link Bot}; it is there while Bob is in the world, null while he is away.
 */
public final class Companion {
    public final String name;
    public UUID owner;
    public String ownerName;
    public final Settings settings = new Settings();
    /** Jobs waiting, first one next. */
    public final Deque<Job> queue = new ArrayDeque<>();
    public Job current;
    public boolean paused;
    /** Where he goes back to (and respawns, with a bed there). */
    public GlobalPos home;
    public final List<RoleChest> chests = new ArrayList<>();
    public final Map<String, Area> areas = new LinkedHashMap<>();
    /** Where he was when last saved (he comes back there). */
    public GlobalPos lastPos;
    /** What he said lately, newest last (for the screen). */
    public final Deque<String> log = new ArrayDeque<>();

    /** The engine's bot while he is in the world. */
    public transient Bot bot;
    /** The wand's two corners (set with left and right click). */
    public transient GlobalPos corner1, corner2;
    /** When the current job last reported progress (game time). */
    public transient long reportedAt;

    public Companion(String name, UUID owner, String ownerName) {
        this.name = name;
        this.owner = owner;
        this.ownerName = ownerName;
    }

    public ServerPlayer ownerPlayer(MinecraftServer server) {
        return owner == null ? null : server.getPlayerList().getPlayer(owner);
    }

    public Lang lang(MinecraftServer server) {
        return Lang.of(ownerPlayer(server));
    }

    /** "&lt;Bob&gt; text" to the owner (if online), in his language; kept in the log either way. */
    public void say(MinecraftServer server, String en, String de) {
        Lang l = lang(server);
        String text = l.t(en, de);
        log.addLast(text);
        while (log.size() > 50) log.removeFirst();
        ServerPlayer p = ownerPlayer(server);
        if (p != null) p.sendSystemMessage(line(Component.literal(text)));
    }

    /** A line from Bob with anything in it (clickable buttons, say). */
    public void tell(MinecraftServer server, Component text) {
        log.addLast(text.getString());
        while (log.size() > 50) log.removeFirst();
        ServerPlayer p = ownerPlayer(server);
        if (p != null) p.sendSystemMessage(line(text));
    }

    /** Bob's name the way chat shows a speaker, then the text. */
    public MutableComponent line(Component text) {
        return Component.literal("<").append(Component.literal(name).withStyle(ChatFormatting.GOLD)).append("> ").append(text);
    }

    public List<RoleChest> chests(RoleChest.Role role) {
        List<RoleChest> out = new ArrayList<>();
        for (RoleChest c : chests) if (c.role() == role) out.add(c);
        return out;
    }

    /** The chest of this role nearest to {@code at} in the same dimension, or null. */
    public RoleChest nearestChest(RoleChest.Role role, ResourceKey<Level> dim, BlockPos at) {
        RoleChest best = null;
        double bestD = Double.MAX_VALUE;
        for (RoleChest c : chests) {
            if (c.role() != role || c.pos().dimension() != dim) continue;
            double d = c.block().distSqr(at);
            if (d < bestD) {
                bestD = d;
                best = c;
            }
        }
        return best;
    }

    public boolean protectedAt(ResourceKey<Level> dim, BlockPos p) {
        for (Area a : areas.values()) if (a.kind() == Area.Kind.PROTECTED && a.contains(dim, p)) return true;
        return false;
    }

    /** Queued behind what is going on. */
    public void enqueue(Job job) {
        queue.addLast(job);
    }

    /** What he is doing now is put back at the head of the queue (unless endless) and this starts. */
    public void now(Job job) {
        if (current != null && !current.endless() && bot != null) {
            current.ended(this, bot, false);
            queue.addFirst(current);
        } else if (current != null && bot != null) current.ended(this, bot, false);
        current = null;
        queue.addFirst(job);
        paused = false;
        if (bot != null) bot.clearTasks();
    }

    /** Everything dropped: the job now and the queue. */
    public void stopAll() {
        if (current != null && bot != null) current.ended(this, bot, false);
        current = null;
        queue.clear();
        if (bot != null) bot.clearTasks();
    }

    /** The job now ended; the next in the queue comes. */
    public void skip() {
        if (current != null && bot != null) current.ended(this, bot, false);
        current = null;
        if (bot != null) bot.clearTasks();
    }

    public CompoundTag save() {
        CompoundTag t = new CompoundTag();
        t.putString("name", name);
        if (owner != null) t.putString("owner", owner.toString());
        if (ownerName != null) t.putString("ownerName", ownerName);
        t.put("settings", settings.save());
        ListTag jobs = new ListTag();
        if (current != null) jobs.add(current.toTag());
        for (Job j : queue) jobs.add(j.toTag());
        t.put("jobs", jobs);
        t.putBoolean("paused", paused);
        if (home != null) t.put("home", place(home));
        if (lastPos != null) t.put("lastPos", place(lastPos));
        ListTag cs = new ListTag();
        for (RoleChest c : chests) cs.add(c.save());
        t.put("chests", cs);
        ListTag as = new ListTag();
        for (Area a : areas.values()) as.add(a.save());
        t.put("areas", as);
        return t;
    }

    public static Companion load(CompoundTag t) {
        UUID owner = null;
        try {
            owner = UUID.fromString(t.getStringOr("owner", ""));
        } catch (IllegalArgumentException ignored) {
        }
        Companion c = new Companion(t.getStringOr("name", "Bob"), owner, t.getStringOr("ownerName", "?"));
        c.settings.load(t.getCompoundOrEmpty("settings"));
        for (int i = 0; i < t.getListOrEmpty("jobs").size(); i++) {
            Job j = Jobs.load(t.getListOrEmpty("jobs").getCompoundOrEmpty(i));
            if (j != null) c.queue.addLast(j);
        }
        c.paused = t.getBooleanOr("paused", false);
        c.home = t.getCompound("home").map(Companion::place).orElse(null);
        c.lastPos = t.getCompound("lastPos").map(Companion::place).orElse(null);
        var cs = t.getListOrEmpty("chests");
        for (int i = 0; i < cs.size(); i++) c.chests.add(RoleChest.load(cs.getCompoundOrEmpty(i)));
        var as = t.getListOrEmpty("areas");
        for (int i = 0; i < as.size(); i++) {
            Area a = Area.load(as.getCompoundOrEmpty(i));
            c.areas.put(a.name(), a);
        }
        return c;
    }

    static CompoundTag place(GlobalPos p) {
        CompoundTag t = new CompoundTag();
        t.putString("dim", p.dimension().identifier().toString());
        t.putLong("pos", p.pos().asLong());
        return t;
    }

    static GlobalPos place(CompoundTag t) {
        var dim = ResourceKey.create(Registries.DIMENSION, Identifier.parse(t.getStringOr("dim", "minecraft:overworld")));
        return GlobalPos.of(dim, BlockPos.of(t.getLongOr("pos", 0)));
    }
}
