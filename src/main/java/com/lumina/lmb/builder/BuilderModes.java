package com.lumina.lmb.builder;

/** The builder's modes, any combination: bit flags stored on the item. */
public final class BuilderModes {
    /** Take the structure down instead of building it. */
    public static final int DECONSTRUCT = 1;
    /** Build the mirror image (only forms when the definition allows mirroring). */
    public static final int MIRROR = 2;
    /** Leave hatches out (casing in their place); taking down, leave them standing. */
    public static final int NO_HATCHES = 4;

    public static final int[] ALL = {DECONSTRUCT, MIRROR, NO_HATCHES};

    private BuilderModes() {}

    public static boolean has(int modes, int mode) {
        return (modes & mode) != 0;
    }

    public static String key(int mode) {
        return switch (mode) {
            case DECONSTRUCT -> "lumina_multiblock.builder.mode.deconstruct";
            case MIRROR -> "lumina_multiblock.builder.mode.mirror";
            case NO_HATCHES -> "lumina_multiblock.builder.mode.no_hatches";
            default -> throw new IllegalArgumentException("mode " + mode);
        };
    }
}
