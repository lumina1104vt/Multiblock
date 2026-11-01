package com.lumina.lmb.core;

/** The engine's only way to look at the world. */
public interface WorldView<S> {
    /** The block at {@code pos}, or null when its chunk is not loaded. Never loads a chunk. */
    S get(long pos);

    boolean isLoaded(int chunkX, int chunkZ);

    /**
     * True when the block keeps state for one machine only (a maintenance hatch), so it can belong to one
     * structure at a time. Blocks are shareable unless they say otherwise.
     */
    boolean exclusive(long pos, S state);
}
