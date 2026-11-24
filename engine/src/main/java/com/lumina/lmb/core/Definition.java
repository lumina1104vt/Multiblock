package com.lumina.lmb.core;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;

/**
 * One kind of structure: its shape, the turns it may be built in, count limits and how it forms.
 *
 * <p><b>Versions.</b> Every saved structure remembers the fingerprint of the definition it formed under
 * (id, {@link Builder#version}, parameters and the role layout). When the fingerprint no longer matches,
 * the structure is checked again under the new definition and counts as damaged until it passes. The
 * fingerprint cannot see inside matchers: change the version when only the accepted blocks change. To let
 * structures built under an old version keep running, list that version's shape with
 * {@link Builder#legacy}; it is used for those structures only, never to recognise new ones.
 */
public final class Definition<S> {
    private final String id;
    private final Shape<S> shape;
    private final List<Transform> transforms;
    private final List<CountLimit> limits;
    private final boolean autoForm;
    private final StructureListener<S> listener;
    private final long fingerprint;
    private final Map<Long, Shape<S>> legacy;
    private final Role<S> anchorRole;
    private final Set<String> hatchKinds;

    private final Map<Shape<S>, Map<List<Integer>, RoleField<S>>> fields = new IdentityHashMap<>();
    private final Map<RoleField<S>, List<Transform>> orientations = new IdentityHashMap<>();
    private final Map<RoleField<S>, Map<List<Integer>, List<Transform>>> facedOrientations = new IdentityHashMap<>();
    private final int[] localFront, localUp;
    private final Function<S, int[]> frontOf, upOf;
    private List<int[]> combos;

    private Definition(Builder<S> b) {
        this.id = b.id;
        this.shape = b.shape;
        this.transforms = List.copyOf(b.transforms);
        this.limits = List.copyOf(b.limits);
        this.autoForm = b.autoForm;
        this.listener = b.listener;
        this.fingerprint = fingerprint(b.id, b.version, b.shape);
        Map<Long, Shape<S>> old = new LinkedHashMap<>();
        b.legacy.forEach((version, s) -> old.put(fingerprint(b.id, version, s), s));
        this.legacy = Map.copyOf(old);
        this.anchorRole = field(shape, minParams(shape)).anchorRole();
        this.hatchKinds = Set.copyOf(b.hatchKinds);
        this.localFront = b.localFront;
        this.frontOf = b.frontOf;
        this.localUp = b.localUp;
        this.upOf = b.upOf;
    }

    /** A block the builder leaves out (or keeps when taking a structure down) in "no hatches" mode. */
    public boolean isHatch(Match m) {
        return m != null && hatchKinds.contains(m.kind());
    }

    public static <S> Builder<S> builder(String id, Shape<S> shape) {
        return new Builder<>(id, shape);
    }

    public String id() {
        return id;
    }

    public Shape<S> shape() {
        return shape;
    }

    public List<Transform> transforms() {
        return transforms;
    }

    public List<CountLimit> limits() {
        return limits;
    }

    public boolean autoForm() {
        return autoForm;
    }

    public StructureListener<S> listener() {
        return listener;
    }

    public long fingerprint() {
        return fingerprint;
    }

    /** The shape a structure saved under {@code fingerprint} is checked with, or null when it is unknown. */
    Shape<S> shapeFor(long fingerprint) {
        return fingerprint == this.fingerprint ? shape : legacy.get(fingerprint);
    }

    /** The controller's role: placing a block it accepts starts looking for this structure. */
    public Role<S> anchorRole() {
        return anchorRole;
    }

    public boolean anchorMatches(S state) {
        return anchorRole.matcher().test(state) != null;
    }

    private static int[] minParams(Shape<?> shape) {
        return shape.params().stream().mapToInt(Shape.Param::min).toArray();
    }

    /** True when every value is one the shape allows. */
    static boolean validParams(Shape<?> shape, int[] values) {
        List<Shape.Param> ps = shape.params();
        if (values.length != ps.size()) return false;
        for (int i = 0; i < values.length; i++) {
            Shape.Param p = ps.get(i);
            if (values[i] < p.min() || values[i] > p.max() || (values[i] - p.min()) % p.step() != 0) return false;
        }
        return true;
    }

    /** The role field for these parameter values (built once, then cached). */
    public synchronized RoleField<S> field(Shape<S> of, int[] values) {
        List<Integer> key = Arrays.stream(values).boxed().toList();
        return fields.computeIfAbsent(of, k -> new HashMap<>()).computeIfAbsent(key, k -> of.build(values));
    }

    public RoleField<S> field(int[] values) {
        return field(shape, values);
    }

    /** Every combination of parameter values, smallest first. */
    public synchronized List<int[]> paramCombos() {
        if (combos == null) {
            List<int[]> out = new ArrayList<>();
            List<Shape.Param> ps = shape.params();
            int[] v = minParams(shape);
            while (true) {
                out.add(v.clone());
                int i = 0;
                while (i < ps.size()) {
                    v[i] += ps.get(i).step();
                    if (v[i] <= ps.get(i).max()) break;
                    v[i] = ps.get(i).min();
                    i++;
                }
                if (i == ps.size()) break;
            }
            combos = List.copyOf(out);
        }
        return combos;
    }

    /**
     * The allowed transforms that give different structures for this field: a cube with the controller in
     * its centre looks the same in all four turns, so only one is tried; a hexagram turned a quarter is a
     * different shape, but turned half-way it is the same, so two are tried.
     */
    public synchronized List<Transform> orientations(RoleField<S> field) {
        return orientations.computeIfAbsent(field, f -> distinct(f, transforms));
    }

    /**
     * The turns open to a controller in {@code anchorState}: for a definition with a {@link Builder#facing},
     * only those that bring the structure's front round to the way the controller faces, as on a
     * GregTech controller, so where the structure goes is never a guess. With a spin as well, its top too:
     * four spins to each of the six fronts, one turn in all.
     */
    public synchronized List<Transform> orientations(RoleField<S> field, S anchorState) {
        int[] front = frontOf == null || anchorState == null ? null : frontOf.apply(anchorState);
        if (front == null) return orientations(field);
        int[] up = upOf == null ? null : upOf.apply(anchorState);
        List<Integer> key = up == null ? List.of(front[0], front[1], front[2])
                : List.of(front[0], front[1], front[2], up[0], up[1], up[2]);
        return facedOrientations.computeIfAbsent(field, f -> new HashMap<>()).computeIfAbsent(key, k -> {
            List<Transform> turned = new ArrayList<>();
            for (Transform t : transforms) {
                if (brings(t, localFront, front) && (up == null || brings(t, localUp, up))) turned.add(t);
            }
            return distinct(field, turned);
        });
    }

    private static boolean brings(Transform t, int[] from, int[] to) {
        return t.x(from[0], from[1], from[2]) == to[0] && t.y(from[0], from[1], from[2]) == to[1]
                && t.z(from[0], from[1], from[2]) == to[2];
    }

    private List<Transform> distinct(RoleField<S> f, List<Transform> allowed) {
        List<Transform> distinct = new ArrayList<>();
        List<long[]> seen = new ArrayList<>();
        for (Transform t : allowed) {
            long[] key = signature(f, t);
            boolean dup = false;
            for (long[] k : seen) {
                if (Arrays.equals(k, key)) {
                    dup = true;
                    break;
                }
            }
            if (!dup) {
                distinct.add(t);
                seen.add(key);
            }
        }
        return List.copyOf(distinct);
    }

    /** The turned cells as sorted (offset from the anchor, role) keys. */
    private static long[] signature(RoleField<?> f, Transform t) {
        long[] key = new long[f.count()];
        int j = 0;
        for (int i = 0; i < f.volume(); i++) {
            int role = f.roleId(i);
            if (role == 0) continue;
            int lx = f.x(i) - f.anchorX(), ly = f.y(i) - f.anchorY(), lz = f.z(i) - f.anchorZ();
            long wx = t.x(lx, ly, lz) + 1024L, wy = t.y(lx, ly, lz) + 1024L, wz = t.z(lx, ly, lz) + 1024L;
            key[j++] = wx << 30 | wy << 19 | wz << 8 | role;
        }
        Arrays.sort(key);
        return key;
    }

    private static <S> long fingerprint(String id, int version, Shape<S> shape) {
        Fnv h = new Fnv();
        h.str(id);
        h.i(version);
        for (Shape.Param p : shape.params()) {
            h.str(p.name());
            h.i(p.min());
            h.i(p.max());
            h.i(p.step());
        }
        RoleField<S> f = shape.build(minParams(shape));
        h.i(f.sizeX());
        h.i(f.sizeY());
        h.i(f.sizeZ());
        h.i(f.anchorX());
        h.i(f.anchorY());
        h.i(f.anchorZ());
        long[] names = new long[f.paletteSize()];
        for (int r = 1; r < names.length; r++) {
            Fnv n = new Fnv();
            n.str(f.paletteRole(r).name());
            names[r] = n.h;
        }
        for (int i = 0; i < f.volume(); i++) h.l(names[f.roleId(i)]);
        return h.h;
    }

    private static final class Fnv {
        long h = 0xcbf29ce484222325L;

        void b(int v) {
            h = (h ^ (v & 0xFF)) * 0x100000001b3L;
        }

        void i(int v) {
            for (int k = 0; k < 4; k++) b(v >>> (8 * k));
        }

        void l(long v) {
            i((int) v);
            i((int) (v >>> 32));
        }

        void str(String s) {
            byte[] bytes = s.getBytes(StandardCharsets.UTF_8);
            i(bytes.length);
            for (byte x : bytes) b(x);
        }
    }

    public static final class Builder<S> {
        private final String id;
        private final Shape<S> shape;
        private List<Transform> transforms = Transform.YAW;
        private final List<CountLimit> limits = new ArrayList<>();
        private boolean autoForm = true;
        private int version;
        private final Map<Integer, Shape<S>> legacy = new LinkedHashMap<>();
        private StructureListener<S> listener = new StructureListener<>() {};
        private final Set<String> hatchKinds = new HashSet<>();
        private int[] localFront, localUp;
        private Function<S, int[]> frontOf, upOf;

        private Builder(String id, Shape<S> shape) {
            this.id = id;
            this.shape = shape;
        }

        /**
         * The controller has a front: {@code localFront} is the way the unturned structure's controller
         * faces (a unit vector), {@code frontOf} the way a controller block faces in the world (null when
         * it doesn't say).
         */
        public Builder<S> facing(int[] localFront, Function<S, int[]> frontOf) {
            this.localFront = localFront;
            this.frontOf = frontOf;
            return this;
        }

        /**
         * The controller also has a spin, for a structure that isn't the same all round its front:
         * {@code localUp} is the unturned controller's top, {@code upOf} a controller block's top in the
         * world. Use with {@link #facing}.
         */
        public Builder<S> spin(int[] localUp, Function<S, int[]> upOf) {
            this.localUp = localUp;
            this.upOf = upOf;
            return this;
        }

        /** Default {@link Transform#YAW}. */
        public Builder<S> transforms(List<Transform> allowed) {
            if (allowed.isEmpty()) throw new IllegalArgumentException("no transforms");
            this.transforms = allowed;
            return this;
        }

        public Builder<S> limit(String kind, int min, int max) {
            limits.add(new CountLimit(kind, min, max));
            return this;
        }

        /** Form only when asked ({@code Engine#tryForm}, e.g. a wrench), not when the last block is placed. */
        public Builder<S> manualForm() {
            this.autoForm = false;
            return this;
        }

        /** Raise it when the accepted blocks change but the layout does not. Default 0. */
        public Builder<S> version(int version) {
            this.version = version;
            return this;
        }

        /** Structures built under {@code oldVersion} with {@code oldShape} keep running. */
        public Builder<S> legacy(int oldVersion, Shape<S> oldShape) {
            legacy.put(oldVersion, oldShape);
            return this;
        }

        /** Kinds ({@link Match#kind()}) that are hatches: input / output / maintenance hatches and the like. */
        public Builder<S> hatchKinds(String... kinds) {
            hatchKinds.addAll(Arrays.asList(kinds));
            return this;
        }

        public Builder<S> listener(StructureListener<S> listener) {
            this.listener = listener;
            return this;
        }

        public Definition<S> build() {
            return new Definition<>(this);
        }
    }
}
