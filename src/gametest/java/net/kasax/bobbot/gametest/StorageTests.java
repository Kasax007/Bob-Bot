package net.kasax.bobbot.gametest;

import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.kasax.bobbot.companion.Area;
import net.kasax.bobbot.companion.Companion;
import net.kasax.bobbot.companion.PackMenu;
import net.kasax.bobbot.companion.RoleChest;
import net.kasax.bobbot.companion.job.SortJob;
import net.kasax.bobbot.control.ChatControlAccess;
import net.kasax.bobbot.storage.Wand;
import net.kasax.bobbot.world.Containers;
import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;

import static net.kasax.bobbot.gametest.BotArena.FEET;
import static net.kasax.bobbot.gametest.BotArena.STRUCTURE;

/** Bob and the owner's chests: sorting the way the owner sorted, fetching, his pack as a chest screen. */
public class StorageTests {

    static BlockPos chest(GameTestHelper h, BotArena a, int x, int z, ItemStack... content) {
        BlockPos p = a.abs(x, FEET, z);
        h.getLevel().setBlock(p, Blocks.CHEST.defaultBlockState(), 3);
        var box = Containers.at(h.getLevel(), p);
        for (ItemStack st : content) Containers.insert(box, st);
        return p;
    }

    static int count(GameTestHelper h, BlockPos chest, Item item) {
        return Containers.count(Containers.at(h.getLevel(), chest), st -> st.is(item));
    }

    /**
     * A storage room of chests sorted by kind (logs, ingots, food) and an empty one; in the
     * drop-off chest birch logs, gold ingots, cooked beef and string. Sorted: each to its kind,
     * the string (a kind of its own) into the empty chest, the drop-off empty, his pickaxe kept.
     */
    @GameTest(structure = STRUCTURE, maxTicks = 2400, skyAccess = true, padding = 8)
    public void sortsLikeThePlayer(GameTestHelper h) {
        BotArena a = BotArena.flat(h, "sorts_like_the_player");
        BlockPos logs = chest(h, a, 20, 30, new ItemStack(Items.OAK_LOG, 20));
        BlockPos ingots = chest(h, a, 23, 30, new ItemStack(Items.IRON_INGOT, 5));
        BlockPos food = chest(h, a, 26, 30, new ItemStack(Items.BREAD, 7));
        BlockPos empty = chest(h, a, 29, 30);
        BlockPos dropoff = chest(h, a, 12, 12, new ItemStack(Items.BIRCH_LOG, 30), new ItemStack(Items.GOLD_INGOT, 9),
                new ItemStack(Items.COOKED_BEEF, 6), new ItemStack(Items.STRING, 11));
        ServerPlayer owner = CompanionTests.owner(h, a, 4, 4);
        Companion c = CompanionTests.bob(h, a, owner, 14, 14);
        c.settings.followWhenIdle = false;
        c.bot.body().getInventory().add(new ItemStack(Items.IRON_PICKAXE));
        c.chests.add(new RoleChest(GlobalPos.of(h.getLevel().dimension(), dropoff), RoleChest.Role.DROPOFF));
        // The storage room, marked with the wand by its corners.
        Wand.corner(owner, 0, a.abs(18, FEET - 1, 28));
        Wand.corner(owner, 1, a.abs(31, FEET + 2, 32));
        Wand.addArea(owner, c, Area.Kind.STORAGE);
        h.assertTrue(c.areas.values().stream().anyMatch(ar -> ar.kind() == Area.Kind.STORAGE), "no storage room made");
        h.runAfterDelay(5, () -> ChatControlAccess.handle(owner, c, "räum die Ablage ein"));
        h.succeedWhen(() -> {
            h.assertTrue(count(h, logs, Items.BIRCH_LOG) == 30, "birch logs not with the logs: " + count(h, logs, Items.BIRCH_LOG));
            h.assertTrue(count(h, ingots, Items.GOLD_INGOT) == 9, "gold not with the ingots: " + count(h, ingots, Items.GOLD_INGOT));
            h.assertTrue(count(h, food, Items.COOKED_BEEF) == 6, "beef not with the food: " + count(h, food, Items.COOKED_BEEF));
            h.assertTrue(count(h, empty, Items.STRING) == 11, "string not in a chest of its own: " + count(h, empty, Items.STRING));
            h.assertTrue(Containers.at(h.getLevel(), dropoff).isEmpty(), "the drop-off is not empty");
            h.assertTrue(c.current == null || !(c.current instanceof SortJob), "still sorting");
            h.assertTrue(c.bot.body().getInventory().countItem(Items.IRON_PICKAXE) == 1, "his pickaxe went into the storage");
            CompanionTests.done(h, c, owner);
        });
    }

    /** "Bring mir 8 Eisen aus dem Lager": out of the chest, into the owner's hands. */
    @GameTest(structure = STRUCTURE, maxTicks = 1600, skyAccess = true, padding = 8)
    public void fetchFromStorage(GameTestHelper h) {
        BotArena a = BotArena.flat(h, "fetch_from_storage");
        BlockPos store = chest(h, a, 30, 30, new ItemStack(Items.IRON_INGOT, 20), new ItemStack(Items.COAL, 10));
        ServerPlayer owner = CompanionTests.owner(h, a, 6, 6);
        Companion c = CompanionTests.bob(h, a, owner, 10, 10);
        c.settings.followWhenIdle = false;
        c.chests.add(new RoleChest(GlobalPos.of(h.getLevel().dimension(), store), RoleChest.Role.STORAGE));
        h.runAfterDelay(5, () -> ChatControlAccess.handle(owner, c, "bring mir 8 eisen aus dem lager"));
        h.succeedWhen(() -> {
            h.assertTrue(owner.getInventory().countItem(Items.IRON_INGOT) == 8, "the owner has " + owner.getInventory().countItem(Items.IRON_INGOT) + " iron");
            h.assertTrue(count(h, store, Items.IRON_INGOT) == 12, "the chest has " + count(h, store, Items.IRON_INGOT) + " iron left");
            CompanionTests.done(h, c, owner);
        });
    }

    /** His pack as a chest screen: the armour row takes only armour of its slot, and he wears what goes in. */
    @GameTest(structure = STRUCTURE, maxTicks = 100, skyAccess = true, padding = 8)
    public void packScreen(GameTestHelper h) {
        BotArena a = BotArena.flat(h, "pack_screen");
        ServerPlayer owner = CompanionTests.owner(h, a, 6, 6);
        Companion c = CompanionTests.bob(h, a, owner, 8, 8);
        c.bot.body().getInventory().setItem(9, new ItemStack(Items.BREAD, 5));
        h.runAfterDelay(3, () -> {
            PackMenu.open(owner, c);
            h.assertTrue(owner.containerMenu instanceof PackMenu, "no pack screen open");
            var menu = owner.containerMenu;
            h.assertTrue(menu.getSlot(0).getItem().is(Items.BREAD), "the first row is not his pack");
            h.assertTrue(!menu.getSlot(36).mayPlace(new ItemStack(Items.DIRT)), "dirt fits the helmet slot");
            h.assertTrue(!menu.getSlot(36).mayPlace(new ItemStack(Items.IRON_BOOTS)), "boots fit the helmet slot");
            h.assertTrue(menu.getSlot(36).mayPlace(new ItemStack(Items.IRON_HELMET)), "a helmet does not fit the helmet slot");
            h.assertTrue(!menu.getSlot(44).mayPickup(owner), "the filler can be taken");
            menu.getSlot(36).set(new ItemStack(Items.IRON_HELMET));
            h.assertTrue(c.bot.body().getItemBySlot(EquipmentSlot.HEAD).is(Items.IRON_HELMET), "he does not wear the helmet");
            owner.closeContainer();
            CompanionTests.done(h, c, owner);
        });
    }
}
