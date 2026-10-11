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

    // ---- names to show (in the owner's language) --------------------------------------------------

    private record Shown(String en, String de) {
    }

    private static final Map<Item, Shown> SHOWN = new HashMap<>();
    private static final Map<EntityType<?>, Shown> SHOWN_MOBS = new HashMap<>();
    private static final java.util.List<java.util.Map.Entry<Set<Item>, Shown>> GROUPS = new java.util.ArrayList<>();

    static {
        GROUPS.add(Map.entry(tagged(ItemTags.LOGS), new Shown("logs", "Holzstämme")));
        GROUPS.add(Map.entry(tagged(ItemTags.PLANKS), new Shown("planks", "Bretter")));
        GROUPS.add(Map.entry(tagged(ItemTags.WOOL), new Shown("wool", "Wolle")));
        GROUPS.add(Map.entry(tagged(ItemTags.SAPLINGS), new Shown("saplings", "Setzlinge")));
        String[][] shown = {
                {"cobblestone", "cobblestone", "Bruchstein"}, {"iron_ingot", "iron ingots", "Eisenbarren"}, {"raw_iron", "raw iron", "Roheisen"},
                {"gold_ingot", "gold ingots", "Goldbarren"}, {"copper_ingot", "copper ingots", "Kupferbarren"}, {"diamond", "diamonds", "Diamanten"},
                {"emerald", "emeralds", "Smaragde"}, {"coal", "coal", "Kohle"}, {"charcoal", "charcoal", "Holzkohle"}, {"bread", "bread", "Brot"},
                {"cooked_beef", "steak", "Steak"}, {"apple", "apples", "Äpfel"}, {"carrot", "carrots", "Karotten"}, {"potato", "potatoes", "Kartoffeln"},
                {"wheat", "wheat", "Weizen"}, {"torch", "torches", "Fackeln"}, {"chest", "chests", "Kisten"}, {"crafting_table", "crafting tables", "Werkbänke"},
                {"furnace", "furnaces", "Öfen"}, {"sand", "sand", "Sand"}, {"gravel", "gravel", "Kies"}, {"glass", "glass", "Glas"}, {"dirt", "dirt", "Erde"},
                {"clay_ball", "clay", "Ton"}, {"leather", "leather", "Leder"}, {"string", "string", "Fäden"}, {"bone", "bones", "Knochen"},
                {"feather", "feathers", "Federn"}, {"arrow", "arrows", "Pfeile"}, {"stick", "sticks", "Stöcke"}, {"sugar_cane", "sugar cane", "Zuckerrohr"},
                {"pumpkin", "pumpkins", "Kürbisse"}, {"melon_slice", "melon slices", "Melonenscheiben"}, {"egg", "eggs", "Eier"}, {"obsidian", "obsidian", "Obsidian"},
                {"slime_ball", "slime balls", "Schleimbälle"}, {"gunpowder", "gunpowder", "Schwarzpulver"}, {"ender_pearl", "ender pearls", "Enderperlen"},
                {"stone", "stone", "Stein"}, {"redstone", "redstone", "Redstone"}, {"lapis_lazuli", "lapis lazuli", "Lapislazuli"}, {"quartz", "quartz", "Quarz"},
                {"oak_log", "oak logs", "Eichenstämme"}, {"birch_log", "birch logs", "Birkenstämme"}, {"spruce_log", "spruce logs", "Fichtenstämme"},
                {"oak_planks", "oak planks", "Eichenbretter"}, {"cooked_porkchop", "cooked porkchops", "gebratene Koteletts"}, {"cooked_chicken", "cooked chicken", "gebratenes Hühnchen"},
                {"wheat_seeds", "wheat seeds", "Weizenkörner"}, {"bucket", "buckets", "Eimer"}, {"water_bucket", "water buckets", "Wassereimer"},
        };
        for (String[] r : shown) {
            Item it = BuiltInRegistries.ITEM.getValue(Identifier.withDefaultNamespace(r[0]));
            if (it != Items.AIR) SHOWN.put(it, new Shown(r[1], r[2]));
        }
        SHOWN_MOBS.put(net.minecraft.world.entity.EntityTypes.COW, new Shown("cows", "Kühe"));
        SHOWN_MOBS.put(net.minecraft.world.entity.EntityTypes.PIG, new Shown("pigs", "Schweine"));
        SHOWN_MOBS.put(net.minecraft.world.entity.EntityTypes.SHEEP, new Shown("sheep", "Schafe"));
        SHOWN_MOBS.put(net.minecraft.world.entity.EntityTypes.CHICKEN, new Shown("chickens", "Hühner"));
        SHOWN_MOBS.put(net.minecraft.world.entity.EntityTypes.RABBIT, new Shown("rabbits", "Kaninchen"));
        SHOWN_MOBS.put(net.minecraft.world.entity.EntityTypes.ZOMBIE, new Shown("zombies", "Zombies"));
        SHOWN_MOBS.put(net.minecraft.world.entity.EntityTypes.SKELETON, new Shown("skeletons", "Skelette"));
        SHOWN_MOBS.put(net.minecraft.world.entity.EntityTypes.SPIDER, new Shown("spiders", "Spinnen"));
        SHOWN_MOBS.put(net.minecraft.world.entity.EntityTypes.CREEPER, new Shown("creepers", "Creeper"));
        SHOWN_MOBS.put(net.minecraft.world.entity.EntityTypes.ENDERMAN, new Shown("endermen", "Endermen"));
        SHOWN_MOBS.put(net.minecraft.world.entity.EntityTypes.SLIME, new Shown("slimes", "Schleime"));
        SHOWN_MOBS.put(net.minecraft.world.entity.EntityTypes.SQUID, new Shown("squids", "Tintenfische"));
    }

    /** What to call these items for the owner: "logs" for any log, "Eisenbarren", else the game's own (English) name. */
    public static String display(Set<Item> items, net.kasax.bobbot.companion.Lang l) {
        if (items.isEmpty()) return "?";
        for (var g : GROUPS) if (g.getKey().containsAll(items) && items.size() > 1) return l.t(g.getValue().en(), g.getValue().de());
        java.util.List<String> out = new java.util.ArrayList<>();
        for (Item i : items) {
            Shown sh = SHOWN.get(i);
            out.add(sh != null ? l.t(sh.en(), sh.de()) : i.getDefaultInstance().getHoverName().getString());
            if (out.size() == 3) break;
        }
        return String.join(l.t(" or ", " oder "), out) + (items.size() > 3 ? " …" : "");
    }

    public static String display(EntityType<?> type, net.kasax.bobbot.companion.Lang l) {
        Shown sh = SHOWN_MOBS.get(type);
        return sh != null ? l.t(sh.en(), sh.de()) : type.getDescription().getString();
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
