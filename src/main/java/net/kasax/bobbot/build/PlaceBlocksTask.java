package net.kasax.bobbot.build;

import net.kasax.bobbot.bot.Bot;
import net.kasax.bobbot.bot.BotTask;
import net.kasax.bobbot.protect.PlacedBlocks;
import net.kasax.bobbot.work.Work;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.BedBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BedPart;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Blocks of a build put down, the lowest first (and of those the nearest): each from the pack,
 * standing within reach and never in its way, the block set the way the blueprint has it (facing,
 * half, axis) - a door with its top, a bed with its head. What its support still lacks waits; what
 * the pack lacks is noted.
 */
public final class PlaceBlocksTask implements BotTask {
    private final List<Site.Target> left;
    private Site.Target current;
    private int ticks, idle;
    private BlockPos aside;
    public int placed;
    public final Set<Item> missing = new HashSet<>();
    public final List<Site.Target> waiting = new ArrayList<>();
    public final Set<BlockPos> blocked = new HashSet<>();

    public PlaceBlocksTask(List<Site.Target> targets) {
        this.left = new ArrayList<>(targets);
    }

    @Override
    public Result tick(Bot bot) {
        ServerLevel level = (ServerLevel) bot.body().level();
        if (++ticks > 20 * 60 * 4) return Result.DONE;
        if (current == null) {
            current = next(bot);
            idle = 0;
            aside = null;
            if (current == null) return Result.DONE;
        }
        BlockPos pos = current.pos();
        BlockState want = current.state(), now = level.getBlockState(pos);
        if (now == want) {
            current = null;
            return Result.RUNNING;
        }
        // The same block, another way round: set right (nothing to take from the pack for that).
        if (now.getBlock() == want.getBlock()) {
            if (reached(bot, pos)) {
                PlacedBlocks.placing(PlacedBlocks.By.PLAYER, () -> level.setBlock(pos, want, Block.UPDATE_ALL));
                current = null;
            }
            return Result.RUNNING;
        }
        // Something else in the way: taken out first if that is his to do, else left.
        if (!now.isAir() && !now.canBeReplaced()) {
            if (!bot.actions().mayBreak(pos)) {
                blocked.add(pos);
                current = null;
                return Result.RUNNING;
            }
            if (reached(bot, pos)) {
                if (!bot.actions().breakTick(pos) && ++idle > 300) {
                    blocked.add(pos);
                    current = null;
                }
            }
            return Result.RUNNING;
        }
        ItemStack need = Site.itemFor(want);
        if (!need.isEmpty() && bot.body().getInventory().countItem(need.getItem()) < need.getCount()) {
            missing.add(need.getItem());
            current = null;
            return Result.RUNNING;
        }
        if (!want.canSurvive(level, pos)) {
            waiting.add(current);
            current = null;
            return Result.RUNNING;
        }
        // Not where he stands (a block that has a body to it).
        if (!want.getCollisionShape(level, pos).isEmpty() && bot.body().getBoundingBox().intersects(new AABB(pos))) {
            stepAside(bot, level, pos);
            return Result.RUNNING;
        }
        if (!reached(bot, pos)) return Result.RUNNING;
        if (put(bot, level, pos, want, need)) placed++;
        current = null;
        return Result.RUNNING;
    }

    private boolean reached(Bot bot, BlockPos pos) {
        switch (Work.reach(bot, pos, 4.4)) {
            case THERE -> {
                return true;
            }
            case NO_WAY -> {
                if (++idle > 3) {
                    bot.say("no way to " + pos.toShortString() + " to build there");
                    blocked.add(pos);
                    current = null;
                }
                return false;
            }
            default -> {
                return false;
            }
        }
    }

    /** The lowest of what is left, and of those the nearest. */
    private Site.Target next(Bot bot) {
        if (left.isEmpty()) return null;
        int lowest = Integer.MAX_VALUE;
        for (Site.Target t : left) lowest = Math.min(lowest, t.pos().getY());
        BlockPos at = bot.body().blockPosition();
        Site.Target best = null;
        double bestD = Double.MAX_VALUE;
        for (Site.Target t : left) {
            if (t.pos().getY() != lowest) continue;
            double d = t.pos().distSqr(at);
            if (d < bestD) {
                bestD = d;
                best = t;
            }
        }
        left.remove(best);
        return best;
    }

    /** Out of the way of a block about to go where he stands: a step to a free spot beside it. */
    private void stepAside(Bot bot, ServerLevel level, BlockPos pos) {
        if (aside == null) {
            // (Two blocks off first: one block off, the body still hangs over the edge into it.)
            for (int n = 2; n >= 1 && aside == null; n--) {
                for (Direction d : Direction.Plane.HORIZONTAL) {
                    if (aside != null) break;
                    BlockPos q = pos.relative(d, n);
                    if (level.getBlockState(q).getCollisionShape(level, q).isEmpty() && level.getBlockState(q.above()).getCollisionShape(level, q.above()).isEmpty()
                            && !level.getBlockState(q.below()).getCollisionShape(level, q.below()).isEmpty()) aside = q;
                }
            }
            if (aside == null) {
                bot.say("standing where " + pos.toShortString() + " goes, and nowhere to step aside");
                waiting.add(current);
                current = null;
                return;
            }
        }
        var nav = bot.navigator();
        var body = bot.body();
        if (body.blockPosition().equals(aside)) {
            // There: into the middle of the block (out of the one next door).
            nav.stop();
            double dx = aside.getX() + 0.5 - body.getX(), dz = aside.getZ() + 0.5 - body.getZ();
            body.setYRot((float) (net.minecraft.util.Mth.atan2(dz, dx) * net.minecraft.util.Mth.RAD_TO_DEG) - 90f);
            body.forward = dx * dx + dz * dz > 0.01 ? 0.3f : 0f;
        } else if (nav.status() != net.kasax.bobbot.bot.BotNavigator.Status.MOVING) nav.goTo(aside);
        else nav.tick();
        if (nav.status() == net.kasax.bobbot.bot.BotNavigator.Status.FAILED || ++idle > 200) {
            bot.say("could not step out of the way of " + pos.toShortString());
            waiting.add(current);
            current = null;
        }
    }

    /** The block put down as the blueprint has it, the item(s) out of the pack. */
    static boolean put(Bot bot, ServerLevel level, BlockPos pos, BlockState want, ItemStack need) {
        var body = bot.body();
        if (!need.isEmpty()) {
            int left = need.getCount();
            var inv = body.getInventory();
            for (int i = 0; i < inv.getNonEquipmentItems().size() && left > 0; i++) {
                ItemStack st = inv.getItem(i);
                if (!st.is(need.getItem())) continue;
                int n = Math.min(left, st.getCount());
                // (A bucket poured out stays a bucket.)
                if (st.is(Items.WATER_BUCKET) || st.is(Items.LAVA_BUCKET)) inv.setItem(i, new ItemStack(Items.BUCKET));
                else st.shrink(n);
                left -= n;
            }
        }
        BlockState state = want;
        if (state.hasProperty(BlockStateProperties.WATERLOGGED)) state = state.setValue(BlockStateProperties.WATERLOGGED, false);
        final BlockState put = state;
        body.lookAt(Vec3.atCenterOf(pos));
        PlacedBlocks.placing(PlacedBlocks.By.PLAYER, () -> {
            // (Two-block things go down whole: a door with its top, a bed with its head.)
            if (put.hasProperty(BlockStateProperties.DOUBLE_BLOCK_HALF) && put.getValue(BlockStateProperties.DOUBLE_BLOCK_HALF) == DoubleBlockHalf.LOWER) {
                level.setBlock(pos, put, Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE);
                level.setBlock(pos.above(), put.setValue(BlockStateProperties.DOUBLE_BLOCK_HALF, DoubleBlockHalf.UPPER), Block.UPDATE_ALL);
            } else if (put.getBlock() instanceof BedBlock && put.getValue(BedBlock.PART) == BedPart.FOOT) {
                level.setBlock(pos, put, Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE);
                level.setBlock(pos.relative(put.getValue(BedBlock.FACING)), put.setValue(BedBlock.PART, BedPart.HEAD), Block.UPDATE_ALL);
            } else {
                level.setBlock(pos, put, Block.UPDATE_ALL);
            }
            return null;
        });
        body.swing(InteractionHand.MAIN_HAND, net.minecraft.world.item.component.SwingAnimation.DEFAULT, true);
        var sound = put.getSoundType();
        level.playSound(null, pos, sound.getPlaceSound(), SoundSource.BLOCKS, (sound.getVolume() + 1f) / 2f, sound.getPitch() * 0.8f);
        return true;
    }

    @Override
    public String describe() {
        return "build (" + left.size() + " left)";
    }
}
