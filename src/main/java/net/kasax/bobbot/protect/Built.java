package net.kasax.bobbot.protect;

import net.kasax.bobbot.bot.plan.BotKnowledge;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.MinecraftServer;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;

import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Set;

/**
 * Blocks somebody made: what comes out of a crafting table or a furnace (planks, glass, bricks,
 * doors, wool, concrete...) and is not also simply found in the land (sandstone, terracotta,
 * packed ice are both). Seen in the world, they are part of something somebody built - a house
 * from before Bob kept count, a village - and Bob does not dig through them.
 */
public final class Built {
    private Built() {
    }

    /** Made, but lying about in nature as well: no sign of a building. */
    private static final Set<Block> NATURAL = Set.of(Blocks.STONE, Blocks.DEEPSLATE, Blocks.SMOOTH_BASALT, Blocks.SANDSTONE,
            Blocks.RED_SANDSTONE, Blocks.PACKED_ICE, Blocks.BLUE_ICE, Blocks.SNOW_BLOCK, Blocks.CLAY, Blocks.COARSE_DIRT,
            Blocks.DRIPSTONE_BLOCK, Blocks.AMETHYST_BLOCK, Blocks.BONE_BLOCK, Blocks.MAGMA_BLOCK, Blocks.GLOWSTONE,
            Blocks.MOSSY_COBBLESTONE, Blocks.SPONGE, Blocks.WET_SPONGE, Blocks.RAW_IRON_BLOCK, Blocks.RAW_COPPER_BLOCK,
            Blocks.MUDDY_MANGROVE_ROOTS, Blocks.MOSS_CARPET, Blocks.PALE_MOSS_CARPET, Blocks.SNOW, Blocks.COBWEB,
            Blocks.HONEYCOMB_BLOCK, Blocks.SLIME_BLOCK, Blocks.CRACKED_STONE_BRICKS, Blocks.DIRT, Blocks.COBBLESTONE,
            Blocks.COBBLED_DEEPSLATE, Blocks.CALCITE, Blocks.TUFF, Blocks.OBSIDIAN,
            // (Grown on a field or in the jungle, and harvested: a melon is not a building.)
            Blocks.MELON);

    private static volatile Set<Block> crafted;
    private static MinecraftServer madeFor;

    /** Every made block (worked out once from the recipes; safe to read from any thread after). */
    public static Set<Block> crafted(MinecraftServer server) {
        Set<Block> c = crafted;
        if (c != null && madeFor == server) return c;
        BotKnowledge k = BotKnowledge.get(server);
        Set<Block> out = Collections.newSetFromMap(new IdentityHashMap<>());
        for (Block b : BuiltInRegistries.BLOCK) {
            Item item = b.asItem();
            if (item == Items.AIR || NATURAL.contains(b) || b.defaultBlockState().is(BlockTags.TERRACOTTA)) continue;
            if (b.defaultBlockState().is(BlockTags.LOGS) || b.defaultBlockState().is(BlockTags.LEAVES)) continue;
            if (!k.routesTo(item).isEmpty()) out.add(b);
        }
        // (Not made at a table or a furnace, but never found loose in the land either: concrete
        // sets from its powder in water.)
        for (Block b : BuiltInRegistries.BLOCK) {
            if (BuiltInRegistries.BLOCK.getKey(b).getPath().endsWith("_concrete")) out.add(b);
        }
        crafted = Collections.unmodifiableSet(out);
        madeFor = server;
        return crafted;
    }

    public static boolean isCrafted(MinecraftServer server, Block b) {
        return crafted(server).contains(b);
    }
}
