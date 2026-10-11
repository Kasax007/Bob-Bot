package net.kasax.bobbot.companion.task;

import net.kasax.bobbot.bot.Bot;
import net.kasax.bobbot.bot.BotNavigator;
import net.kasax.bobbot.bot.BotTask;
import net.kasax.bobbot.world.Containers;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Container;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;

import java.util.List;
import java.util.function.Predicate;

/**
 * To a chest, the lid up, and something out of it or into it - the way a player does it, standing
 * at the chest, a moment with it open. Taking: up to a count of what matches, as much as the pack
 * holds. Putting: the stacks of the given slots (or all that match).
 */
public final class ChestTask implements BotTask {
    public enum Mode { TAKE, PUT }

    private final BlockPos chest;
    private final Mode mode;
    private final Predicate<ItemStack> what;
    private final int count;
    private final List<Integer> slots;
    /** Slots of the pack to keep free when taking (for what is picked up on the way). */
    private final int keepFree;
    private int ticks, repath, waited;
    /** Items moved; whether the chest (putting) or the pack (taking) ran full; whether the chest was gone. */
    public int moved;
    public boolean full, missing;

    private ChestTask(BlockPos chest, Mode mode, Predicate<ItemStack> what, int count, List<Integer> slots, int keepFree) {
        this.chest = chest.immutable();
        this.mode = mode;
        this.what = what;
        this.count = count;
        this.slots = slots;
        this.keepFree = keepFree;
    }

    /** Up to {@code count} of what matches out of the chest into the pack. */
    public static ChestTask take(BlockPos chest, Predicate<ItemStack> what, int count, int keepFree) {
        return new ChestTask(chest, Mode.TAKE, what, count, null, keepFree);
    }

    /** These slots of the pack into the chest. */
    public static ChestTask put(BlockPos chest, List<Integer> slots) {
        return new ChestTask(chest, Mode.PUT, st -> true, Integer.MAX_VALUE, slots, 0);
    }

    /** Everything that matches into the chest. */
    public static ChestTask put(BlockPos chest, Predicate<ItemStack> what) {
        return new ChestTask(chest, Mode.PUT, what, Integer.MAX_VALUE, null, 0);
    }

    @Override
    public Result tick(Bot bot) {
        var body = bot.body();
        ServerLevel level = (ServerLevel) body.level();
        if (++ticks > 3600) return Result.FAILED;
        Container container = Containers.at(level, chest);
        if (container == null) {
            missing = true;
            return Result.FAILED;
        }
        if (body.getEyePosition().distanceTo(Vec3.atCenterOf(chest)) > 4.0) {
            if (repath-- <= 0 || bot.navigator().status() != BotNavigator.Status.MOVING) {
                repath = 30;
                bot.navigator().goNear(chest, 2.5);
            }
            if (bot.navigator().tick() == BotNavigator.Status.FAILED && ticks > 600) return Result.FAILED;
            return Result.RUNNING;
        }
        bot.navigator().stop();
        body.lookAt(Vec3.atCenterOf(chest));
        if (waited++ == 0) Containers.open(level, chest, true);
        if (waited < 6) return Result.RUNNING;
        var inv = body.getInventory();
        if (mode == Mode.PUT) {
            var items = inv.getNonEquipmentItems();
            for (int i = 0; i < items.size(); i++) {
                if (slots != null && !slots.contains(i)) continue;
                ItemStack st = inv.getItem(i);
                if (st.isEmpty() || !what.test(st)) continue;
                ItemStack left = Containers.insert(container, st);
                moved += st.getCount() - left.getCount();
                inv.setItem(i, left);
                if (!left.isEmpty()) full = true;
            }
        } else {
            int want = count;
            for (int i = 0; i < container.getContainerSize() && want > 0; i++) {
                ItemStack st = container.getItem(i);
                if (st.isEmpty() || !what.test(st)) continue;
                if (free(inv) <= keepFree && !hasRoomOnStack(inv, st)) {
                    full = true;
                    break;
                }
                ItemStack got = st.split(Math.min(want, st.getCount()));
                int n = got.getCount();
                inv.add(got);
                // (What did not go into the pack back into the chest.)
                if (!got.isEmpty()) {
                    st.grow(got.getCount());
                    n -= got.getCount();
                    full = true;
                }
                moved += n;
                want -= n;
                if (full) break;
            }
            container.setChanged();
        }
        Containers.open(level, chest, false);
        return Result.DONE;
    }

    private static int free(net.minecraft.world.entity.player.Inventory inv) {
        int n = 0;
        for (ItemStack st : inv.getNonEquipmentItems()) if (st.isEmpty()) n++;
        return n;
    }

    private static boolean hasRoomOnStack(net.minecraft.world.entity.player.Inventory inv, ItemStack like) {
        for (ItemStack st : inv.getNonEquipmentItems()) {
            if (ItemStack.isSameItemSameComponents(st, like) && st.getCount() < st.getMaxStackSize()) return true;
        }
        return false;
    }

    @Override
    public String describe() {
        return (mode == Mode.PUT ? "put things into" : "take things from") + " the chest at " + chest.toShortString();
    }
}
