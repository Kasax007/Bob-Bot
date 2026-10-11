package net.kasax.bobbot.control;

import net.kasax.bobbot.bot.BotManager;
import net.kasax.bobbot.companion.Companion;
import net.kasax.bobbot.companion.CompanionManager;
import net.kasax.bobbot.companion.Lang;
import net.kasax.bobbot.companion.RoleChest;
import net.kasax.bobbot.companion.Settings;
import net.kasax.bobbot.companion.job.ComeJob;
import net.kasax.bobbot.companion.job.FishJob;
import net.kasax.bobbot.companion.job.FollowJob;
import net.kasax.bobbot.companion.job.GatherJob;
import net.kasax.bobbot.companion.job.HomeJob;
import net.kasax.bobbot.companion.job.HuntJob;
import net.kasax.bobbot.companion.job.Job;
import net.kasax.bobbot.companion.job.StayJob;
import net.kasax.bobbot.world.Containers;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * What the owner can tell Bob to do - the same whether it comes as a command, a click in a menu,
 * a line in chat or a button in Bob's screen. Each answers in Bob's voice.
 */
public final class Orders {
    private Orders() {
    }

    /** Which of his Bobs a player's orders go to (the first one, unless he picked another). */
    private static final Map<UUID, String> SELECTED = new HashMap<>();

    public static void select(ServerPlayer p, Companion c) {
        SELECTED.put(p.getUUID(), c.name);
    }

    /** The player's Bob his orders go to, or null (and he is told how to get one). */
    public static Companion target(ServerPlayer p, boolean complain) {
        Companion c = CompanionManager.byName(SELECTED.get(p.getUUID()));
        if (c == null || !p.getUUID().equals(c.owner)) c = CompanionManager.primary(p.getUUID());
        if (c == null && complain) {
            Lang l = Lang.of(p);
            p.sendSystemMessage(Component.literal(l.t("You have no Bob yet. ", "Du hast noch keinen Bob. "))
                    .append(Menus.button(l.t("Call Bob", "Bob rufen"), "/bob spawn", l.t("Bob joins you", "Bob kommt zu dir"))));
        }
        return c;
    }

    /** Bob in the world (called to the owner if he is away). */
    public static boolean present(ServerPlayer p, Companion c) {
        if (c.bot != null) return true;
        CompanionManager.summon(p.level().getServer(), c, p.level(), p.position());
        return c.bot != null;
    }

    private static void ok(ServerPlayer p, Companion c, String en, String de) {
        c.say(p.level().getServer(), en, de);
    }

    /** A job for Bob: at once if he is idle (or only following), else after what he is doing. */
    public static void give(ServerPlayer p, Companion c, Job job, boolean now) {
        if (!present(p, c)) return;
        var server = p.level().getServer();
        Lang l = Lang.of(p);
        boolean idle = c.current == null || c.current.endless();
        if (now || idle) {
            c.now(job);
            ok(p, c, "Okay, I'll " + job.describe(Lang.EN) + ".", "Okay, ich werde " + job.describe(Lang.DE) + ".");
        } else {
            c.enqueue(job);
            int n = c.queue.size();
            c.tell(server, Component.literal(l.t("Okay, after \"" + c.current.describe(l) + "\" (" + n + ". in the queue). ",
                    "Okay, nach \"" + c.current.describe(l) + "\" (" + n + ". in der Warteschlange). "))
                    .append(Menus.button(l.t("Now", "Jetzt"), "/bob queue now " + n, l.t("Do it now instead", "Stattdessen jetzt"))));
        }
    }

    public static void come(ServerPlayer p, Companion c) {
        give(p, c, new ComeJob(), true);
    }

    public static void follow(ServerPlayer p, Companion c, boolean guard) {
        give(p, c, new FollowJob(guard), true);
    }

    public static void stay(ServerPlayer p, Companion c) {
        if (!present(p, c)) return;
        give(p, c, new StayJob(c.bot.body().blockPosition()), true);
    }

    public static void home(ServerPlayer p, Companion c) {
        if (c.home == null) {
            Lang l = Lang.of(p);
            c.tell(p.level().getServer(), Component.literal(l.t("I have no home yet. ", "Ich habe noch kein Zuhause. "))
                    .append(Menus.button(l.t("Here", "Hier"), "/bob home set", l.t("Your position becomes his home", "Deine Position wird sein Zuhause"))));
            return;
        }
        give(p, c, new HomeJob(), true);
    }

    public static void setHome(ServerPlayer p, Companion c) {
        c.home = GlobalPos.of(p.level().dimension(), p.blockPosition());
        ok(p, c, "My home is here now: " + p.blockPosition().toShortString() + ".", "Mein Zuhause ist jetzt hier: " + p.blockPosition().toShortString() + ".");
    }

    public static void stop(ServerPlayer p, Companion c) {
        c.stopAll();
        c.paused = false;
        ok(p, c, "Stopped. I'll wait for orders.", "Gestoppt. Ich warte auf Befehle.");
    }

    public static void pause(ServerPlayer p, Companion c, boolean pause) {
        c.paused = pause;
        if (c.bot != null) c.bot.clearTasks();
        if (pause) ok(p, c, "Paused.", "Pause.");
        else ok(p, c, "On with it.", "Weiter geht's.");
    }

    public static void skip(ServerPlayer p, Companion c) {
        if (c.current == null) {
            ok(p, c, "I'm not doing anything.", "Ich mache gerade nichts.");
            return;
        }
        String was = c.current.describe(Lang.of(p));
        c.skip();
        ok(p, c, "Left \"" + was + "\".", "\"" + was + "\" abgebrochen.");
    }

    public static void get(ServerPlayer p, Companion c, Set<Item> items, int count, GatherJob.Then then) {
        give(p, c, new GatherJob(items, count, then), false);
    }

    public static void hunt(ServerPlayer p, Companion c, EntityType<?> type, int count) {
        give(p, c, new HuntJob(type, count), false);
    }

    public static void fish(ServerPlayer p, Companion c, int count) {
        give(p, c, new FishJob(count), false);
    }

    /** What he is doing, how he is, where he is - with buttons. */
    public static void status(ServerPlayer p, Companion c) {
        Lang l = Lang.of(p);
        var server = p.level().getServer();
        MutableComponent m = Component.empty();
        if (c.bot == null) {
            m.append(l.t("I'm not in the world. ", "Ich bin nicht in der Welt. "))
                    .append(Menus.button(l.t("Call", "Rufen"), "/bob spawn", l.t("Bob joins you", "Bob kommt zu dir")));
            c.tell(server, m);
            return;
        }
        var body = c.bot.body();
        if (c.paused) m.append(l.t("Paused. ", "Pausiert. "));
        if (c.current != null) {
            String pr = c.current.progress(l);
            m.append(l.t("Doing: ", "Ich mache: ")).append(Component.literal(c.current.describe(l)).withStyle(ChatFormatting.WHITE));
            if (!pr.isEmpty()) m.append(" (" + pr + ")");
            m.append(". ");
        } else m.append(l.t("Nothing to do. ", "Nichts zu tun. "));
        if (!c.queue.isEmpty()) m.append(l.t(c.queue.size() + " more queued. ", c.queue.size() + " weitere in der Warteschlange. "));
        m.append(Component.literal(l.t("HP ", "Leben ") + Math.round(body.getHealth()) + "/20, " + l.t("food ", "Hunger ")
                + body.getFoodData().getFoodLevel() + "/20, " + l.t("at ", "bei ") + body.blockPosition().toShortString() + ". ").withStyle(ChatFormatting.GRAY));
        m.append(Menus.button("Stop", "/bob stop", l.t("Stop everything", "Alles abbrechen"))).append(" ")
                .append(Menus.button(l.t("Skip", "Überspringen"), "/bob skip", l.t("Leave this job, next one", "Diesen Auftrag lassen, nächster")))
                .append(" ").append(Menus.button(l.t("Menu", "Menü"), "/bob", l.t("All orders", "Alle Befehle")));
        c.tell(server, m);
    }

    public static void queue(ServerPlayer p, Companion c) {
        Lang l = Lang.of(p);
        var server = p.level().getServer();
        if (c.current == null && c.queue.isEmpty()) {
            ok(p, c, "My queue is empty.", "Meine Warteschlange ist leer.");
            return;
        }
        if (c.current != null) c.tell(server, Component.literal(l.t("Now: ", "Jetzt: ") + c.current.describe(l)));
        int i = 1;
        for (Job j : c.queue) {
            p.sendSystemMessage(Component.literal("  " + i + ". " + j.describe(l) + " ").append(
                    Menus.button(l.t("now", "jetzt"), "/bob queue now " + i, l.t("Do it now", "Jetzt erledigen"))).append(" ").append(
                    Menus.button("x", "/bob queue remove " + i, l.t("Remove", "Entfernen"))));
            i++;
        }
    }

    /** The queue's n-th job (1-based) now. */
    public static void queueNow(ServerPlayer p, Companion c, int n) {
        Job j = remove(c, n);
        if (j == null) return;
        give(p, c, j, true);
    }

    public static void queueRemove(ServerPlayer p, Companion c, int n) {
        Job j = remove(c, n);
        if (j != null) ok(p, c, "Removed: " + j.describe(Lang.EN) + ".", "Entfernt: " + j.describe(Lang.DE) + ".");
    }

    private static Job remove(Companion c, int n) {
        int i = 1;
        for (var it = c.queue.iterator(); it.hasNext(); i++) {
            Job j = it.next();
            if (i == n) {
                it.remove();
                return j;
            }
        }
        return null;
    }

    /** The container the player looks at (within six blocks), or null. */
    public static BlockPos lookedAtContainer(ServerPlayer p) {
        var from = p.getEyePosition();
        var to = from.add(p.getLookAngle().scale(6));
        BlockHitResult hit = p.level().clip(new ClipContext(from, to, ClipContext.Block.OUTLINE, ClipContext.Fluid.NONE, p));
        if (hit.getType() != HitResult.Type.BLOCK) return null;
        return Containers.isContainer(p.level(), hit.getBlockPos()) ? hit.getBlockPos() : null;
    }

    public static void chestAdd(ServerPlayer p, Companion c, RoleChest.Role role, BlockPos pos) {
        Lang l = Lang.of(p);
        if (pos == null || !Containers.isContainer(p.level(), pos)) {
            ok(p, c, "Look at a chest (or barrel) first - or click it with my wand.", "Schau zuerst auf eine Kiste (oder ein Fass) - oder klick sie mit meinem Zauberstab an.");
            return;
        }
        GlobalPos gp = GlobalPos.of(p.level().dimension(), pos.immutable());
        c.chests.removeIf(rc -> rc.pos().equals(gp));
        c.chests.add(new RoleChest(gp, role));
        ok(p, c, "Got it: the chest at " + pos.toShortString() + " is my " + role.en + " chest.",
                "Alles klar: Die Kiste bei " + pos.toShortString() + " ist meine " + role.de + "-Kiste.");
    }

    public static void chestRemove(ServerPlayer p, Companion c, BlockPos pos) {
        if (pos == null) {
            ok(p, c, "Look at the chest you mean.", "Schau auf die Kiste, die du meinst.");
            return;
        }
        GlobalPos gp = GlobalPos.of(p.level().dimension(), pos.immutable());
        boolean had = c.chests.removeIf(rc -> rc.pos().equals(gp));
        if (had) ok(p, c, "That chest isn't mine any more.", "Diese Kiste gehört nicht mehr zu mir.");
        else ok(p, c, "That chest isn't one of mine.", "Diese Kiste gehört nicht zu mir.");
    }

    public static void chestList(ServerPlayer p, Companion c) {
        Lang l = Lang.of(p);
        if (c.chests.isEmpty()) {
            ok(p, c, "I have no chests yet. Look at one and click a role in the menu.", "Ich habe noch keine Kisten. Schau auf eine und klick im Menü eine Rolle an.");
            return;
        }
        c.tell(p.level().getServer(), Component.literal(l.t("My chests:", "Meine Kisten:")));
        for (RoleChest rc : c.chests) {
            p.sendSystemMessage(Component.literal("  " + rc.role().label(l) + " - " + rc.block().toShortString()
                    + " (" + rc.pos().dimension().identifier().getPath() + ")").withStyle(ChatFormatting.GRAY));
        }
    }

    public static void settings(ServerPlayer p, Companion c) {
        Lang l = Lang.of(p);
        c.tell(p.level().getServer(), Component.literal(l.t("My settings (click to change):", "Meine Einstellungen (zum Ändern klicken):")));
        c.settings.all().forEach((k, v) -> {
            MutableComponent line = Component.literal("  " + k + " = ").withStyle(ChatFormatting.GRAY).append(Component.literal(v).withStyle(ChatFormatting.WHITE)).append("  ");
            for (String opt : Settings.values(k)) {
                if (!opt.equals(v)) line.append(Menus.button(opt, "/bob set " + k + " " + opt, k + " = " + opt)).append(" ");
            }
            p.sendSystemMessage(line);
        });
    }

    public static void set(ServerPlayer p, Companion c, String key, String value) {
        String err = c.settings.set(key, value);
        if (err != null) ok(p, c, "Hm: " + err + ".", "Hm: " + err + ".");
        else ok(p, c, "Okay: " + key + " = " + value + ".", "Okay: " + key + " = " + value + ".");
    }

    public static void debug(ServerPlayer p, boolean on) {
        if (on) BotManager.DEBUG_LISTENERS.add(p.getUUID());
        else BotManager.DEBUG_LISTENERS.remove(p.getUUID());
        p.sendSystemMessage(Component.literal("Bob debug " + (on ? "on" : "off")).withStyle(ChatFormatting.GRAY));
    }
}
