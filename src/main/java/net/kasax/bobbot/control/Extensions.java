package net.kasax.bobbot.control;

import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import net.minecraft.commands.CommandSourceStack;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/** More {@code /bob} subcommands, added by the parts of the mod that bring them (work, storage, building). */
public final class Extensions {
    private Extensions() {
    }

    private static final List<Consumer<LiteralArgumentBuilder<CommandSourceStack>>> ADD = new ArrayList<>();

    public static void register(Consumer<LiteralArgumentBuilder<CommandSourceStack>> add) {
        ADD.add(add);
    }

    static void add(LiteralArgumentBuilder<CommandSourceStack> root) {
        for (var a : ADD) a.accept(root);
    }
}
