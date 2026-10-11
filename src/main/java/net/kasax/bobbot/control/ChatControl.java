package net.kasax.bobbot.control;

import net.fabricmc.fabric.api.message.v1.ServerMessageEvents;
import net.kasax.bobbot.bot.BotPlayer;
import net.kasax.bobbot.bot.task.EatTask;
import net.kasax.bobbot.companion.Companion;
import net.kasax.bobbot.companion.CompanionManager;
import net.kasax.bobbot.companion.Lang;
import net.kasax.bobbot.companion.job.GatherJob;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.item.Item;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Orders in plain chat, English or German: "Bob, come here", "bob folge mir", "Bob, bring mir 16
 * Eisen", "bob fell 32 trees", "Bob: what are you doing?". A line is for Bob when it starts with the
 * name of one of the player's Bobs (or just "bob"); everything else is ordinary chat.
 */
public final class ChatControl {
    private ChatControl() {
    }

    /** One way of understanding a line: when it matches, what to do. */
    public record Rule(Pattern words, Handler handler) {
    }

    @FunctionalInterface
    public interface Handler {
        void handle(ServerPlayer p, Companion c, String text, int number);
    }

    private static final List<Rule> RULES = new ArrayList<>();

    /** A way of understanding more (added by the parts of the mod for work, storage, building). Earlier rules win. */
    public static void rule(String regex, Handler handler) {
        RULES.add(new Rule(Pattern.compile(regex), handler));
    }

    private static final Pattern NUMBER = Pattern.compile("(-?\\d+)");

    public static void register() {
        ServerMessageEvents.CHAT_MESSAGE.register((message, sender, params) -> {
            if (sender instanceof BotPlayer) return;
            String raw = message.signedContent();
            sender.level().getServer().execute(() -> onChat(sender, raw));
        });
        // (The order of these matters: the first that matches is taken.)
        rule("\\b(stop|stopp|halt|cancel|abbrechen|hör auf|hoer auf|aufhören|aufhoeren|lass das)\\b", (p, c, t, n) -> Orders.stop(p, c));
        rule("\\b(pause|pausier\\w*|warte kurz)\\b", (p, c, t, n) -> Orders.pause(p, c, true));
        rule("\\b(weiter|resume|continue|mach weiter|carry on)\\b", (p, c, t, n) -> Orders.pause(p, c, false));
        rule("\\b(skip|überspring\\w*|ueberspring\\w*|nächste\\w*|naechste\\w*|next)\\b", (p, c, t, n) -> Orders.skip(p, c));
        rule("\\b(help|hilfe|menu|menü|menue|was kannst du|what can you do)\\b|^\\?$", (p, c, t, n) -> Menus.show(p, c));
        rule("\\b(status|was machst du|what are you doing|wie läuft|wie laeuft|how is it going|report|bericht|wo bist du|where are you)\\b",
                (p, c, t, n) -> Orders.status(p, c));
        rule("\\b(queue|warteschlange|liste der aufträge|what's next|was kommt)\\b", (p, c, t, n) -> Orders.queue(p, c));
        rule("\\b(komm mit|folg\\w*|follow|begleite\\w*)\\b", (p, c, t, n) -> Orders.follow(p, c, false));
        rule("\\b(guard|protect|beschütz\\w*|beschuetz\\w*|verteidig\\w*|pass auf mich auf|bodyguard)\\b", (p, c, t, n) -> Orders.follow(p, c, true));
        rule("\\b(come|komm|kommen|zu mir|hierher|hier her|here)\\b", (p, c, t, n) -> Orders.come(p, c));
        rule("\\b(stay|bleib\\w*|wait|warte\\w*)\\b", (p, c, t, n) -> Orders.stay(p, c));
        rule("\\b(dein zuhause ist hier|your home is here|wohn hier|live here)\\b", (p, c, t, n) -> Orders.setHome(p, c));
        rule("\\b(go home|nach hause|heim|home)\\b", (p, c, t, n) -> Orders.home(p, c));
        rule("\\b(fish|fischen|angel\\w*)\\b", (p, c, t, n) -> Orders.fish(p, c, n > 0 ? n : 10));
        rule("\\b(kill|hunt|töte\\w*|toete\\w*|jag\\w*|schlacht\\w*)\\b", ChatControl::hunt);
        rule("\\b(bring\\w*|hol\\w*|get|besorg\\w*|gib mir|give me|ich brauche|i need|beschaff\\w*)\\b", ChatControl::get);
        rule("\\b(eat|iss|essen)\\b", (p, c, t, n) -> {
            if (Orders.present(p, c)) c.bot.interject(new EatTask());
        });
        rule("\\b(danke|thanks|thank you|thx|merci)\\b", (p, c, t, n) -> c.say(p.level().getServer(), "You're welcome!", "Gern geschehen!"));
        rule("^(hi|hallo|hello|hey|moin|servus|na)\\b", (p, c, t, n) -> c.say(p.level().getServer(), "Hi! What can I do for you?", "Hi! Was kann ich für dich tun?"));
    }

    private static void onChat(ServerPlayer p, String raw) {
        String line = raw.trim();
        String lower = line.toLowerCase(Locale.ROOT);
        Companion c = null;
        String rest = null;
        for (Companion cand : CompanionManager.ofOwner(p.getUUID())) {
            String n = cand.name.toLowerCase(Locale.ROOT);
            rest = addressed(lower, n);
            if (rest != null) {
                c = cand;
                break;
            }
        }
        if (c == null) {
            rest = addressed(lower, "bob");
            if (rest == null) return;
            c = Orders.target(p, true);
            if (c == null) return;
        }
        Orders.select(p, c);
        handle(p, c, rest);
    }

    /** The rest of the line if it speaks to {@code name} ("bob, ...", "@bob ...", "hey bob ..."); else null. */
    static String addressed(String lower, String name) {
        String l = lower.replaceFirst("^(hey|hi|ok|okay|yo|ey)\\s+", "");
        if (l.startsWith("@")) l = l.substring(1);
        if (!l.startsWith(name)) return null;
        String rest = l.substring(name.length());
        if (!rest.isEmpty() && Character.isLetterOrDigit(rest.charAt(0))) return null; // ("bobby" is not "bob")
        return rest.replaceFirst("^[\\s,:;!.\\-]+", "").trim();
    }

    static void handle(ServerPlayer p, Companion c, String text) {
        String t = text.replaceAll("[!?.,;]+$", "").trim();
        Lang l = Lang.of(p);
        if (t.isEmpty()) {
            c.say(p.level().getServer(), "Yes?", "Ja?");
            return;
        }
        Matcher num = NUMBER.matcher(t);
        int n = num.find() ? Integer.parseInt(num.group(1)) : 0;
        for (Rule r : RULES) {
            if (r.words().matcher(t).find()) {
                r.handler().handle(p, c, t, n);
                return;
            }
        }
        c.tell(p.level().getServer(), Component.literal(l.t("Sorry, I didn't get that. ", "Das habe ich nicht verstanden. "))
                .append(Menus.button(l.t("What I can do", "Was ich kann"), "/bob help", l.t("All orders", "Alle Befehle"))));
    }

    /** The words of the line that are left once the verbs, fill words and numbers are out ("bring mir 16 eisen" - "eisen"). */
    public static String object(String t, String verbs) {
        String s = t.replaceAll(verbs, " ").replaceAll("-?\\d+", " ")
                .replaceAll("\\b(mir|me|us|uns|bitte|please|pls|some|ein paar|paar|noch|more|mehr|du|you|ich|i|need|brauche|for|für|fuer|the|die|der|das|den|a|an|ein|eine|einen|of|von|stück|stueck|x|mal|gleich|jetzt|now|schnell|quick)\\b", " ")
                .replaceAll("\\s+", " ").trim();
        return s;
    }

    private static void get(ServerPlayer p, Companion c, String t, int n) {
        boolean chest = t.matches(".*\\b(für die kiste|fuer die kiste|in die kiste|for the chest|into the chest|to the chest|in the chest)\\b.*");
        String what = object(t.replaceAll("\\b(für die kiste|fuer die kiste|in die kiste|for the chest|into the chest|to the chest|in the chest)\\b", " "),
                "\\b(bring\\w*|hol\\w*|get|besorg\\w*|gib|give|beschaff\\w*)\\b");
        Set<Item> items = Names.items(what);
        if (items.isEmpty()) {
            c.say(p.level().getServer(), "What should I get? I don't know \"" + what + "\".", "Was soll ich holen? \"" + what + "\" kenne ich nicht.");
            return;
        }
        Orders.get(p, c, items, n > 0 ? n : 1, chest ? GatherJob.Then.CHEST : GatherJob.Then.OWNER);
    }

    private static void hunt(ServerPlayer p, Companion c, String t, int n) {
        String what = object(t, "\\b(kill|hunt|töte\\w*|toete\\w*|jag\\w*|schlacht\\w*)\\b");
        EntityType<?> type = Names.mob(what);
        if (type == null) {
            c.say(p.level().getServer(), "What should I hunt? I don't know \"" + what + "\".", "Was soll ich jagen? \"" + what + "\" kenne ich nicht.");
            return;
        }
        Orders.hunt(p, c, type, n > 0 ? n : 1);
    }
}
