package net.kasax.bobbot.companion.job;

import net.kasax.bobbot.bot.Bot;
import net.kasax.bobbot.bot.BotTask;
import net.kasax.bobbot.bot.plan.ObtainPlanner;
import net.kasax.bobbot.bot.task.ObtainTask;
import net.kasax.bobbot.companion.Companion;
import net.kasax.bobbot.companion.Lang;
import net.kasax.bobbot.companion.task.GiveTask;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;

import java.util.LinkedHashSet;
import java.util.Set;

/**
 * "Get me 32 iron ingots": whatever it takes - mined, crafted, smelted, hunted - the planner knows
 * the way from what is in the pack and in sight. Then handed to the owner, or into the drop-off
 * chest, or kept.
 */
public final class GatherJob extends Job {
    public enum Then { OWNER, CHEST, KEEP }

    private final Set<Item> items = new LinkedHashSet<>();
    private int count;
    private Then then = Then.OWNER;
    private int start = -1, tries;
    private boolean got, handed;

    public GatherJob() {
    }

    public GatherJob(Set<Item> items, int count, Then then) {
        this.items.addAll(items);
        this.count = count;
        this.then = then;
    }

    @Override
    public String kind() {
        return "gather";
    }

    private int have(Bot bot) {
        return ObtainPlanner.countAny(bot.body(), items);
    }

    @Override
    public void started(Companion c, Bot bot) {
        // (What he already carries counts: "get me 10" with 4 in the pack means 6 more.)
        start = 0;
    }

    @Override
    public BotTask next(Companion c, Bot bot) {
        if (items.isEmpty() || count <= 0) return null;
        if (!got) {
            if (have(bot) >= count) got = true;
            else {
                if (++tries > 4) {
                    fail("I could not get " + net.kasax.bobbot.control.Names.display(items, Lang.EN), "ich konnte keine " + net.kasax.bobbot.control.Names.display(items, Lang.DE) + " besorgen");
                    return null;
                }
                return new ObtainTask(items, count);
            }
        }
        if (then == Then.OWNER && !handed) {
            handed = true;
            ServerPlayer owner = c.ownerPlayer(bot.server());
            if (owner == null) return null;
            return new GiveTask(owner, st -> items.contains(st.getItem()), count);
        }
        return null;
    }

    @Override
    public boolean delivers() {
        return then == Then.CHEST;
    }

    @Override
    public String describe(Lang l) {
        String what = count + "× " + net.kasax.bobbot.control.Names.display(items, l);
        return switch (then) {
            case OWNER -> l.t("bring you " + what, "dir " + what + " bringen");
            case CHEST -> l.t("get " + what + " for the chest", what + " für die Kiste holen");
            case KEEP -> l.t("get " + what, what + " besorgen");
        };
    }

    @Override
    public String progress(Lang l) {
        return got ? l.t("got them", "habe alles") : "";
    }

    @Override
    protected void save(CompoundTag t) {
        ListTag ids = new ListTag();
        for (Item i : items) ids.add(StringTag.valueOf(BuiltInRegistries.ITEM.getKey(i).toString()));
        t.put("items", ids);
        t.putInt("count", count);
        t.putString("then", then.name());
    }

    @Override
    protected void load(CompoundTag t) {
        var ids = t.getListOrEmpty("items");
        for (int i = 0; i < ids.size(); i++) {
            Item it = BuiltInRegistries.ITEM.getValue(Identifier.parse(ids.getStringOr(i, "minecraft:air")));
            if (it != Items.AIR) items.add(it);
        }
        count = t.getIntOr("count", 1);
        try {
            then = Then.valueOf(t.getStringOr("then", "OWNER"));
        } catch (IllegalArgumentException e) {
            then = Then.OWNER;
        }
    }
}
