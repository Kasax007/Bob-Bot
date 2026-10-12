package net.kasax.bobbot.build;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.BedBlock;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LiquidBlock;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.SlabBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BedPart;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.level.block.state.properties.SlabType;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * A blueprint put somewhere: where its corner goes, turned and mirrored, every cell of it as a
 * block of the world - and what each takes from the pack.
 */
public final class Site {
    /** One block of the build: where in the world, and what goes there. */
    public record Target(BlockPos pos, BlockState state) {
    }

    public final Blueprint blueprint;
    public final BlockPos origin;
    public final Rotation rotation;
    public final Mirror mirror;
    public final List<Target> targets = new ArrayList<>();
    public final BoundingBox box;

    public Site(Blueprint bp, BlockPos origin, Rotation rotation, Mirror mirror) {
        this.blueprint = bp;
        this.origin = origin.immutable();
        this.rotation = rotation;
        this.mirror = mirror;
        int minX = Integer.MAX_VALUE, minY = Integer.MAX_VALUE, minZ = Integer.MAX_VALUE, maxX = Integer.MIN_VALUE, maxY = Integer.MIN_VALUE, maxZ = Integer.MIN_VALUE;
        for (int y = 0; y < bp.size.getY(); y++) {
            for (int z = 0; z < bp.size.getZ(); z++) {
                for (int x = 0; x < bp.size.getX(); x++) {
                    BlockState s = bp.at(x, y, z);
                    BlockPos w = world(x, y, z);
                    minX = Math.min(minX, w.getX());
                    minY = Math.min(minY, w.getY());
                    minZ = Math.min(minZ, w.getZ());
                    maxX = Math.max(maxX, w.getX());
                    maxY = Math.max(maxY, w.getY());
                    maxZ = Math.max(maxZ, w.getZ());
                    if (s == null || s.is(Blocks.STRUCTURE_VOID)) continue;
                    targets.add(new Target(w, s.mirror(mirror).rotate(rotation)));
                }
            }
        }
        box = new BoundingBox(minX, minY, minZ, maxX, maxY, maxZ);
    }

    /** Where a cell of the blueprint lands. */
    public BlockPos world(int x, int y, int z) {
        return origin.offset(StructureTemplate.transform(new BlockPos(x, y, z), mirror, rotation, BlockPos.ZERO));
    }

    /**
     * Put down in front of a player looking {@code facing}: the build stretches away from him,
     * its middle on his line, two blocks ahead, its floor at his feet.
     */
    public static Site inFront(Blueprint bp, BlockPos feet, Direction facing) {
        Rotation r = switch (facing) {
            case SOUTH -> Rotation.NONE;
            case WEST -> Rotation.CLOCKWISE_90;
            case NORTH -> Rotation.CLOCKWISE_180;
            case EAST -> Rotation.COUNTERCLOCKWISE_90;
            default -> Rotation.NONE;
        };
        BlockPos local = new BlockPos(bp.size.getX() / 2, 0, -2);
        BlockPos origin = feet.subtract(StructureTemplate.transform(local, Mirror.NONE, r, BlockPos.ZERO));
        return new Site(bp, origin, r, Mirror.NONE);
    }

    /** The same blueprint turned a quarter further (round the same corner). */
    public Site turned() {
        return new Site(blueprint, origin, rotation.getRotated(Rotation.CLOCKWISE_90), mirror);
    }

    // ---- what it takes ------------------------------------------------------------------------------

    /** The item (and how many) that puts this state down; empty for what comes along with another half, or nothing at all. */
    public static ItemStack itemFor(BlockState s) {
        if (s.isAir()) return ItemStack.EMPTY;
        if (s.hasProperty(BlockStateProperties.DOUBLE_BLOCK_HALF) && s.getValue(BlockStateProperties.DOUBLE_BLOCK_HALF) == DoubleBlockHalf.UPPER) return ItemStack.EMPTY;
        if (s.getBlock() instanceof BedBlock && s.getValue(BedBlock.PART) == BedPart.HEAD) return ItemStack.EMPTY;
        if (s.is(Blocks.PISTON_HEAD) || s.is(Blocks.MOVING_PISTON)) return ItemStack.EMPTY;
        if (s.getBlock() instanceof LiquidBlock) {
            if (s.getFluidState().isSource()) return new ItemStack(s.is(Blocks.LAVA) ? Items.LAVA_BUCKET : Items.WATER_BUCKET);
            return ItemStack.EMPTY;
        }
        if (s.is(Blocks.FARMLAND) || s.is(Blocks.DIRT_PATH)) return new ItemStack(Items.DIRT);
        Item item = s.getBlock().asItem();
        if (item == Items.AIR) return ItemStack.EMPTY;
        int n = 1;
        if (s.getBlock() instanceof SlabBlock && s.getValue(SlabBlock.TYPE) == SlabType.DOUBLE) n = 2;
        else if (s.hasProperty(BlockStateProperties.CANDLES)) n = s.getValue(BlockStateProperties.CANDLES);
        else if (s.hasProperty(BlockStateProperties.PICKLES)) n = s.getValue(BlockStateProperties.PICKLES);
        else if (s.hasProperty(BlockStateProperties.EGGS)) n = s.getValue(BlockStateProperties.EGGS);
        else if (s.is(Blocks.SNOW) && s.hasProperty(BlockStateProperties.LAYERS)) n = s.getValue(BlockStateProperties.LAYERS);
        return new ItemStack(item, n);
    }

    /** Everything the build takes, by item. */
    public Map<Item, Integer> materials() {
        Map<Item, Integer> out = new LinkedHashMap<>();
        for (Target t : targets) {
            ItemStack st = itemFor(t.state());
            if (!st.isEmpty()) out.merge(st.getItem(), st.getCount(), Integer::sum);
        }
        return out;
    }
}
