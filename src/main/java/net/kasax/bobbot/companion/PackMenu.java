package net.kasax.bobbot.companion;

import net.kasax.bobbot.bot.BotPlayer;
import net.minecraft.ChatFormatting;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.ChestMenu;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.equipment.Equippable;

/**
 * Bob's pack as a chest to look into: his inventory in the top four rows (the hotbar the fourth),
 * then his armour, head to feet, and his other hand. Take things, give him things, dress him -
 * a plain chest screen, so any client shows it.
 */
public final class PackMenu extends ChestMenu {
    private static final EquipmentSlot[] ARMOUR = {EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET};
    private static final int ARMOUR_AT = 36, OFFHAND_AT = 40, SIZE = 45;

    private PackMenu(int id, Inventory viewer, View view) {
        super(MenuType.GENERIC_9x5, id, viewer, view, 5);
        for (int i = ARMOUR_AT; i < SIZE; i++) {
            Slot old = slots.get(i);
            Slot s;
            if (i < OFFHAND_AT) {
                EquipmentSlot es = ARMOUR[i - ARMOUR_AT];
                s = new Slot(view, i, old.x, old.y) {
                    @Override
                    public boolean mayPlace(ItemStack st) {
                        Equippable e = st.get(DataComponents.EQUIPPABLE);
                        return e != null && e.slot() == es;
                    }

                    @Override
                    public int getMaxStackSize() {
                        return 1;
                    }
                };
            } else if (i == OFFHAND_AT) {
                s = new Slot(view, i, old.x, old.y);
            } else {
                s = new Slot(view, i, old.x, old.y) {
                    @Override
                    public boolean mayPlace(ItemStack st) {
                        return false;
                    }

                    @Override
                    public boolean mayPickup(Player p) {
                        return false;
                    }
                };
            }
            s.index = i;
            slots.set(i, s);
        }
    }

    /** Opens Bob's pack to the player. */
    public static void open(ServerPlayer viewer, Companion c) {
        if (c.bot == null || !c.bot.body().isAlive()) return;
        BotPlayer body = c.bot.body();
        Lang l = Lang.of(viewer);
        viewer.openMenu(new SimpleMenuProvider((id, inv, p) -> new PackMenu(id, inv, new View(body)),
                Component.literal(l.t(c.name + "'s pack", c.name + "s Rucksack"))));
    }

    /** The pack, the armour and the other hand of Bob's body as one container of 45 slots. */
    private static final class View implements Container {
        private final BotPlayer body;
        private static final ItemStack FILLER = filler();

        View(BotPlayer body) {
            this.body = body;
        }

        private static ItemStack filler() {
            ItemStack st = new ItemStack(Items.GLASS_PANE);
            st.set(DataComponents.CUSTOM_NAME, Component.literal(" ").withStyle(ChatFormatting.DARK_GRAY));
            st.set(DataComponents.TOOLTIP_DISPLAY, new net.minecraft.world.item.component.TooltipDisplay(true, new java.util.LinkedHashSet<>()));
            return st;
        }

        /** Rows one to three: the pack (slots 9..35); row four: the hotbar (0..8). */
        private static int packSlot(int i) {
            return i < 27 ? i + 9 : i - 27;
        }

        @Override
        public int getContainerSize() {
            return SIZE;
        }

        @Override
        public boolean isEmpty() {
            for (int i = 0; i < SIZE; i++) if (!getItem(i).isEmpty() && i <= OFFHAND_AT) return false;
            return true;
        }

        @Override
        public ItemStack getItem(int i) {
            if (i < ARMOUR_AT) return body.getInventory().getItem(packSlot(i));
            if (i < OFFHAND_AT) return body.getItemBySlot(ARMOUR[i - ARMOUR_AT]);
            if (i == OFFHAND_AT) return body.getItemBySlot(EquipmentSlot.OFFHAND);
            return FILLER;
        }

        @Override
        public ItemStack removeItem(int i, int n) {
            if (i > OFFHAND_AT) return ItemStack.EMPTY;
            ItemStack st = getItem(i);
            if (st.isEmpty()) return ItemStack.EMPTY;
            ItemStack out = st.split(n);
            setItem(i, st);
            return out;
        }

        @Override
        public ItemStack removeItemNoUpdate(int i) {
            if (i > OFFHAND_AT) return ItemStack.EMPTY;
            ItemStack st = getItem(i);
            setItem(i, ItemStack.EMPTY);
            return st;
        }

        @Override
        public void setItem(int i, ItemStack st) {
            if (i < ARMOUR_AT) body.getInventory().setItem(packSlot(i), st);
            else if (i < OFFHAND_AT) body.setItemSlot(ARMOUR[i - ARMOUR_AT], st);
            else if (i == OFFHAND_AT) body.setItemSlot(EquipmentSlot.OFFHAND, st);
        }

        @Override
        public void setChanged() {
        }

        @Override
        public boolean stillValid(Player p) {
            return body.isAlive() && !body.isRemoved();
        }

        @Override
        public void clearContent() {
        }
    }
}
