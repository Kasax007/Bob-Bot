package net.kasax.bobbot.work;

import net.kasax.bobbot.bot.Bot;
import net.kasax.bobbot.bot.BotInventory;
import net.kasax.bobbot.bot.BotTask;
import net.kasax.bobbot.bot.task.CollectDropsTask;
import net.kasax.bobbot.companion.Area;
import net.kasax.bobbot.companion.Companion;
import net.kasax.bobbot.companion.Lang;
import net.kasax.bobbot.companion.job.Job;
import net.kasax.bobbot.companion.task.IdleTask;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CropBlock;
import net.minecraft.world.level.block.NetherWartBlock;
import net.minecraft.world.level.block.state.BlockState;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * "Harvest the fields": every ripe crop round him (or in a work area) taken and the seed put back
 * at once, melons and pumpkins cut from their stems, sugar cane down to the bottom piece; then
 * what was dropped picked up and the harvest into the drop-off chest. On and on if asked: a round
 * whenever something is ripe again.
 */
public final class FarmJob extends Job {
    private String area;
    private BlockPos centre;
    private boolean loop;
    private int harvested, rounds;
    private final Map<BlockPos, Item> replant = new HashMap<>();
    private boolean collecting;
    private BlockPos lastField;
    private int replantTries;
    private static final int RADIUS = 24;

    public FarmJob() {
    }

    public FarmJob(String area, boolean loop) {
        this.area = area;
        this.loop = loop;
    }

    @Override
    public String kind() {
        return "farm";
    }

    @Override
    public String describe(Lang l) {
        String where = area == null ? "" : " (" + area + ")";
        return loop ? l.t("keep the fields harvested" + where, "die Felder laufend ernten" + where) : l.t("harvest the fields" + where, "die Felder ernten" + where);
    }

    @Override
    public String progress(Lang l) {
        return l.t(harvested + " harvested", harvested + " geerntet");
    }

    @Override
    public boolean delivers() {
        return true;
    }

    @Override
    public boolean endless() {
        return loop;
    }

    /** What to put back where this grew, or null if nothing goes back (melon, cane: the plant stays). */
    static Item seedFor(BlockState s) {
        if (s.is(Blocks.WHEAT)) return Items.WHEAT_SEEDS;
        if (s.is(Blocks.CARROTS)) return Items.CARROT;
        if (s.is(Blocks.POTATOES)) return Items.POTATO;
        if (s.is(Blocks.BEETROOTS)) return Items.BEETROOT_SEEDS;
        if (s.is(Blocks.NETHER_WART)) return Items.NETHER_WART;
        if (s.is(Blocks.TORCHFLOWER_CROP)) return Items.TORCHFLOWER_SEEDS;
        return null;
    }

    /** Ripe and fit to take. */
    static boolean ripe(ServerLevel level, BlockPos p, BlockState s) {
        if (s.getBlock() instanceof CropBlock crop) return crop.isMaxAge(s);
        if (s.getBlock() instanceof NetherWartBlock) return s.getValue(NetherWartBlock.AGE) >= 3;
        if (s.is(Blocks.MELON) || s.is(Blocks.PUMPKIN)) {
            // (Only one grown from a stem beside it: a pumpkin put down for show stays.)
            for (var d : net.minecraft.core.Direction.Plane.HORIZONTAL) {
                BlockState n = level.getBlockState(p.relative(d));
                if (n.is(Blocks.ATTACHED_MELON_STEM) || n.is(Blocks.ATTACHED_PUMPKIN_STEM)) return true;
            }
            return false;
        }
        // Sugar cane and bamboo: all but the bottom piece.
        if (s.is(Blocks.SUGAR_CANE)) return level.getBlockState(p.below()).is(Blocks.SUGAR_CANE);
        if (s.is(Blocks.BAMBOO)) return level.getBlockState(p.below()).is(Blocks.BAMBOO);
        return false;
    }

    @Override
    public BotTask next(Companion c, Bot bot) {
        ServerLevel level = (ServerLevel) bot.body().level();
        Area a = area == null ? null : c.areas.get(area);
        if (centre == null) centre = a != null ? a.centre() : bot.body().blockPosition();
        // What the harvest dropped picked up (the seeds among it), then the seeds that could
        // not go back at once put in.
        if (collecting) {
            collecting = false;
            return new CollectDropsTask(lastField == null ? bot.body().blockPosition() : lastField, 12, 300);
        }
        if (!replant.isEmpty()) plantWhatCan(bot, level);
        List<BlockPos> ripe = new ArrayList<>();
        BlockPos.MutableBlockPos m = new BlockPos.MutableBlockPos();
        BlockPos min = a != null ? a.min() : centre.offset(-RADIUS, -4, -RADIUS), max = a != null ? a.max() : centre.offset(RADIUS, 4, RADIUS);
        for (int x = min.getX(); x <= max.getX(); x++) {
            for (int z = min.getZ(); z <= max.getZ(); z++) {
                for (int y = min.getY(); y <= max.getY(); y++) {
                    BlockState s = level.getBlockState(m.set(x, y, z));
                    if (s.isAir()) continue;
                    if (ripe(level, m, s)) ripe.add(m.immutable());
                }
            }
        }
        if (ripe.isEmpty()) {
            if (!replant.isEmpty() && replantTries++ < 3) return new net.kasax.bobbot.bot.task.GoToTask(replant.keySet().iterator().next(), 3);
            if (!loop) {
                if (rounds == 0) fail("nothing is ripe here", "hier ist nichts reif");
                return null;
            }
            rounds++;
            return new IdleTask(1200, c.ownerPlayer(bot.server()));
        }
        rounds++;
        replantTries = 0;
        long sx = 0, sy = 0, sz = 0;
        for (BlockPos p : ripe) {
            sx += p.getX();
            sy += p.getY();
            sz += p.getZ();
        }
        lastField = new BlockPos((int) (sx / ripe.size()), (int) (sy / ripe.size()), (int) (sz / ripe.size()));
        Map<BlockPos, Item> seeds = new HashMap<>();
        for (BlockPos p : ripe) {
            Item seed = seedFor(level.getBlockState(p));
            if (seed != null) seeds.put(p, seed);
        }
        return new BreakBlocksTask("harvest", ripe, s -> true, false, (b, p) -> {
            Item seed = seeds.get(p);
            if (seed != null && !plant(b, (ServerLevel) b.body().level(), p, seed)) replant.put(p, seed);
        });
    }

    private void plantWhatCan(Bot bot, ServerLevel level) {
        for (var it = replant.entrySet().iterator(); it.hasNext(); ) {
            var e = it.next();
            if (!level.getBlockState(e.getKey()).isAir()) {
                it.remove();
                continue;
            }
            if (plant(bot, level, e.getKey(), e.getValue())) it.remove();
        }
    }

    /** The seed onto the farmland (or soul sand) where the crop was, if in reach and in the pack. */
    private static boolean plant(Bot bot, ServerLevel level, BlockPos p, Item seed) {
        if (!level.getBlockState(p).isAir() || !bot.actions().inReach(p)) return false;
        int slot = BotInventory.slotOf(bot.body(), seed);
        if (slot < 0) return false;
        bot.tools().select(slot);
        return bot.actions().placeHeld(p);
    }

    @Override
    public void stepEnded(Companion c, Bot bot, BotTask step, boolean ok) {
        if (step instanceof BreakBlocksTask b) {
            harvested += b.broken;
            collecting = b.broken > 0;
        }
    }

    @Override
    protected void save(CompoundTag t) {
        if (area != null) t.putString("area", area);
        t.putBoolean("loop", loop);
        if (centre != null) t.putLong("centre", centre.asLong());
    }

    @Override
    protected void load(CompoundTag t) {
        area = t.getString("area").orElse(null);
        loop = t.getBooleanOr("loop", false);
        centre = t.getLong("centre").map(BlockPos::of).orElse(null);
    }
}
