package net.kasax.bobbot.control;

import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.suggestion.SuggestionProvider;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.kasax.bobbot.companion.Companion;
import net.kasax.bobbot.companion.CompanionManager;
import net.kasax.bobbot.companion.Lang;
import net.kasax.bobbot.companion.RoleChest;
import net.kasax.bobbot.companion.Settings;
import net.kasax.bobbot.companion.job.GatherJob;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.item.Item;

import java.util.Arrays;
import java.util.Locale;
import java.util.Set;
import java.util.function.BiConsumer;

/**
 * {@code /bob ...}: every order as a command (the chat menu's buttons run these too). Any player
 * may have Bobs; the orders go to his own.
 */
public final class BobCommands {
    private BobCommands() {
    }

    private static final SuggestionProvider<CommandSourceStack> ITEMS = (ctx, b) ->
            SharedSuggestionProvider.suggestResource(BuiltInRegistries.ITEM.keySet(), b);
    private static final SuggestionProvider<CommandSourceStack> MOBS = (ctx, b) ->
            SharedSuggestionProvider.suggestResource(BuiltInRegistries.ENTITY_TYPE.keySet(), b);
    private static final SuggestionProvider<CommandSourceStack> ROLES = (ctx, b) ->
            SharedSuggestionProvider.suggest(Arrays.stream(RoleChest.Role.values()).map(r -> r.name().toLowerCase(Locale.ROOT)), b);
    private static final SuggestionProvider<CommandSourceStack> KEYS = (ctx, b) -> SharedSuggestionProvider.suggest(Settings.keys(), b);
    private static final SuggestionProvider<CommandSourceStack> VALUES = (ctx, b) ->
            SharedSuggestionProvider.suggest(Settings.values(StringArgumentType.getString(ctx, "key")), b);
    private static final SuggestionProvider<CommandSourceStack> MY_BOBS = (ctx, b) -> {
        ServerPlayer p = ctx.getSource().getPlayer();
        return SharedSuggestionProvider.suggest(p == null ? java.util.List.of() : CompanionManager.ofOwner(p.getUUID()).stream().map(c -> c.name).toList(), b);
    };

    /** Runs an order on the player's Bob (if he has one). */
    private static int on(CommandContext<CommandSourceStack> ctx, BiConsumer<ServerPlayer, Companion> order) throws CommandSyntaxException {
        ServerPlayer p = ctx.getSource().getPlayerOrException();
        Companion c = Orders.target(p, true);
        if (c == null) return 0;
        order.accept(p, c);
        return 1;
    }

    private static LiteralArgumentBuilder<CommandSourceStack> simple(String name, BiConsumer<ServerPlayer, Companion> order) {
        return Commands.literal(name).executes(ctx -> on(ctx, order));
    }

    public static void register() {
        CommandRegistrationCallback.EVENT.register((dispatcher, registry, env) -> {
            var root = Commands.literal("bob")
                    .executes(ctx -> on(ctx, Menus::show))
                    .then(simple("help", Menus::show))
                    .then(simple("menu", Menus::show))
                    .then(Commands.literal("spawn").executes(ctx -> spawn(ctx, "Bob"))
                            .then(Commands.argument("name", StringArgumentType.word()).executes(ctx -> spawn(ctx, StringArgumentType.getString(ctx, "name")))))
                    .then(simple("dismiss", (p, c) -> {
                        CompanionManager.dismiss(p.level().getServer(), c);
                        p.sendSystemMessage(Component.literal(Lang.of(p).t(c.name + " went off for now. /bob spawn calls him back.",
                                c.name + " ist erst mal weg. /bob spawn ruft ihn zurück.")));
                    }))
                    .then(Commands.literal("forget").then(Commands.argument("name", StringArgumentType.word()).suggests(MY_BOBS).executes(ctx -> {
                        ServerPlayer p = ctx.getSource().getPlayerOrException();
                        Companion c = CompanionManager.byName(StringArgumentType.getString(ctx, "name"));
                        if (c == null || !p.getUUID().equals(c.owner)) return 0;
                        CompanionManager.delete(p.level().getServer(), c);
                        p.sendSystemMessage(Component.literal(Lang.of(p).t(c.name + " is gone for good.", c.name + " ist für immer weg.")));
                        return 1;
                    })))
                    .then(Commands.literal("list").executes(ctx -> {
                        ServerPlayer p = ctx.getSource().getPlayerOrException();
                        var mine = CompanionManager.ofOwner(p.getUUID());
                        Lang l = Lang.of(p);
                        if (mine.isEmpty()) Orders.target(p, true);
                        for (Companion c : mine) {
                            p.sendSystemMessage(Component.literal(c.name + (c.bot == null ? l.t(" (away) ", " (weg) ") : " ")).append(
                                    Menus.button(l.t("choose", "wählen"), "/bob select " + c.name, l.t("Orders go to him", "Befehle gehen an ihn"))));
                        }
                        return mine.size();
                    }))
                    .then(Commands.literal("select").then(Commands.argument("name", StringArgumentType.word()).suggests(MY_BOBS).executes(ctx -> {
                        ServerPlayer p = ctx.getSource().getPlayerOrException();
                        Companion c = CompanionManager.byName(StringArgumentType.getString(ctx, "name"));
                        if (c == null || !p.getUUID().equals(c.owner)) return 0;
                        Orders.select(p, c);
                        c.say(p.level().getServer(), "You're talking to me now.", "Du sprichst jetzt mit mir.");
                        return 1;
                    })))
                    .then(simple("come", Orders::come))
                    .then(simple("follow", (p, c) -> Orders.follow(p, c, false)))
                    .then(simple("guard", (p, c) -> Orders.follow(p, c, true)))
                    .then(simple("stay", Orders::stay))
                    .then(simple("home", Orders::home).then(simple("set", Orders::setHome)))
                    .then(simple("stop", Orders::stop))
                    .then(simple("pause", (p, c) -> Orders.pause(p, c, true)))
                    .then(simple("resume", (p, c) -> Orders.pause(p, c, false)))
                    .then(simple("skip", Orders::skip))
                    .then(simple("status", Orders::status))
                    .then(simple("queue", Orders::queue)
                            .then(Commands.literal("now").then(Commands.argument("n", IntegerArgumentType.integer(1))
                                    .executes(ctx -> on(ctx, (p, c) -> Orders.queueNow(p, c, IntegerArgumentType.getInteger(ctx, "n"))))))
                            .then(Commands.literal("remove").then(Commands.argument("n", IntegerArgumentType.integer(1))
                                    .executes(ctx -> on(ctx, (p, c) -> Orders.queueRemove(p, c, IntegerArgumentType.getInteger(ctx, "n"))))))
                            .then(simple("clear", (p, c) -> {
                                c.queue.clear();
                                c.say(p.level().getServer(), "Queue cleared.", "Warteschlange geleert.");
                            })))
                    .then(gather("get", GatherJob.Then.OWNER))
                    .then(gather("gather", GatherJob.Then.CHEST))
                    .then(Commands.literal("hunt").then(Commands.argument("mob", StringArgumentType.string()).suggests(MOBS)
                            .executes(ctx -> hunt(ctx, 1))
                            .then(Commands.argument("count", IntegerArgumentType.integer(1, 256)).executes(ctx -> hunt(ctx, IntegerArgumentType.getInteger(ctx, "count"))))))
                    .then(Commands.literal("fish").executes(ctx -> on(ctx, (p, c) -> Orders.fish(p, c, 10)))
                            .then(Commands.argument("count", IntegerArgumentType.integer(1, 512))
                                    .executes(ctx -> on(ctx, (p, c) -> Orders.fish(p, c, IntegerArgumentType.getInteger(ctx, "count"))))))
                    .then(Commands.literal("chest")
                            .then(Commands.literal("add").then(Commands.argument("role", StringArgumentType.word()).suggests(ROLES).executes(ctx -> on(ctx, (p, c) -> {
                                RoleChest.Role role = RoleChest.Role.parse(StringArgumentType.getString(ctx, "role"));
                                if (role == null) {
                                    c.say(p.level().getServer(), "Roles: dropoff, input, storage, overflow, fuel, food.",
                                            "Rollen: dropoff (Ablage), input (Material), storage (Lager), overflow (Überlauf), fuel (Brennstoff), food (Essen).");
                                    return;
                                }
                                Orders.chestAdd(p, c, role, Orders.lookedAtContainer(p));
                            }))))
                            .then(simple("remove", (p, c) -> Orders.chestRemove(p, c, Orders.lookedAtContainer(p))))
                            .then(simple("list", Orders::chestList)))
                    .then(simple("settings", Orders::settings))
                    .then(Commands.literal("set").then(Commands.argument("key", StringArgumentType.word()).suggests(KEYS)
                            .then(Commands.argument("value", StringArgumentType.word()).suggests(VALUES).executes(ctx -> on(ctx, (p, c) ->
                                    Orders.set(p, c, StringArgumentType.getString(ctx, "key"), StringArgumentType.getString(ctx, "value")))))))
                    .then(Commands.literal("debug")
                            .then(Commands.literal("on").executes(ctx -> {
                                Orders.debug(ctx.getSource().getPlayerOrException(), true);
                                return 1;
                            }))
                            .then(Commands.literal("off").executes(ctx -> {
                                Orders.debug(ctx.getSource().getPlayerOrException(), false);
                                return 1;
                            })));
            Extensions.add(root);
            dispatcher.register(root);
        });
    }

    private static LiteralArgumentBuilder<CommandSourceStack> gather(String name, GatherJob.Then then) {
        return Commands.literal(name).then(Commands.argument("item", StringArgumentType.string()).suggests(ITEMS)
                .executes(ctx -> gather(ctx, then, 1))
                .then(Commands.argument("count", IntegerArgumentType.integer(1, 2304)).executes(ctx -> gather(ctx, then, IntegerArgumentType.getInteger(ctx, "count")))));
    }

    private static int gather(CommandContext<CommandSourceStack> ctx, GatherJob.Then then, int count) throws CommandSyntaxException {
        String what = StringArgumentType.getString(ctx, "item");
        Set<Item> items = Names.items(what);
        return on(ctx, (p, c) -> {
            if (items.isEmpty()) {
                c.say(p.level().getServer(), "I don't know \"" + what + "\".", "\"" + what + "\" kenne ich nicht.");
                return;
            }
            Orders.get(p, c, items, count, then);
        });
    }

    private static int hunt(CommandContext<CommandSourceStack> ctx, int count) throws CommandSyntaxException {
        String what = StringArgumentType.getString(ctx, "mob");
        EntityType<?> type = Names.mob(what);
        return on(ctx, (p, c) -> {
            if (type == null) {
                c.say(p.level().getServer(), "I don't know \"" + what + "\".", "\"" + what + "\" kenne ich nicht.");
                return;
            }
            Orders.hunt(p, c, type, count);
        });
    }

    private static int spawn(CommandContext<CommandSourceStack> ctx, String name) throws CommandSyntaxException {
        ServerPlayer p = ctx.getSource().getPlayerOrException();
        Lang l = Lang.of(p);
        var server = p.level().getServer();
        Companion c = CompanionManager.byName(name);
        if (c == null && name.equals("Bob") && CompanionManager.primary(p.getUUID()) != null) c = CompanionManager.primary(p.getUUID());
        if (c != null && p.getUUID().equals(c.owner)) {
            if (c.bot == null) CompanionManager.summon(server, c, p.level(), p.position());
            else Orders.come(p, c);
            Orders.select(p, c);
            return 1;
        }
        // "Bob" is taken by someone else's Bob: "Bob_<player>" then.
        if (c != null && name.equals("Bob")) name = ("Bob_" + p.getGameProfile().name()).substring(0, Math.min(16, 4 + p.getGameProfile().name().length()));
        String problem = CompanionManager.nameProblem(server, p.getUUID(), name);
        if (problem != null) {
            p.sendSystemMessage(Component.literal(l.t("Can't call a Bob named " + name + ": " + problem + ".", "Kann keinen Bob namens " + name + " rufen: " + problem + ".")));
            return 0;
        }
        c = CompanionManager.create(p, name);
        CompanionManager.summon(server, c, p.level(), p.position());
        Orders.select(p, c);
        c.say(server, "Hi " + p.getGameProfile().name() + "! I'm " + c.name + ". Tell me what to do - in chat (\"" + c.name + ", fell trees\") or with /bob.",
                "Hi " + p.getGameProfile().name() + "! Ich bin " + c.name + ". Sag mir, was ich tun soll - im Chat (\"" + c.name + ", fäll Bäume\") oder mit /bob.");
        Menus.show(p, c);
        return 1;
    }
}
