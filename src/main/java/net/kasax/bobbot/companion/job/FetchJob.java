package net.kasax.bobbot.companion.job;

import net.kasax.bobbot.bot.Bot;
import net.kasax.bobbot.bot.BotTask;
import net.kasax.bobbot.bot.plan.ObtainPlanner;
import net.kasax.bobbot.companion.Companion;
import net.kasax.bobbot.companion.Lang;
import net.kasax.bobbot.companion.RoleChest;
import net.kasax.bobbot.companion.task.ChestTask;
import net.kasax.bobbot.companion.task.GiveTask;
import net.kasax.bobbot.storage.Storage;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.util.EnumSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Predicate;

/**
 * "Bring me 32 iron from the storage": out of the owner's chests (the nearest that has it first,
 * then the next) and to the owner. Short of the count: what there was, and word of it.
 */
public final class FetchJob extends Job {
    private final Set<Item> items = new LinkedHashSet<>();
    private int count;
    private boolean given, exhausted;
    private int got, tries;

    public FetchJob() {
    }

    public FetchJob(Set<Item> items, int count) {
        this.items.addAll(items);
        this.count = count;
    }

    @Override
    public String kind() {
        return "fetch";
    }

    @Override
    public String describe(Lang l) {
        String what = count + "× " + net.kasax.bobbot.control.Names.display(items, l);
        return l.t("bring you " + what + " from your chests", "dir " + what + " aus deinen Kisten bringen");
    }

    private Predicate<ItemStack> what() {
        return st -> items.contains(st.getItem());
    }

    /** Every chest of the owner's Bob may take from. */
    static List<Storage.Box> all(Companion c, ServerLevel level) {
        return Storage.boxes(c, level, EnumSet.allOf(RoleChest.Role.class));
    }

    @Override
    public BotTask next(Companion c, Bot bot) {
        if (items.isEmpty() || count <= 0) return null;
        int have = ObtainPlanner.countAny(bot.body(), items);
        if (have < count && !exhausted && ++tries <= 12) {
            ServerLevel level = (ServerLevel) bot.body().level();
            var found = Storage.find(level, all(c, level), what());
            if (!found.isEmpty()) {
                // (The nearest of those that have some.)
                BlockPos at = bot.body().blockPosition();
                Storage.Found best = found.get(0);
                for (var f : found) if (f.box().pos().distSqr(at) < best.box().pos().distSqr(at)) best = f;
                return ChestTask.take(best.box().pos(), what(), count - have, 0);
            }
            exhausted = true;
        }
        if (given) return null;
        if (have == 0) {
            fail("there are no " + net.kasax.bobbot.control.Names.display(items, Lang.EN) + " in your chests",
                    "in deinen Kisten sind keine " + net.kasax.bobbot.control.Names.display(items, Lang.DE));
            return null;
        }
        given = true;
        got = Math.min(have, count);
        ServerPlayer owner = c.ownerPlayer(bot.server());
        if (owner == null) return null;
        return new GiveTask(owner, what(), got);
    }

    @Override
    public void ended(Companion c, Bot bot, boolean done) {
        if (done && got > 0 && got < count) {
            Lang l = c.lang(bot.server());
            var server = bot.server();
            String id = BuiltInRegistries.ITEM.getKey(items.iterator().next()).getPath();
            c.tell(server, net.minecraft.network.chat.Component.literal(l.t("There were only " + got + " in your chests. ",
                    "In deinen Kisten waren nur " + got + ". ")).append(net.kasax.bobbot.control.Menus.button(
                    l.t("Get the rest", "Den Rest besorgen"), "/bob get " + id + " " + (count - got), l.t("Mine, craft or hunt the rest", "Den Rest abbauen, herstellen oder jagen"))));
        }
    }

    @Override
    protected void save(CompoundTag t) {
        ListTag ids = new ListTag();
        for (Item i : items) ids.add(StringTag.valueOf(BuiltInRegistries.ITEM.getKey(i).toString()));
        t.put("items", ids);
        t.putInt("count", count);
    }

    @Override
    protected void load(CompoundTag t) {
        var ids = t.getListOrEmpty("items");
        for (int i = 0; i < ids.size(); i++) {
            Item it = BuiltInRegistries.ITEM.getValue(Identifier.parse(ids.getStringOr(i, "minecraft:air")));
            if (it != Items.AIR) items.add(it);
        }
        count = t.getIntOr("count", 1);
    }
}
