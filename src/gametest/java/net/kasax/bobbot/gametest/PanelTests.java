package net.kasax.bobbot.gametest;

import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.kasax.bobbot.companion.Companion;
import net.kasax.bobbot.companion.job.FollowJob;
import net.kasax.bobbot.control.ControlPanel;
import net.kasax.bobbot.work.WoodJob;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import static net.kasax.bobbot.gametest.BotArena.STRUCTURE;

/** The control panel's icons, the line over his head, and his pack kept over a death. */
public class PanelTests {

    /** Clicks in the panel: follow, a setting to its next value, stop. */
    @GameTest(structure = STRUCTURE, maxTicks = 100, skyAccess = true, padding = 8)
    public void panelClicks(GameTestHelper h) {
        BotArena a = BotArena.flat(h, "panel_clicks");
        ServerPlayer owner = CompanionTests.owner(h, a, 6, 6);
        Companion c = CompanionTests.bob(h, a, owner, 10, 10);
        h.runAfterDelay(3, () -> {
            ControlPanel.open(owner, c);
            h.assertTrue(owner.containerMenu instanceof ControlPanel, "no panel open");
            var menu = owner.containerMenu;
            h.assertTrue(menu.getSlot(10).getItem().is(Items.LEAD), "no follow icon");
            menu.clicked(10, 0, ContainerInput.PICKUP, owner);
            h.assertTrue(c.queue.peekFirst() instanceof FollowJob || c.current instanceof FollowJob, "the click did not make him follow");
            boolean keep = c.settings.keepInventory;
            menu.clicked(40, 0, ContainerInput.PICKUP, owner);
            h.assertTrue(c.settings.keepInventory != keep, "keepInventory did not change");
            menu.clicked(40, 0, ContainerInput.PICKUP, owner);
            h.assertTrue(c.settings.keepInventory == keep, "keepInventory did not change back");
            // (The icons themselves never move into anyone's pack.)
            menu.clicked(10, 0, ContainerInput.QUICK_MOVE, owner);
            h.assertTrue(owner.getInventory().countItem(Items.LEAD) == 0, "an icon was taken");
            menu.clicked(16, 0, ContainerInput.PICKUP, owner);
            h.assertTrue(c.current == null && c.queue.isEmpty(), "stop did not stop");
            owner.closeContainer();
            CompanionTests.done(h, c, owner);
        });
    }

    /** Working: a line over his head saying what. */
    @GameTest(structure = STRUCTURE, maxTicks = 200, skyAccess = true, padding = 8)
    public void lineOverHisHead(GameTestHelper h) {
        BotArena a = BotArena.flat(h, "line_over_his_head");
        ServerPlayer owner = CompanionTests.owner(h, a, 6, 6);
        Companion c = CompanionTests.bob(h, a, owner, 10, 10);
        c.settings.followWhenIdle = false;
        c.now(new WoodJob(0, null));
        h.succeedWhen(() -> {
            var tags = h.getLevel().getEntitiesOfClass(ArmorStand.class, c.bot.body().getBoundingBox().inflate(2, 3, 2), e -> e.entityTags().contains("bobbot_status"));
            h.assertTrue(!tags.isEmpty() && tags.get(0).getCustomName() != null && tags.get(0).getCustomName().getString().contains("wood"),
                    "no line over his head" + (tags.isEmpty() ? "" : ": " + tags.get(0).getCustomName()));
            CompanionTests.done(h, c, owner);
        });
    }

    /** Died with a pickaxe and bread: back on his feet with both (keepInventory). */
    @GameTest(structure = STRUCTURE, maxTicks = 600, skyAccess = true, padding = 8)
    public void keepsHisPack(GameTestHelper h) {
        BotArena a = BotArena.flat(h, "keeps_his_pack");
        // (Dead at night, he would wait for the morning.)
        h.getLevel().getServer().getCommands().performPrefixedCommand(h.getLevel().getServer().createCommandSourceStack().withSuppressedOutput(), "time set 1000");
        ServerPlayer owner = CompanionTests.owner(h, a, 6, 6);
        Companion c = CompanionTests.bob(h, a, owner, 10, 10);
        c.settings.followWhenIdle = false;
        c.bot.body().getInventory().add(new ItemStack(Items.IRON_PICKAXE));
        c.bot.body().getInventory().add(new ItemStack(Items.BREAD, 7));
        c.bot.body().experienceLevel = 5;
        var first = c.bot.body();
        // (A player who has just joined cannot be hurt until his client has loaded: 60 ticks.)
        h.runAfterDelay(80, () -> {
            first.hurtServer(h.getLevel(), h.getLevel().damageSources().genericKill(), Float.MAX_VALUE);
            h.assertTrue(first.isDeadOrDying(), "he did not die");
        });
        h.succeedWhen(() -> {
            var body = c.bot.body();
            h.assertTrue(body != first && body.isAlive(), "not back yet");
            h.assertTrue(body.getInventory().countItem(Items.IRON_PICKAXE) == 1 && body.getInventory().countItem(Items.BREAD) == 7, "his things are gone");
            h.assertTrue(body.experienceLevel == 5, "his experience is gone");
            h.assertTrue(h.getLevel().getEntitiesOfClass(net.minecraft.world.entity.item.ItemEntity.class, first.getBoundingBox().inflate(4)).isEmpty(), "things were dropped");
            h.assertTrue(h.getLevel().getEntitiesOfClass(net.minecraft.world.entity.ExperienceOrb.class, first.getBoundingBox().inflate(4)).isEmpty(), "experience was dropped");
            CompanionTests.done(h, c, owner);
        });
    }
}
