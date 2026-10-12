package net.kasax.bobbot.work;

import net.kasax.bobbot.bot.Bot;
import net.kasax.bobbot.bot.BotTask;
import net.kasax.bobbot.bot.plan.BotKnowledge;
import net.kasax.bobbot.bot.plan.ObtainPlanner;
import net.kasax.bobbot.bot.task.SmeltTask;
import net.kasax.bobbot.companion.Companion;
import net.kasax.bobbot.companion.Lang;
import net.kasax.bobbot.companion.RoleChest;
import net.kasax.bobbot.companion.job.Job;
import net.kasax.bobbot.companion.task.ChestTask;
import net.kasax.bobbot.control.Names;
import net.kasax.bobbot.storage.Storage;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * "Smelt the iron": what there is to smelt (raw ore, sand, food...) out of his pack and the
 * drop-off and material chests, fuel from the fuel chest (or coal, charcoal, wood he carries), into
 * a furnace near by (or his own, put down) - the bars, the glass, the steaks into the drop-off
 * chest after.
 */
public final class SmeltJob extends Job {
    /** What goes in - what comes out (the furnace's common work). */
    static final Map<Item, Item> SMELTS = new LinkedHashMap<>();

    static {
        SMELTS.put(Items.RAW_IRON, Items.IRON_INGOT);
        SMELTS.put(Items.RAW_GOLD, Items.GOLD_INGOT);
        SMELTS.put(Items.RAW_COPPER, Items.COPPER_INGOT);
        SMELTS.put(Items.IRON_ORE, Items.IRON_INGOT);
        SMELTS.put(Items.GOLD_ORE, Items.GOLD_INGOT);
        SMELTS.put(Items.COPPER_ORE, Items.COPPER_INGOT);
        SMELTS.put(Items.SAND, Items.GLASS);
        SMELTS.put(Items.RED_SAND, Items.GLASS);
        SMELTS.put(Items.COBBLESTONE, Items.STONE);
        SMELTS.put(Items.CLAY_BALL, Items.BRICK);
        SMELTS.put(Items.NETHERRACK, Items.NETHER_BRICK);
        SMELTS.put(Items.KELP, Items.DRIED_KELP);
        SMELTS.put(Items.BEEF, Items.COOKED_BEEF);
        SMELTS.put(Items.PORKCHOP, Items.COOKED_PORKCHOP);
        SMELTS.put(Items.CHICKEN, Items.COOKED_CHICKEN);
        SMELTS.put(Items.MUTTON, Items.COOKED_MUTTON);
        SMELTS.put(Items.RABBIT, Items.COOKED_RABBIT);
        SMELTS.put(Items.COD, Items.COOKED_COD);
        SMELTS.put(Items.SALMON, Items.COOKED_SALMON);
        SMELTS.put(Items.POTATO, Items.BAKED_POTATO);
        SMELTS.put(Items.ANCIENT_DEBRIS, Items.NETHERITE_SCRAP);
        SMELTS.put(Items.WET_SPONGE, Items.SPONGE);
    }

    static final Set<Item> FUEL = Set.of(Items.COAL, Items.CHARCOAL, Items.COAL_BLOCK, Items.LAVA_BUCKET, Items.BLAZE_ROD, Items.DRIED_KELP_BLOCK);

    /** Only this to smelt (null: whatever there is). */
    private Item input;
    private int made;
    private boolean fetched, fueled;
    private int tries;

    public SmeltJob() {
    }

    public SmeltJob(Item input) {
        this.input = input;
    }

    @Override
    public String kind() {
        return "smelt";
    }

    @Override
    public String describe(Lang l) {
        return input == null ? l.t("smelt what there is", "einschmelzen, was da ist") : l.t("smelt ", "") + Names.display(Set.of(input), l) + l.t("", " schmelzen");
    }

    @Override
    public String progress(Lang l) {
        return l.t(made + " done", made + " fertig");
    }

    @Override
    public boolean delivers() {
        return true;
    }

    private boolean wanted(ItemStack st) {
        return input == null ? SMELTS.containsKey(st.getItem()) && !st.is(Items.COBBLESTONE) && !st.is(Items.NETHERRACK) && !st.is(Items.POTATO)
                : st.is(input);
    }

    @Override
    public BotTask next(Companion c, Bot bot) {
        ServerLevel level = (ServerLevel) bot.body().level();
        if (++tries > 40) return null;
        // What to smelt from the chests (the drop-off, the material chests).
        if (!fetched) {
            fetched = true;
            var boxes = Storage.boxes(c, level, EnumSet.of(RoleChest.Role.DROPOFF, RoleChest.Role.INPUT));
            var found = Storage.find(level, boxes, this::wanted);
            if (!found.isEmpty()) {
                fetched = false; // (another chest after this one)
                return ChestTask.take(found.get(0).box().pos(), this::wanted, 64 * 6, 3);
            }
        }
        // Fuel: what he carries, else from the fuel chest.
        int fuel = 0;
        for (ItemStack st : bot.body().getInventory().getNonEquipmentItems()) if (FUEL.contains(st.getItem())) fuel += st.getCount();
        if (fuel == 0 && !fueled) {
            fueled = true;
            var boxes = Storage.boxes(c, level, EnumSet.of(RoleChest.Role.FUEL, RoleChest.Role.STORAGE, RoleChest.Role.DROPOFF));
            var found = Storage.find(level, boxes, st -> FUEL.contains(st.getItem()));
            if (!found.isEmpty()) return ChestTask.take(found.get(0).box().pos(), st -> FUEL.contains(st.getItem()), 32, 2);
        }
        // One kind at a time into the furnace.
        for (ItemStack st : bot.body().getInventory().getNonEquipmentItems()) {
            if (st.isEmpty() || !wanted(st)) continue;
            Item out = SMELTS.get(st.getItem());
            BotKnowledge.SmeltRoute route = route(bot, st.getItem(), out);
            if (route == null) continue;
            int n = ObtainPlanner.countAny(bot.body(), Set.of(st.getItem()));
            return new SmeltTask(route, n);
        }
        if (made == 0) fail("there is nothing to smelt (or no fuel)", "es gibt nichts zu schmelzen (oder keinen Brennstoff)");
        return null;
    }

    private static BotKnowledge.SmeltRoute route(Bot bot, Item in, Item out) {
        if (out == null) return null;
        for (var r : BotKnowledge.get(bot.server()).routesTo(out)) {
            if (r instanceof BotKnowledge.SmeltRoute s && s.input().test(new ItemStack(in))) return s;
        }
        return null;
    }

    @Override
    public void stepEnded(Companion c, Bot bot, BotTask step, boolean ok) {
        if (step instanceof SmeltTask && ok) made++;
    }

    @Override
    protected void save(CompoundTag t) {
        if (input != null) t.putString("input", BuiltInRegistries.ITEM.getKey(input).toString());
    }

    @Override
    protected void load(CompoundTag t) {
        input = t.getString("input").map(s -> BuiltInRegistries.ITEM.getValue(Identifier.parse(s))).filter(i -> i != Items.AIR).orElse(null);
    }
}
