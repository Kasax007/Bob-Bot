package net.kasax.bobbot.companion.job;

import net.minecraft.nbt.CompoundTag;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.Supplier;

/** Every kind of job by its saved name, to bring a saved queue back. */
public final class Jobs {
    private Jobs() {
    }

    private static final Map<String, Supplier<Job>> KINDS = new LinkedHashMap<>();

    public static void register(String kind, Supplier<Job> make) {
        KINDS.put(kind, make);
    }

    static {
        register("follow", FollowJob::new);
        register("come", ComeJob::new);
        register("stay", StayJob::new);
        register("home", HomeJob::new);
        register("gather", GatherJob::new);
        register("hunt", HuntJob::new);
        register("fish", FishJob::new);
    }

    /** A job from its saved form, or null if the kind is unknown (a newer version's). */
    public static Job load(CompoundTag t) {
        Supplier<Job> make = KINDS.get(t.getStringOr("kind", ""));
        if (make == null) return null;
        Job j = make.get();
        j.load(t);
        return j;
    }
}
