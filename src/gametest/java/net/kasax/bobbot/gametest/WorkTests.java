package net.kasax.bobbot.gametest;

import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.kasax.bobbot.companion.Companion;
import net.kasax.bobbot.companion.job.Job;
import net.kasax.bobbot.control.ChatControlAccess;
import net.kasax.bobbot.storage.Wand;
import net.kasax.bobbot.work.FarmJob;
import net.kasax.bobbot.work.GuardJob;
import net.kasax.bobbot.work.LightJob;
import net.kasax.bobbot.work.MineJob;
import net.kasax.bobbot.work.QuarryJob;
import net.kasax.bobbot.work.SmeltJob;
import net.kasax.bobbot.work.WoodJob;
import net.kasax.bobbot.work.CollectJob;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CropBlock;
import net.minecraft.world.level.block.FarmlandBlock;

import static net.kasax.bobbot.gametest.BotArena.FEET;
import static net.kasax.bobbot.gametest.BotArena.GROUND;
import static net.kasax.bobbot.gametest.BotArena.STRUCTURE;

/** Bob's work: felling and replanting, a branch mine, digging out a box, the harvest, and the orders for them in chat. */
public class WorkTests {

    /** Two grown oaks, one tall (a pillar beside it to reach the top): both felled whole, saplings back in. */
    @GameTest(structure = STRUCTURE, maxTicks = 3600, skyAccess = true, padding = 8)
    public void fellsAndReplants(GameTestHelper h) {
        BotArena a = BotArena.flat(h, "fells_and_replants");
        ProtectionTests.tree(a, 14, 20, 5);
        ProtectionTests.tree(a, 26, 20, 9);
        ServerPlayer owner = CompanionTests.owner(h, a, 4, 4);
        Companion c = CompanionTests.bob(h, a, owner, 20, 14);
        c.settings.followWhenIdle = false;
        var inv = c.bot.body().getInventory();
        inv.add(new ItemStack(Items.IRON_AXE));
        inv.add(new ItemStack(Items.DIRT, 32));
        inv.add(new ItemStack(Items.OAK_SAPLING, 2));
        c.now(new WoodJob(14, null));
        h.succeedWhen(() -> {
            for (int y = 0; y < 9; y++) {
                h.assertTrue(!h.getLevel().getBlockState(a.abs(26, FEET + y, 20)).is(BlockTags.LOGS), "a log left in the tall tree at +" + y);
                if (y < 5) h.assertTrue(!h.getLevel().getBlockState(a.abs(14, FEET + y, 20)).is(BlockTags.LOGS), "a log left in the small tree at +" + y);
            }
            h.assertTrue(h.getLevel().getBlockState(a.abs(14, FEET, 20)).is(Blocks.OAK_SAPLING), "no sapling where the small tree stood");
            h.assertTrue(h.getLevel().getBlockState(a.abs(26, FEET, 20)).is(Blocks.OAK_SAPLING), "no sapling where the tall tree stood");
            CompanionTests.done(h, c, owner);
        });
    }

    /** A branch mine six below: the stairs, the main tunnel, the branches; the iron in a wall taken, torches down. */
    @GameTest(structure = STRUCTURE, maxTicks = 4800, skyAccess = true, padding = 8)
    public void branchMine(GameTestHelper h) {
        BotArena a = BotArena.flat(h, "branch_mine");
        ServerPlayer owner = CompanionTests.owner(h, a, 4, 4);
        Companion c = CompanionTests.bob(h, a, owner, 20, 30);
        c.settings.followWhenIdle = false;
        var inv = c.bot.body().getInventory();
        inv.add(new ItemStack(Items.IRON_PICKAXE));
        inv.add(new ItemStack(Items.TORCH, 16));
        // Iron in the wall of the main tunnel (it runs north from the foot of the stairs).
        int y = FEET - 6;
        // (Stairs north from z 30: six steps, the foot at z 24; the main tunnel z 23..16.)
        BlockPos ore = a.abs(21, y, 21);
        h.getLevel().setBlock(ore, Blocks.IRON_ORE.defaultBlockState(), 3);
        h.getLevel().setBlock(ore.east(), Blocks.IRON_ORE.defaultBlockState(), 3);
        c.now(new MineJob(a.abs(0, y, 0).getY(), 8, 5, Direction.NORTH));
        h.succeedWhen(() -> {
            h.assertTrue(c.current == null, "still mining");
            h.assertTrue(h.getLevel().getBlockState(a.abs(20, y, 20)).isAir() && h.getLevel().getBlockState(a.abs(20, y + 1, 20)).isAir(), "no main tunnel at z 20");
            h.assertTrue(h.getLevel().getBlockState(a.abs(17, y, 20)).isAir() || h.getLevel().getBlockState(a.abs(23, y, 20)).isAir(), "no branch at z 20");
            h.assertTrue(!h.getLevel().getBlockState(ore).is(Blocks.IRON_ORE) && !h.getLevel().getBlockState(ore.east()).is(Blocks.IRON_ORE), "the iron is still in the wall");
            h.assertTrue(c.bot.body().getInventory().countItem(Items.RAW_IRON) >= 2, "no raw iron in the pack");
            h.assertTrue(c.bot.body().getInventory().countItem(Items.TORCH) < 16, "no torch put down");
            CompanionTests.done(h, c, owner);
        });
    }

    /** A box of dirt and stone marked with the wand and "dig that out": empty after. */
    @GameTest(structure = STRUCTURE, maxTicks = 3600, skyAccess = true, padding = 8)
    public void digsOutTheBox(GameTestHelper h) {
        BotArena a = BotArena.flat(h, "digs_out_the_box");
        ServerPlayer owner = CompanionTests.owner(h, a, 4, 4);
        Companion c = CompanionTests.bob(h, a, owner, 12, 12);
        c.settings.followWhenIdle = false;
        var inv = c.bot.body().getInventory();
        inv.add(new ItemStack(Items.IRON_PICKAXE));
        inv.add(new ItemStack(Items.IRON_SHOVEL));
        Wand.corner(owner, 0, a.abs(18, GROUND, 18));
        Wand.corner(owner, 1, a.abs(21, GROUND - 2, 21));
        h.runAfterDelay(5, () -> ChatControlAccess.handle(owner, c, "heb das aus"));
        h.succeedWhen(() -> {
            for (int x = 18; x <= 21; x++)
                for (int z = 18; z <= 21; z++)
                    for (int y = GROUND - 2; y <= GROUND; y++)
                        h.assertTrue(h.getLevel().getBlockState(a.abs(x, y, z)).isAir(), "not dug at " + x + "," + y + "," + z);
            h.assertTrue(c.current == null, "still digging");
            CompanionTests.done(h, c, owner);
        });
    }

    /** Ripe wheat on farmland: harvested, the seeds back in, the wheat in his pack. */
    @GameTest(structure = STRUCTURE, maxTicks = 2400, skyAccess = true, padding = 8)
    public void harvestsAndReplants(GameTestHelper h) {
        BotArena a = BotArena.flat(h, "harvests_and_replants");
        var ripe = ((CropBlock) Blocks.WHEAT).getStateForAge(7);
        for (int x = 18; x <= 20; x++) {
            for (int z = 18; z <= 20; z++) {
                h.getLevel().setBlock(a.abs(x, GROUND, z), Blocks.FARMLAND.defaultBlockState().setValue(FarmlandBlock.MOISTURE, 7), 3);
                h.getLevel().setBlock(a.abs(x, FEET, z), ripe, 3);
            }
        }
        ServerPlayer owner = CompanionTests.owner(h, a, 4, 4);
        Companion c = CompanionTests.bob(h, a, owner, 14, 14);
        c.settings.followWhenIdle = false;
        h.runAfterDelay(5, () -> ChatControlAccess.handle(owner, c, "ernte die felder"));
        h.succeedWhen(() -> {
            h.assertTrue(c.current == null, "still farming");
            int replanted = 0;
            for (int x = 18; x <= 20; x++)
                for (int z = 18; z <= 20; z++) if (h.getLevel().getBlockState(a.abs(x, FEET, z)).is(Blocks.WHEAT)
                        && !((CropBlock) Blocks.WHEAT).isMaxAge(h.getLevel().getBlockState(a.abs(x, FEET, z)))) replanted++;
            h.assertTrue(replanted == 9, "replanted " + replanted + " of 9");
            h.assertTrue(c.bot.body().getInventory().countItem(Items.WHEAT) >= 9, "wheat in the pack: " + c.bot.body().getInventory().countItem(Items.WHEAT));
            CompanionTests.done(h, c, owner);
        });
    }

    /** The orders for work, said in chat, German and English. */
    @GameTest(structure = STRUCTURE, maxTicks = 100, skyAccess = true, padding = 8)
    public void workOrdersInChat(GameTestHelper h) {
        BotArena a = BotArena.flat(h, "work_orders_in_chat");
        ServerPlayer owner = CompanionTests.owner(h, a, 6, 6);
        Companion c = CompanionTests.bob(h, a, owner, 10, 10);
        c.settings.followWhenIdle = false;
        h.runAfterDelay(3, () -> {
            expect(h, c, owner, "fäll 3 bäume", WoodJob.class);
            expect(h, c, owner, "farm wood", WoodJob.class);
            expect(h, c, owner, "geh strip minen auf -50", MineJob.class);
            expect(h, c, owner, "mine for diamonds", MineJob.class);
            expect(h, c, owner, "ernte die felder", FarmJob.class);
            expect(h, c, owner, "schmelz das eisen", SmeltJob.class);
            expect(h, c, owner, "bewache das hier", GuardJob.class);
            expect(h, c, owner, "mach hier licht", LightJob.class);
            expect(h, c, owner, "sammel die items auf", CollectJob.class);
            Wand.corner(owner, 0, a.abs(18, GROUND, 18));
            Wand.corner(owner, 1, a.abs(19, GROUND, 19));
            expect(h, c, owner, "dig that out", QuarryJob.class);
            CompanionTests.done(h, c, owner);
        });
    }

    private static void expect(GameTestHelper h, Companion c, ServerPlayer owner, String line, Class<? extends Job> kind) {
        ChatControlAccess.handle(owner, c, line);
        Job j = c.queue.peekFirst() != null ? c.queue.peekFirst() : c.current;
        h.assertTrue(kind.isInstance(j), "\"" + line + "\": " + (j == null ? "nothing" : j.getClass().getSimpleName()));
        c.stopAll();
    }

    /**
     * Blocks for the fall tile's pillar (soil or stone, whichever) on flat grass with no wall about:
     * the grass of the field round about, a block deep - no staircase down for it (that took
     * minutes in self-play, and by the water it could not be dug at all).
     */
    @GameTest(structure = STRUCTURE, maxTicks = 1400, skyAccess = true, padding = 8)
    public void pillarBlocksFromTheField(GameTestHelper h) {
        BotArena a = BotArena.flat(h, "pillar_blocks_from_the_field");
        a.spawn(20, FEET, 20);
        var body = a.bot().body();
        var blocks = java.util.Set.of(Items.DIRT, Items.COBBLESTONE, Items.COBBLED_DEEPSLATE, Items.NETHERRACK);
        boolean[] deep = {false};
        a.run(new net.kasax.bobbot.bot.task.ObtainTask(blocks, 23), 1400, () -> {
            if (body.getY() < a.abs(0, FEET - 1, 0).getY() - 0.5) deep[0] = true;
            h.assertTrue(!deep[0], "pillar_blocks_from_the_field: dug deeper than the field's one block");
            return net.kasax.bobbot.bot.plan.ObtainPlanner.countAny(body, blocks) >= 23 && a.bot().current() == null;
        });
    }

    /**
     * "5 dye colours", red and yellow already collected, orange next: another red and yellow made
     * for it, and the orange - the first red and yellow kept. (Keeping every red dye there ever
     * was, it made red dye thirty-six times in a self-play game, and never the orange.)
     */
    @GameTest(structure = STRUCTURE, maxTicks = 400, skyAccess = true, padding = 8)
    public void orangeKeepsRedAndYellow(GameTestHelper h) {
        BotArena a = BotArena.flat(h, "orange_keeps_red_and_yellow");
        a.spawn(20, FEET, 20, new ItemStack(Items.DYE.pick(net.minecraft.world.item.DyeColor.RED)), new ItemStack(Items.DYE.pick(net.minecraft.world.item.DyeColor.YELLOW)), new ItemStack(Items.POPPY, 2),
                new ItemStack(Items.DANDELION, 2));
        var body = a.bot().body();
        var kinds = java.util.Set.of(Items.DYE.pick(net.minecraft.world.item.DyeColor.RED), Items.DYE.pick(net.minecraft.world.item.DyeColor.YELLOW), Items.DYE.pick(net.minecraft.world.item.DyeColor.ORANGE));
        a.run(new net.kasax.bobbot.bot.task.ObtainTask(java.util.Set.of(Items.DYE.pick(net.minecraft.world.item.DyeColor.ORANGE)), 1, new net.kasax.bobbot.bot.plan.ObtainPlanner()).keeping(kinds), 400,
                () -> net.kasax.bobbot.bot.plan.ObtainPlanner.countAny(body, java.util.Set.of(Items.DYE.pick(net.minecraft.world.item.DyeColor.ORANGE))) >= 1
                        && net.kasax.bobbot.bot.plan.ObtainPlanner.countAny(body, java.util.Set.of(Items.DYE.pick(net.minecraft.world.item.DyeColor.RED))) >= 1
                        && net.kasax.bobbot.bot.plan.ObtainPlanner.countAny(body, java.util.Set.of(Items.DYE.pick(net.minecraft.world.item.DyeColor.YELLOW))) >= 1 && a.bot().current() == null);
    }

    /**
     * Three flint from a gravel bank six high: the gravel taken from the top of its columns,
     * nothing coming down on the head (a self-play game dug at the foot of a gravel bank and
     * suffocated in what came down).
     */
    @GameTest(structure = STRUCTURE, maxTicks = 3600, skyAccess = true, padding = 8)
    public void flintFromAGravelBank(GameTestHelper h) {
        BotArena a = BotArena.flat(h, "flint_from_a_gravel_bank");
        a.fill(24, FEET, 17, 28, FEET + 5, 23, Blocks.GRAVEL);
        a.spawn(20, FEET, 20, new ItemStack(Items.STONE_SHOVEL));
        var body = a.bot().body();
        boolean[] buried = {false};
        a.run(new net.kasax.bobbot.bot.task.ObtainTask(java.util.Set.of(Items.FLINT), 3), 3600, () -> {
            buried[0] |= body.isInWall();
            h.assertTrue(!buried[0], "flint_from_a_gravel_bank: buried in the gravel");
            return net.kasax.bobbot.bot.plan.ObtainPlanner.countAny(body, java.util.Set.of(Items.FLINT)) >= 3 && a.bot().current() == null;
        });
    }
}
