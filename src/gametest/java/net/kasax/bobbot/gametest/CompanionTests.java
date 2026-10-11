package net.kasax.bobbot.gametest;

import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.kasax.bobbot.bot.Bot;
import net.kasax.bobbot.bot.BotManager;
import net.kasax.bobbot.companion.Companion;
import net.kasax.bobbot.companion.CompanionManager;
import net.kasax.bobbot.companion.Lang;
import net.kasax.bobbot.companion.job.FollowJob;
import net.kasax.bobbot.companion.job.GatherJob;
import net.kasax.bobbot.companion.job.HuntJob;
import net.kasax.bobbot.companion.task.DepositTask;
import net.kasax.bobbot.control.ChatControlAccess;
import net.kasax.bobbot.world.Containers;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;

import java.util.concurrent.atomic.AtomicInteger;

import static net.kasax.bobbot.gametest.BotArena.FEET;
import static net.kasax.bobbot.gametest.BotArena.STRUCTURE;

/** Bob as a companion: understanding orders, coming to his owner, emptying his pack into a chest. */
public class CompanionTests {
    private static final AtomicInteger N = new AtomicInteger();

    /** A stand-in owner: another bot body in the arena (a real player is not to be had in a test). */
    static ServerPlayer owner(GameTestHelper h, BotArena a, int x, int z) {
        Bot o = BotManager.spawn(h.getLevel().getServer(), "Owner" + N.incrementAndGet(), h.getLevel(), Vec3.atBottomCenterOf(a.abs(x, FEET, z)));
        o.body().setGameMode(net.minecraft.world.level.GameType.SURVIVAL);
        h.onEachTick(() -> o.body().stopInputs());
        return o.body();
    }

    /** A Bob for this owner, in the arena at (x, z). */
    static Companion bob(GameTestHelper h, BotArena a, ServerPlayer owner, int x, int z) {
        Companion c = CompanionManager.create(owner, "Bobt" + N.incrementAndGet());
        CompanionManager.summon(h.getLevel().getServer(), c, h.getLevel(), Vec3.atBottomCenterOf(a.abs(x, FEET, z)));
        c.bot.body().setGameMode(net.minecraft.world.level.GameType.SURVIVAL);
        c.bot.verbose = true;
        a.use(c.bot);
        return c;
    }

    /** Bob and the stand-in owner out of the world, the test passed. */
    static void done(GameTestHelper h, Companion c, ServerPlayer owner) {
        CompanionManager.delete(h.getLevel().getServer(), c);
        Bot o = BotManager.byName(owner.getGameProfile().name());
        if (o != null) BotManager.remove(h.getLevel().getServer(), o);
        h.succeed();
    }

    /** Lines in chat, English and German, understood as the orders they are. */
    @GameTest(structure = STRUCTURE, maxTicks = 100, skyAccess = true, padding = 8)
    public void chatOrders(GameTestHelper h) {
        BotArena a = BotArena.flat(h, "chat_orders");
        ServerPlayer owner = owner(h, a, 10, 10);
        Companion c = bob(h, a, owner, 22, 22);
        h.runAfterDelay(5, () -> {
            ChatControlAccess.handle(owner, c, "folge mir");
            h.assertTrue(c.queue.peekFirst() instanceof FollowJob || c.current instanceof FollowJob, "folge mir: no follow job");
            ChatControlAccess.handle(owner, c, "stop");
            h.assertTrue(c.current == null && c.queue.isEmpty(), "stop: jobs left");
            ChatControlAccess.handle(owner, c, "bring mir 3 bretter");
            var j = c.queue.peekFirst();
            h.assertTrue(j instanceof GatherJob && j.describe(Lang.EN).contains("3×"), "bring mir 3 bretter: " + (j == null ? "nothing" : j.describe(Lang.EN)));
            ChatControlAccess.handle(owner, c, "stop");
            ChatControlAccess.handle(owner, c, "kill 2 cows");
            h.assertTrue(c.queue.peekFirst() instanceof HuntJob, "kill 2 cows: no hunt");
            ChatControlAccess.handle(owner, c, "stop");
            done(h, c, owner);
        });
    }

    /** Called from some 30 blocks off: at the owner's side. */
    @GameTest(structure = STRUCTURE, maxTicks = 600, skyAccess = true, padding = 8)
    public void comeToOwner(GameTestHelper h) {
        BotArena a = BotArena.flat(h, "come_to_owner");
        ServerPlayer owner = owner(h, a, 6, 6);
        Companion c = bob(h, a, owner, 32, 32);
        h.runAfterDelay(5, () -> ChatControlAccess.handle(owner, c, "komm her"));
        h.succeedWhen(() -> {
            h.assertTrue(c.bot != null && c.bot.body().distanceTo(owner) < 3.5, "not there yet");
            done(h, c, owner);
        });
    }

    /** The pack into the chest, all but the pickaxe, the sword and a stack of food and of blocks. */
    @GameTest(structure = STRUCTURE, maxTicks = 600, skyAccess = true, padding = 8)
    public void depositKeepsGear(GameTestHelper h) {
        BotArena a = BotArena.flat(h, "deposit_keeps_gear");
        BlockPos chest = a.abs(26, FEET, 20);
        h.getLevel().setBlock(chest, Blocks.CHEST.defaultBlockState(), 3);
        a.spawn(16, FEET, 20, new ItemStack(Items.IRON_PICKAXE), new ItemStack(Items.STONE_SWORD), new ItemStack(Items.RAW_IRON, 12),
                new ItemStack(Items.OAK_LOG, 40), new ItemStack(Items.BREAD, 10), new ItemStack(Items.DIRT, 30));
        a.run(new DepositTask(chest, net.kasax.bobbot.companion.Deliveries.keeper(a.bot())), 580, () -> {
            var box = Containers.at(h.getLevel(), chest);
            int iron = Containers.count(box, st -> st.is(Items.RAW_IRON)), logs = Containers.count(box, st -> st.is(Items.OAK_LOG));
            if (iron < 12 || logs < 40) return false;
            var inv = a.bot().body().getInventory();
            h.assertTrue(inv.countItem(Items.IRON_PICKAXE) == 1 && inv.countItem(Items.STONE_SWORD) == 1, "the tools went too");
            h.assertTrue(inv.countItem(Items.BREAD) == 10 && inv.countItem(Items.DIRT) == 30, "food or blocks went too");
            return true;
        });
    }
}
