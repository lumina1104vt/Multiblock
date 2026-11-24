package com.lumina.lmb.core;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * One of the 48 ways to turn and mirror a structure on the block grid (axis permutations with signs).
 * A definition lists the ones it allows; {@link #YAW} (the four horizontal turns) is the default.
 */
public final class Transform {
    private static final int[][] PERMS = {{0, 1, 2}, {0, 2, 1}, {1, 0, 2}, {1, 2, 0}, {2, 0, 1}, {2, 1, 0}};
    private static final Transform[] ALL = new Transform[48];

    static {
        for (int p = 0; p < 6; p++) {
            for (int s = 0; s < 8; s++) {
                ALL[p * 8 + s] = new Transform(p * 8 + s, PERMS[p], s);
            }
        }
    }

    public static final Transform IDENTITY = ALL[0];
    /** Quarter turn about the vertical axis: (x, y, z) → (−z, y, x). */
    public static final Transform ROTATE_Y = of(new int[]{2, 1, 0}, new int[]{-1, 1, 1});
    /** Left and right swapped: (x, y, z) → (−x, y, z). */
    public static final Transform MIRROR_X = of(new int[]{0, 1, 2}, new int[]{-1, 1, 1});
    /** Quarter turn about the x axis, for structures that may lie on their side: (x, y, z) → (x, −z, y). */
    public static final Transform ROTATE_X = of(new int[]{0, 2, 1}, new int[]{1, -1, 1});

    /** The four horizontal turns (the default). */
    public static final List<Transform> YAW = closure(ROTATE_Y);
    /** The four horizontal turns and their mirror images. */
    public static final List<Transform> YAW_MIRROR = closure(ROTATE_Y, MIRROR_X);
    /** Every turn, lying down and upside down included, without mirroring. */
    public static final List<Transform> ROTATIONS = closure(ROTATE_Y, ROTATE_X);
    /** Everything. */
    public static final List<Transform> ALL_48 = List.of(ALL);

    private final int id;
    private final int[] perm;
    private final int[] sign = new int[3];

    private Transform(int id, int[] perm, int signMask) {
        this.id = id;
        this.perm = perm;
        for (int i = 0; i < 3; i++) sign[i] = (signMask >> i & 1) == 0 ? 1 : -1;
    }

    public static Transform get(int id) {
        return ALL[id];
    }

    private static Transform of(int[] perm, int[] sign) {
        for (int p = 0; p < 6; p++) {
            if (PERMS[p][0] == perm[0] && PERMS[p][1] == perm[1] && PERMS[p][2] == perm[2]) {
                int mask = 0;
                for (int i = 0; i < 3; i++) if (sign[i] < 0) mask |= 1 << i;
                return ALL[p * 8 + mask];
            }
        }
        throw new IllegalArgumentException("not a permutation");
    }

    /** Every transform that the generators reach, the identity first. */
    public static List<Transform> closure(Transform... generators) {
        Set<Transform> seen = new LinkedHashSet<>();
        ArrayDeque<Transform> queue = new ArrayDeque<>();
        seen.add(IDENTITY);
        queue.add(IDENTITY);
        while (!queue.isEmpty()) {
            Transform t = queue.poll();
            for (Transform g : generators) {
                Transform n = t.then(g);
                if (seen.add(n)) queue.add(n);
            }
        }
        return List.copyOf(new ArrayList<>(seen));
    }

    public int id() {
        return id;
    }

    private static int pick(int axis, int x, int y, int z) {
        return axis == 0 ? x : axis == 1 ? y : z;
    }

    public int x(int x, int y, int z) {
        return sign[0] * pick(perm[0], x, y, z);
    }

    public int y(int x, int y, int z) {
        return sign[1] * pick(perm[1], x, y, z);
    }

    public int z(int x, int y, int z) {
        return sign[2] * pick(perm[2], x, y, z);
    }

    /** This transform followed by {@code next}. */
    public Transform then(Transform next) {
        int[] p = new int[3];
        int[] s = new int[3];
        for (int i = 0; i < 3; i++) {
            p[i] = perm[next.perm[i]];
            s[i] = next.sign[i] * sign[next.perm[i]];
        }
        return of(p, s);
    }

    public Transform inverse() {
        for (Transform t : ALL) if (then(t) == IDENTITY) return t;
        throw new IllegalStateException();
    }

    /** False for mirror images. */
    public boolean isRotation() {
        int parity = (perm[0] == 0 ? 0 : 1) + (perm[1] == 1 ? 0 : 1) + (perm[2] == 2 ? 0 : 1);
        // a permutation of 3 axes moving 0 or 3 of them is even, moving 2 is odd
        boolean even = parity != 2;
        return even == (sign[0] * sign[1] * sign[2] > 0);
    }

    @Override
    public String toString() {
        String[] names = {"x", "y", "z"};
        StringBuilder b = new StringBuilder("(");
        for (int i = 0; i < 3; i++) {
            if (i > 0) b.append(", ");
            b.append(sign[i] < 0 ? "-" : "").append(names[perm[i]]);
        }
        return b.append(')').toString();
    }
}
