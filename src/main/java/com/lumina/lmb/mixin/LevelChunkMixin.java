package com.lumina.lmb.mixin;

import com.lumina.lmb.world.MultiblockData;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Every block change in a loaded chunk goes through here: players, pistons, explosions, fluids and other
 * mods' {@code setBlock} alike (NeoForge's block events only cover players and entities). Edits that write
 * chunk sections directly are caught when the chunk loads again.
 */
@Mixin(LevelChunk.class)
public abstract class LevelChunkMixin {
    @Shadow
    @Final
    Level level;

    // at every return: some paths return null although the block did change
    @Inject(method = "setBlockState", at = @At("RETURN"))
    private void lmb$blockChanged(BlockPos pos, BlockState state, boolean isMoving, CallbackInfoReturnable<BlockState> cir) {
        if (level instanceof ServerLevel server) MultiblockData.blockChanged(server, pos, state);
    }
}
