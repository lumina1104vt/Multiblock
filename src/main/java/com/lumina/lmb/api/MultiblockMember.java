package com.lumina.lmb.api;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Implemented by a Block or BlockEntity to decide whether several structures may hold it at once. Blocks
 * are shareable by default, also when they implement this without overriding: an input hatch is only a
 * source of items, so two machines can draw from it. Override to return false for a block that keeps
 * state for one machine (a maintenance hatch). Blocks of other mods: the block tag
 * {@code lumina_multiblock:exclusive}.
 */
public interface MultiblockMember {
    default boolean canShare(Level level, BlockPos pos, BlockState state) {
        return true;
    }
}
