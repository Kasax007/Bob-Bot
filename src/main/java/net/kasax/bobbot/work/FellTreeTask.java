package net.kasax.bobbot.work;

import net.kasax.bobbot.bot.Bot;
import net.kasax.bobbot.bot.BotInventory;
import net.kasax.bobbot.bot.BotTask;
import net.kasax.bobbot.bot.task.CollectDropsTask;
import net.kasax.bobbot.protect.PlacedBlocks;
import net.kasax.bobbot.protect.Trees;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;

import java.util.HashSet;
import java.util.Set;

/**
 * One tree felled the way a player does it: the trunk from the bottom up (building up beside it
 * for what is out of reach), what fell picked up, his own pillar taken down again, and a sapling
 * of the same kind put back where the trunk stood.
 */
public final class FellTreeTask implements BotTask {
    private enum Phase { FELL, DOWN, COLLECT, CLEAN, PLANT, DONE }

    private final Trees.Tree tree;
    private final boolean replant;
    private final Set<BlockPos> skipped = new HashSet<>();
    private Phase phase = Phase.FELL;
    private BlockPos current;
    private CollectDropsTask collect;
    private int ticks, idle, downSteps;
    /** Logs broken (for the job's count). */
    public int felled;

    /** The kind of log the tree is of (for its sapling). */
    private final net.minecraft.world.level.block.Block kind;

    public FellTreeTask(Trees.Tree tree, net.minecraft.world.level.block.Block kind, boolean replant) {
        this.tree = tree;
        this.kind = kind;
        this.replant = replant;
    }

    @Override
    public Result tick(Bot bot) {
        ServerLevel level = (ServerLevel) bot.body().level();
        if (++ticks > 20 * 120) return felled > 0 ? Result.DONE : Result.FAILED;
        switch (phase) {
            case FELL -> fell(bot, level);
            case DOWN -> down(bot, level);
            case COLLECT -> {
                if (collect == null) collect = new CollectDropsTask(tree.base(), 7, 160);
                if (collect.tick(bot) != Result.RUNNING) phase = Phase.CLEAN;
            }
            case CLEAN -> {
                if (clean(bot, level)) phase = replant ? Phase.PLANT : Phase.DONE;
            }
            case PLANT -> {
                // (To the stump first: the sapling goes where the trunk stood.)
                Work.Reach r = Work.reach(bot, tree.base(), 4.0);
                if (r == Work.Reach.ON_THE_WAY && ticks < 20 * 110) return Result.RUNNING;
                plant(bot, level);
                phase = Phase.DONE;
            }
            case DONE -> {
                return Result.DONE;
            }
        }
        return Result.RUNNING;
    }

    private void fell(Bot bot, ServerLevel level) {
        if (current == null || !level.getBlockState(current).is(BlockTags.LOGS)) {
            current = null;
            for (BlockPos p : tree.logs()) {
                if (skipped.contains(p) || !level.getBlockState(p).is(BlockTags.LOGS) || !bot.actions().mayBreak(p)) continue;
                current = p;
                break;
            }
            idle = 0;
            if (current == null) {
                phase = Phase.COLLECT;
                return;
            }
        }
        switch (Work.reach(bot, current, 4.2)) {
            case THERE -> {
                if (bot.actions().breakTick(current)) {
                    felled++;
                    current = null;
                } else if (++idle > 400) {
                    skipped.add(current);
                    current = null;
                }
            }
            case NO_WAY -> {
                skipped.add(current);
                current = null;
            }
            case ON_THE_WAY -> {
            }
        }
    }

    /** Standing on a pillar of his own: taken down block by block (dug from on top). */
    private void down(Bot bot, ServerLevel level) {
        var body = bot.body();
        BlockPos under = body.blockPosition().below();
        if (body.onGround() && PlacedBlocks.at(level, under) == PlacedBlocks.By.BOB && downSteps < 24) {
            bot.navigator().stop();
            body.stopInputs();
            if (bot.actions().breakTick(under)) downSteps++;
            return;
        }
        if (!body.onGround() && downSteps > 0) return; // (on the way down)
        phase = Phase.COLLECT;
    }

    /**
     * What is left of his own blocks round the stump (the pillar he stood on, a step) taken away,
     * top first. True once there is none (or it cannot be done).
     */
    private boolean clean(Bot bot, ServerLevel level) {
        BlockPos base = tree.base();
        if (cleaning == null || level.getBlockState(cleaning).isAir()) {
            cleaning = null;
            for (int y = 14; y >= 0 && cleaning == null; y--) {
                for (int dx = -1; dx <= 1 && cleaning == null; dx++) {
                    for (int dz = -1; dz <= 1 && cleaning == null; dz++) {
                        BlockPos p = base.offset(dx, y, dz);
                        if (!skipped.contains(p) && PlacedBlocks.at(level, p) == PlacedBlocks.By.BOB && !level.getBlockState(p).isAir()) cleaning = p;
                    }
                }
            }
            if (cleaning == null) return true;
        }
        switch (Work.reach(bot, cleaning, 4.2)) {
            case THERE -> bot.actions().breakTick(cleaning);
            case NO_WAY -> {
                skipped.add(cleaning);
                cleaning = null;
            }
            case ON_THE_WAY -> {
            }
        }
        return false;
    }

    private BlockPos cleaning;

    /** A sapling of the tree's own kind (any, else) where the trunk stood. */
    private void plant(Bot bot, ServerLevel level) {
        BlockPos base = tree.base();
        if (!level.getBlockState(base).isAir()) return;
        var body = bot.body();
        int slot = -1;
        Item own = saplingFor(kind);
        if (own != null && grows(own, level, base)) slot = BotInventory.slotOf(body, own);
        if (slot < 0) {
            var items = body.getInventory().getNonEquipmentItems();
            for (int i = 0; i < items.size() && slot < 0; i++) if (items.get(i).is(ItemTags.SAPLINGS) && grows(items.get(i).getItem(), level, base)) slot = i;
        }
        if (slot < 0) {
            bot.say("no sapling to plant at " + base.toShortString());
            return;
        }
        if (body.getEyePosition().distanceTo(net.minecraft.world.phys.Vec3.atCenterOf(base)) > 5.0) {
            bot.say("too far from the stump to plant");
            return;
        }
        bot.tools().select(slot);
        if (!bot.actions().placeHeld(base)) bot.say("could not plant at " + base.toShortString());
    }

    /** Whether this sapling takes root at {@code p} (the ground it needs under it). */
    private static boolean grows(Item sapling, ServerLevel level, BlockPos p) {
        return sapling instanceof net.minecraft.world.item.BlockItem bi && bi.getBlock().defaultBlockState().canSurvive(level, p);
    }

    /** The sapling for a kind of log, or null if there is none by that name. */
    public static Item saplingFor(net.minecraft.world.level.block.Block log) {
        Identifier id = BuiltInRegistries.BLOCK.getKey(log);
        String path = id.getPath().replace("stripped_", "").replace("_log", "_sapling").replace("_wood", "_sapling").replace("_stem", "_fungus");
        if (path.equals("mangrove_sapling")) path = "mangrove_propagule";
        Item it = BuiltInRegistries.ITEM.getValue(Identifier.fromNamespaceAndPath(id.getNamespace(), path));
        return it == Items.AIR ? null : it;
    }

    @Override
    public String describe() {
        return "fell the tree at " + tree.base().toShortString();
    }

}
