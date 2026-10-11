package net.kasax.bobbot.companion;

import net.kasax.bobbot.bot.Bot;
import net.kasax.bobbot.bot.BotActions;
import net.kasax.bobbot.bot.BotTask;
import net.kasax.bobbot.companion.task.DepositTask;
import net.minecraft.core.component.DataComponents;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.util.HashMap;
import java.util.Map;

/**
 * The trips to the drop-off chest: when a gathering job has filled the pack, and once more when it
 * is done. What Bob keeps: his tools and weapons, armour and shield, torches, some food and some
 * blocks to build up and across with.
 */
public final class Deliveries {
    private Deliveries() {
    }

    /** Companions whose finished job wants its haul delivered. */
    private static final java.util.Set<Companion> FINAL = java.util.Collections.newSetFromMap(new java.util.WeakHashMap<>());

    static void requestFinal(Companion c) {
        FINAL.add(c);
    }

    /** A trip is due: the pack nearly full during a gathering job, or a finished one's haul. */
    static boolean needed(Companion c, Bot bot) {
        var chest = c.nearestChest(RoleChest.Role.DROPOFF, bot.body().level().dimension(), bot.body().blockPosition());
        if (chest == null) {
            FINAL.remove(c);
            return false;
        }
        if (FINAL.contains(c)) return hasLoot(bot);
        return c.current != null && c.current.delivers() && c.settings.deliverWhenFull && freeSlots(bot) <= 1 && hasLoot(bot);
    }

    static BotTask trip(Companion c, Bot bot) {
        FINAL.remove(c);
        var chest = c.nearestChest(RoleChest.Role.DROPOFF, bot.body().level().dimension(), bot.body().blockPosition());
        return new DepositTask(chest.block(), keeper(bot));
    }

    static int freeSlots(Bot bot) {
        int n = 0;
        for (ItemStack st : bot.body().getInventory().getNonEquipmentItems()) if (st.isEmpty()) n++;
        return n;
    }

    static boolean hasLoot(Bot bot) {
        var keep = keeper(bot);
        for (ItemStack st : bot.body().getInventory().getNonEquipmentItems()) if (!st.isEmpty() && !keep.test(st)) return true;
        return false;
    }

    /** True for what stays in the pack: tools, armour, torches, a stack of food, a stack of blocks. */
    public static java.util.function.Predicate<ItemStack> keeper(Bot bot) {
        // (The first stacks of food and of blocks are kept, the rest goes.)
        Map<String, Integer> kept = new HashMap<>();
        return st -> {
            if (gear(st)) return true;
            if (st.has(DataComponents.FOOD)) return kept.merge("food", st.getCount(), Integer::sum) <= 32;
            if (BotActions.THROWAWAY.contains(st.getItem())) return kept.merge("blocks", st.getCount(), Integer::sum) <= 64;
            return false;
        };
    }

    /** Tools, weapons, armour, the shield, torches, buckets, the rod: his working kit. */
    public static boolean gear(ItemStack st) {
        if (st.isDamageableItem()) return true;
        if (st.is(ItemTags.PICKAXES) || st.is(ItemTags.AXES) || st.is(ItemTags.SHOVELS) || st.is(ItemTags.HOES) || st.is(ItemTags.SWORDS)) return true;
        if (st.has(DataComponents.EQUIPPABLE)) return true;
        return st.is(Items.TORCH) || st.is(Items.BUCKET) || st.is(Items.WATER_BUCKET) || st.is(Items.LAVA_BUCKET)
                || st.is(Items.CRAFTING_TABLE) || st.is(Items.ARROW) || st.is(Items.SHIELD);
    }
}
