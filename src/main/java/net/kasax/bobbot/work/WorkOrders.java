package net.kasax.bobbot.work;

import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import net.kasax.bobbot.companion.Area;
import net.kasax.bobbot.companion.Companion;
import net.kasax.bobbot.companion.job.Jobs;
import net.kasax.bobbot.control.BobCommands;
import net.kasax.bobbot.control.ChatControl;
import net.kasax.bobbot.control.Extensions;
import net.kasax.bobbot.control.Names;
import net.kasax.bobbot.control.Orders;
import net.kasax.bobbot.storage.Wand;
import net.minecraft.commands.Commands;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.Item;

import java.util.Set;

/** Bob's work as orders: felling, mining, digging out, farming, smelting, guarding, lighting, tidying up. */
public final class WorkOrders {
    private WorkOrders() {
    }

    public static void register() {
        Jobs.register("wood", WoodJob::new);
        Jobs.register("mine", MineJob::new);
        Jobs.register("quarry", QuarryJob::new);
        Jobs.register("farm", FarmJob::new);
        Jobs.register("smelt", SmeltJob::new);
        Jobs.register("guard", GuardJob::new);
        Jobs.register("light", LightJob::new);
        Jobs.register("collect", CollectJob::new);
        Extensions.register(root -> root
                .then(BobCommands.simple("wood", (p, c) -> wood(p, c, 0, null))
                        .then(Commands.argument("count", IntegerArgumentType.integer(1, 4096))
                                .executes(ctx -> BobCommands.on(ctx, (p, c) -> wood(p, c, IntegerArgumentType.getInteger(ctx, "count"), null))))
                        .then(Commands.literal("in").then(Commands.argument("area", StringArgumentType.word())
                                .executes(ctx -> BobCommands.on(ctx, (p, c) -> wood(p, c, 0, StringArgumentType.getString(ctx, "area")))))))
                .then(BobCommands.simple("mine", (p, c) -> Orders.give(p, c, new MineJob(-58, 48), false))
                        .then(Commands.argument("y", IntegerArgumentType.integer(-63, 300))
                                .executes(ctx -> BobCommands.on(ctx, (p, c) -> Orders.give(p, c, new MineJob(IntegerArgumentType.getInteger(ctx, "y"), 48), false)))
                                .then(Commands.argument("length", IntegerArgumentType.integer(4, 512))
                                        .executes(ctx -> BobCommands.on(ctx, (p, c) -> Orders.give(p, c,
                                                new MineJob(IntegerArgumentType.getInteger(ctx, "y"), IntegerArgumentType.getInteger(ctx, "length")), false))))))
                .then(BobCommands.simple("dig", WorkOrders::dig)
                        .then(Commands.argument("area", StringArgumentType.word()).executes(ctx -> BobCommands.on(ctx, (p, c) -> {
                            Area a = c.areas.get(StringArgumentType.getString(ctx, "area"));
                            if (a == null) c.say(p.level().getServer(), "I have no such area.", "Diesen Bereich kenne ich nicht.");
                            else Orders.give(p, c, QuarryJob.of(a), false);
                        }))))
                .then(BobCommands.simple("farm", (p, c) -> farm(p, c, false))
                        .then(BobCommands.simple("loop", (p, c) -> farm(p, c, true))))
                .then(BobCommands.simple("smelt", (p, c) -> Orders.give(p, c, new SmeltJob(), false))
                        .then(Commands.argument("item", StringArgumentType.string()).executes(ctx -> BobCommands.on(ctx, (p, c) -> {
                            Set<Item> items = Names.items(StringArgumentType.getString(ctx, "item"));
                            Orders.give(p, c, new SmeltJob(items.isEmpty() ? null : items.iterator().next()), false);
                        }))))
                .then(BobCommands.simple("patrol", (p, c) -> Orders.give(p, c, new GuardJob(p.blockPosition(), 16), true))
                        .then(Commands.argument("radius", IntegerArgumentType.integer(4, 64))
                                .executes(ctx -> BobCommands.on(ctx, (p, c) -> Orders.give(p, c, new GuardJob(p.blockPosition(), IntegerArgumentType.getInteger(ctx, "radius")), true)))))
                .then(BobCommands.simple("light", (p, c) -> Orders.give(p, c, new LightJob(p.blockPosition(), 16), false))
                        .then(Commands.argument("radius", IntegerArgumentType.integer(4, 48))
                                .executes(ctx -> BobCommands.on(ctx, (p, c) -> Orders.give(p, c, new LightJob(p.blockPosition(), IntegerArgumentType.getInteger(ctx, "radius")), false)))))
                .then(BobCommands.simple("collect", (p, c) -> Orders.give(p, c, new CollectJob(p.blockPosition(), 16), false))));

        // In chat. (These go before the plainer orders: "farm wood" is not "farm", "guard this place" not "guard me".)
        ChatControl.ruleFirst("\\b(sammel\\w*|einsammeln|aufsammeln|pick (it |them |that |everything |things )?up|collect)\\b",
                (p, c, t, n) -> Orders.give(p, c, new CollectJob(p.blockPosition(), n >= 4 && n <= 48 ? n : 16), false));
        ChatControl.ruleFirst("\\b(licht|beleucht\\w*|light (it |this |that |everything )?up|fackeln)\\b",
                (p, c, t, n) -> Orders.give(p, c, new LightJob(p.blockPosition(), n >= 4 && n <= 48 ? n : 16), false));
        ChatControl.ruleFirst("\\bbewach\\w*\\b|\\b(guard|watch) (this|here|the|that)\\b|\\bpatrol\\w*\\b|\\bwache\\b",
                (p, c, t, n) -> Orders.give(p, c, new GuardJob(p.blockPosition(), n >= 4 && n <= 64 ? n : 16), true));
        ChatControl.ruleFirst("\\b(schmelz\\w*|smelt\\w*|einschmelzen)\\b", (p, c, t, n) -> {
            String what = ChatControl.object(t, "\\b(schmelz\\w*|smelt\\w*|einschmelzen|die|das|den|the|all|alles)\\b");
            Set<Item> items = what.isBlank() ? Set.of() : Names.items(what);
            Orders.give(p, c, new SmeltJob(items.isEmpty() ? null : rawOf(items.iterator().next())), false);
        });
        ChatControl.ruleFirst("\\b(ernte\\w*|harvest\\w*|felder|feld)\\b|\\bfarm(en)?\\b(?!.*\\b(holz|wood)\\b)",
                (p, c, t, n) -> farm(p, c, t.matches(".*\\b(immer|ständig|staendig|laufend|dauernd|keep|always|weiter)\\b.*")));
        ChatControl.ruleFirst("\\b(heb\\w*|grab\\w*|dig)\\b.*\\b(aus|out)\\b|\\b(ausheben|quarry|steinbruch)\\b", (p, c, t, n) -> dig(p, c));
        ChatControl.ruleFirst("\\b(strip\\w*|branch\\w*|minen|mine|bergbau|erze|ores|dig for|grab\\w* nach)\\b", (p, c, t, n) ->
                Orders.give(p, c, new MineJob(t.matches(".*-?\\d+.*") && Math.abs(n) <= 320 && n != 0 ? n : levelFor(t), 48), false));
        ChatControl.ruleFirst("\\b(fäll\\w*|faell\\w*|fell\\w*|chop\\w*|holzfäll\\w*|holzfaell\\w*)\\b|\\b(farm\\w*)\\b.*\\b(holz|wood)\\b|\\b(holz|wood)\\b.*\\bfarm\\w*\\b",
                (p, c, t, n) -> {
                    int logs = n <= 0 ? 0 : t.matches(".*\\b(bäume|baeume|baum|trees?)\\b.*") ? n * 5 : n;
                    wood(p, c, logs, null);
                });
    }

    /** "smelt the iron": the raw ore is meant. */
    private static Item rawOf(Item i) {
        if (i == net.minecraft.world.item.Items.IRON_INGOT) return net.minecraft.world.item.Items.RAW_IRON;
        if (i == net.minecraft.world.item.Items.GOLD_INGOT) return net.minecraft.world.item.Items.RAW_GOLD;
        if (i == net.minecraft.world.item.Items.COPPER_INGOT) return net.minecraft.world.item.Items.RAW_COPPER;
        if (i == net.minecraft.world.item.Items.GLASS) return net.minecraft.world.item.Items.SAND;
        return i;
    }

    /** The level for an ore named in the line (diamonds deep, iron at 16, ...). */
    static int levelFor(String t) {
        if (t.matches(".*\\b(eisen|iron)\\b.*")) return 16;
        if (t.matches(".*\\b(gold)\\b.*")) return -16;
        if (t.matches(".*\\b(kupfer|copper)\\b.*")) return 48;
        if (t.matches(".*\\b(lapis)\\b.*")) return 0;
        if (t.matches(".*\\b(kohle|coal)\\b.*")) return 64;
        return -58;
    }

    static void wood(ServerPlayer p, Companion c, int count, String area) {
        // A work area the owner stands in: the felling stays in it.
        if (area == null) {
            for (Area a : c.areas.values()) if (a.kind() == Area.Kind.WORK && a.contains(p.level().dimension(), p.blockPosition())) area = a.name();
        }
        Orders.give(p, c, new WoodJob(count, area), false);
    }

    static void farm(ServerPlayer p, Companion c, boolean loop) {
        String area = null;
        for (Area a : c.areas.values()) if (a.kind() == Area.Kind.WORK && a.contains(p.level().dimension(), p.blockPosition())) area = a.name();
        Orders.give(p, c, new FarmJob(area, loop), false);
    }

    /** The box marked with the wand (or the work area the owner stands in) dug out. */
    public static void dig(ServerPlayer p, Companion c) {
        BlockPos[] box = Wand.box(p);
        if (box != null) {
            Orders.give(p, c, new QuarryJob(box[0], box[1]), false);
            return;
        }
        for (Area a : c.areas.values()) {
            if (a.kind() == Area.Kind.WORK && a.contains(p.level().dimension(), p.blockPosition())) {
                Orders.give(p, c, QuarryJob.of(a), false);
                return;
            }
        }
        c.say(p.level().getServer(), "Mark the box with my wand first (left click one corner, right click the other).",
                "Markiere den Bereich zuerst mit meinem Zauberstab (Linksklick die eine Ecke, Rechtsklick die andere).");
    }
}
