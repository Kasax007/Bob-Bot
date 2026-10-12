package net.kasax.bobbot.work;

import net.fabricmc.fabric.api.tag.convention.v2.ConventionalBlockTags;
import net.kasax.bobbot.bot.Bot;
import net.kasax.bobbot.bot.BotActions;
import net.kasax.bobbot.bot.BotInventory;
import net.kasax.bobbot.bot.BotNavigator;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.FallingBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

import java.util.Set;

/** What Bob's jobs share: getting within reach of a block, ores, liquids, the junk of digging, torches. */
public final class Work {
    private Work() {
    }

    public enum Reach { THERE, ON_THE_WAY, NO_WAY }

    /**
     * Within reach of {@code pos} (from the eyes), walking (or building up) there if not. The walk
     * aims a little closer than the reach; arrived and still a hair too far (the body is not in
     * the middle of its block), a closer spot is asked for - twice, then that will do.
     */
    public static Reach reach(Bot bot, BlockPos pos, double range) {
        var body = bot.body();
        double d = body.getEyePosition().distanceTo(Vec3.atCenterOf(pos));
        if (d <= range) {
            bot.navigator().stop();
            WALKS.remove(bot);
            return Reach.THERE;
        }
        BotNavigator nav = bot.navigator();
        Walk w = WALKS.get(bot);
        if (w == null || !w.pos.equals(pos)) {
            w = new Walk(pos.immutable());
            WALKS.put(bot, w);
            nav.goNear(pos, Math.min(range, BotActions.REACH) - 0.3);
        } else if (nav.status() == BotNavigator.Status.ARRIVED) {
            // (Where the walk ends the eyes are a little off the middle of the block.)
            if (d <= BotActions.REACH + 0.3 || w.closer >= 2) {
                bot.navigator().stop();
                WALKS.remove(bot);
                return d <= BotActions.REACH + 0.6 ? Reach.THERE : Reach.NO_WAY;
            }
            w.closer++;
            nav.goNear(pos, Math.max(1.5, Math.min(range, BotActions.REACH) - 0.3 - w.closer));
        } else if (nav.status() != BotNavigator.Status.MOVING) {
            nav.goNear(pos, Math.min(range, BotActions.REACH) - 0.3);
        }
        BotNavigator.Status s = nav.tick();
        if (s == BotNavigator.Status.FAILED) {
            WALKS.remove(bot);
            return Reach.NO_WAY;
        }
        return Reach.ON_THE_WAY;
    }

    private static final class Walk {
        final BlockPos pos;
        int closer;

        Walk(BlockPos pos) {
            this.pos = pos;
        }
    }

    private static final java.util.Map<Bot, Walk> WALKS = new java.util.WeakHashMap<>();

    /** An ore of any kind (the game's and the common tag, so modded ores count). */
    public static boolean ore(BlockState s) {
        return s.is(ConventionalBlockTags.ORES) || s.is(BlockTags.ORES) || s.is(BlockTags.IRON_ORES) || s.is(BlockTags.COPPER_ORES)
                || s.is(BlockTags.GOLD_ORES) || s.is(net.minecraft.world.level.block.Blocks.NETHER_QUARTZ_ORE)
                || s.is(net.minecraft.world.level.block.Blocks.ANCIENT_DEBRIS);
    }

    /** Lava or water right beside {@code cell} (that would run in once it is opened). */
    public static boolean liquidBeside(ServerLevel level, BlockPos cell) {
        for (Direction d : Direction.values()) {
            if (!level.getFluidState(cell.relative(d)).isEmpty()) return true;
        }
        return !level.getFluidState(cell).isEmpty();
    }

    /** Lava right beside or in {@code cell}. */
    public static boolean lavaBeside(ServerLevel level, BlockPos cell) {
        for (Direction d : Direction.values()) {
            if (level.getFluidState(cell.relative(d)).is(net.minecraft.tags.FluidTags.LAVA)) return true;
        }
        return level.getFluidState(cell).is(net.minecraft.tags.FluidTags.LAVA);
    }

    /** Sand or gravel over {@code cell}: it comes down once the cell is open. */
    public static boolean fallingAbove(ServerLevel level, BlockPos cell) {
        return level.getBlockState(cell.above()).getBlock() instanceof FallingBlock;
    }

    /** Rock and soil of no use in numbers: thrown away beyond a stack to build with. */
    public static final Set<Item> JUNK = Set.of(Items.COBBLESTONE, Items.COBBLED_DEEPSLATE, Items.DIORITE, Items.ANDESITE, Items.GRANITE,
            Items.TUFF, Items.GRAVEL, Items.DIRT, Items.NETHERRACK, Items.CALCITE, Items.DRIPSTONE_BLOCK, Items.SMOOTH_BASALT, Items.BASALT,
            Items.BLACKSTONE, Items.SANDSTONE);

    /**
     * Junk thrown out of the pack (what a player does deep in a mine): everything of it but one
     * stack of cobblestone (or what there is) to build with. Returns the stacks thrown.
     */
    public static int dropJunk(Bot bot) {
        var inv = bot.body().getInventory();
        int kept = 0, thrown = 0;
        for (int i = 0; i < inv.getNonEquipmentItems().size(); i++) {
            ItemStack st = inv.getItem(i);
            if (st.isEmpty() || !JUNK.contains(st.getItem())) continue;
            if (kept < 64 && (st.is(Items.COBBLESTONE) || st.is(Items.COBBLED_DEEPSLATE) || st.is(Items.DIRT) || st.is(Items.NETHERRACK))) {
                kept += st.getCount();
                continue;
            }
            bot.body().drop(st.copy(), false, net.minecraft.util.Prediction.SERVER_ONLY);
            inv.setItem(i, ItemStack.EMPTY);
            thrown++;
        }
        return thrown;
    }

    public static int freeSlots(Bot bot) {
        int n = 0;
        for (ItemStack st : bot.body().getInventory().getNonEquipmentItems()) if (st.isEmpty()) n++;
        return n;
    }

    /** A torch put on the floor at {@code cell} (in the dark, with a torch in the pack). */
    public static boolean torch(Bot bot, BlockPos cell) {
        var body = bot.body();
        ServerLevel level = (ServerLevel) body.level();
        if (!level.getBlockState(cell).isAir()) return false;
        if (level.getBrightness(net.minecraft.world.level.LightLayer.BLOCK, cell) >= 8) return false;
        if (level.getBlockState(cell.below()).getCollisionShape(level, cell.below()).isEmpty()) return false;
        int slot = BotInventory.slotOf(body, Items.TORCH);
        if (slot < 0 || !bot.actions().inReach(cell)) return false;
        bot.tools().select(slot);
        return bot.actions().placeHeld(cell);
    }
}
