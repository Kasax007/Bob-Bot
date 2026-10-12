package net.kasax.bobbot.storage;

import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.player.AttackEntityCallback;
import net.fabricmc.fabric.api.event.player.UseEntityCallback;
import net.kasax.bobbot.bot.BotArmor;
import net.kasax.bobbot.bot.BotPlayer;
import net.kasax.bobbot.companion.Area;
import net.kasax.bobbot.companion.Companion;
import net.kasax.bobbot.companion.CompanionManager;
import net.kasax.bobbot.companion.Deliveries;
import net.kasax.bobbot.companion.Lang;
import net.kasax.bobbot.companion.PackMenu;
import net.kasax.bobbot.companion.RoleChest;
import net.kasax.bobbot.companion.job.FetchJob;
import net.kasax.bobbot.companion.job.Job;
import net.kasax.bobbot.companion.job.SortJob;
import net.kasax.bobbot.companion.task.DepositTask;
import net.kasax.bobbot.companion.task.GiveTask;
import net.kasax.bobbot.control.BobCommands;
import net.kasax.bobbot.control.ChatControl;
import net.kasax.bobbot.control.Extensions;
import net.kasax.bobbot.control.Menus;
import net.kasax.bobbot.control.Names;
import net.kasax.bobbot.control.Orders;
import net.kasax.bobbot.control.Show;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.commands.arguments.coordinates.BlockPosArgument;
import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

import java.util.Arrays;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * The owner's things and Bob: the wand, chests and areas, sorting into the storage, fetching from
 * it, emptying Bob's pack, looking into it, handing him things.
 */
public final class StorageOrders {
    private StorageOrders() {
    }

    public static void register() {
        Wand.register();
        ServerLifecycleEvents.END_DATA_PACK_RELOAD.register((server, rm, ok) -> Similarity.clear());
        ServerLifecycleEvents.SERVER_STARTED.register(server -> Similarity.clear());
        UseEntityCallback.EVENT.register((player, level, hand, entity, hit) -> {
            if (level.isClientSide() || hit != null || hand != InteractionHand.MAIN_HAND || !(player instanceof ServerPlayer p)
                    || !(entity instanceof BotPlayer body) || p.isSpectator()) return InteractionResult.PASS;
            Companion c = CompanionManager.of(body);
            if (c == null) return InteractionResult.PASS;
            if (!p.getUUID().equals(c.owner)) {
                p.sendSystemMessage(c.line(Component.literal(Lang.of(p).t("Hi! I'm " + c.ownerName + "'s Bob.", "Hi! Ich bin der Bob von " + c.ownerName + "."))));
                return InteractionResult.SUCCESS;
            }
            Orders.select(p, c);
            ItemStack held = p.getItemInHand(hand);
            if (p.isShiftKeyDown() && !held.isEmpty() && !Wand.is(held)) gift(p, c, held);
            else if (p.isShiftKeyDown()) PackMenu.open(p, c);
            else net.kasax.bobbot.control.ControlPanel.open(p, c);
            return InteractionResult.SUCCESS;
        });
        AttackEntityCallback.EVENT.register((player, level, hand, entity, hit) -> {
            if (level.isClientSide() || !(player instanceof ServerPlayer p) || !(entity instanceof BotPlayer body)) return InteractionResult.PASS;
            Companion c = CompanionManager.of(body);
            if (c == null || !p.getUUID().equals(c.owner)) return InteractionResult.PASS;
            // (His own owner's blow: missed on purpose. A word, now and then.)
            long now = level.getGameTime();
            if (now - OUCH.getOrDefault(p.getUUID(), -1000L) > 100) {
                OUCH.put(p.getUUID(), now);
                c.say(p.level().getServer(), "Hey, it's me!", "Hey, ich bin's!");
            }
            return InteractionResult.FAIL;
        });
        Extensions.register(root -> root
                .then(BobCommands.simple("wand", StorageOrders::giveWand))
                .then(Commands.literal("area")
                        .then(Commands.literal("add").then(Commands.argument("kind", StringArgumentType.word())
                                .suggests((ctx, b) -> SharedSuggestionProvider.suggest(new String[]{"work", "protected", "storage"}, b))
                                .executes(ctx -> BobCommands.on(ctx, (p, c) -> {
                                    Area.Kind k = Area.Kind.parse(StringArgumentType.getString(ctx, "kind"));
                                    if (k == null) c.say(p.level().getServer(), "Kinds: work, protected, storage.", "Arten: work (Arbeit), protected (geschützt), storage (Lager).");
                                    else Wand.addArea(p, c, k);
                                }))))
                        .then(BobCommands.simple("list", StorageOrders::areas))
                        .then(Commands.literal("remove").then(Commands.argument("name", StringArgumentType.word())
                                .executes(ctx -> BobCommands.on(ctx, (p, c) -> removeArea(p, c, StringArgumentType.getString(ctx, "name"))))))
                        .then(Commands.literal("show").then(Commands.argument("name", StringArgumentType.word())
                                .executes(ctx -> BobCommands.on(ctx, (p, c) -> {
                                    Area a = c.areas.get(StringArgumentType.getString(ctx, "name"));
                                    if (a != null && a.dim() == p.level().dimension()) Show.box(p, a.min(), a.max(), 8, a.kind() == Area.Kind.PROTECTED);
                                })))))
                .then(Commands.literal("chest")
                        .then(Commands.literal("set").then(Commands.argument("role", StringArgumentType.word())
                                .suggests((ctx, b) -> SharedSuggestionProvider.suggest(Arrays.stream(RoleChest.Role.values()).map(r -> r.name().toLowerCase(Locale.ROOT)), b))
                                .then(Commands.argument("pos", BlockPosArgument.blockPos()).executes(ctx -> BobCommands.on(ctx, (p, c) -> {
                                    RoleChest.Role role = RoleChest.Role.parse(StringArgumentType.getString(ctx, "role"));
                                    BlockPos pos;
                                    try {
                                        pos = BlockPosArgument.getLoadedBlockPos(ctx, "pos");
                                    } catch (Exception e) {
                                        return;
                                    }
                                    if (role != null) Orders.chestAdd(p, c, role, pos);
                                })))))
                        .then(Commands.literal("unset").then(Commands.argument("pos", BlockPosArgument.blockPos()).executes(ctx -> BobCommands.on(ctx, (p, c) -> {
                            try {
                                Orders.chestRemove(p, c, BlockPosArgument.getLoadedBlockPos(ctx, "pos"));
                            } catch (Exception ignored) {
                            }
                        })))))
                .then(BobCommands.simple("sort", (p, c) -> Orders.give(p, c, new SortJob(SortJob.From.ALL), false))
                        .then(BobCommands.simple("pack", (p, c) -> Orders.give(p, c, new SortJob(SortJob.From.PACK), false)))
                        .then(BobCommands.simple("dropoff", (p, c) -> Orders.give(p, c, new SortJob(SortJob.From.DROPOFF), false))))
                .then(BobCommands.simple("deposit", StorageOrders::deposit))
                .then(BobCommands.simple("inventory", (p, c) -> {
                    if (Orders.present(p, c)) PackMenu.open(p, c);
                }))
                .then(BobCommands.simple("giveall", StorageOrders::giveAll))
                .then(Commands.literal("fetch").then(Commands.argument("item", StringArgumentType.string())
                        .suggests((ctx, b) -> SharedSuggestionProvider.suggestResource(BuiltInRegistries.ITEM.keySet(), b))
                        .executes(ctx -> fetch(ctx, 1))
                        .then(Commands.argument("count", IntegerArgumentType.integer(1, 2304)).executes(ctx -> fetch(ctx, IntegerArgumentType.getInteger(ctx, "count")))))));
        ChatControl.ruleFirst("\\b(sortier\\w*|sort|tidy\\w*|aufr(ä|ae)um\\w*|einr(ä|ae)um\\w*|put (it |them |things )?away)\\b|\\br(ä|ae)um\\w*\\b.*\\b(auf|ein)\\b",
                (p, c, t, n) -> Orders.give(p, c, new SortJob(t.matches(".*\\b(rucksack|pack|taschen|pockets|inventar)\\b.*") ? SortJob.From.PACK : SortJob.From.ALL), false));
        ChatControl.ruleFirst("\\b(zauberstab|wand)\\b", (p, c, t, n) -> giveWand(p, c));
        ChatControl.ruleFirst("\\b(inventar|inventory|rucksack|was hast du dabei|what do you have|what are you carrying|zeig mir deine sachen|show me your (stuff|things|pack))\\b",
                (p, c, t, n) -> {
                    if (Orders.present(p, c)) PackMenu.open(p, c);
                });
        ChatControl.ruleFirst("\\b(gib mir alles|give me everything|alles her|gib mir deine sachen|give me your (stuff|things))\\b", (p, c, t, n) -> giveAll(p, c));
        ChatControl.ruleFirst("\\b(leer\\w* (deine|den|your)|empty your|deposit|abliefern|ablegen|pack aus|unload)\\b", (p, c, t, n) -> deposit(p, c));
    }

    private static final Map<UUID, Long> OUCH = new HashMap<>();

    public static void giveWand(ServerPlayer p, Companion c) {
        Lang l = Lang.of(p);
        p.getInventory().placeItemBackInInventory(Wand.make(l), net.minecraft.util.Prediction.SERVER_ONLY);
        c.say(p.level().getServer(), "Here's my wand: left click a block for one corner, right click for the other; right click a chest to tell me what it's for.",
                "Hier ist mein Zauberstab: Linksklick auf einen Block für die eine Ecke, Rechtsklick für die andere; Rechtsklick auf eine Kiste, um mir zu sagen, wofür sie ist.");
    }

    private static void areas(ServerPlayer p, Companion c) {
        Lang l = Lang.of(p);
        if (c.areas.isEmpty()) {
            c.say(p.level().getServer(), "I have no areas. Mark one with my wand.", "Ich habe keine Bereiche. Markiere einen mit meinem Zauberstab.");
            return;
        }
        c.tell(p.level().getServer(), Component.literal(l.t("My areas:", "Meine Bereiche:")));
        for (Area a : c.areas.values()) {
            p.sendSystemMessage(Component.literal("  " + a.name() + " (" + l.t(a.kind().en, a.kind().de) + "): " + a.show() + " ").withStyle(ChatFormatting.GRAY)
                    .append(Menus.button(l.t("show", "zeigen"), "/bob area show " + a.name(), l.t("Outline it", "Umriss zeigen"))).append(" ")
                    .append(Menus.button("x", "/bob area remove " + a.name(), l.t("Remove", "Entfernen"))));
        }
    }

    private static void removeArea(ServerPlayer p, Companion c, String name) {
        if (c.areas.remove(name) == null) c.say(p.level().getServer(), "I have no area \"" + name + "\".", "Ich habe keinen Bereich \"" + name + "\".");
        else c.say(p.level().getServer(), "Area \"" + name + "\" removed.", "Bereich \"" + name + "\" entfernt.");
    }

    /** His pack (but his kit) into the drop-off chest now - or sorted into the storage if there is none. */
    public static void deposit(ServerPlayer p, Companion c) {
        if (!Orders.present(p, c)) return;
        var level = (ServerLevel) c.bot.body().level();
        var chest = c.nearestChest(RoleChest.Role.DROPOFF, level.dimension(), c.bot.body().blockPosition());
        if (chest == null) {
            if (!Storage.boxes(c, level, Set.of(RoleChest.Role.STORAGE, RoleChest.Role.OVERFLOW)).isEmpty()) {
                Orders.give(p, c, new SortJob(SortJob.From.PACK), true);
                return;
            }
            c.say(p.level().getServer(), "I have no drop-off chest. Right click a chest with my wand and pick \"drop-off\".",
                    "Ich habe keine Ablage-Kiste. Klick mit meinem Zauberstab rechts auf eine Kiste und wähle \"Ablage\".");
            return;
        }
        Orders.give(p, c, new DepositJob(), true);
    }

    /** Everything but his kit, handed to the owner. */
    public static void giveAll(ServerPlayer p, Companion c) {
        if (!Orders.present(p, c)) return;
        var keep = Deliveries.keeper(c.bot);
        c.bot.doNow(new GiveTask(p, st -> !keep.test(st), Integer.MAX_VALUE));
        c.say(p.level().getServer(), "Here, all I found - I keep my tools and a bite to eat.", "Hier, alles, was ich gefunden habe - mein Werkzeug und etwas zu essen behalte ich.");
    }

    /** A stack from the owner's hand into Bob's pack (armour put on if it is better). */
    private static void gift(ServerPlayer p, Companion c, ItemStack held) {
        var body = c.bot.body();
        ItemStack copy = held.copy();
        String name = copy.getHoverName().getString();
        int before = copy.getCount();
        body.getInventory().add(copy);
        int taken = before - copy.getCount();
        if (taken <= 0) {
            c.say(p.level().getServer(), "My pack is full.", "Mein Rucksack ist voll.");
            return;
        }
        held.shrink(taken);
        boolean wear = BotArmor.wearBest(c.bot);
        c.say(p.level().getServer(), "Thanks for the " + taken + "× " + name + (wear ? " - I'm wearing it." : "!"),
                "Danke für " + taken + "× " + name + (wear ? " - ich ziehe es gleich an." : "!"));
    }

    private static int fetch(com.mojang.brigadier.context.CommandContext<net.minecraft.commands.CommandSourceStack> ctx, int count)
            throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        String what = StringArgumentType.getString(ctx, "item");
        Set<Item> items = Names.items(what);
        return BobCommands.on(ctx, (p, c) -> {
            if (items.isEmpty()) c.say(p.level().getServer(), "I don't know \"" + what + "\".", "\"" + what + "\" kenne ich nicht.");
            else Orders.give(p, c, new FetchJob(items, count), false);
        });
    }

    /** "Empty your pack": one trip to the drop-off chest. */
    public static final class DepositJob extends Job {
        private boolean done;

        @Override
        public String kind() {
            return "deposit";
        }

        @Override
        public String describe(Lang l) {
            return l.t("empty my pack into the drop-off chest", "meinen Rucksack in die Ablage leeren");
        }

        @Override
        public net.kasax.bobbot.bot.BotTask next(Companion c, net.kasax.bobbot.bot.Bot bot) {
            if (done) return null;
            done = true;
            var chest = c.nearestChest(RoleChest.Role.DROPOFF, bot.body().level().dimension(), bot.body().blockPosition());
            if (chest == null) {
                fail("I have no drop-off chest here", "ich habe hier keine Ablage-Kiste");
                return null;
            }
            return new DepositTask(chest.block(), Deliveries.keeper(bot));
        }
    }
}
