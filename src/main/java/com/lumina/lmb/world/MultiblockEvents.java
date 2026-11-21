package com.lumina.lmb.world;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.level.ChunkEvent;
import net.minecraftforge.event.level.LevelEvent;

/** Feeds the engines: one per server level, ticked with it. Block changes come from LevelChunkMixin. */
public final class MultiblockEvents {
    private MultiblockEvents() {}

    public static void onLevelLoad(LevelEvent.Load event) {
        if (event.getLevel() instanceof ServerLevel level) MultiblockData.attach(level);
    }

    public static void onLevelUnload(LevelEvent.Unload event) {
        if (event.getLevel() instanceof ServerLevel level) MultiblockData.detach(level);
    }

    public static void onLevelTick(TickEvent.LevelTickEvent event) {
        if (event.phase == TickEvent.Phase.END && event.level instanceof ServerLevel level) {
            MultiblockData data = MultiblockData.get(level);
            if (data != null) data.tick();
        }
    }

    /** May fire before the chunk is fully loaded, so it is only queued; the engine looks at it next tick. */
    public static void onChunkLoad(ChunkEvent.Load event) {
        if (event.getLevel() instanceof ServerLevel level && event.getChunk() instanceof LevelChunk chunk) {
            MultiblockData data = MultiblockData.get(level);
            if (data != null) data.chunkLoaded(chunk.getPos());
        }
    }
}
