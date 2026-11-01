package com.lumina.lmb.core;

/**
 * Block positions packed into a long, with the same bit layout as {@code BlockPos.asLong}, and chunk
 * keys with the layout of {@code ChunkPos.asLong}. The engine is plain Java (so it can be tested without
 * Minecraft); the Minecraft side converts for free.
 */
public final class Pos {
    private Pos() {}

    public static long pack(int x, int y, int z) {
        return ((long) x & 0x3FFFFFFL) << 38 | ((long) y & 0xFFFL) | ((long) z & 0x3FFFFFFL) << 12;
    }

    public static int x(long p) {
        return (int) (p >> 38);
    }

    public static int y(long p) {
        return (int) (p << 52 >> 52);
    }

    public static int z(long p) {
        return (int) (p << 26 >> 38);
    }

    public static long chunk(int cx, int cz) {
        return (cx & 0xFFFFFFFFL) | (cz & 0xFFFFFFFFL) << 32;
    }

    public static long chunkOf(long p) {
        return chunk(x(p) >> 4, z(p) >> 4);
    }

    public static int chunkX(long chunk) {
        return (int) chunk;
    }

    public static int chunkZ(long chunk) {
        return (int) (chunk >>> 32);
    }

    public static String str(long p) {
        return x(p) + ", " + y(p) + ", " + z(p);
    }
}
