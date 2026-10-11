package net.kasax.bobbot.bot;

import it.unimi.dsi.fastutil.longs.LongCollection;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.state.BlockState;

/**
 * What a bot must leave alone: somebody's house, a chest, an area marked off. The hands ask before
 * they break a block, the path search before it plans to dig through one or to build in the way.
 * Without a guard (a Lockout game, the tests) everything goes.
 */
public interface BlockGuard {
    /** Whether the bot may break the block at {@code pos} (on the server thread). */
    boolean mayBreak(ServerLevel level, BlockPos pos, BlockState state);

    /** Whether the bot may set one of its own blocks at {@code pos} (a pillar, a bridge). */
    default boolean mayPlace(ServerLevel level, BlockPos pos) {
        return true;
    }

    /**
     * The same answers for a path search, which runs off the server thread: worked out now, on
     * the server thread, for the chunks the search will read (packed chunk positions).
     */
    default Frozen freeze(ServerLevel level, LongCollection chunks) {
        return Frozen.ALL;
    }

    /** The guard's answers fixed for one search; asked from the search's thread. */
    interface Frozen {
        boolean mayBreak(BlockPos pos, BlockState state);

        default boolean mayPlace(BlockPos pos) {
            return true;
        }

        Frozen ALL = (p, s) -> true;
    }

    BlockGuard NONE = (level, pos, state) -> true;
}
