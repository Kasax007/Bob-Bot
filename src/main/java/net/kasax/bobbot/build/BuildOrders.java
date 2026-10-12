package net.kasax.bobbot.build;

import com.mojang.brigadier.arguments.StringArgumentType;
import net.kasax.bobbot.companion.Companion;
import net.kasax.bobbot.companion.Lang;
import net.kasax.bobbot.companion.RoleChest;
import net.kasax.bobbot.companion.job.Jobs;
import net.kasax.bobbot.control.BobCommands;
import net.kasax.bobbot.control.ChatControl;
import net.kasax.bobbot.control.Extensions;
import net.kasax.bobbot.control.Menus;
import net.kasax.bobbot.control.Names;
import net.kasax.bobbot.control.Orders;
import net.kasax.bobbot.control.Show;
import net.kasax.bobbot.storage.Storage;
import net.kasax.bobbot.storage.Wand;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.Item;

import java.util.EnumSet;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Building with Bob: a blueprint chosen, put down in front of the owner (shown as an outline,
 * with what it takes and what the input chests hold), turned or moved till it sits right, then
 * built. And a build of the world copied as a blueprint, to build it again somewhere else.
 */
public final class BuildOrders {
    private BuildOrders() {
    }

    /** The build each player is placing (shown, not yet begun). */
    private static final Map<UUID, Site> PENDING = new HashMap<>();

    public static void register() {
        Jobs.register("build", BuildJob::new);
        Extensions.register(root -> root
                .then(Commands.literal("build")
                        .executes(ctx -> BobCommands.on(ctx, BuildOrders::list))
                        .then(BobCommands.simple("list", BuildOrders::list))
                        .then(BobCommands.simple("go", BuildOrders::go))
                        .then(BobCommands.simple("turn", BuildOrders::turn))
                        .then(BobCommands.simple("here", BuildOrders::here))
                        .then(BobCommands.simple("cancel", BuildOrders::cancel))
                        .then(BobCommands.simple("materials", BuildOrders::materials))
                        .then(Commands.argument("name", StringArgumentType.greedyString())
                                .suggests((ctx, b) -> SharedSuggestionProvider.suggest(Blueprints.all().keySet(), b))
                                .executes(ctx -> BobCommands.on(ctx, (p, c) -> place(p, c, StringArgumentType.getString(ctx, "name"))))))
                .then(Commands.literal("copy").then(Commands.argument("name", StringArgumentType.word())
                        .executes(ctx -> BobCommands.on(ctx, (p, c) -> copy(p, c, StringArgumentType.getString(ctx, "name")))))));
        // (Each rule goes before the ones registered earlier: the plain "build ..." last, so "build it" and "turn it" win.)
        ChatControl.ruleFirst("\\b(bau|baue|bauen|build)\\b", (p, c, t, n) -> {
            var m = java.util.regex.Pattern.compile("\\b(bau|baue|bauen|build)\\b\\s*(mir\\s+|me\\s+|hier\\s+|here\\s+)?(das|die|den|ein|eine|einen|the|a|an)?\\s*(.*)$").matcher(t);
            String name = m.find() ? m.group(4).trim() : "";
            if (name.isEmpty() || name.matches("(was|something|etwas)")) list(p, c);
            else place(p, c, name);
        });
        ChatControl.ruleFirst("\\b(merk dir|remember|kopier\\w*|copy|speicher\\w*|save)\\b.*\\b(als|as)\\b\\s+(\\S+)", (p, c, t, n) -> {
            var m = java.util.regex.Pattern.compile("\\b(als|as)\\b\\s+(\\S+)").matcher(t);
            if (m.find()) copy(p, c, m.group(2));
        });
        ChatControl.ruleFirst("\\b(bau los|baue los|fang an|leg los|build it|start building|go ahead|los geht's|losgehts)\\b", (p, c, t, n) -> go(p, c));
        ChatControl.ruleFirst("\\b(dreh\\w*|turn|rotate)\\b", (p, c, t, n) -> turn(p, c));
    }

    static void list(ServerPlayer p, Companion c) {
        Lang l = Lang.of(p);
        var all = Blueprints.all();
        if (all.isEmpty()) {
            c.say(p.level().getServer(), "I have no blueprints. Put .litematic, .nbt or .schem files into the \"schematics\" folder - or mark a build with my wand and say \"remember this as house\".",
                    "Ich habe keine Baupläne. Leg .litematic-, .nbt- oder .schem-Dateien in den Ordner \"schematics\" - oder markiere einen Bau mit meinem Zauberstab und sag \"merk dir das als haus\".");
            return;
        }
        c.tell(p.level().getServer(), Component.literal(l.t("What shall I build? (click one, it is shown in front of you first)", "Was soll ich bauen? (klick einen an, er wird dir erst gezeigt)")));
        MutableComponent line = Component.literal("  ");
        int n = 0;
        for (String name : all.keySet()) {
            line.append(Menus.button(name, "/bob build " + name, l.t("Show it in front of you", "Vor dir zeigen"))).append(" ");
            if (++n % 6 == 0) {
                p.sendSystemMessage(line);
                line = Component.literal("  ");
            }
        }
        if (n % 6 != 0) p.sendSystemMessage(line);
    }

    /** The blueprint put down in front of the owner and shown. */
    static void place(ServerPlayer p, Companion c, String name) {
        Blueprint bp = Blueprints.get(p.level().getServer(), name);
        if (bp == null) {
            c.say(p.level().getServer(), "I know no blueprint \"" + name + "\".", "Einen Bauplan \"" + name + "\" kenne ich nicht.");
            list(p, c);
            return;
        }
        Site site = Site.inFront(bp, p.blockPosition(), p.getDirection());
        PENDING.put(p.getUUID(), site);
        show(p, c, site);
    }

    private static void show(ServerPlayer p, Companion c, Site site) {
        Lang l = Lang.of(p);
        var box = site.box;
        Show.box(p, new BlockPos(box.minX(), box.minY(), box.minZ()), new BlockPos(box.maxX(), box.maxY(), box.maxZ()), 20, false);
        Map<Item, Integer> need = site.materials();
        Map<Item, Integer> lacking = lacking(p, c, need);
        int total = 0;
        for (int v : need.values()) total += v;
        MutableComponent m = Component.literal(l.t("\"" + site.blueprint.name + "\": " + (box.getXSpan()) + "×" + box.getYSpan() + "×" + box.getZSpan()
                        + ", " + site.blueprint.blocks() + " blocks. ",
                "\"" + site.blueprint.name + "\": " + box.getXSpan() + "×" + box.getYSpan() + "×" + box.getZSpan() + ", " + site.blueprint.blocks() + " Blöcke. "));
        if (lacking.isEmpty()) m.append(Component.literal(l.t("All materials are in my input chests. ", "Alles Material ist in meinen Material-Kisten. ")).withStyle(ChatFormatting.GREEN));
        else {
            int miss = 0;
            for (int v : lacking.values()) miss += v;
            m.append(Component.literal(l.t(miss + " of " + total + " items are missing. ", miss + " von " + total + " Gegenständen fehlen. ")).withStyle(ChatFormatting.YELLOW));
        }
        m.append(Menus.button(l.t("Build", "Bauen"), "/bob build go", l.t("Bob builds it here", "Bob baut es hier"))).append(" ")
                .append(Menus.button(l.t("Turn", "Drehen"), "/bob build turn", l.t("A quarter turn", "Eine Vierteldrehung"))).append(" ")
                .append(Menus.button(l.t("Here", "Hierher"), "/bob build here", l.t("In front of where you stand now", "Vor deinen jetzigen Platz"))).append(" ")
                .append(Menus.button(l.t("Materials", "Material"), "/bob build materials", l.t("What it takes, what is missing", "Was es braucht, was fehlt"))).append(" ")
                .append(Menus.button(l.t("Cancel", "Abbrechen"), "/bob build cancel", l.t("Not this", "Doch nicht")));
        c.tell(p.level().getServer(), m);
    }

    /** What the input chests and Bob's pack lack of what the build takes. */
    private static Map<Item, Integer> lacking(ServerPlayer p, Companion c, Map<Item, Integer> need) {
        ServerLevel level = p.level();
        var inputs = Storage.boxes(c, level, EnumSet.of(RoleChest.Role.INPUT));
        Map<Item, Integer> out = new java.util.LinkedHashMap<>();
        for (var e : need.entrySet()) {
            int have = Storage.total(level, inputs, st -> st.is(e.getKey()));
            if (c.bot != null) have += c.bot.body().getInventory().countItem(e.getKey());
            if (have < e.getValue()) out.put(e.getKey(), e.getValue() - have);
        }
        return out;
    }

    static void materials(ServerPlayer p, Companion c) {
        Site site = PENDING.get(p.getUUID());
        Lang l = Lang.of(p);
        if (site == null) {
            c.say(p.level().getServer(), "Choose a blueprint first.", "Wähle zuerst einen Bauplan.");
            return;
        }
        Map<Item, Integer> need = site.materials(), lacking = lacking(p, c, need);
        c.tell(p.level().getServer(), Component.literal(l.t("\"" + site.blueprint.name + "\" takes:", "\"" + site.blueprint.name + "\" braucht:")));
        for (var e : need.entrySet()) {
            int miss = lacking.getOrDefault(e.getKey(), 0);
            p.sendSystemMessage(Component.literal("  " + e.getValue() + "× " + Names.display(Set.of(e.getKey()), l)
                    + (miss > 0 ? l.t("  (" + miss + " missing)", "  (" + miss + " fehlen)") : "")).withStyle(miss > 0 ? ChatFormatting.YELLOW : ChatFormatting.GRAY));
        }
    }

    static void turn(ServerPlayer p, Companion c) {
        Site site = PENDING.get(p.getUUID());
        if (site == null) {
            c.say(p.level().getServer(), "Choose a blueprint first.", "Wähle zuerst einen Bauplan.");
            return;
        }
        site = site.turned();
        PENDING.put(p.getUUID(), site);
        show(p, c, site);
    }

    static void here(ServerPlayer p, Companion c) {
        Site site = PENDING.get(p.getUUID());
        if (site == null) {
            c.say(p.level().getServer(), "Choose a blueprint first.", "Wähle zuerst einen Bauplan.");
            return;
        }
        site = Site.inFront(site.blueprint, p.blockPosition(), p.getDirection());
        PENDING.put(p.getUUID(), site);
        show(p, c, site);
    }

    static void cancel(ServerPlayer p, Companion c) {
        if (PENDING.remove(p.getUUID()) != null) c.say(p.level().getServer(), "Okay, not that one.", "Okay, den doch nicht.");
    }

    static void go(ServerPlayer p, Companion c) {
        Site site = PENDING.remove(p.getUUID());
        if (site == null) {
            c.say(p.level().getServer(), "Choose a blueprint first: /bob build", "Wähle zuerst einen Bauplan: /bob build");
            return;
        }
        if (c.chests(RoleChest.Role.INPUT).isEmpty())
            c.say(p.level().getServer(), "Note: I have no input chest - I'll build with what I carry. Right click a chest with my wand and pick \"input\".",
                    "Hinweis: Ich habe keine Material-Kiste - ich baue mit dem, was ich dabeihabe. Klick mit meinem Zauberstab eine Kiste an und wähle \"Material\".");
        Orders.give(p, c, new BuildJob(site), false);
    }

    /** The box marked with the wand saved as a blueprint. */
    static void copy(ServerPlayer p, Companion c, String name) {
        BlockPos[] box = Wand.box(p);
        if (box == null) {
            c.say(p.level().getServer(), "Mark the build with my wand first (left click one corner, right click the other).",
                    "Markiere den Bau zuerst mit meinem Zauberstab (Linksklick die eine Ecke, Rechtsklick die andere).");
            return;
        }
        long volume = (long) (Math.abs(box[0].getX() - box[1].getX()) + 1) * (Math.abs(box[0].getY() - box[1].getY()) + 1) * (Math.abs(box[0].getZ() - box[1].getZ()) + 1);
        if (volume > 64 * 64 * 64) {
            c.say(p.level().getServer(), "That box is too big for me to remember (at most 64×64×64).", "Der Bereich ist mir zu groß (höchstens 64×64×64).");
            return;
        }
        Blueprint bp = Blueprint.copy(p.level(), name, box[0], box[1]);
        try {
            bp.save(Blueprints.savePath(name));
        } catch (java.io.IOException e) {
            c.say(p.level().getServer(), "I couldn't save it: " + e.getMessage(), "Ich konnte es nicht speichern: " + e.getMessage());
            return;
        }
        c.say(p.level().getServer(), "I've remembered that as \"" + name + "\" (" + bp.blocks() + " blocks). Say \"build " + name + "\" where you want it again.",
                "Ich habe mir das als \"" + name + "\" gemerkt (" + bp.blocks() + " Blöcke). Sag \"bau " + name + "\", wo du es noch einmal willst.");
    }
}
