package net.kasax.bobbot.companion;

import net.minecraft.nbt.CompoundTag;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/** How Bob goes about things: every switch the owner can set with {@code /bob set <key> <value>} or in Bob's screen. */
public final class Settings {
    public enum Combat { PASSIVE, DEFENSIVE, AGGRESSIVE }

    public enum Night { CONTINUE, SHELTER, SLEEP }

    public enum Talk { QUIET, NORMAL, CHATTY }

    /** Fights back when attacked and defends the owner; passive: runs; aggressive: also clears monsters near the owner. */
    public Combat combat = Combat.DEFENSIVE;
    /** At night: carry on, take shelter (dug in) or sleep when the owner sleeps. */
    public Night night = Night.CONTINUE;
    /** How much Bob reports: only problems, start and end of every job, or progress now and then too. */
    public Talk talk = Talk.NORMAL;
    /** Blocks to keep from the owner when following. */
    public int followDistance = 3;
    /** Teleports to the owner when left far behind or in another dimension (as a tamed wolf does). */
    public boolean teleport = true;
    /** Keeps everything he carries when he dies (only Bob, not the world's players). */
    public boolean keepInventory = true;
    /** Works through the queue while the owner is offline; otherwise leaves with the owner. */
    public boolean workWhileAway = true;
    /** Follows the owner when there is nothing to do; otherwise waits where he is. */
    public boolean followWhenIdle = true;
    /** Saplings back into the ground where he felled a tree. */
    public boolean replant = true;
    /** Torches from his pack into dark tunnels and spots he works in. */
    public boolean torches = true;
    /** Generated structures (villages, temples, outposts...) are not his to dig up. */
    public boolean protectStructures = true;
    /** Back to the drop-off chest when the pack is full, then on with the job. */
    public boolean deliverWhenFull = true;
    /** Takes no tool below this many uses left (keeps the owner's good pickaxe from breaking). */
    public int toolReserve = 10;
    /** What he is doing, in a line over his head. */
    public boolean nameTag = true;
    /** The job and how far along it is, in a bar at the top of the owner's screen. */
    public boolean bossBar = true;

    private static final List<String> KEYS = List.of("combat", "night", "talk", "followDistance", "teleport", "keepInventory",
            "workWhileAway", "followWhenIdle", "replant", "torches", "protectStructures", "deliverWhenFull", "toolReserve", "nameTag", "bossBar");

    public static List<String> keys() {
        return KEYS;
    }

    /** The values a key takes (for suggestions). */
    public static List<String> values(String key) {
        return switch (key) {
            case "combat" -> List.of("passive", "defensive", "aggressive");
            case "night" -> List.of("continue", "shelter", "sleep");
            case "talk" -> List.of("quiet", "normal", "chatty");
            case "followDistance" -> List.of("2", "3", "5", "8");
            case "toolReserve" -> List.of("0", "5", "10", "25");
            default -> List.of("true", "false");
        };
    }

    /** Sets a key from text; returns an error message, or null when it took. */
    public String set(String key, String value) {
        String v = value.toLowerCase(Locale.ROOT);
        try {
            switch (key) {
                case "combat" -> combat = Combat.valueOf(v.toUpperCase(Locale.ROOT));
                case "night" -> night = Night.valueOf(v.toUpperCase(Locale.ROOT));
                case "talk" -> talk = Talk.valueOf(v.toUpperCase(Locale.ROOT));
                case "followDistance" -> followDistance = Math.max(1, Math.min(16, Integer.parseInt(v)));
                case "toolReserve" -> toolReserve = Math.max(0, Math.min(200, Integer.parseInt(v)));
                case "teleport" -> teleport = bool(v);
                case "keepInventory" -> keepInventory = bool(v);
                case "workWhileAway" -> workWhileAway = bool(v);
                case "followWhenIdle" -> followWhenIdle = bool(v);
                case "replant" -> replant = bool(v);
                case "torches" -> torches = bool(v);
                case "protectStructures" -> protectStructures = bool(v);
                case "deliverWhenFull" -> deliverWhenFull = bool(v);
                case "nameTag" -> nameTag = bool(v);
                case "bossBar" -> bossBar = bool(v);
                default -> {
                    return "unknown setting " + key;
                }
            }
            return null;
        } catch (IllegalArgumentException e) {
            return "not a value for " + key + ": " + value;
        }
    }

    private static boolean bool(String v) {
        if (v.equals("true") || v.equals("on") || v.equals("yes") || v.equals("ja") || v.equals("an")) return true;
        if (v.equals("false") || v.equals("off") || v.equals("no") || v.equals("nein") || v.equals("aus")) return false;
        throw new IllegalArgumentException(v);
    }

    /** Every key with its value, in order (for the list and the screen). */
    public Map<String, String> all() {
        Map<String, String> m = new LinkedHashMap<>();
        m.put("combat", combat.name().toLowerCase(Locale.ROOT));
        m.put("night", night.name().toLowerCase(Locale.ROOT));
        m.put("talk", talk.name().toLowerCase(Locale.ROOT));
        m.put("followDistance", String.valueOf(followDistance));
        m.put("teleport", String.valueOf(teleport));
        m.put("keepInventory", String.valueOf(keepInventory));
        m.put("workWhileAway", String.valueOf(workWhileAway));
        m.put("followWhenIdle", String.valueOf(followWhenIdle));
        m.put("replant", String.valueOf(replant));
        m.put("torches", String.valueOf(torches));
        m.put("protectStructures", String.valueOf(protectStructures));
        m.put("deliverWhenFull", String.valueOf(deliverWhenFull));
        m.put("toolReserve", String.valueOf(toolReserve));
        m.put("nameTag", String.valueOf(nameTag));
        m.put("bossBar", String.valueOf(bossBar));
        return m;
    }

    public CompoundTag save() {
        CompoundTag t = new CompoundTag();
        all().forEach(t::putString);
        return t;
    }

    public void load(CompoundTag t) {
        for (String k : KEYS) t.getString(k).ifPresent(v -> set(k, v));
    }
}
