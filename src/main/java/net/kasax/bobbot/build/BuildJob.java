package net.kasax.bobbot.build;

import net.kasax.bobbot.bot.Bot;
import net.kasax.bobbot.bot.BotTask;
import net.kasax.bobbot.companion.Companion;
import net.kasax.bobbot.companion.Deliveries;
import net.kasax.bobbot.companion.Lang;
import net.kasax.bobbot.companion.RoleChest;
import net.kasax.bobbot.companion.job.Job;
import net.kasax.bobbot.companion.task.ChestTask;
import net.kasax.bobbot.companion.task.DepositTask;
import net.kasax.bobbot.companion.task.IdleTask;
import net.kasax.bobbot.control.Names;
import net.kasax.bobbot.protect.PlacedBlocks;
import net.kasax.bobbot.storage.Storage;
import net.kasax.bobbot.work.BreakBlocksTask;
import net.kasax.bobbot.work.Work;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.state.BlockState;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * "Build the house": a blueprint built where the owner put it, from the materials in his input
 * chests. The site cleared first (only what is nature's - nobody's house is dug away for it), then
 * block by block from the bottom up; what has no support yet waits for it. Materials are fetched
 * as the pack takes them; short of something, Bob says what and waits for it (or the owner says
 * stop). What he builds is the owner's: he never takes it down again. His own scaffolding goes
 * when he is done.
 */
public final class BuildJob extends Job {
    private String blueprint;
    private BlockPos origin;
    private Rotation rotation = Rotation.NONE;
    private Mirror mirror = Mirror.NONE;
    private boolean clear = true;

    private Site site;
    private int placed, waits, rounds, stalls, takeFails;
    private final Set<BlockPos> blocked = new HashSet<>();
    private final Set<Item> missingSaid = new HashSet<>();
    private boolean cleaned, depositTried;

    public BuildJob() {
    }

    public BuildJob(Site site) {
        this.site = site;
        this.blueprint = site.blueprint.name;
        this.origin = site.origin;
        this.rotation = site.rotation;
        this.mirror = site.mirror;
    }

    @Override
    public String kind() {
        return "build";
    }

    @Override
    public String describe(Lang l) {
        return l.t("build \"" + blueprint + "\" at " + origin.toShortString(), "\"" + blueprint + "\" bei " + origin.toShortString() + " bauen");
    }

    @Override
    public String progress(Lang l) {
        int total = site == null ? 0 : site.blueprint.blocks();
        return l.t(placed + " of " + total + " blocks", placed + " von " + total + " Blöcken");
    }

    @Override
    public double fraction() {
        int total = site == null ? 0 : site.blueprint.blocks();
        return total == 0 ? -1 : Math.min(1, placed / (double) total);
    }

    @Override
    public BotTask next(Companion c, Bot bot) {
        ServerLevel level = (ServerLevel) bot.body().level();
        if (site == null) {
            Blueprint bp = Blueprints.get(bot.server(), blueprint);
            if (bp == null) {
                fail("I can't find the blueprint \"" + blueprint + "\"", "ich finde den Bauplan \"" + blueprint + "\" nicht");
                return null;
            }
            site = new Site(bp, origin, rotation, mirror);
        }
        if (++rounds > 5000) return null;
        // The site cleared, top down: nature's blocks where the blueprint has air.
        if (clear) {
            List<BlockPos> clearing = new ArrayList<>();
            for (Site.Target t : site.targets) {
                if (!t.state().isAir() || blocked.contains(t.pos())) continue;
                BlockState now = level.getBlockState(t.pos());
                if (now.isAir() || !now.getFluidState().isEmpty() || now.canBeReplaced() && now.getCollisionShape(level, t.pos()).isEmpty()) continue;
                if (!bot.actions().mayBreak(t.pos()) || Work.liquidBeside(level, t.pos())) {
                    blocked.add(t.pos());
                    continue;
                }
                clearing.add(t.pos());
            }
            if (!clearing.isEmpty()) {
                clearing.sort(Comparator.comparingInt((BlockPos p) -> -p.getY()));
                return new BreakBlocksTask("clear the site", clearing.subList(0, Math.min(32, clearing.size())), s -> true, true, null);
            }
        }
        // What is still to go down.
        List<Site.Target> todo = new ArrayList<>();
        Map<Item, Integer> need = new LinkedHashMap<>();
        for (Site.Target t : site.targets) {
            if (t.state().isAir() || blocked.contains(t.pos())) continue;
            if (level.getBlockState(t.pos()) == t.state()) continue;
            todo.add(t);
            ItemStack st = Site.itemFor(t.state());
            if (!st.isEmpty()) need.merge(st.getItem(), st.getCount(), Integer::sum);
        }
        if (todo.isEmpty()) return cleanUp(bot, level);
        // Room in the pack for the materials (his loot from before into the drop-off chest).
        if (Work.freeSlots(bot) < 6 && !depositTried) {
            depositTried = true;
            var chest = c.nearestChest(RoleChest.Role.DROPOFF, level.dimension(), bot.body().blockPosition());
            if (chest != null) {
                var keep = Deliveries.keeper(bot);
                return new DepositTask(chest.block(), st -> keep.test(st) || need.containsKey(st.getItem()));
            }
        }
        // Materials: what the pack lacks for the next blocks (bottom up), out of the input chests.
        todo.sort(Comparator.comparingInt((Site.Target t) -> t.pos().getY()));
        if (takeFails < 3) {
            Map<Item, Integer> lacking = new LinkedHashMap<>();
            int slots = Math.max(1, Work.freeSlots(bot) - 2);
            for (Site.Target t : todo) {
                ItemStack st = Site.itemFor(t.state());
                if (st.isEmpty() || lacking.containsKey(st.getItem())) continue;
                int all = need.getOrDefault(st.getItem(), 0), have = bot.body().getInventory().countItem(st.getItem());
                if (have >= all) continue;
                if (lacking.size() >= slots) break;
                lacking.put(st.getItem(), all - have);
            }
            if (!lacking.isEmpty()) {
                var inputs = Storage.boxes(c, level, EnumSet.of(RoleChest.Role.INPUT));
                for (var f : Storage.find(level, inputs, st -> lacking.containsKey(st.getItem()))) {
                    return ChestTask.takeList(f.box().pos(), lacking, 1);
                }
            }
        }
        // Down with what there is.
        List<Site.Target> can = new ArrayList<>();
        Set<Item> short_ = new HashSet<>();
        for (Site.Target t : todo) {
            ItemStack st = Site.itemFor(t.state());
            if (st.isEmpty() || bot.body().getInventory().countItem(st.getItem()) >= st.getCount()) can.add(t);
            else short_.add(st.getItem());
        }
        if (!can.isEmpty() && stalls < 3) {
            waits = 0;
            return new PlaceBlocksTask(can.subList(0, Math.min(96, can.size())));
        }
        // (Nothing went down three times running: what still waits for a support it will not
        // get is left out.)
        if (!can.isEmpty()) {
            for (Site.Target t : can) blocked.add(t.pos());
            stalls = 0;
            return new IdleTask(1, null);
        }
        // Short of something: said once, then waited for (the owner fills the chest, or says stop).
        Set<Item> news = new HashSet<>(short_);
        news.removeAll(missingSaid);
        if (!news.isEmpty()) {
            missingSaid.addAll(news);
            Map<Item, Integer> amounts = new LinkedHashMap<>();
            for (Site.Target t : todo) {
                ItemStack st = Site.itemFor(t.state());
                if (!st.isEmpty() && short_.contains(st.getItem())) amounts.merge(st.getItem(), st.getCount(), Integer::sum);
            }
            StringBuilder en = new StringBuilder(), de = new StringBuilder();
            int n = 0;
            for (var e : amounts.entrySet()) {
                if (n++ == 8) {
                    en.append(", …");
                    de.append(", …");
                    break;
                }
                if (n > 1) {
                    en.append(", ");
                    de.append(", ");
                }
                en.append(e.getValue()).append("× ").append(Names.display(Set.of(e.getKey()), Lang.EN));
                de.append(e.getValue()).append("× ").append(Names.display(Set.of(e.getKey()), Lang.DE));
            }
            c.say(bot.server(), "For \"" + blueprint + "\" I'm missing " + en + ". Put it into my input chest and I'll go on.",
                    "Für \"" + blueprint + "\" fehlen mir " + de + ". Leg es in meine Material-Kiste, dann baue ich weiter.");
        }
        if (++waits > 360) {
            fail("I waited half an hour for the missing materials", "ich habe eine halbe Stunde auf das fehlende Material gewartet");
            return null;
        }
        return new IdleTask(100, c.ownerPlayer(bot.server()));
    }

    /** His own blocks (pillars, steps) in and round the build taken away; then done. */
    private BotTask cleanUp(Bot bot, ServerLevel level) {
        if (cleaned) return null;
        List<BlockPos> own = new ArrayList<>();
        var box = site.box;
        for (int x = box.minX() - 2; x <= box.maxX() + 2; x++)
            for (int y = box.minY() - 1; y <= box.maxY() + 2; y++)
                for (int z = box.minZ() - 2; z <= box.maxZ() + 2; z++) {
                    BlockPos p = new BlockPos(x, y, z);
                    if (PlacedBlocks.at(level, p) == PlacedBlocks.By.BOB && !level.getBlockState(p).isAir()) own.add(p);
                }
        if (own.isEmpty()) {
            cleaned = true;
            return null;
        }
        own.sort(Comparator.comparingInt((BlockPos p) -> -p.getY()));
        cleaned = true;
        return new BreakBlocksTask("take down my scaffolding", own, s -> true, false, null);
    }

    @Override
    public void stepEnded(Companion c, Bot bot, BotTask step, boolean ok) {
        if (step instanceof PlaceBlocksTask p) {
            placed += p.placed;
            blocked.addAll(p.blocked);
            if (p.placed > 0) {
                stalls = 0;
                takeFails = 0;
            } else stalls++;
        } else if (step instanceof ChestTask t) {
            if (t.moved == 0) takeFails++;
            else takeFails = 0;
        }
    }

    @Override
    public void ended(Companion c, Bot bot, boolean done) {
        if (done && !blocked.isEmpty()) {
            c.say(bot.server(), blocked.size() + " blocks of the build I had to leave (something of somebody's in the way, or no way there).",
                    blocked.size() + " Blöcke des Baus musste ich auslassen (etwas Fremdes im Weg oder kein Weg dorthin).");
        }
    }

    @Override
    protected void save(CompoundTag t) {
        t.putString("blueprint", blueprint);
        t.putLong("origin", origin.asLong());
        t.putString("rotation", rotation.name());
        t.putString("mirror", mirror.name());
        t.putBoolean("clear", clear);
        t.putInt("placed", placed);
    }

    @Override
    protected void load(CompoundTag t) {
        blueprint = t.getStringOr("blueprint", "?");
        origin = BlockPos.of(t.getLongOr("origin", 0));
        try {
            rotation = Rotation.valueOf(t.getStringOr("rotation", "NONE"));
            mirror = Mirror.valueOf(t.getStringOr("mirror", "NONE"));
        } catch (IllegalArgumentException e) {
            rotation = Rotation.NONE;
            mirror = Mirror.NONE;
        }
        clear = t.getBooleanOr("clear", true);
        placed = t.getIntOr("placed", 0);
    }
}
