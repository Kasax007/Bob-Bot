package net.kasax.bobbot.protect;

import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.StructurePiece;
import net.minecraft.world.level.levelgen.structure.StructureStart;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * The world's own buildings - villages, temples, outposts, strongholds - are not Bob's to dig up:
 * the boxes of their pieces, chunk by chunk. Not the mineshafts (they run under everything, and a
 * tunnel through one is fair), nor ruins and wrecks.
 */
public final class Structures {
    private Structures() {
    }

    /** Structures Bob may dig through (by name, without the namespace). */
    private static final Set<String> FREE = Set.of("mineshaft", "mineshaft_mesa", "buried_treasure", "ruined_portal",
            "ruined_portal_desert", "ruined_portal_jungle", "ruined_portal_swamp", "ruined_portal_mountain", "ruined_portal_ocean",
            "ruined_portal_nether", "nether_fossil", "shipwreck", "shipwreck_beached", "ocean_ruin_cold", "ocean_ruin_warm");

    /** Per dimension: per chunk, the boxes of the protected pieces that reach into it. */
    private static final Map<ResourceKey<Level>, Long2ObjectOpenHashMap<List<BoundingBox>>> CACHE = new HashMap<>();

    public static void clear() {
        CACHE.clear();
    }

    private static boolean guarded(ServerLevel level, Structure s) {
        Identifier id = level.registryAccess().lookupOrThrow(Registries.STRUCTURE).getKey(s);
        return id == null || !FREE.contains(id.getPath());
    }

    /** The protected pieces reaching into this chunk (worked out once per chunk; server thread). */
    public static List<BoundingBox> boxes(ServerLevel level, int chunkX, int chunkZ) {
        var perChunk = CACHE.computeIfAbsent(level.dimension(), k -> new Long2ObjectOpenHashMap<>());
        long key = ChunkPos.pack(chunkX, chunkZ);
        List<BoundingBox> got = perChunk.get(key);
        if (got != null) return got;
        List<BoundingBox> out = List.of();
        try {
            int x0 = chunkX << 4, z0 = chunkZ << 4;
            for (StructureStart start : level.structureManager().startsForStructure(chunkX, chunkZ, s -> guarded(level, s))) {
                if (!start.isValid()) continue;
                for (StructurePiece piece : start.getPieces()) {
                    BoundingBox b = piece.getBoundingBox();
                    if (b.maxX() < x0 || b.minX() > x0 + 15 || b.maxZ() < z0 || b.minZ() > z0 + 15) continue;
                    if (out.isEmpty()) out = new ArrayList<>();
                    out.add(b);
                }
            }
        } catch (RuntimeException e) {
            out = List.of();
        }
        perChunk.put(key, out);
        return out;
    }

    /** Whether {@code pos} lies in a piece of a protected structure (server thread). */
    public static boolean protectedAt(ServerLevel level, BlockPos pos) {
        if (level.getChunkSource().getChunkNow(pos.getX() >> 4, pos.getZ() >> 4) == null) return false;
        for (BoundingBox b : boxes(level, pos.getX() >> 4, pos.getZ() >> 4)) if (b.isInside(pos)) return true;
        return false;
    }
}
