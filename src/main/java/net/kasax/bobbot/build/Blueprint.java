package net.kasax.bobbot.build;

import com.mojang.serialization.Dynamic;
import net.minecraft.SharedConstants;
import net.minecraft.commands.arguments.blocks.BlockStateParser;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderGetter;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.Vec3i;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.IntTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtAccounter;
import net.minecraft.nbt.NbtIo;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.datafix.DataFixTypes;
import net.minecraft.util.datafix.fixes.References;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Something to build: the size of its box and what goes where in it (air too, so the builder
 * knows what to clear). Read from Litematica's {@code .litematic}, the structure block's {@code .nbt}
 * and WorldEdit's {@code .schem}; or copied from the world (a box marked with the wand).
 */
public final class Blueprint {
    public final String name;
    public final Vec3i size;
    /** The states, x fastest, then z, then y (as {@link #index}); null for a cell outside every region. */
    private final BlockState[] cells;

    private Blueprint(String name, Vec3i size) {
        this.name = name;
        this.size = size;
        this.cells = new BlockState[size.getX() * size.getY() * size.getZ()];
    }

    public int index(int x, int y, int z) {
        return (y * size.getZ() + z) * size.getX() + x;
    }

    /** The state at a position in the box, or null if the blueprint says nothing about it. */
    public BlockState at(int x, int y, int z) {
        return cells[index(x, y, z)];
    }

    private void set(int x, int y, int z, BlockState s) {
        cells[index(x, y, z)] = s;
    }

    /** How many blocks (not air) it has. */
    public int blocks() {
        int n = 0;
        for (BlockState s : cells) if (s != null && !s.isAir()) n++;
        return n;
    }

    // ---- reading -------------------------------------------------------------------------------

    public static Blueprint read(MinecraftServer server, Path file) throws IOException {
        String fn = file.getFileName().toString();
        String name = fn.replaceAll("\\.(litematic|nbt|schem|schematic)$", "");
        CompoundTag root = NbtIo.readCompressed(file, NbtAccounter.unlimitedHeap());
        HolderGetter<Block> blocks = server.registryAccess().lookupOrThrow(Registries.BLOCK);
        if (fn.endsWith(".litematic")) return litematic(server, name, root, blocks);
        if (fn.endsWith(".schem") || fn.endsWith(".schematic")) return sponge(server, name, root);
        return structure(server, name, root, blocks);
    }

    /** Litematica: regions, each with a palette and its states packed into longs. */
    private static Blueprint litematic(MinecraftServer server, String name, CompoundTag root, HolderGetter<Block> blocks) {
        int version = root.getIntOr("MinecraftDataVersion", currentVersion());
        CompoundTag regions = root.getCompoundOrEmpty("Regions");
        // The whole box: all regions together (a region's size may be negative: it grows the other way).
        int minX = Integer.MAX_VALUE, minY = Integer.MAX_VALUE, minZ = Integer.MAX_VALUE, maxX = Integer.MIN_VALUE, maxY = Integer.MIN_VALUE, maxZ = Integer.MIN_VALUE;
        for (String key : regions.keySet()) {
            CompoundTag r = regions.getCompoundOrEmpty(key);
            int[] lo = regionMin(r), sz = regionSize(r);
            minX = Math.min(minX, lo[0]);
            minY = Math.min(minY, lo[1]);
            minZ = Math.min(minZ, lo[2]);
            maxX = Math.max(maxX, lo[0] + sz[0] - 1);
            maxY = Math.max(maxY, lo[1] + sz[1] - 1);
            maxZ = Math.max(maxZ, lo[2] + sz[2] - 1);
        }
        if (minX == Integer.MAX_VALUE) return new Blueprint(name, new Vec3i(1, 1, 1));
        Blueprint bp = new Blueprint(name, new Vec3i(maxX - minX + 1, maxY - minY + 1, maxZ - minZ + 1));
        for (String key : regions.keySet()) {
            CompoundTag r = regions.getCompoundOrEmpty(key);
            int[] lo = regionMin(r), sz = regionSize(r);
            ListTag paletteTag = r.getListOrEmpty("BlockStatePalette");
            List<BlockState> palette = new ArrayList<>();
            for (int i = 0; i < paletteTag.size(); i++) palette.add(state(server, blocks, paletteTag.getCompoundOrEmpty(i), version));
            long[] data = r.getLongArray("BlockStates").orElse(new long[0]);
            int bits = Math.max(2, 32 - Integer.numberOfLeadingZeros(Math.max(1, palette.size() - 1)));
            long volume = (long) sz[0] * sz[1] * sz[2];
            for (long i = 0; i < volume; i++) {
                int v = bitsAt(data, bits, i);
                if (v < 0 || v >= palette.size()) continue;
                int x = (int) (i % sz[0]), z = (int) ((i / sz[0]) % sz[2]), y = (int) (i / ((long) sz[0] * sz[2]));
                bp.set(lo[0] + x - minX, lo[1] + y - minY, lo[2] + z - minZ, palette.get(v));
            }
        }
        return bp;
    }

    private static int[] regionSize(CompoundTag r) {
        CompoundTag s = r.getCompoundOrEmpty("Size");
        return new int[]{Math.abs(s.getIntOr("x", 1)), Math.abs(s.getIntOr("y", 1)), Math.abs(s.getIntOr("z", 1))};
    }

    private static int[] regionMin(CompoundTag r) {
        CompoundTag p = r.getCompoundOrEmpty("Position"), s = r.getCompoundOrEmpty("Size");
        int[] out = new int[3];
        String[] axes = {"x", "y", "z"};
        for (int i = 0; i < 3; i++) {
            int pos = p.getIntOr(axes[i], 0), size = s.getIntOr(axes[i], 1);
            out[i] = size < 0 ? pos + size + 1 : pos;
        }
        return out;
    }

    /** One value of Litematica's packed array (values may run over into the next long). */
    static int bitsAt(long[] data, int bits, long index) {
        long start = index * bits;
        int a = (int) (start >> 6), b = (int) ((index + 1L) * bits - 1L >> 6);
        if (a >= data.length || b >= data.length) return -1;
        int off = (int) (start & 63);
        long mask = (1L << bits) - 1L;
        if (a == b) return (int) (data[a] >>> off & mask);
        return (int) ((data[a] >>> off | data[b] << (64 - off)) & mask);
    }

    /** The structure block's format: a palette and a list of blocks by position. */
    private static Blueprint structure(MinecraftServer server, String name, CompoundTag root, HolderGetter<Block> blocks) {
        int version = root.getIntOr("DataVersion", currentVersion());
        if (version < currentVersion()) root = DataFixTypes.STRUCTURE.update(server.getFixerUpper(), root, version, currentVersion());
        ListTag sizeTag = root.getListOrEmpty("size");
        Vec3i size = new Vec3i(sizeTag.getIntOr(0, 1), sizeTag.getIntOr(1, 1), sizeTag.getIntOr(2, 1));
        Blueprint bp = new Blueprint(name, size);
        ListTag paletteTag = root.getListOrEmpty("palette");
        if (paletteTag.isEmpty()) {
            ListTag palettes = root.getListOrEmpty("palettes");
            if (!palettes.isEmpty()) paletteTag = palettes.getListOrEmpty(0);
        }
        List<BlockState> palette = new ArrayList<>();
        for (int i = 0; i < paletteTag.size(); i++) palette.add(NbtUtils.readBlockState(blocks, paletteTag.getCompoundOrEmpty(i)));
        // (What the list leaves out - a structure void - is left as it is: null.)
        ListTag list = root.getListOrEmpty("blocks");
        for (int i = 0; i < list.size(); i++) {
            CompoundTag b = list.getCompoundOrEmpty(i);
            ListTag pos = b.getListOrEmpty("pos");
            int s = b.getIntOr("state", 0);
            if (s < 0 || s >= palette.size()) continue;
            int x = pos.getIntOr(0, 0), y = pos.getIntOr(1, 0), z = pos.getIntOr(2, 0);
            if (x < 0 || y < 0 || z < 0 || x >= size.getX() || y >= size.getY() || z >= size.getZ()) continue;
            bp.set(x, y, z, palette.get(s));
        }
        return bp;
    }

    /** WorldEdit's Sponge format (version 2 and 3): a palette of state strings and varint indices. */
    private static Blueprint sponge(MinecraftServer server, String name, CompoundTag root) {
        CompoundTag schem = root.getCompound("Schematic").orElse(root);
        int w = schem.getShortOr("Width", (short) 1), h = schem.getShortOr("Height", (short) 1), l = schem.getShortOr("Length", (short) 1);
        CompoundTag blocksTag = schem.getCompound("Blocks").orElse(schem);
        CompoundTag paletteTag = blocksTag.getCompoundOrEmpty("Palette");
        byte[] data = blocksTag.getByteArray(blocksTag.contains("Data") ? "Data" : "BlockData").orElse(schem.getByteArray("BlockData").orElse(new byte[0]));
        HolderLookup<Block> lookup = server.registryAccess().lookupOrThrow(Registries.BLOCK);
        Map<Integer, BlockState> palette = new HashMap<>();
        for (String key : paletteTag.keySet()) {
            BlockState st;
            try {
                st = BlockStateParser.parseForBlock(lookup, key, false).blockState();
            } catch (Exception e) {
                st = Blocks.AIR.defaultBlockState();
            }
            palette.put(paletteTag.getIntOr(key, 0), st);
        }
        Blueprint bp = new Blueprint(name, new Vec3i(w, h, l));
        int i = 0, index = 0;
        while (i < data.length && index < w * h * l) {
            int value = 0, shift = 0;
            byte b;
            do {
                b = data[i++];
                value |= (b & 0x7F) << shift;
                shift += 7;
            } while ((b & 0x80) != 0 && i < data.length);
            int x = index % w, z = (index / w) % l, y = index / (w * l);
            BlockState st = palette.get(value);
            if (st != null) bp.set(x, y, z, st);
            index++;
        }
        return bp;
    }

    private static int currentVersion() {
        return SharedConstants.getCurrentVersion().dataVersion().version();
    }

    /** A palette entry brought up to this version of the game (a block renamed since, say). */
    private static BlockState state(MinecraftServer server, HolderGetter<Block> blocks, CompoundTag tag, int version) {
        CompoundTag t = tag;
        if (version < currentVersion()) {
            try {
                t = (CompoundTag) server.getFixerUpper().update(References.BLOCK_STATE, new Dynamic<>(NbtOps.INSTANCE, tag), version, currentVersion()).getValue();
            } catch (RuntimeException ignored) {
            }
        }
        return NbtUtils.readBlockState(blocks, t);
    }

    // ---- copying from the world -------------------------------------------------------------------

    /** The box between two corners as it stands, as a blueprint. */
    public static Blueprint copy(ServerLevel level, String name, BlockPos a, BlockPos b) {
        BlockPos min = BlockPos.min(a, b), max = BlockPos.max(a, b);
        Blueprint bp = new Blueprint(name, new Vec3i(max.getX() - min.getX() + 1, max.getY() - min.getY() + 1, max.getZ() - min.getZ() + 1));
        for (int x = 0; x < bp.size.getX(); x++)
            for (int y = 0; y < bp.size.getY(); y++)
                for (int z = 0; z < bp.size.getZ(); z++) bp.set(x, y, z, level.getBlockState(min.offset(x, y, z)));
        return bp;
    }

    /** Saved in the structure block's format (any structure block can load it too). */
    public void save(Path file) throws IOException {
        Map<BlockState, Integer> index = new HashMap<>();
        ListTag palette = new ListTag(), list = new ListTag();
        for (int x = 0; x < size.getX(); x++) {
            for (int y = 0; y < size.getY(); y++) {
                for (int z = 0; z < size.getZ(); z++) {
                    BlockState s = at(x, y, z);
                    if (s == null) continue;
                    Integer i = index.get(s);
                    if (i == null) {
                        i = palette.size();
                        index.put(s, i);
                        palette.add(NbtUtils.writeBlockState(s));
                    }
                    CompoundTag b = new CompoundTag();
                    ListTag pos = new ListTag();
                    pos.add(IntTag.valueOf(x));
                    pos.add(IntTag.valueOf(y));
                    pos.add(IntTag.valueOf(z));
                    b.put("pos", pos);
                    b.putInt("state", i);
                    list.add(b);
                }
            }
        }
        CompoundTag root = new CompoundTag();
        ListTag sz = new ListTag();
        sz.add(IntTag.valueOf(size.getX()));
        sz.add(IntTag.valueOf(size.getY()));
        sz.add(IntTag.valueOf(size.getZ()));
        root.put("size", sz);
        root.put("palette", palette);
        root.put("blocks", list);
        root.put("entities", new ListTag());
        root.putInt("DataVersion", currentVersion());
        Files.createDirectories(file.getParent());
        NbtIo.writeCompressed(root, file);
    }
}
