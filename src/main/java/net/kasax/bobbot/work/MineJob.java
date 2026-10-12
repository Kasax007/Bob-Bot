package net.kasax.bobbot.work;

import net.kasax.bobbot.bot.Bot;
import net.kasax.bobbot.bot.BotTask;
import net.kasax.bobbot.bot.task.GoToTask;
import net.kasax.bobbot.bot.task.ObtainTask;
import net.kasax.bobbot.companion.Companion;
import net.kasax.bobbot.companion.Lang;
import net.kasax.bobbot.companion.job.Job;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.util.Set;

/**
 * A branch mine, the way players dig for diamonds: a staircase down to the level, a main tunnel
 * two high, and every fourth block a branch to either side; the ore that shows in the walls taken
 * with its vein, torches in the dark, the rock thrown away (but a stack), the rest into the
 * drop-off chest when the pack is full - and back to where he left off. Never into lava or water:
 * there a tunnel ends.
 */
public final class MineJob extends Job {
    private int y = -58, length = 48, branch = 16;
    private Direction dir;
    /** Where the main tunnel starts (the foot of the stairs), and how far it has got. */
    private BlockPos main;
    private int at, side; // side 0: next the main tunnel on, 1: the left branch, 2: the right one
    private int turns, ores, dug, walks;
    private boolean triedPick, triedTorches;
    private String lastStop;

    public MineJob() {
    }

    public MineJob(int y, int length) {
        this.y = y;
        this.length = length;
    }

    public MineJob(int y, int length, int branch, net.minecraft.core.Direction dir) {
        this(y, length);
        this.branch = branch;
        this.dir = dir;
    }

    @Override
    public String kind() {
        return "mine";
    }

    @Override
    public String describe(Lang l) {
        return l.t("branch mine at y " + y, "eine Strip-Mine auf y " + y + " graben");
    }

    @Override
    public String progress(Lang l) {
        return l.t(at + "/" + length + " of the main tunnel, " + ores + " ore", at + "/" + length + " vom Hauptgang, " + ores + " Erze");
    }

    @Override
    public double fraction() {
        return Math.min(1, at / (double) length);
    }

    @Override
    public boolean delivers() {
        return true;
    }

    @Override
    public BotTask next(Companion c, Bot bot) {
        var body = bot.body();
        if (Work.freeSlots(bot) <= 4) Work.dropJunk(bot);
        // A pickaxe that does iron (stone at least), and torches if there is coal for them.
        if (!triedPick && !hasPickaxe(body)) {
            triedPick = true;
            return new ObtainTask(Set.of(Items.STONE_PICKAXE, Items.IRON_PICKAXE, Items.DIAMOND_PICKAXE, Items.NETHERITE_PICKAXE), 1);
        }
        if (!triedTorches && c.settings.torches && body.getInventory().countItem(Items.TORCH) < 8
                && (body.getInventory().countItem(Items.COAL) > 0 || body.getInventory().countItem(Items.CHARCOAL) > 0)) {
            triedTorches = true;
            return new ObtainTask(Set.of(Items.TORCH), 16);
        }
        if (dir == null) dir = body.getDirection().getAxis().isHorizontal() ? body.getDirection() : Direction.NORTH;
        int torchEvery = c.settings.torches ? 8 : 0;
        BlockPos feet = body.blockPosition();
        // Down the stairs first.
        if (main == null) {
            if (feet.getY() > y) return DigPathTask.stairsDown(feet, dir, y, torchEvery);
            main = feet;
        }
        if (at >= length) return null;
        // Back to the main tunnel after a branch (or a trip to the chest).
        BlockPos here = main.relative(dir, at);
        if (!feet.equals(here)) {
            if (++walks > 6) {
                fail("I can't get back into my mine", "ich komme nicht zurück in meine Mine");
                return null;
            }
            return new GoToTask(here, 0.6);
        }
        walks = 0;
        switch (side) {
            case 0 -> {
                side = 1;
                return DigPathTask.tunnel(here, dir, 4, torchEvery);
            }
            case 1 -> {
                side = 2;
                return DigPathTask.tunnel(here, dir.getCounterClockWise(), branch, torchEvery);
            }
            default -> {
                side = 0;
                return DigPathTask.tunnel(here, dir.getClockWise(), branch, torchEvery);
            }
        }
    }

    private static boolean hasPickaxe(net.kasax.bobbot.bot.BotPlayer body) {
        for (ItemStack st : body.getInventory().getNonEquipmentItems()) {
            if (st.is(ItemTags.PICKAXES) && !st.is(Items.WOODEN_PICKAXE) && !st.is(Items.GOLDEN_PICKAXE)) return true;
        }
        return false;
    }

    @Override
    public void stepEnded(Companion c, Bot bot, BotTask step, boolean ok) {
        if (!(step instanceof DigPathTask d)) return;
        ores += d.oresMined;
        dug += d.dug;
        var body = bot.body();
        if (main == null) {
            // The stairs ended early (lava, water): another way down, else here it is.
            if (d.stopped != null && body.blockPosition().getY() > y) {
                lastStop = d.stopped;
                if (++turns < 4) dir = dir.getClockWise();
                else main = body.blockPosition();
            }
            return;
        }
        // The main tunnel: on by what was dug; ended early, it ends here.
        if (side == 1) {
            BlockPos reached = body.blockPosition();
            int moved = Math.abs((reached.getX() - main.getX()) * dir.getStepX() + (reached.getZ() - main.getZ()) * dir.getStepZ());
            at = Math.max(at, moved);
            if (d.stopped != null) {
                lastStop = d.stopped;
                at = length; // (no further this way)
            }
        }
    }

    @Override
    public void ended(Companion c, Bot bot, boolean done) {
        if (done && lastStop != null) {
            c.say(bot.server(), "The mine ended early: " + lastStop + ".", "Die Mine endete früher: " + (lastStop.equals("lava ahead") ? "Lava voraus"
                    : lastStop.equals("water ahead") ? "Wasser voraus" : lastStop) + ".");
        }
    }

    @Override
    protected void save(CompoundTag t) {
        t.putInt("y", y);
        t.putInt("length", length);
        t.putInt("branch", branch);
        if (dir != null) t.putString("dir", dir.getName());
        if (main != null) t.putLong("main", main.asLong());
        t.putInt("at", at);
        t.putInt("side", side);
        t.putInt("ores", ores);
    }

    @Override
    protected void load(CompoundTag t) {
        y = t.getIntOr("y", -58);
        length = t.getIntOr("length", 48);
        branch = t.getIntOr("branch", 16);
        dir = t.getString("dir").map(Direction::byName).orElse(null);
        main = t.getLong("main").map(BlockPos::of).orElse(null);
        at = t.getIntOr("at", 0);
        side = t.getIntOr("side", 0);
        ores = t.getIntOr("ores", 0);
    }
}
