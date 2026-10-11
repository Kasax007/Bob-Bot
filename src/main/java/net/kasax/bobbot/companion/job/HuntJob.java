package net.kasax.bobbot.companion.job;

import net.kasax.bobbot.bot.Bot;
import net.kasax.bobbot.bot.BotTask;
import net.kasax.bobbot.bot.task.KillTask;
import net.kasax.bobbot.companion.Companion;
import net.kasax.bobbot.companion.Lang;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.EntityType;

import java.util.Set;

/** Hunts so many of a kind (cows for leather, zombies for a farm's sake...), picking up what they drop. */
public final class HuntJob extends Job {
    private EntityType<?> type;
    private int count;
    private boolean sent;

    public HuntJob() {
    }

    public HuntJob(EntityType<?> type, int count) {
        this.type = type;
        this.count = count;
    }

    @Override
    public String kind() {
        return "hunt";
    }

    @Override
    public BotTask next(Companion c, Bot bot) {
        if (sent || type == null) return null;
        sent = true;
        return new KillTask(Set.of(type), Set.of(), 0, count);
    }

    @Override
    public boolean delivers() {
        return true;
    }

    @Override
    public String describe(Lang l) {
        String what = count + "× " + (type == null ? "?" : type.getDescription().getString());
        return l.t("hunt " + what, what + " jagen");
    }

    @Override
    protected void save(CompoundTag t) {
        if (type != null) t.putString("type", BuiltInRegistries.ENTITY_TYPE.getKey(type).toString());
        t.putInt("count", count);
    }

    @Override
    protected void load(CompoundTag t) {
        type = BuiltInRegistries.ENTITY_TYPE.getOptional(Identifier.parse(t.getStringOr("type", "minecraft:pig"))).orElse(null);
        count = t.getIntOr("count", 1);
    }
}
