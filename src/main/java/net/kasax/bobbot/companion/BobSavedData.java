package net.kasax.bobbot.companion;

import com.mojang.serialization.Codec;
import net.kasax.bobbot.BobBot;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.datafix.DataFixTypes;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.saveddata.SavedDataType;

/** Every Bob of the world (owner, jobs, chests, areas, settings), kept with the overworld's data. */
public final class BobSavedData extends SavedData {
    private static final Identifier KEY = Identifier.fromNamespaceAndPath(BobBot.MOD_ID, "companions");
    private static final Codec<BobSavedData> CODEC = CompoundTag.CODEC.xmap(BobSavedData::new, BobSavedData::toTag);
    public static final SavedDataType<BobSavedData> TYPE = new SavedDataType<>(KEY, BobSavedData::new, CODEC, DataFixTypes.LEVEL);

    /** As loaded; the manager turns it into companions once and writes them back on every save. */
    private CompoundTag tag = new CompoundTag();

    private BobSavedData() {
    }

    private BobSavedData(CompoundTag tag) {
        this.tag = tag.copy();
    }

    private CompoundTag toTag() {
        CompoundTag t = new CompoundTag();
        ListTag list = new ListTag();
        for (Companion c : CompanionManager.all()) list.add(c.save());
        t.put("companions", list);
        return t;
    }

    public static BobSavedData get(MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(TYPE);
    }

    ListTag loaded() {
        return tag.getListOrEmpty("companions");
    }

    /** Asks for a save with the next world save (positions, jobs and settings change all the time). */
    public void touch() {
        setDirty();
    }
}
