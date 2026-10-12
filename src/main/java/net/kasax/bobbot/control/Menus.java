package net.kasax.bobbot.control;

import net.kasax.bobbot.companion.Companion;
import net.kasax.bobbot.companion.Lang;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.level.ServerPlayer;

/**
 * Bob's menu in chat: buttons to click (they run the {@code /bob} command behind them), so every
 * order is a click away without typing - in any client, modded or not.
 */
public final class Menus {
    private Menus() {
    }

    /** A button that runs a command. */
    public static MutableComponent button(String label, String command, String hover) {
        return Component.literal("[" + label + "]").withStyle(s -> s.withColor(ChatFormatting.AQUA)
                .withClickEvent(new ClickEvent.RunCommand(command))
                .withHoverEvent(new HoverEvent.ShowText(Component.literal(hover + "\n" + command).withStyle(ChatFormatting.GRAY))));
    }

    /** A button that puts a command into the chat line to finish typing (a count, an item). */
    public static MutableComponent suggest(String label, String command, String hover) {
        return Component.literal("[" + label + "]").withStyle(s -> s.withColor(ChatFormatting.YELLOW)
                .withClickEvent(new ClickEvent.SuggestCommand(command))
                .withHoverEvent(new HoverEvent.ShowText(Component.literal(hover + "\n" + command).withStyle(ChatFormatting.GRAY))));
    }

    private static MutableComponent row(Lang l, String en, String de, MutableComponent... buttons) {
        MutableComponent line = Component.literal(l.t(en, de) + ": ").withStyle(ChatFormatting.GRAY);
        for (MutableComponent b : buttons) line.append(b).append(" ");
        return line;
    }

    /** The whole menu: orders, work, chests, settings. */
    public static void show(ServerPlayer p, Companion c) {
        Lang l = Lang.of(p);
        p.sendSystemMessage(Component.literal("—— " + c.name + " ——").withStyle(ChatFormatting.GOLD));
        p.sendSystemMessage(row(l, "Orders", "Befehle",
                button(l.t("Come", "Komm"), "/bob come", l.t("Bob comes to you", "Bob kommt zu dir")),
                button(l.t("Follow", "Folgen"), "/bob follow", l.t("Bob follows you", "Bob folgt dir")),
                button(l.t("Guard", "Beschützen"), "/bob guard", l.t("Bob follows you and fights what attacks you", "Bob folgt dir und bekämpft, was dich angreift")),
                button(l.t("Stay", "Bleib"), "/bob stay", l.t("Bob waits here", "Bob wartet hier")),
                button("Stop", "/bob stop", l.t("Stop everything", "Alles abbrechen")),
                button("Status", "/bob status", l.t("What is Bob doing?", "Was macht Bob?"))));
        p.sendSystemMessage(row(l, "Work", "Arbeit",
                suggest(l.t("Wood", "Holz"), "/bob wood 64", l.t("Fell grown trees and replant (never your log houses); no count: on and on", "Gewachsene Bäume fällen und nachpflanzen (nie deine Holzhäuser); ohne Zahl: immer weiter")),
                suggest(l.t("Strip mine", "Strip-Mine"), "/bob mine -58", l.t("A branch mine at that height (ore, torches, junk thrown away)", "Eine Strip-Mine auf dieser Höhe (Erze, Fackeln, Schutt weg)")),
                button(l.t("Dig out", "Ausheben"), "/bob dig", l.t("Dig out the box marked with the wand", "Den mit dem Zauberstab markierten Bereich ausheben")),
                button(l.t("Harvest", "Ernten"), "/bob farm", l.t("Harvest and replant the fields nearby", "Felder in der Nähe ernten und neu säen")),
                button(l.t("Smelt", "Schmelzen"), "/bob smelt", l.t("Smelt what lies in the drop-off chest", "Einschmelzen, was in der Ablage liegt")),
                button(l.t("Sort", "Sortieren"), "/bob sort", l.t("Sort into your chests the way you sorted them", "In deine Kisten sortieren, so wie du sortiert hast")),
                button(l.t("Drop off", "Abliefern"), "/bob deposit", l.t("Empty his pack into the drop-off chest", "Rucksack in die Ablage-Kiste leeren"))));
        p.sendSystemMessage(row(l, "Also", "Auch",
                button(l.t("Guard here", "Hier bewachen"), "/bob patrol", l.t("Stay here and fight monsters that come near", "Hier bleiben und Monster in der Nähe bekämpfen")),
                button(l.t("Light up", "Licht"), "/bob light", l.t("Torches until no dark spot is left round you", "Fackeln, bis um dich keine dunkle Stelle mehr ist")),
                button(l.t("Pick up", "Aufsammeln"), "/bob collect", l.t("Pick up everything lying about", "Alles aufsammeln, was herumliegt")),
                suggest(l.t("Fish", "Angeln"), "/bob fish 10", l.t("Fish so many catches", "So viele Fänge angeln"))));
        p.sendSystemMessage(row(l, "Get", "Holen",
                suggest(l.t("Bring me…", "Bring mir…"), "/bob get iron_ingot 16", l.t("Anything: mined, crafted, smelted or hunted", "Alles: abgebaut, gecraftet, geschmolzen oder gejagt")),
                suggest(l.t("Hunt…", "Jagen…"), "/bob hunt cow 5", l.t("Hunt so many of a mob", "So viele Tiere eines Typs jagen")),
                suggest(l.t("Build…", "Bauen…"), "/bob build list", l.t("Build a schematic from the input chests", "Ein Schematic aus den Material-Kisten bauen"))));
        p.sendSystemMessage(row(l, "Chests", "Kisten",
                button(l.t("Drop-off", "Ablage"), "/bob chest add dropoff", l.t("The chest you look at becomes his drop-off", "Die Kiste, auf die du schaust, wird seine Ablage")),
                button(l.t("Input", "Material"), "/bob chest add input", l.t("Materials for building", "Material zum Bauen")),
                button(l.t("Storage", "Lager"), "/bob chest add storage", l.t("Part of your sorted storage", "Teil deines sortierten Lagers")),
                button(l.t("Wand", "Zauberstab"), "/bob wand", l.t("Mark chests and areas by clicking", "Kisten und Bereiche per Klick markieren")),
                button(l.t("List", "Liste"), "/bob chest list", l.t("His chests", "Seine Kisten"))));
        p.sendSystemMessage(row(l, "Places", "Orte",
                button(l.t("Protect here", "Hier schützen"), "/bob protect", l.t("Bob never breaks or builds anything around you", "Bob baut um dich herum nie etwas ab oder hin")),
                button(l.t("Areas", "Bereiche"), "/bob area list", l.t("His work areas, storage rooms, protected areas", "Seine Arbeitsbereiche, Lagerräume, geschützten Bereiche")),
                button(l.t("May you?", "Darfst du?"), "/bob protect check", l.t("Would Bob break the block you look at?", "Würde Bob den Block abbauen, auf den du schaust?")),
                button(l.t("Set home", "Zuhause setzen"), "/bob home set", l.t("Your position becomes his home", "Deine Position wird sein Zuhause"))));
        p.sendSystemMessage(row(l, "More", "Mehr",
                button(l.t("Inventory", "Inventar"), "/bob inventory", l.t("Open his inventory: give him tools, armour, food", "Sein Inventar öffnen: Werkzeug, Rüstung, Essen geben")),
                button(l.t("Give me all", "Gib mir alles"), "/bob giveall", l.t("Everything but his kit, to you", "Alles außer seiner Ausrüstung, an dich")),
                button(l.t("Queue", "Warteschlange"), "/bob queue", l.t("What comes next", "Was als Nächstes kommt")),
                button(l.t("Settings", "Einstellungen"), "/bob settings", l.t("How he goes about things", "Wie er vorgeht")),
                button(l.t("Home", "Nach Hause"), "/bob home", l.t("Back to his home", "Zurück nach Hause"))));
        p.sendSystemMessage(Component.literal(l.t("Or just write in chat: \"" + c.name + ", fell 32 trees\" · \"" + c.name + ", bring me 16 iron\"",
                "Oder schreib einfach in den Chat: \"" + c.name + ", fäll 32 Bäume\" · \"" + c.name + ", bring mir 16 Eisen\"")).withStyle(ChatFormatting.DARK_GRAY));
    }
}
