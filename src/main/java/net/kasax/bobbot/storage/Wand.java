package net.kasax.bobbot.storage;

import net.fabricmc.fabric.api.event.player.AttackBlockCallback;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.fabricmc.fabric.api.event.player.UseItemCallback;
import net.kasax.bobbot.companion.Area;
import net.kasax.bobbot.companion.Companion;
import net.kasax.bobbot.companion.Lang;
import net.kasax.bobbot.companion.RoleChest;
import net.kasax.bobbot.control.Menus;
import net.kasax.bobbot.control.Orders;
import net.kasax.bobbot.control.Show;
import net.kasax.bobbot.world.Containers;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.component.ItemLore;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Bob's wand: a stick with a shine, to show him things by clicking. Left click a block: one corner,
 * right click: the other - then what the box is for (Bob's work, protected, a storage room).
 * Right click a chest: what the chest is for. A plain stick underneath, so any client can join.
 */
public final class Wand {
    private Wand() {
    }

    private static final String MARK = "bobbot_wand";

    public static ItemStack make(Lang l) {
        ItemStack st = new ItemStack(Items.STICK);
        st.set(DataComponents.CUSTOM_NAME, Component.literal(l.t("Bob's Wand", "Bobs Zauberstab")).withStyle(s -> s.withColor(ChatFormatting.GOLD).withItalic(false)));
        st.set(DataComponents.ENCHANTMENT_GLINT_OVERRIDE, true);
        st.set(DataComponents.LORE, new ItemLore(List.of(
                lore(l.t("Left click a block: first corner", "Linksklick auf Block: erste Ecke")),
                lore(l.t("Right click a block: second corner", "Rechtsklick auf Block: zweite Ecke")),
                lore(l.t("Right click a chest: give it a role", "Rechtsklick auf Kiste: Rolle geben")),
                lore(l.t("Right click the air: Bob's menu", "Rechtsklick in die Luft: Bobs Menü")))));
        CompoundTag tag = new CompoundTag();
        tag.putBoolean(MARK, true);
        st.set(DataComponents.CUSTOM_DATA, CustomData.of(tag));
        st.set(DataComponents.MAX_STACK_SIZE, 1);
        return st;
    }

    private static Component lore(String s) {
        return Component.literal(s).withStyle(st -> st.withColor(ChatFormatting.GRAY).withItalic(false));
    }

    public static boolean is(ItemStack st) {
        if (st.isEmpty() || !st.is(Items.STICK)) return false;
        CustomData d = st.get(DataComponents.CUSTOM_DATA);
        return d != null && d.copyTag().getBooleanOr(MARK, false);
    }

    /** The corners a player has clicked (per player; one box at a time). */
    private static final Map<UUID, GlobalPos[]> CORNERS = new HashMap<>();

    public static void register() {
        AttackBlockCallback.EVENT.register((player, level, hand, pos, dir) -> {
            if (!(player instanceof ServerPlayer p) || !is(p.getItemInHand(hand))) return InteractionResult.PASS;
            corner(p, 0, pos);
            return InteractionResult.SUCCESS;
        });
        UseBlockCallback.EVENT.register((player, level, hand, hit) -> {
            if (!(player instanceof ServerPlayer p) || !is(p.getItemInHand(hand))) return InteractionResult.PASS;
            BlockPos pos = hit.getBlockPos();
            if (!p.isShiftKeyDown() && Containers.isContainer(p.level(), pos)) chestMenu(p, pos);
            else corner(p, 1, pos);
            return InteractionResult.SUCCESS;
        });
        UseItemCallback.EVENT.register((player, level, hand) -> {
            if (!(player instanceof ServerPlayer p) || !is(p.getItemInHand(hand))) return InteractionResult.PASS;
            if (p.isShiftKeyDown()) {
                CORNERS.remove(p.getUUID());
                p.sendSystemMessage(Component.literal(Lang.of(p).t("Corners cleared.", "Ecken gelöscht.")).withStyle(ChatFormatting.GRAY));
            } else {
                Companion c = Orders.target(p, true);
                if (c != null) Menus.show(p, c);
            }
            return InteractionResult.SUCCESS;
        });
    }

    public static void corner(ServerPlayer p, int which, BlockPos pos) {
        Lang l = Lang.of(p);
        GlobalPos[] cs = CORNERS.computeIfAbsent(p.getUUID(), k -> new GlobalPos[2]);
        cs[which] = GlobalPos.of(p.level().dimension(), pos.immutable());
        // (Corners in two dimensions make no box: the other one goes.)
        if (cs[1 - which] != null && cs[1 - which].dimension() != p.level().dimension()) cs[1 - which] = null;
        p.sendSystemMessage(Component.literal(l.t(which == 0 ? "First corner: " : "Second corner: ", which == 0 ? "Erste Ecke: " : "Zweite Ecke: ")
                + pos.toShortString()).withStyle(ChatFormatting.GRAY));
        if (cs[0] == null || cs[1] == null) return;
        BlockPos a = cs[0].pos(), b = cs[1].pos();
        Show.box(p, a, b, 8, false);
        int dx = Math.abs(a.getX() - b.getX()) + 1, dy = Math.abs(a.getY() - b.getY()) + 1, dz = Math.abs(a.getZ() - b.getZ()) + 1;
        MutableComponent m = Component.literal(l.t("Box " + dx + "×" + dy + "×" + dz + ": ", "Bereich " + dx + "×" + dy + "×" + dz + ": "));
        m.append(Menus.button(l.t("Work here", "Hier arbeiten"), "/bob area add work", l.t("Bob's jobs (felling, farming, digging) stay in this box", "Bobs Arbeit (Fällen, Ernten, Graben) bleibt in diesem Bereich"))).append(" ");
        m.append(Menus.button(l.t("Protect", "Schützen"), "/bob area add protected", l.t("Bob never breaks or builds anything in it", "Bob baut darin nie etwas ab oder hin"))).append(" ");
        m.append(Menus.button(l.t("Storage room", "Lagerraum"), "/bob area add storage", l.t("Every chest in it is part of your sorted storage", "Jede Kiste darin gehört zu deinem sortierten Lager"))).append(" ");
        m.append(Menus.suggest(l.t("Dig it out", "Ausheben"), "/bob dig", l.t("Bob digs out the whole box (a quarry, a cellar)", "Bob hebt den ganzen Bereich aus (Steinbruch, Keller)")));
        p.sendSystemMessage(m);
    }

    /** The box the player has marked (both corners in his dimension), or null. */
    public static BlockPos[] box(ServerPlayer p) {
        GlobalPos[] cs = CORNERS.get(p.getUUID());
        if (cs == null || cs[0] == null || cs[1] == null || cs[0].dimension() != p.level().dimension() || cs[1].dimension() != p.level().dimension()) return null;
        return new BlockPos[]{cs[0].pos(), cs[1].pos()};
    }

    /** The marked box becomes one of Bob's areas. */
    public static void addArea(ServerPlayer p, Companion c, Area.Kind kind) {
        Lang l = Lang.of(p);
        BlockPos[] box = box(p);
        if (box == null) {
            c.say(p.level().getServer(), "Mark a box with my wand first: left click one corner, right click the other.",
                    "Markiere zuerst einen Bereich mit meinem Zauberstab: Linksklick die eine Ecke, Rechtsklick die andere.");
            return;
        }
        String base = switch (kind) {
            case WORK -> "work";
            case PROTECTED -> "protect";
            case STORAGE -> "storage";
        };
        int i = 1;
        while (c.areas.containsKey(base + i)) i++;
        Area a = Area.of(base + i, p.level().dimension(), box[0], box[1], kind);
        c.areas.put(a.name(), a);
        Show.box(p, a.min(), a.max(), 5, kind == Area.Kind.PROTECTED);
        CORNERS.remove(p.getUUID());
        switch (kind) {
            case WORK -> c.say(p.level().getServer(), "Got it: \"" + a.name() + "\" is where I work (" + a.show() + ").",
                    "Alles klar: In \"" + a.name() + "\" arbeite ich (" + a.show() + ").");
            case PROTECTED -> c.say(p.level().getServer(), "Got it: I won't break or build anything in \"" + a.name() + "\".",
                    "Alles klar: In \"" + a.name() + "\" baue ich nichts ab und nichts hin.");
            case STORAGE -> {
                int n = Storage.containersIn((ServerLevel) p.level(), a).size();
                c.say(p.level().getServer(), "Got it: \"" + a.name() + "\" is your storage room - " + n + " chests and barrels I sort into.",
                        "Alles klar: \"" + a.name() + "\" ist dein Lagerraum - " + n + " Kisten und Fässer, in die ich einsortiere.");
            }
        }
    }

    /** What the chest is for: a button per role. */
    private static void chestMenu(ServerPlayer p, BlockPos pos) {
        Lang l = Lang.of(p);
        Companion c = Orders.target(p, true);
        if (c == null) return;
        BlockPos k = Storage.canonical((ServerLevel) p.level(), pos);
        RoleChest now = null;
        for (RoleChest rc : c.chests) if (rc.pos().dimension() == p.level().dimension() && Storage.canonical((ServerLevel) p.level(), rc.block()).equals(k)) now = rc;
        MutableComponent m = Component.literal(now == null ? l.t("This chest is for: ", "Diese Kiste ist für: ")
                : l.t("This is my " + now.role().en + " chest. Change to: ", "Das ist meine " + now.role().de + "-Kiste. Ändern zu: "));
        String at = pos.getX() + " " + pos.getY() + " " + pos.getZ();
        for (RoleChest.Role r : RoleChest.Role.values()) {
            if (now != null && now.role() == r) continue;
            m.append(Menus.button(r.label(l), "/bob chest set " + r.name().toLowerCase(java.util.Locale.ROOT) + " " + at, hint(l, r))).append(" ");
        }
        if (now != null) m.append(Menus.button(l.t("none", "keine"), "/bob chest unset " + at, l.t("Not one of Bob's chests any more", "Nicht mehr Bobs Kiste")));
        c.tell(p.level().getServer(), m);
    }

    private static String hint(Lang l, RoleChest.Role r) {
        return switch (r) {
            case DROPOFF -> l.t("Bob empties his pack here after a job", "Bob leert hier nach einem Auftrag seinen Rucksack");
            case INPUT -> l.t("Materials for building and crafting", "Material zum Bauen und Herstellen");
            case STORAGE -> l.t("Part of your sorted storage", "Teil deines sortierten Lagers");
            case OVERFLOW -> l.t("What has no place in the storage", "Was im Lager keinen Platz hat");
            case FUEL -> l.t("Coal and wood for the furnaces", "Kohle und Holz für die Öfen");
            case FOOD -> l.t("Food Bob may eat and take along", "Essen, das Bob essen und mitnehmen darf");
        };
    }
}
