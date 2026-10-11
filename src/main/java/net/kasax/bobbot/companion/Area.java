package net.kasax.bobbot.companion;

import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.levelgen.structure.BoundingBox;

import java.util.Locale;

/** A box of the world the owner marked with the wand: where Bob works, or where he never touches a block. */
public record Area(String name, ResourceKey<Level> dim, BlockPos min, BlockPos max, Kind kind) {
    public enum Kind {
        /** Bob's to work in (a field to farm, a forest to fell, a hole to dig). */
        WORK("work", "Arbeit"),
        /** Never a block broken or placed in it, whatever the job. */
        PROTECTED("protected", "geschützt");

        public final String en, de;

        Kind(String en, String de) {
            this.en = en;
            this.de = de;
        }

        public static Kind parse(String s) {
            String v = s.toLowerCase(Locale.ROOT);
            for (Kind k : values()) if (k.name().toLowerCase(Locale.ROOT).equals(v) || k.en.equals(v) || k.de.equals(v)) return k;
            return switch (v) {
                case "protect", "schutz", "schützen" -> PROTECTED;
                case "arbeiten", "feld", "field", "farm" -> WORK;
                default -> null;
            };
        }
    }

    public static Area of(String name, ResourceKey<Level> dim, BlockPos a, BlockPos b, Kind kind) {
        return new Area(name, dim, BlockPos.min(a, b), BlockPos.max(a, b), kind);
    }

    public boolean contains(ResourceKey<Level> d, BlockPos p) {
        return d == dim && p.getX() >= min.getX() && p.getX() <= max.getX() && p.getY() >= min.getY() && p.getY() <= max.getY()
                && p.getZ() >= min.getZ() && p.getZ() <= max.getZ();
    }

    public BoundingBox box() {
        return BoundingBox.fromCorners(min, max);
    }

    public long volume() {
        return (long) (max.getX() - min.getX() + 1) * (max.getY() - min.getY() + 1) * (max.getZ() - min.getZ() + 1);
    }

    public BlockPos centre() {
        return new BlockPos((min.getX() + max.getX()) / 2, (min.getY() + max.getY()) / 2, (min.getZ() + max.getZ()) / 2);
    }

    public String show() {
        return min.toShortString() + " .. " + max.toShortString();
    }

    public CompoundTag save() {
        CompoundTag t = new CompoundTag();
        t.putString("name", name);
        t.putString("dim", dim.identifier().toString());
        t.putLong("min", min.asLong());
        t.putLong("max", max.asLong());
        t.putString("kind", kind.name());
        return t;
    }

    public static Area load(CompoundTag t) {
        var dim = ResourceKey.create(Registries.DIMENSION, Identifier.parse(t.getStringOr("dim", "minecraft:overworld")));
        Kind kind;
        try {
            kind = Kind.valueOf(t.getStringOr("kind", "WORK"));
        } catch (IllegalArgumentException e) {
            kind = Kind.WORK;
        }
        return new Area(t.getStringOr("name", "area"), dim, BlockPos.of(t.getLongOr("min", 0)), BlockPos.of(t.getLongOr("max", 0)), kind);
    }
}
