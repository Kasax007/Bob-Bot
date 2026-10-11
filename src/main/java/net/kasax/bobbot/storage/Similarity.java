package net.kasax.bobbot.storage;

import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.tags.ItemTags;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.Map;
import java.util.Set;

/**
 * How alike two kinds of item are, the way a player sorts: logs with logs, ingots with ingots,
 * the iron things together, food with food, tools with tools. 1 is the same item, 0 nothing in
 * common. Worked out from the item tags (the game's own groups and the common "c:" ones), the
 * words of the names, and what the items are (blocks, food, tools, armour).
 */
public final class Similarity {
    private Similarity() {
    }

    private static final Map<Item, Set<TagKey<Item>>> TAGS = new IdentityHashMap<>();

    private static Set<TagKey<Item>> tags(Item item) {
        return TAGS.computeIfAbsent(item, i -> {
            Set<TagKey<Item>> out = new HashSet<>();
            i.builtInRegistryHolder().tags().forEach(out::add);
            return out;
        });
    }

    /** Forgotten (tags reloaded). */
    public static void clear() {
        TAGS.clear();
    }

    public static double of(ItemStack a, ItemStack b) {
        return of(a.getItem(), b.getItem());
    }

    public static double of(Item a, Item b) {
        if (a == b) return 1;
        double score = 0;
        // The game's groups: shared tags, against all the two have.
        Set<TagKey<Item>> ta = tags(a), tb = tags(b);
        if (!ta.isEmpty() && !tb.isEmpty()) {
            int shared = 0;
            for (TagKey<Item> t : ta) if (tb.contains(t)) shared++;
            int all = ta.size() + tb.size() - shared;
            if (shared > 0) score = Math.max(score, 0.35 + 0.5 * shared / (double) all);
        }
        // The words of the names: "oak_log" and "birch_log" end alike, "iron_ingot" and
        // "iron_block" start alike.
        String[] wa = BuiltInRegistries.ITEM.getKey(a).getPath().split("_"), wb = BuiltInRegistries.ITEM.getKey(b).getPath().split("_");
        if (wa.length > 1 && wb.length > 1 && wa[wa.length - 1].equals(wb[wb.length - 1])) score = Math.max(score, 0.6);
        if (wa[0].equals(wb[0]) && (wa.length > 1 || wb.length > 1)) score = Math.max(score, 0.5);
        // What they are.
        ItemStack sa = new ItemStack(a), sb = new ItemStack(b);
        boolean foodA = sa.has(DataComponents.FOOD), foodB = sb.has(DataComponents.FOOD);
        if (foodA && foodB) score = Math.max(score, 0.55);
        boolean toolA = sa.has(DataComponents.TOOL) || sa.is(ItemTags.SWORDS), toolB = sb.has(DataComponents.TOOL) || sb.is(ItemTags.SWORDS);
        if (toolA && toolB) score = Math.max(score, 0.5);
        boolean armourA = sa.has(DataComponents.EQUIPPABLE) && !(a instanceof BlockItem), armourB = sb.has(DataComponents.EQUIPPABLE) && !(b instanceof BlockItem);
        if (armourA && armourB) score = Math.max(score, 0.5);
        if (foodA != foodB || toolA != toolB || armourA != armourB) score *= 0.8;
        // (Blocks with blocks, things with things: a little alike, never enough on its own.)
        if (a instanceof BlockItem == b instanceof BlockItem) score = Math.max(score, 0.15);
        return Math.min(score, 0.95);
    }
}
