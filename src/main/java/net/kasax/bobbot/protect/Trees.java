package net.kasax.bobbot.protect;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LeavesBlock;
import net.minecraft.world.level.block.state.BlockState;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Trees that grew, as against logs somebody put together. A grown tree has leaves that grew with
 * it: they decay when the tree is gone (not "persistent", as leaves a player puts down are). A log
 * cabin has none; nor has a tree trunk somebody stood up as a pillar. Bob fells only grown trees,
 * and of those only the logs nobody placed and that are not part of a wall beside them.
 */
public final class Trees {
    private Trees() {
    }

    /** One grown tree: its logs, bottom first. */
    public record Tree(List<BlockPos> logs, BlockPos base, boolean grown) {
    }

    /** Blocks a grown tree's crown is made of (leaves that grew, the Nether's wart blocks). */
    private static boolean grownCrown(BlockState s) {
        if (s.getBlock() instanceof LeavesBlock) return s.hasProperty(LeavesBlock.PERSISTENT) && !s.getValue(LeavesBlock.PERSISTENT);
        return s.is(Blocks.NETHER_WART_BLOCK) || s.is(Blocks.WARPED_WART_BLOCK) || s.is(Blocks.SHROOMLIGHT);
    }

    private static boolean wood(BlockState s) {
        return s.is(BlockTags.LOGS);
    }

    private record Cached(Tree tree, long at) {
    }

    private static final Map<BlockPos, Cached> CACHE = new HashMap<>();
    private static final int MAX_LOGS = 400, REACH = 10;

    public static void clear() {
        CACHE.clear();
    }

    /** Whether the log at {@code pos} is part of a grown tree, fair to fell. */
    public static boolean grown(ServerLevel level, BlockPos pos) {
        Tree t = treeAt(level, pos);
        return t != null && t.grown() && t.logs().contains(pos);
    }

    /**
     * The tree the log at {@code pos} belongs to: the logs of its kind joined to it (also corner
     * to corner, as branches go), not those a player placed nor those against something built,
     * and whether leaves grew on them. Null if there is no log there.
     */
    public static Tree treeAt(ServerLevel level, BlockPos pos) {
        long now = level.getGameTime();
        Cached c = CACHE.get(pos);
        if (c != null && now - c.at() < 600) return c.tree();
        BlockState start = level.getBlockState(pos);
        if (!wood(start)) return null;
        Block kind = start.getBlock();
        Set<BlockPos> seen = new HashSet<>();
        List<BlockPos> logs = new ArrayList<>();
        ArrayDeque<BlockPos> open = new ArrayDeque<>();
        open.add(pos.immutable());
        seen.add(pos.immutable());
        int crown = 0;
        while (!open.isEmpty() && logs.size() < MAX_LOGS) {
            BlockPos p = open.poll();
            BlockState s = level.getBlockState(p);
            if (s.getBlock() != kind) continue;
            if (PlacedBlocks.at(level, p) == PlacedBlocks.By.PLAYER || againstBuilt(level, p)) continue;
            logs.add(p);
            for (int dx = -1; dx <= 1; dx++) {
                for (int dy = -1; dy <= 1; dy++) {
                    for (int dz = -1; dz <= 1; dz++) {
                        if (dx == 0 && dy == 0 && dz == 0) continue;
                        BlockPos q = p.offset(dx, dy, dz);
                        if (Math.abs(q.getX() - pos.getX()) > REACH || Math.abs(q.getZ() - pos.getZ()) > REACH) continue;
                        BlockState n = level.getBlockState(q);
                        // (Leaves right beside a log: the crown it grew.)
                        if (crown < 8 && (Math.abs(dx) + Math.abs(dy) + Math.abs(dz) == 1) && grownCrown(n)) crown++;
                        if (n.getBlock() == kind && seen.add(q.immutable())) open.add(q.immutable());
                    }
                }
            }
        }
        logs.sort((a, b) -> a.getY() != b.getY() ? Integer.compare(a.getY(), b.getY()) : Long.compare(a.asLong(), b.asLong()));
        BlockPos base = logs.isEmpty() ? pos.immutable() : logs.get(0);
        // (Three leaves of its own at least: a single grown leaf blown against a cabin wall is no crown.)
        Tree t = new Tree(logs, base, crown >= 3 && !logs.isEmpty());
        if (CACHE.size() > 4096) CACHE.clear();
        for (BlockPos l : logs) CACHE.put(l, new Cached(t, now));
        CACHE.put(pos.immutable(), new Cached(t, now));
        return t;
    }

    /** Right against something made (planks, glass, a door) or a chest: part of a wall, not a trunk. */
    private static boolean againstBuilt(ServerLevel level, BlockPos p) {
        var crafted = Built.crafted(level.getServer());
        for (net.minecraft.core.Direction d : net.minecraft.core.Direction.values()) {
            BlockState n = level.getBlockState(p.relative(d));
            if (n.is(Blocks.BEE_NEST)) continue; // (grows on trees)
            if (crafted.contains(n.getBlock()) || n.hasBlockEntity()) return true;
        }
        return false;
    }

    /** The cache for this block forgotten (it was broken, the tree changed). */
    public static void forget(BlockPos pos) {
        CACHE.remove(pos);
    }
}
