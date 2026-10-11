package net.kasax.bobbot.storage;

import net.kasax.bobbot.companion.Area;
import net.kasax.bobbot.companion.Companion;
import net.kasax.bobbot.companion.RoleChest;
import net.kasax.bobbot.world.Containers;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Container;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.ChestBlock;
import net.minecraft.world.level.block.entity.BaseContainerBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.ChestType;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Predicate;

/**
 * The owner's chests as Bob sees them: which are the sorted storage (chests he was told are
 * storage, and every chest and barrel in a storage room), where a thing belongs there, and where
 * something is to be had.
 */
public final class Storage {
    private Storage() {
    }

    /** One container (a double chest once, by its first half) and what it is for. */
    public record Box(BlockPos pos, RoleChest.Role role) {
    }

    /** The biggest storage room scanned (a box larger than this is cut to it). */
    private static final int MAX_ROOM = 96;

    /**
     * The owner's containers of these roles in this dimension: the role chests, and for storage
     * the containers in his storage rooms. Gone chests are left out.
     */
    public static List<Box> boxes(Companion c, ServerLevel level, Set<RoleChest.Role> roles) {
        Map<BlockPos, Box> out = new LinkedHashMap<>();
        for (RoleChest rc : c.chests) {
            if (rc.pos().dimension() != level.dimension() || !roles.contains(rc.role())) continue;
            if (!level.isLoaded(rc.block()) || Containers.at(level, rc.block()) == null) continue;
            out.putIfAbsent(canonical(level, rc.block()), new Box(canonical(level, rc.block()), rc.role()));
        }
        if (roles.contains(RoleChest.Role.STORAGE)) {
            for (Area a : c.areas.values()) {
                if (a.kind() != Area.Kind.STORAGE || a.dim() != level.dimension()) continue;
                for (BlockPos p : containersIn(level, a)) {
                    BlockPos k = canonical(level, p);
                    // (A chest given a role of its own keeps it, even in the storage room.)
                    if (!out.containsKey(k) && !roleOf(c, level, k)) out.put(k, new Box(k, RoleChest.Role.STORAGE));
                }
            }
        }
        return new ArrayList<>(out.values());
    }

    private static boolean roleOf(Companion c, ServerLevel level, BlockPos k) {
        for (RoleChest rc : c.chests) if (rc.pos().dimension() == level.dimension() && canonical(level, rc.block()).equals(k)) return true;
        return false;
    }

    /** The chests and barrels in an area (loaded chunks only). */
    public static List<BlockPos> containersIn(ServerLevel level, Area a) {
        List<BlockPos> out = new ArrayList<>();
        BlockPos min = a.min(), max = a.max();
        int x1 = Math.min(max.getX(), min.getX() + MAX_ROOM), z1 = Math.min(max.getZ(), min.getZ() + MAX_ROOM);
        for (int cx = min.getX() >> 4; cx <= x1 >> 4; cx++) {
            for (int cz = min.getZ() >> 4; cz <= z1 >> 4; cz++) {
                var chunk = level.getChunkSource().getChunkNow(cx, cz);
                if (chunk == null) continue;
                for (var be : chunk.getBlockEntities().values()) {
                    if (!(be instanceof BaseContainerBlockEntity)) continue;
                    BlockPos p = be.getBlockPos();
                    if (a.contains(level.dimension(), p) && p.getX() <= x1 && p.getZ() <= z1) out.add(p.immutable());
                }
            }
        }
        out.sort(Comparator.comparingLong(BlockPos::asLong));
        return out;
    }

    /** A double chest by one of its halves always (the one further west or north). */
    public static BlockPos canonical(ServerLevel level, BlockPos p) {
        BlockState s = level.getBlockState(p);
        if (s.getBlock() instanceof ChestBlock && s.getValue(ChestBlock.TYPE) != ChestType.SINGLE) {
            BlockPos other = p.relative(ChestBlock.getConnectedDirection(s));
            if (other.getX() < p.getX() || other.getZ() < p.getZ()) return other.immutable();
        }
        return p.immutable();
    }

    /** How much of what matches lies where (most first). */
    public record Found(Box box, int count) {
    }

    public static List<Found> find(ServerLevel level, List<Box> boxes, Predicate<ItemStack> what) {
        List<Found> out = new ArrayList<>();
        for (Box b : boxes) {
            Container ct = Containers.at(level, b.pos());
            if (ct == null) continue;
            int n = Containers.count(ct, what);
            if (n > 0) out.add(new Found(b, n));
        }
        out.sort(Comparator.comparingInt(Found::count).reversed());
        return out;
    }

    /** How much of what matches is in all of these together. */
    public static int total(ServerLevel level, List<Box> boxes, Predicate<ItemStack> what) {
        int n = 0;
        for (Found f : find(level, boxes, what)) n += f.count();
        return n;
    }

    /** Below this, a chest of other things is no place for it (an empty chest is better). */
    private static final double ALIKE = 0.45;

    /**
     * Where a stack belongs, the way the owner sorted: a chest that holds the same already (with
     * room for it), else the one whose things are most like it, else an empty storage chest (a new
     * kind of thing starts a chest of its own), else the overflow chest. Null: nowhere.
     * {@code full}: boxes known to be full (skipped).
     */
    public static Box placeFor(ServerLevel level, List<Box> storage, ItemStack st, Set<BlockPos> full) {
        Box same = null, alike = null, empty = null, overflow = null;
        int sameCount = 0;
        double alikeScore = ALIKE;
        for (Box b : storage) {
            if (full.contains(b.pos())) continue;
            Container ct = Containers.at(level, b.pos());
            if (ct == null || !fits(ct, st)) continue;
            if (b.role() == RoleChest.Role.OVERFLOW) {
                if (overflow == null) overflow = b;
                continue;
            }
            if (b.role() != RoleChest.Role.STORAGE) continue;
            int n = 0;
            double best = 0;
            Set<Item> kinds = new HashSet<>();
            boolean anything = false;
            for (int i = 0; i < ct.getContainerSize(); i++) {
                ItemStack in = ct.getItem(i);
                if (in.isEmpty()) continue;
                anything = true;
                if (in.is(st.getItem())) n += in.getCount();
                else if (kinds.add(in.getItem())) best = Math.max(best, Similarity.of(in, st));
            }
            if (!anything) {
                if (empty == null) empty = b;
                continue;
            }
            if (n > sameCount) {
                sameCount = n;
                same = b;
            }
            if (best > alikeScore) {
                alikeScore = best;
                alike = b;
            }
        }
        if (same != null) return same;
        if (alike != null) return alike;
        if (empty != null) return empty;
        return overflow;
    }

    /** Whether some of the stack still goes in (onto a like stack or into an empty slot). */
    public static boolean fits(Container ct, ItemStack st) {
        for (int i = 0; i < ct.getContainerSize(); i++) {
            ItemStack in = ct.getItem(i);
            if (in.isEmpty()) {
                if (ct.canPlaceItem(i, st)) return true;
            } else if (ItemStack.isSameItemSameComponents(in, st) && in.getCount() < Math.min(ct.getMaxStackSize(in), in.getMaxStackSize())) return true;
        }
        return false;
    }

    /**
     * The stacks of a pack shared out over the storage: box by box, which slots go there.
     * Slots with no place are listed under null.
     */
    public static Map<Box, List<Integer>> plan(ServerLevel level, List<Box> storage, List<ItemStack> pack, Predicate<ItemStack> sortable) {
        Map<Box, List<Integer>> out = new LinkedHashMap<>();
        Map<Item, Box> chosen = new HashMap<>();
        Set<BlockPos> full = new HashSet<>();
        for (int i = 0; i < pack.size(); i++) {
            ItemStack st = pack.get(i);
            if (st.isEmpty() || !sortable.test(st)) continue;
            // (All of a kind to the same chest, unless that one is full.)
            Box b = chosen.get(st.getItem());
            if (b == null) {
                b = placeFor(level, storage, st, full);
                if (b != null) chosen.put(st.getItem(), b);
            }
            out.computeIfAbsent(b, k -> new ArrayList<>()).add(i);
        }
        return out;
    }
}
