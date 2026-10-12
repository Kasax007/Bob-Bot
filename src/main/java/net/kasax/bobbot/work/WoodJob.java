package net.kasax.bobbot.work;

import net.kasax.bobbot.bot.Bot;
import net.kasax.bobbot.bot.BotActions;
import net.kasax.bobbot.bot.BotTask;
import net.kasax.bobbot.bot.BotWorld;
import net.kasax.bobbot.bot.task.ObtainTask;
import net.kasax.bobbot.companion.Area;
import net.kasax.bobbot.companion.Companion;
import net.kasax.bobbot.companion.Lang;
import net.kasax.bobbot.companion.job.Job;
import net.kasax.bobbot.companion.task.IdleTask;
import net.kasax.bobbot.protect.Trees;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * "Fell trees" / "farm wood": grown trees felled one after the other, nearest first, round where
 * he was asked (or in a work area), saplings put back; the logs into the drop-off chest when the
 * pack is full. With no count, on and on (the saplings he planted grow meanwhile). Never a log
 * of somebody's house: only trees that grew (see {@link Trees}).
 */
public final class WoodJob extends Job {
    private int count;
    private String area;
    private BlockPos centre;
    private int got;
    private final Set<BlockPos> failed = new HashSet<>();
    private boolean triedAxe, triedBlocks;
    private int waits;
    private BlockPos felling;
    /** How far round the centre he looks for trees (without an area). */
    private static final int RADIUS = 40;

    public WoodJob() {
    }

    public WoodJob(int count, String area) {
        this.count = count;
        this.area = area;
    }

    @Override
    public String kind() {
        return "wood";
    }

    @Override
    public String describe(Lang l) {
        String where = area == null ? "" : l.t(" in \"" + area + "\"", " in \"" + area + "\"");
        return count > 0 ? l.t("fell trees for " + count + " logs" + where, "Bäume für " + count + " Holzstämme fällen" + where)
                : l.t("farm wood" + where, "Holz farmen" + where);
    }

    @Override
    public String progress(Lang l) {
        return count > 0 ? got + "/" + count + l.t(" logs", " Stämme") : got + l.t(" logs", " Stämme");
    }

    @Override
    public double fraction() {
        return count > 0 ? Math.min(1, got / (double) count) : -1;
    }

    @Override
    public boolean delivers() {
        return true;
    }

    @Override
    public boolean endless() {
        return count <= 0;
    }

    @Override
    public BotTask next(Companion c, Bot bot) {
        ServerLevel level = (ServerLevel) bot.body().level();
        Area a = area == null ? null : c.areas.get(area);
        if (area != null && a == null) {
            fail("the area \"" + area + "\" is gone", "den Bereich \"" + area + "\" gibt es nicht mehr");
            return null;
        }
        if (centre == null) centre = a != null ? a.centre() : bot.body().blockPosition();
        if (count > 0 && got >= count) return null;
        var body = bot.body();
        // An axe makes it four times quicker: made (or got) once if there is none.
        if (!triedAxe && !hasAxe(body)) {
            triedAxe = true;
            return new ObtainTask(Set.of(Items.WOODEN_AXE, Items.STONE_AXE, Items.IRON_AXE, Items.DIAMOND_AXE, Items.GOLDEN_AXE, Items.NETHERITE_AXE), 1);
        }
        // Some blocks to build up to the top of a tall trunk.
        if (!triedBlocks && BotActions.buildingBlocks(body) < 8) {
            triedBlocks = true;
            return new ObtainTask(Set.of(Items.DIRT, Items.COBBLESTONE, Items.COBBLED_DEEPSLATE), 16);
        }
        Trees.Tree tree = nearestTree(level, body.blockPosition(), a);
        if (tree == null) {
            if (count <= 0 && waits++ < 1000) return new IdleTask(600, c.ownerPlayer(bot.server())); // (the saplings growing)
            fail("there are no more grown trees here", "hier gibt es keine gewachsenen Bäume mehr");
            return null;
        }
        waits = 0;
        felling = tree.base();
        return new FellTreeTask(tree, level.getBlockState(tree.base()).getBlock(), c.settings.replant);
    }

    private static boolean hasAxe(net.kasax.bobbot.bot.BotPlayer body) {
        for (ItemStack st : body.getInventory().getNonEquipmentItems()) if (st.is(ItemTags.AXES)) return true;
        return false;
    }

    /** The nearest grown tree round the centre (or in the area) not given up on. */
    private Trees.Tree nearestTree(ServerLevel level, BlockPos at, Area a) {
        BlockPos centreNow = a != null ? a.centre() : centre;
        int radius = a != null ? Math.max(a.max().getX() - a.min().getX(), a.max().getZ() - a.min().getZ()) / 2 + 2 : RADIUS;
        List<BlockPos> logs = BotWorld.nearestN(level, centreNow, radius, 24, s -> s.is(BlockTags.LOGS), false, failed, 64);
        Trees.Tree best = null;
        double bestD = Double.MAX_VALUE;
        Set<BlockPos> seen = new HashSet<>();
        for (BlockPos p : logs) {
            if (seen.contains(p)) continue;
            if (a != null && !a.contains(level.dimension(), p)) continue;
            Trees.Tree t = Trees.treeAt(level, p);
            if (t == null) continue;
            seen.addAll(t.logs());
            if (!t.grown() || failed.contains(t.base())) {
                failed.add(p);
                continue;
            }
            double d = t.base().distSqr(at);
            if (d < bestD) {
                bestD = d;
                best = t;
            }
        }
        return best;
    }

    @Override
    public void stepEnded(Companion c, Bot bot, BotTask step, boolean ok) {
        if (step instanceof FellTreeTask f) {
            got += f.felled;
            if (f.felled == 0 && felling != null) failed.add(felling);
        }
    }

    @Override
    protected void save(CompoundTag t) {
        t.putInt("count", count);
        if (area != null) t.putString("area", area);
        t.putInt("got", got);
        if (centre != null) t.putLong("centre", centre.asLong());
    }

    @Override
    protected void load(CompoundTag t) {
        count = t.getIntOr("count", 0);
        area = t.getString("area").orElse(null);
        got = t.getIntOr("got", 0);
        centre = t.getLong("centre").map(BlockPos::of).orElse(null);
    }
}
