package net.kasax.bobbot.world;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Container;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.ChestBlock;
import net.minecraft.world.level.block.entity.BaseContainerBlockEntity;
import net.minecraft.world.level.block.state.BlockState;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Predicate;

/** Chests, barrels and shulker boxes as Bob reaches into them: a double chest is one container. */
public final class Containers {
    private Containers() {
    }

    /** The container at {@code pos} (both halves of a double chest), or null if there is none. */
    public static Container at(ServerLevel level, BlockPos pos) {
        BlockState state = level.getBlockState(pos);
        if (state.getBlock() instanceof ChestBlock chest) {
            Container c = ChestBlock.getContainer(chest, state, level, pos, true);
            if (c != null) return c;
        }
        var be = level.getBlockEntity(pos);
        return be instanceof BaseContainerBlockEntity c ? c : null;
    }

    public static boolean isContainer(ServerLevel level, BlockPos pos) {
        return at(level, pos) != null;
    }

    /** How many of what matches the container holds. */
    public static int count(Container c, Predicate<ItemStack> what) {
        int n = 0;
        for (int i = 0; i < c.getContainerSize(); i++) {
            ItemStack st = c.getItem(i);
            if (!st.isEmpty() && what.test(st)) n += st.getCount();
        }
        return n;
    }

    /** Puts as much of the stack in as fits (onto like stacks first, then empty slots); returns what is left. */
    public static ItemStack insert(Container c, ItemStack stack) {
        ItemStack left = stack.copy();
        for (int i = 0; i < c.getContainerSize() && !left.isEmpty(); i++) {
            ItemStack in = c.getItem(i);
            if (in.isEmpty() || !ItemStack.isSameItemSameComponents(in, left) || !c.canPlaceItem(i, left)) continue;
            int room = Math.min(c.getMaxStackSize(in), in.getMaxStackSize()) - in.getCount();
            if (room <= 0) continue;
            int n = Math.min(room, left.getCount());
            in.grow(n);
            left.shrink(n);
        }
        for (int i = 0; i < c.getContainerSize() && !left.isEmpty(); i++) {
            if (!c.getItem(i).isEmpty() || !c.canPlaceItem(i, left)) continue;
            int n = Math.min(left.getCount(), Math.min(c.getMaxStackSize(left), left.getMaxStackSize()));
            c.setItem(i, left.split(n));
        }
        c.setChanged();
        return left;
    }

    /** Takes up to {@code max} of what matches; returns the stacks taken. */
    public static List<ItemStack> take(Container c, Predicate<ItemStack> what, int max) {
        List<ItemStack> out = new ArrayList<>();
        int left = max;
        for (int i = 0; i < c.getContainerSize() && left > 0; i++) {
            ItemStack st = c.getItem(i);
            if (st.isEmpty() || !what.test(st)) continue;
            ItemStack got = st.split(Math.min(left, st.getCount()));
            left -= got.getCount();
            out.add(got);
        }
        c.setChanged();
        return out;
    }

    /** Free slots (for the screen and for choosing where to put things). */
    public static int freeSlots(Container c) {
        int n = 0;
        for (int i = 0; i < c.getContainerSize(); i++) if (c.getItem(i).isEmpty()) n++;
        return n;
    }

    /** The lid opens (as when a player looks in) - the sound and the animation. */
    public static void open(ServerLevel level, BlockPos pos, boolean open) {
        BlockState state = level.getBlockState(pos);
        if (state.getBlock() instanceof ChestBlock) {
            level.blockEvent(pos, state.getBlock(), 1, open ? 1 : 0);
            level.playSound(null, pos, open ? net.minecraft.sounds.SoundEvents.CHEST_OPEN : net.minecraft.sounds.SoundEvents.CHEST_CLOSE,
                    net.minecraft.sounds.SoundSource.BLOCKS, 0.5f, level.getRandom().nextFloat() * 0.1f + 0.9f);
        } else if (state.getBlock() instanceof net.minecraft.world.level.block.BarrelBlock) {
            level.playSound(null, pos, open ? net.minecraft.sounds.SoundEvents.BARREL_OPEN : net.minecraft.sounds.SoundEvents.BARREL_CLOSE,
                    net.minecraft.sounds.SoundSource.BLOCKS, 0.5f, 1f);
        }
    }
}
