package net.kasax.bobbot.companion;

import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.core.registries.Registries;

import java.util.Locale;

/** A chest (or barrel, shulker box) the owner has given Bob a job for. */
public record RoleChest(GlobalPos pos, Role role) {
    public enum Role {
        /** Where Bob empties his pack after a job, or when it is full. */
        DROPOFF("drop-off", "Ablage"),
        /** Materials for building and crafting: Bob takes from it, never puts into it. */
        INPUT("input", "Material"),
        /** Part of the owner's sorted storage: Bob sorts into it by what it already holds. */
        STORAGE("storage", "Lager"),
        /** Storage for what has no place yet. */
        OVERFLOW("overflow", "Überlauf"),
        /** Coal and wood for the furnaces. */
        FUEL("fuel", "Brennstoff"),
        /** Food Bob may eat and take along. */
        FOOD("food", "Essen");

        public final String en, de;

        Role(String en, String de) {
            this.en = en;
            this.de = de;
        }

        public String label(Lang l) {
            return l.t(en, de);
        }

        /** From the English or German name, any case (and "dropoff", "lager", ...). */
        public static Role parse(String s) {
            String v = s.toLowerCase(Locale.ROOT).replace("-", "").replace("_", "");
            for (Role r : values()) {
                if (r.name().toLowerCase(Locale.ROOT).equals(v) || r.en.replace("-", "").equals(v) || r.de.toLowerCase(Locale.ROOT).equals(v)) return r;
            }
            return switch (v) {
                case "drop", "deposit", "abgabe", "abliefern" -> DROPOFF;
                case "materials", "material", "eingabe" -> INPUT;
                case "chest", "kiste", "sorted", "sortiert" -> STORAGE;
                case "rest", "sonstiges", "misc" -> OVERFLOW;
                case "coal", "kohle" -> FUEL;
                case "essen", "nahrung" -> FOOD;
                default -> null;
            };
        }
    }

    public BlockPos block() {
        return pos.pos();
    }

    public CompoundTag save() {
        CompoundTag t = new CompoundTag();
        t.putString("dim", pos.dimension().identifier().toString());
        t.putLong("pos", pos.pos().asLong());
        t.putString("role", role.name());
        return t;
    }

    public static RoleChest load(CompoundTag t) {
        var dim = ResourceKey.create(Registries.DIMENSION, Identifier.parse(t.getStringOr("dim", "minecraft:overworld")));
        Role role;
        try {
            role = Role.valueOf(t.getStringOr("role", "DROPOFF"));
        } catch (IllegalArgumentException e) {
            role = Role.DROPOFF;
        }
        return new RoleChest(GlobalPos.of(dim, BlockPos.of(t.getLongOr("pos", 0))), role);
    }
}
