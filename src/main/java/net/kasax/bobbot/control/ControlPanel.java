package net.kasax.bobbot.control;

import net.kasax.bobbot.companion.Companion;
import net.kasax.bobbot.companion.Lang;
import net.kasax.bobbot.companion.PackMenu;
import net.kasax.bobbot.companion.Settings;
import net.kasax.bobbot.companion.job.Job;
import net.kasax.bobbot.companion.job.SortJob;
import net.kasax.bobbot.protect.Protection;
import net.kasax.bobbot.storage.StorageOrders;
import net.kasax.bobbot.work.CollectJob;
import net.kasax.bobbot.work.FarmJob;
import net.kasax.bobbot.work.GuardJob;
import net.kasax.bobbot.work.LightJob;
import net.kasax.bobbot.work.MineJob;
import net.kasax.bobbot.work.SmeltJob;
import net.kasax.bobbot.work.WoodJob;
import net.minecraft.ChatFormatting;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.ChestMenu;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.ItemLore;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.BiConsumer;

/**
 * Bob's control panel: a chest screen of icons (any client shows it). The top: how he is and what
 * he does. Then orders, work, things, his settings (a click goes to the next value) and the queue
 * (a click: that job now; right click: away). Opened by right clicking Bob, with /bob panel, or
 * the key (B) when the mod is on the client too.
 */
public final class ControlPanel extends ChestMenu {
    private final ServerPlayer viewer;
    private final Companion c;
    private final SimpleContainer icons;
    private final Map<Integer, BiConsumer<ServerPlayer, Boolean>> actions = new HashMap<>();

    private ControlPanel(int id, Inventory inv, ServerPlayer viewer, Companion c, SimpleContainer icons) {
        super(MenuType.GENERIC_9x6, id, inv, icons, 6);
        this.viewer = viewer;
        this.c = c;
        this.icons = icons;
        fill();
    }

    public static void open(ServerPlayer p, Companion c) {
        Lang l = Lang.of(p);
        p.openMenu(new SimpleMenuProvider((id, inv, pl) -> new ControlPanel(id, inv, p, c, new SimpleContainer(54)),
                Component.literal(l.t(c.name + " - orders", c.name + " - Befehle"))));
    }

    private static ItemStack icon(Item item, String name, String... lore) {
        ItemStack st = new ItemStack(item);
        st.set(DataComponents.CUSTOM_NAME, Component.literal(name).withStyle(s -> s.withColor(ChatFormatting.GOLD).withItalic(false)));
        List<Component> lines = new ArrayList<>();
        for (String s : lore) lines.add(Component.literal(s).withStyle(x -> x.withColor(ChatFormatting.GRAY).withItalic(false)));
        st.set(DataComponents.LORE, new ItemLore(lines));
        return st;
    }

    private void put(int slot, ItemStack icon, BiConsumer<ServerPlayer, Boolean> action) {
        icons.setItem(slot, icon);
        if (action != null) actions.put(slot, action);
    }

    /** Every icon set up anew (after a click, so the settings and the queue show what they are now). */
    private void fill() {
        Lang l = Lang.of(viewer);
        actions.clear();
        for (int i = 0; i < 54; i++) icons.setItem(i, ItemStack.EMPTY);
        // How he is.
        String doing = c.paused ? l.t("Paused", "Pausiert") : c.current == null ? l.t("Nothing to do", "Nichts zu tun")
                : c.current.describe(l) + (c.current.progress(l).isEmpty() ? "" : " (" + c.current.progress(l) + ")");
        String health = c.bot == null ? l.t("Not in the world", "Nicht in der Welt")
                : l.t("HP ", "Leben ") + Math.round(c.bot.body().getHealth()) + "/20, " + l.t("food ", "Hunger ") + c.bot.body().getFoodData().getFoodLevel() + "/20";
        put(4, icon(Items.PLAYER_HEAD, c.name, doing, health, l.t(c.queue.size() + " more in the queue", c.queue.size() + " weitere in der Warteschlange")), null);
        // Orders.
        put(9, icon(Items.ENDER_PEARL, l.t("Come", "Komm"), l.t("To you", "Zu dir")), (p, r) -> Orders.come(p, c));
        put(10, icon(Items.LEAD, l.t("Follow", "Folgen"), l.t("Stays with you", "Bleibt bei dir")), (p, r) -> Orders.follow(p, c, false));
        put(11, icon(Items.IRON_SWORD, l.t("Guard me", "Beschützen"), l.t("Follows and fights what attacks you", "Folgt und bekämpft, was dich angreift")), (p, r) -> Orders.follow(p, c, true));
        put(12, icon(Items.COBWEB, l.t("Stay", "Bleib"), l.t("Waits here", "Wartet hier")), (p, r) -> Orders.stay(p, c));
        put(13, icon(Items.BED.red(), l.t("Home", "Nach Hause"), l.t("Right click: here is your home", "Rechtsklick: hier ist dein Zuhause")),
                (p, r) -> {
                    if (r) Orders.setHome(p, c);
                    else Orders.home(p, c);
                });
        put(14, icon(Items.CLOCK, c.paused ? l.t("Go on", "Weiter") : l.t("Pause", "Pause")), (p, r) -> Orders.pause(p, c, !c.paused));
        put(15, icon(Items.ARROW, l.t("Skip", "Überspringen"), l.t("Leave this job, the next one", "Diesen Auftrag lassen, den nächsten")), (p, r) -> Orders.skip(p, c));
        put(16, icon(Items.BARRIER, "Stop", l.t("Everything stopped, the queue cleared", "Alles abbrechen, Warteschlange leeren")), (p, r) -> Orders.stop(p, c));
        put(17, icon(Items.COMPASS, "Status", l.t("Where he is, how he is", "Wo er ist, wie es ihm geht")), (p, r) -> Orders.status(p, c));
        // Work.
        put(18, icon(Items.IRON_AXE, l.t("Wood", "Holz"), l.t("Fell trees and replant; left: 64 logs, right: on and on", "Bäume fällen und nachpflanzen; links: 64 Stämme, rechts: immer weiter")),
                (p, r) -> Orders.give(p, c, new WoodJob(r ? 0 : 64, null), false));
        put(19, icon(Items.IRON_PICKAXE, l.t("Strip mine", "Strip-Mine"), l.t("Left: at y -58 (diamonds), right: at y 16 (iron)", "Links: auf y -58 (Diamanten), rechts: auf y 16 (Eisen)")),
                (p, r) -> Orders.give(p, c, new MineJob(r ? 16 : -58, 48), false));
        put(20, icon(Items.IRON_SHOVEL, l.t("Dig out", "Ausheben"), l.t("The box marked with the wand", "Den mit dem Zauberstab markierten Bereich")),
                (p, r) -> net.kasax.bobbot.work.WorkOrders.dig(p, c));
        put(21, icon(Items.WHEAT, l.t("Harvest", "Ernten"), l.t("Left: once, right: on and on", "Links: einmal, rechts: immer wieder")), (p, r) -> Orders.give(p, c, new FarmJob(null, r), false));
        put(22, icon(Items.FURNACE, l.t("Smelt", "Schmelzen"), l.t("What lies in the drop-off chest", "Was in der Ablage liegt")), (p, r) -> Orders.give(p, c, new SmeltJob(), false));
        put(23, icon(Items.FISHING_ROD, l.t("Fish", "Angeln"), l.t("Ten catches", "Zehn Fänge")), (p, r) -> Orders.fish(p, c, 10));
        put(24, icon(Items.TORCH, l.t("Light up", "Licht"), l.t("No dark spot left round you", "Keine dunkle Stelle mehr um dich")), (p, r) -> Orders.give(p, c, new LightJob(p.blockPosition(), 16), false));
        put(25, icon(Items.SHIELD, l.t("Guard here", "Hier bewachen"), l.t("Fights monsters that come near", "Bekämpft Monster in der Nähe")), (p, r) -> Orders.give(p, c, new GuardJob(p.blockPosition(), 16), true));
        put(26, icon(Items.HOPPER, l.t("Pick up", "Aufsammeln"), l.t("Everything lying about", "Alles, was herumliegt")), (p, r) -> Orders.give(p, c, new CollectJob(p.blockPosition(), 16), false));
        // Things.
        put(27, icon(Items.CHEST, l.t("Sort", "Sortieren"), l.t("Into your chests the way you sorted them", "In deine Kisten, so wie du sortiert hast")), (p, r) -> Orders.give(p, c, new SortJob(SortJob.From.ALL), false));
        put(28, icon(Items.BARREL, l.t("Drop off", "Abliefern"), l.t("His pack into the drop-off chest", "Sein Rucksack in die Ablage")), (p, r) -> StorageOrders.deposit(p, c));
        put(29, icon(Items.BUNDLE, l.t("Give me all", "Gib mir alles"), l.t("All but his kit, to you", "Alles außer seiner Ausrüstung, an dich")), (p, r) -> StorageOrders.giveAll(p, c));
        put(30, icon(Items.LEATHER_CHESTPLATE, l.t("His pack", "Sein Rucksack"), l.t("Give him tools, armour, food", "Werkzeug, Rüstung, Essen geben")), (p, r) -> {
            if (Orders.present(p, c)) PackMenu.open(p, c);
        });
        put(31, icon(Items.BLAZE_ROD, l.t("Wand", "Zauberstab"), l.t("Mark chests and areas", "Kisten und Bereiche markieren")), (p, r) -> StorageOrders.giveWand(p, c));
        put(32, icon(Items.BRICKS, l.t("Build", "Bauen"), l.t("Choose a blueprint (in chat)", "Einen Bauplan wählen (im Chat)")), (p, r) -> {
            p.closeContainer();
            p.level().getServer().getCommands().performPrefixedCommand(p.createCommandSourceStack(), "bob build list");
        });
        put(33, icon(Items.ANVIL, l.t("Protect here", "Hier schützen"), l.t("He never breaks or builds anything round you", "Er baut um dich nie etwas ab oder hin")), (p, r) -> Protection.here(p, c, Protection.DEFAULT_RADIUS));
        put(34, icon(Items.MAP, l.t("Areas", "Bereiche"), l.t("His areas (in chat)", "Seine Bereiche (im Chat)")), (p, r) -> {
            p.closeContainer();
            p.level().getServer().getCommands().performPrefixedCommand(p.createCommandSourceStack(), "bob area list");
        });
        put(35, icon(Items.WRITABLE_BOOK, l.t("Get…", "Holen…"), l.t("Type what he shall get", "Tippe, was er holen soll")), (p, r) -> {
            p.closeContainer();
            p.sendSystemMessage(Menus.suggest(l.t("Bring me…", "Bring mir…"), "/bob get ", l.t("Item and count", "Gegenstand und Anzahl")));
        });
        // Settings: a click, the next value.
        Settings s = c.settings;
        setting(36, Items.IRON_SWORD, "combat", s.combat.name().toLowerCase(), l);
        setting(37, Items.BED.white(), "night", s.night.name().toLowerCase(), l);
        setting(38, Items.BOOK, "talk", s.talk.name().toLowerCase(), l);
        setting(39, Items.ENDER_EYE, "teleport", String.valueOf(s.teleport), l);
        setting(40, Items.TOTEM_OF_UNDYING, "keepInventory", String.valueOf(s.keepInventory), l);
        setting(41, Items.OAK_SAPLING, "replant", String.valueOf(s.replant), l);
        setting(42, Items.LANTERN, "torches", String.valueOf(s.torches), l);
        setting(43, Items.STONE_BRICKS, "protectStructures", String.valueOf(s.protectStructures), l);
        setting(44, Items.CHEST_MINECART, "deliverWhenFull", String.valueOf(s.deliverWhenFull), l);
        // The queue.
        int slot = 45;
        if (c.current != null && slot < 54) put(slot++, icon(Items.FILLED_MAP, l.t("Now: ", "Jetzt: ") + c.current.describe(l), c.current.progress(l)), null);
        int n = 1;
        for (Job j : new ArrayList<>(c.queue)) {
            if (slot >= 54) break;
            final int index = n++;
            put(slot++, icon(Items.PAPER, index + ". " + j.describe(l), l.t("Click: now, right click: remove", "Klick: jetzt, Rechtsklick: entfernen")), (p, r) -> {
                if (r) Orders.queueRemove(p, c, index);
                else Orders.queueNow(p, c, index);
            });
        }
    }

    private void setting(int slot, Item item, String key, String value, Lang l) {
        put(slot, icon(item, key + ": " + value, l.t("Click: next value", "Klick: nächster Wert")), (p, r) -> {
            List<String> vals = Settings.values(key);
            int i = vals.indexOf(value);
            Orders.set(p, c, key, vals.get((i + 1 + vals.size()) % vals.size()));
        });
    }

    @Override
    public void clicked(int slot, int button, ContainerInput input, Player player) {
        if (slot >= 0 && slot < 54) {
            var action = actions.get(slot);
            if (action != null && player instanceof ServerPlayer p) {
                action.accept(p, button == 1);
                if (p.containerMenu == this) {
                    fill();
                    broadcastChanges();
                }
            }
            return;
        }
        // (Nothing of his own into the panel: shift clicks and the like do nothing.)
        if (input == ContainerInput.QUICK_MOVE) return;
        super.clicked(slot, button, input, player);
    }

    @Override
    public ItemStack quickMoveStack(Player player, int slot) {
        return ItemStack.EMPTY;
    }
}
