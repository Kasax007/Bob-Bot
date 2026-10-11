package net.kasax.bobbot.protect;

import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.kasax.bobbot.companion.Area;
import net.kasax.bobbot.companion.Companion;
import net.kasax.bobbot.companion.Lang;
import net.kasax.bobbot.control.BobCommands;
import net.kasax.bobbot.control.ChatControl;
import net.kasax.bobbot.control.Extensions;
import net.kasax.bobbot.control.Menus;
import net.kasax.bobbot.control.Show;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.Commands;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;

import java.util.Iterator;

/**
 * Keeping Bob's hands off: the guard every Bob carries (see {@link CompanionGuard}), and the
 * owner's say in it - "protect this" marks the land around him as never to be touched, "may I
 * break that?" tells him what Bob thinks of a block.
 */
public final class Protection {
    private Protection() {
    }

    /** How far round the owner "protect this" reaches, unless he says. */
    public static final int DEFAULT_RADIUS = 24;

    public static void register() {
        PlacedBlocks.register();
        ServerLifecycleEvents.SERVER_STOPPED.register(server -> {
            Structures.clear();
            Trees.clear();
        });
        Extensions.register(root -> root.then(Commands.literal("protect")
                .executes(ctx -> BobCommands.on(ctx, (p, c) -> here(p, c, DEFAULT_RADIUS)))
                .then(Commands.literal("here").executes(ctx -> BobCommands.on(ctx, (p, c) -> here(p, c, DEFAULT_RADIUS)))
                        .then(Commands.argument("radius", IntegerArgumentType.integer(2, 256))
                                .executes(ctx -> BobCommands.on(ctx, (p, c) -> here(p, c, IntegerArgumentType.getInteger(ctx, "radius"))))))
                .then(BobCommands.simple("list", Protection::list))
                .then(BobCommands.simple("check", Protection::check))
                .then(BobCommands.simple("off", Protection::unprotectHere))
                .then(Commands.literal("remove").then(Commands.argument("name", StringArgumentType.word())
                        .executes(ctx -> BobCommands.on(ctx, (p, c) -> remove(p, c, StringArgumentType.getString(ctx, "name"))))))));
        // "Bob, protect this" / "schütz das hier", "this is my house" / "das ist mein Haus".
        ChatControl.ruleFirst("\\b(protect|schütz\\w*|schuetz\\w*|beschütz\\w*|beschuetz\\w*)\\b.*\\b(this|here|house|base|home|hier|das|haus|basis|zuhause|alles)\\b"
                        + "|\\b(this is|das ist|that's|das hier ist)\\b.*\\b(my|mein\\w*)\\b.*\\b(house|base|home|haus|basis|zuhause|farm|hof)\\b"
                        + "|\\b(don't|do not|nicht|nichts|never|nie)\\b.*\\b(break|abbauen|abreißen|abreissen|kaputt\\w*|zerstör\\w*|zerstoer\\w*)\\b",
                (p, c, t, n) -> here(p, c, n >= 2 && n <= 256 ? n : DEFAULT_RADIUS));
        ChatControl.ruleFirst("\\b(darfst du abbauen|may break|can break|kannst du abbauen|nicht mehr schützen|no longer protect|unprotect)\\b",
                (p, c, t, n) -> unprotectHere(p, c));
        ChatControl.ruleFirst("\\b(darfst du das|may you break|can you break|would you break|würdest du das|wuerdest du das)\\b",
                (p, c, t, n) -> check(p, c));
    }

    /** The land round the owner, bottom to top, protected. */
    public static void here(ServerPlayer p, Companion c, int radius) {
        BlockPos at = p.blockPosition();
        var level = p.level();
        BlockPos a = new BlockPos(at.getX() - radius, level.getMinY(), at.getZ() - radius);
        BlockPos b = new BlockPos(at.getX() + radius, level.getMaxY() - 1, at.getZ() + radius);
        int i = 1;
        while (c.areas.containsKey("protect" + i)) i++;
        Area area = Area.of("protect" + i, level.dimension(), a, b, Area.Kind.PROTECTED);
        c.areas.put(area.name(), area);
        Show.box(p, a, b, 6, true);
        int side = 2 * radius + 1;
        c.say(p.level().getServer(), "Got it: I won't break or build anything within " + radius + " blocks of here (" + side + "×" + side
                        + ", \"" + area.name() + "\"). The blocks you place are safe from me anyway.",
                "Alles klar: Im Umkreis von " + radius + " Blöcken baue ich nichts ab und nichts hin (" + side + "×" + side
                        + ", \"" + area.name() + "\"). Was du selbst setzt, ist vor mir sowieso sicher.");
    }

    public static void list(ServerPlayer p, Companion c) {
        Lang l = Lang.of(p);
        boolean any = false;
        for (Area a : c.areas.values()) {
            if (a.kind() != Area.Kind.PROTECTED) continue;
            if (!any) c.tell(p.level().getServer(), Component.literal(l.t("Protected areas:", "Geschützte Bereiche:")));
            any = true;
            p.sendSystemMessage(Component.literal("  " + a.name() + ": " + a.min().getX() + "," + a.min().getZ() + " .. " + a.max().getX() + "," + a.max().getZ()
                    + " (" + a.dim().identifier().getPath() + ") ").withStyle(ChatFormatting.GRAY)
                    .append(Menus.button("x", "/bob protect remove " + a.name(), l.t("Remove", "Entfernen"))));
            if (a.dim() == p.level().dimension()) Show.box(p, a.min(), a.max(), 5, true);
        }
        if (!any) c.say(p.level().getServer(), "No protected areas. Blocks players placed are safe anyway; \"/bob protect\" marks the land around you.",
                "Keine geschützten Bereiche. Von Spielern gesetzte Blöcke sind sowieso sicher; \"/bob protect\" schützt das Land um dich herum.");
    }

    public static void remove(ServerPlayer p, Companion c, String name) {
        Area a = c.areas.get(name);
        if (a == null || a.kind() != Area.Kind.PROTECTED) {
            c.say(p.level().getServer(), "I have no protected area called \"" + name + "\".", "Ich habe keinen geschützten Bereich namens \"" + name + "\".");
            return;
        }
        c.areas.remove(name);
        c.say(p.level().getServer(), "\"" + name + "\" is no longer protected (what players placed there still is).",
                "\"" + name + "\" ist nicht mehr geschützt (was Spieler dort gesetzt haben, schon).");
    }

    /** Every protected area the owner stands in, lifted. */
    public static void unprotectHere(ServerPlayer p, Companion c) {
        int n = 0;
        for (Iterator<Area> it = c.areas.values().iterator(); it.hasNext(); ) {
            Area a = it.next();
            if (a.kind() == Area.Kind.PROTECTED && a.contains(p.level().dimension(), p.blockPosition())) {
                it.remove();
                n++;
            }
        }
        if (n == 0) c.say(p.level().getServer(), "You aren't standing in a protected area.", "Du stehst in keinem geschützten Bereich.");
        else c.say(p.level().getServer(), "Okay, here I may work again (" + n + " area" + (n > 1 ? "s" : "") + " lifted). What players placed stays safe.",
                "Okay, hier darf ich wieder arbeiten (" + n + " Bereich" + (n > 1 ? "e" : "") + " aufgehoben). Was Spieler gesetzt haben, bleibt sicher.");
    }

    /** Whether Bob would break the block the owner looks at, and why not. */
    public static void check(ServerPlayer p, Companion c) {
        var from = p.getEyePosition();
        var to = from.add(p.getLookAngle().scale(8));
        BlockHitResult hit = p.level().clip(new ClipContext(from, to, ClipContext.Block.OUTLINE, ClipContext.Fluid.NONE, p));
        if (hit.getType() != HitResult.Type.BLOCK) {
            c.say(p.level().getServer(), "Look at a block and ask me again.", "Schau auf einen Block und frag mich nochmal.");
            return;
        }
        BlockPos pos = hit.getBlockPos();
        var state = p.level().getBlockState(pos);
        String name = state.getBlock().getName().getString();
        CompanionGuard.Reason why = new CompanionGuard(c).reason(p.level(), pos, state);
        if (why == null) c.say(p.level().getServer(), name + " at " + pos.toShortString() + ": I may break that one if a job needs it.",
                name + " bei " + pos.toShortString() + ": Den darf ich abbauen, wenn ein Auftrag es braucht.");
        else c.say(p.level().getServer(), name + " at " + pos.toShortString() + ": I leave that alone - " + why.en + ".",
                name + " bei " + pos.toShortString() + ": Den lasse ich in Ruhe - " + why.de + ".");
    }
}
