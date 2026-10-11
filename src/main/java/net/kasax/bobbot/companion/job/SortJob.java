package net.kasax.bobbot.companion.job;

import net.kasax.bobbot.bot.Bot;
import net.kasax.bobbot.bot.BotTask;
import net.kasax.bobbot.companion.Companion;
import net.kasax.bobbot.companion.Deliveries;
import net.kasax.bobbot.companion.Lang;
import net.kasax.bobbot.companion.RoleChest;
import net.kasax.bobbot.companion.task.ChestTask;
import net.kasax.bobbot.storage.Storage;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Predicate;

/**
 * "Sort that in": what is in the drop-off chests (and in his pack, but for his own kit) put away
 * into the owner's storage the way the owner sorted it - each thing to the chest that holds the
 * same already, else to the chest with the most alike things, a new kind into an empty chest,
 * the rest into the overflow chest. What has no place at all stays in the drop-off chest.
 */
public final class SortJob extends Job {
    public enum From { PACK, DROPOFF, ALL }

    private From from = From.ALL;
    private record Trip(BlockPos chest, List<Integer> slots) {
    }

    private final Deque<Trip> trips = new ArrayDeque<>();
    /** Kinds with no place anywhere: not taken out of the drop-off again. */
    private final Set<Item> nowhere = new LinkedHashSet<>();
    /** Pack slots filled from the drop-off chests in this job (sorted whatever they hold). */
    private final Set<Integer> carried = new HashSet<>();
    private int[] before;
    private boolean takeStep, returned;
    private int sorted, chests, rounds;
    private final Set<BlockPos> used = new HashSet<>();

    public SortJob() {
    }

    public SortJob(From from) {
        this.from = from;
    }

    @Override
    public String kind() {
        return "sort";
    }

    @Override
    public String describe(Lang l) {
        return switch (from) {
            case PACK -> l.t("sort my pack into your storage", "meinen Rucksack in dein Lager sortieren");
            case DROPOFF -> l.t("sort the drop-off chest into your storage", "die Ablage in dein Lager sortieren");
            case ALL -> l.t("sort everything into your storage", "alles in dein Lager sortieren");
        };
    }

    @Override
    public String progress(Lang l) {
        String s = l.t(sorted + " items into " + used.size() + " chests", sorted + " Gegenstände in " + used.size() + " Kisten");
        if (!nowhere.isEmpty()) s += l.t(", no place for ", ", kein Platz für ") + names(nowhere);
        return s;
    }

    private static String names(Set<Item> items) {
        List<String> n = new ArrayList<>();
        for (Item i : items) {
            n.add(i.getDefaultInstance().getHoverName().getString());
            if (n.size() == 4) break;
        }
        return String.join(", ", n) + (items.size() > 4 ? " …" : "");
    }

    @Override
    public BotTask next(Companion c, Bot bot) {
        ServerLevel level = (ServerLevel) bot.body().level();
        List<Storage.Box> storage = Storage.boxes(c, level, Set.of(RoleChest.Role.STORAGE, RoleChest.Role.OVERFLOW));
        if (storage.isEmpty()) {
            fail("I know no storage chests yet (mark a storage room with my wand, or right click a chest with it)",
                    "ich kenne noch keine Lager-Kisten (markiere einen Lagerraum mit meinem Zauberstab oder klick eine Kiste damit an)");
            return null;
        }
        if (++rounds > 400) return null;
        if (!trips.isEmpty()) {
            Trip t = trips.poll();
            used.add(t.chest());
            return ChestTask.put(t.chest(), t.slots());
        }
        var inv = bot.body().getInventory();
        List<ItemStack> pack = new ArrayList<>(inv.getNonEquipmentItems());
        // (A slot emptied into a chest is no longer one of those carried.)
        carried.removeIf(i -> i >= pack.size() || pack.get(i).isEmpty());
        Predicate<ItemStack> keep = Deliveries.keeper(bot);
        Set<Integer> sortableSlots = new HashSet<>();
        for (int i = 0; i < pack.size(); i++) {
            ItemStack st = pack.get(i);
            if (st.isEmpty() || nowhere.contains(st.getItem())) continue;
            boolean mine = !carried.contains(i);
            if (mine && (from == From.DROPOFF || keep.test(st))) continue;
            sortableSlots.add(i);
        }
        if (!sortableSlots.isEmpty()) {
            List<ItemStack> view = new ArrayList<>();
            for (int i = 0; i < pack.size(); i++) view.add(sortableSlots.contains(i) ? pack.get(i) : ItemStack.EMPTY);
            Map<Storage.Box, List<Integer>> plan = Storage.plan(level, storage, view, st -> true);
            List<Integer> none = plan.remove(null);
            if (none != null) for (int i : none) nowhere.add(pack.get(i).getItem());
            if (!plan.isEmpty()) {
                // (Chest after chest, the nearest next: no running back and forth.)
                BlockPos at = bot.body().blockPosition();
                List<Storage.Box> left = new ArrayList<>(plan.keySet());
                while (!left.isEmpty()) {
                    Storage.Box best = null;
                    double bd = Double.MAX_VALUE;
                    for (Storage.Box b : left) {
                        double d = b.pos().distSqr(at);
                        if (d < bd) {
                            bd = d;
                            best = b;
                        }
                    }
                    left.remove(best);
                    trips.add(new Trip(best.pos(), plan.get(best)));
                    at = best.pos();
                }
                Trip t = trips.poll();
                used.add(t.chest());
                return ChestTask.put(t.chest(), t.slots());
            }
        }
        // What has no place and came out of the drop-off: back in there, not carried about.
        List<Storage.Box> dropoff = Storage.boxes(c, level, Set.of(RoleChest.Role.DROPOFF));
        if (!returned && !dropoff.isEmpty()) {
            List<Integer> back = new ArrayList<>();
            for (int i : carried) if (!inv.getItem(i).isEmpty() && nowhere.contains(inv.getItem(i).getItem())) back.add(i);
            if (!back.isEmpty()) {
                returned = true;
                return ChestTask.put(dropoff.get(0).pos(), back);
            }
        }
        if (from == From.PACK) return null;
        // More in a drop-off chest: as much as the pack takes (a couple of slots kept free).
        for (Storage.Box d : dropoff) {
            Predicate<ItemStack> wanted = st -> !nowhere.contains(st.getItem());
            if (Storage.total(level, List.of(d), wanted) == 0) continue;
            before = counts(bot);
            takeStep = true;
            returned = false;
            return ChestTask.take(d.pos(), wanted, Integer.MAX_VALUE, 2);
        }
        return null;
    }

    private static int[] counts(Bot bot) {
        var items = bot.body().getInventory().getNonEquipmentItems();
        int[] out = new int[items.size()];
        for (int i = 0; i < out.length; i++) out[i] = items.get(i).getCount();
        return out;
    }

    @Override
    public void stepEnded(Companion c, Bot bot, BotTask step, boolean ok) {
        if (step instanceof ChestTask ct) {
            if (takeStep && before != null) {
                int[] now = counts(bot);
                for (int i = 0; i < now.length && i < before.length; i++) if (now[i] > before[i]) carried.add(i);
                takeStep = false;
            } else sorted += ct.moved;
            if (ct.full) trips.clear(); // (a chest ran full: the rest planned again, round it)
        }
    }

    @Override
    public void ended(Companion c, Bot bot, boolean done) {
        if (done && !nowhere.isEmpty()) {
            Lang l = c.lang(bot.server());
            c.say(bot.server(), "No place in your storage for: " + names(nowhere) + " (left in the drop-off chest).",
                    "Kein Platz in deinem Lager für: " + names(nowhere) + " (liegt in der Ablage).");
        }
    }

    @Override
    protected void save(CompoundTag t) {
        t.putString("from", from.name());
    }

    @Override
    protected void load(CompoundTag t) {
        try {
            from = From.valueOf(t.getStringOr("from", "ALL"));
        } catch (IllegalArgumentException e) {
            from = From.ALL;
        }
    }
}
