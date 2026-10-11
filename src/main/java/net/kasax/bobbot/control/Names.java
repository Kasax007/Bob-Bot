package net.kasax.bobbot.control;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Item and mob names as players type them, in English and German ("iron", "Eisen", "diamonds",
 * "Holz", "cows", "Kühe"), to what the game calls them. Registry names work too ("iron_ingot",
 * "minecraft:oak_log").
 */
public final class Names {
    private Names() {
    }

    private static final Map<String, Set<Item>> ITEMS = new HashMap<>();
    private static final Map<String, EntityType<?>> MOBS = new HashMap<>();

    private static void item(Item item, String... words) {
        for (String w : words) ITEMS.computeIfAbsent(w, k -> new LinkedHashSet<>()).add(item);
    }

    private static void items(Set<Item> items, String... words) {
        for (String w : words) ITEMS.computeIfAbsent(w, k -> new LinkedHashSet<>()).addAll(items);
    }

    private static void mob(EntityType<?> type, String... words) {
        for (String w : words) MOBS.put(w, type);
    }

    static {
        items(tagged(ItemTags.LOGS), "wood", "log", "logs", "holz", "stamm", "stämme", "staemme", "baumstamm", "baumstämme");
        items(tagged(ItemTags.PLANKS), "planks", "plank", "bretter", "brett", "holzbretter");
        items(tagged(ItemTags.WOOL), "wool", "wolle");
        items(tagged(ItemTags.SAPLINGS), "sapling", "saplings", "setzling", "setzlinge");
        item(Items.COBBLESTONE, "stone", "cobble", "cobblestone", "stein", "steine", "bruchstein");
        item(Items.IRON_INGOT, "iron", "eisen", "eisenbarren", "ingot", "ingots");
        item(Items.RAW_IRON, "raw_iron", "rohes eisen", "roheisen", "eisenerz");
        item(Items.GOLD_INGOT, "gold", "goldbarren");
        item(Items.COPPER_INGOT, "copper", "kupfer", "kupferbarren");
        item(Items.DIAMOND, "diamond", "diamonds", "diamant", "diamanten", "dias");
        item(Items.EMERALD, "emerald", "emeralds", "smaragd", "smaragde");
        item(Items.COAL, "coal", "kohle");
        item(Items.CHARCOAL, "charcoal", "holzkohle");
        item(Items.REDSTONE, "redstone");
        item(Items.LAPIS_LAZULI, "lapis");
        item(Items.QUARTZ, "quartz", "quarz");
        item(Items.BREAD, "bread", "brot");
        item(Items.COOKED_BEEF, "steak", "fleisch", "meat");
        item(Items.APPLE, "apple", "apples", "apfel", "äpfel", "aepfel");
        item(Items.CARROT, "carrot", "carrots", "karotte", "karotten", "möhren");
        item(Items.POTATO, "potato", "potatoes", "kartoffel", "kartoffeln");
        item(Items.WHEAT, "wheat", "weizen");
        item(Items.TORCH, "torch", "torches", "fackel", "fackeln");
        item(Items.CHEST, "chest", "chests", "kiste", "kisten", "truhe");
        item(Items.CRAFTING_TABLE, "crafting table", "werkbank");
        item(Items.FURNACE, "furnace", "ofen");
        item(Items.SAND, "sand");
        item(Items.GRAVEL, "gravel", "kies");
        item(Items.GLASS, "glass", "glas");
        item(Items.DIRT, "dirt", "erde");
        item(Items.CLAY_BALL, "clay", "ton", "lehm");
        item(Items.LEATHER, "leather", "leder");
        item(Items.STRING, "string", "faden", "fäden");
        item(Items.BONE, "bone", "bones", "knochen");
        item(Items.FEATHER, "feather", "feathers", "feder", "federn");
        item(Items.ARROW, "arrow", "arrows", "pfeil", "pfeile");
        item(Items.STICK, "stick", "sticks", "stock", "stöcke");
        item(Items.SUGAR_CANE, "sugar cane", "zuckerrohr");
        item(Items.PUMPKIN, "pumpkin", "kürbis", "kürbisse");
        item(Items.MELON_SLICE, "melon", "melone");
        item(Items.EGG, "egg", "eggs", "ei", "eier");
        item(Items.OBSIDIAN, "obsidian");
        item(Items.SLIME_BALL, "slime", "schleim");
        item(Items.GUNPOWDER, "gunpowder", "schwarzpulver");
        item(Items.ENDER_PEARL, "pearl", "pearls", "enderperle", "enderperlen");

        mob(net.minecraft.world.entity.EntityTypes.COW, "cow", "cows", "kuh", "kühe", "kuehe", "rind", "rinder");
        mob(net.minecraft.world.entity.EntityTypes.PIG, "pig", "pigs", "schwein", "schweine");
        mob(net.minecraft.world.entity.EntityTypes.SHEEP, "sheep", "schaf", "schafe");
        mob(net.minecraft.world.entity.EntityTypes.CHICKEN, "chicken", "chickens", "huhn", "hühner", "huehner", "hühnchen");
        mob(net.minecraft.world.entity.EntityTypes.RABBIT, "rabbit", "rabbits", "hase", "hasen", "kaninchen");
        mob(net.minecraft.world.entity.EntityTypes.ZOMBIE, "zombie", "zombies");
        mob(net.minecraft.world.entity.EntityTypes.SKELETON, "skeleton", "skeletons", "skelett", "skelette");
        mob(net.minecraft.world.entity.EntityTypes.SPIDER, "spider", "spiders", "spinne", "spinnen");
        mob(net.minecraft.world.entity.EntityTypes.CREEPER, "creeper", "creepers");
        mob(net.minecraft.world.entity.EntityTypes.ENDERMAN, "enderman", "endermen");
        mob(net.minecraft.world.entity.EntityTypes.SLIME, "slime", "slimes", "schleim");
        mob(net.minecraft.world.entity.EntityTypes.SQUID, "squid", "tintenfisch", "tintenfische");
    }

    static Set<Item> tagged(net.minecraft.tags.TagKey<Item> tag) {
        Set<Item> out = new LinkedHashSet<>();
        for (Item i : BuiltInRegistries.ITEM) if (new ItemStack(i).is(tag)) out.add(i);
        return out;
    }

    /** The items a word means, or an empty set. */
    public static Set<Item> items(String text) {
        String t = text.toLowerCase(Locale.ROOT).trim().replaceAll("^(some|ein|eine|einen|etwas|die|der|das|the|a|an)\\s+", "");
        if (t.isEmpty()) return Set.of();
        Set<Item> known = ITEMS.get(t);
        if (known != null) return known;
        // A registry name, as written or with spaces for underscores, singular or plural.
        for (String c : new String[]{t, t.replace(' ', '_'), t.replaceAll("s$", "").replace(' ', '_'), t.replaceAll("es$", "").replace(' ', '_')}) {
            Identifier id = Identifier.tryParse(c.contains(":") ? c : "minecraft:" + c);
            if (id != null) {
                Item it = BuiltInRegistries.ITEM.getValue(id);
                if (it != Items.AIR) return Set.of(it);
            }
        }
        // A known word inside a longer phrase ("rohes eisen", "big oak logs").
        for (String w : t.split("\\s+")) {
            Set<Item> s = ITEMS.get(w);
            if (s != null) return s;
        }
        return Set.of();
    }

    public static EntityType<?> mob(String text) {
        String t = text.toLowerCase(Locale.ROOT).trim();
        EntityType<?> known = MOBS.get(t);
        if (known != null) return known;
        for (String w : t.split("\\s+")) {
            known = MOBS.get(w);
            if (known != null) return known;
        }
        for (String c : new String[]{t.replace(' ', '_'), t.replaceAll("s$", "").replace(' ', '_')}) {
            Identifier id = Identifier.tryParse(c.contains(":") ? c : "minecraft:" + c);
            if (id != null && BuiltInRegistries.ENTITY_TYPE.containsKey(id)) return BuiltInRegistries.ENTITY_TYPE.getValue(id);
        }
        return null;
    }
}
