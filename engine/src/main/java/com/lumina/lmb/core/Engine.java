package com.lumina.lmb.core;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;

/**
 * Every structure of one dimension. Block changes and chunk loads come in through {@link #blockChanged}
 * and {@link #chunkLoaded}; {@link #tick} (once per server tick) applies them and fires the listeners.
 *
 * <p>How a structure is found: placing a block that some definition's controller role accepts creates a
 * pending structure. It keeps every placement it could be part of (each size × each distinct turn) and
 * counts each one's wrong cells as blocks change, so it forms the moment the last block goes in. When the
 * candidates together hold too many cells, it falls back to checking them again whenever a block in range
 * changes, at most once a second.
 *
 * <p>How a block change finds its structures: every chunk lists the structures whose box reaches into
 * it, usually none or one or two; the change is looked up in those only.
 *
 * <p>Unloaded chunks: cells keep their last state; nothing loads a chunk to look. A pending structure with
 * a cell it has never seen does not form. A formed structure loaded from the save trusts its unseen cells
 * until their chunks load and are checked.
 */
public final class Engine<S> {
    /** Pending structures track their candidates cell by cell while they hold at most this many cells. */
    static final int DEFAULT_TRACK_BUDGET = 1 << 18;
    static final int RESCAN_INTERVAL = 20;
    private static final int MAX_PASSES = 8;

    private final WorldView<S> world;
    private final Map<String, Definition<S>> definitions;
    private final Runnable onSave;
    int trackBudget = DEFAULT_TRACK_BUDGET;

    private final Map<Long, Instance<S>> byAnchor = new LinkedHashMap<>();
    private final Map<Long, List<Instance<S>>> index = new HashMap<>();
    /** Exclusive blocks and the formed (or damaged) structure holding each. */
    private final Map<Long, Instance<S>> claims = new HashMap<>();
    private final ArrayDeque<Long> dirty = new ArrayDeque<>();
    private final Set<Long> dirtySet = new HashSet<>();
    private final Set<Long> dirtyChunks = new LinkedHashSet<>();
    private final Set<Instance<S>> touched = new LinkedHashSet<>();
    private final List<Runnable> events = new ArrayList<>();
    /** Hear about every structure, after its definition's own listener (the lib uses it to sync clients). */
    private final List<StructureListener<S>> observers = new ArrayList<>();
    /** Saved structures whose definition is not registered (its mod removed): kept and saved again as they were. */
    private final List<Record> orphans = new ArrayList<>();
    private long tick;

    /**
     * @param definitions a live view: definitions registered later are picked up
     * @param onSave      called whenever something that is saved changed
     */
    public Engine(WorldView<S> world, Map<String, Definition<S>> definitions, Runnable onSave) {
        this.world = world;
        this.definitions = definitions;
        this.onSave = onSave;
    }

    public void addObserver(StructureListener<S> observer) {
        observers.add(observer);
    }

    // ---- input

    /** A block changed (or may have). Cheap when nothing cares about the position. */
    public void blockChanged(long pos, S state) {
        if (dirtySet.contains(pos)) return;
        if (index.containsKey(Pos.chunkOf(pos)) || byAnchor.containsKey(pos) || anchorDefinition(state) != null) markDirty(pos);
    }

    /** Looks at a position next tick, without filtering. */
    public void markDirty(long pos) {
        if (dirtySet.add(pos)) dirty.add(pos);
    }

    /** A chunk was loaded; its cells are checked next tick (also catches blocks edited while unloaded). */
    public void chunkLoaded(int chunkX, int chunkZ) {
        long c = Pos.chunk(chunkX, chunkZ);
        if (index.containsKey(c)) dirtyChunks.add(c);
    }

    public void tick() {
        tick++;
        for (int pass = 0; pass < MAX_PASSES; pass++) {
            processChunks();
            processDirty();
            rescanPending();
            settle();
            if (dirty.isEmpty() && dirtyChunks.isEmpty() && touched.isEmpty()) break;
        }
        fireEvents();
    }

    // ---- queries

    /** The structure whose controller is at {@code anchor}. */
    public Instance<S> at(long anchor) {
        return byAnchor.get(anchor);
    }

    public Collection<Instance<S>> all() {
        return Collections.unmodifiableCollection(byAnchor.values());
    }

    /** Formed or damaged structures that hold the block at {@code pos} (more than one for a shared block). */
    public List<Instance<S>> owners(long pos) {
        List<Instance<S>> out = new ArrayList<>();
        List<Instance<S>> list = index.get(Pos.chunkOf(pos));
        if (list == null) return out;
        for (Instance<S> inst : list) {
            if (inst.tracker == null) continue;
            int i = inst.tracker.indexOf(pos);
            if (i >= 0 && inst.tracker.code(i) >= Tracker.MATCHED) out.add(inst);
        }
        return out;
    }

    /**
     * Why it is not formed, or what is damaged. For a pending structure: the placement closest to complete
     * (the one the player is most likely building).
     */
    public List<Problem> problems(Instance<S> inst, int limit) {
        Tracker<S> t = inst.tracker != null ? inst.tracker : closest(inst);
        return t == null ? List.of() : t.problems(limit);
    }

    /** Wrong cells by role, for a pending structure of its closest placement. */
    public Map<String, Integer> missing(Instance<S> inst) {
        Tracker<S> t = inst.tracker != null ? inst.tracker : closest(inst);
        return t == null ? Map.of() : t.missingByRole();
    }

    /** A placement and how many of its cells are wrong or not seen. */
    public record Candidate<S>(Placement<S> placement, int wrong) {
    }

    /**
     * Where the structure may be, closest to complete first: the formed placement alone, or for a pending
     * structure every size and turn. The builder and the projection pick from these.
     */
    public List<Candidate<S>> candidates(Instance<S> inst) {
        List<Tracker<S>> options = inst.tracker != null ? List.of(inst.tracker) : trackersForRanking(inst);
        List<Candidate<S>> out = new ArrayList<>();
        for (Tracker<S> t : options) out.add(new Candidate<>(t, t.missingTotal() + t.unseen()));
        out.sort((a, b) -> Integer.compare(a.wrong(), b.wrong()));
        return out;
    }

    private Tracker<S> closest(Instance<S> inst) {
        Tracker<S> best = null;
        for (Tracker<S> t : trackersForRanking(inst)) {
            if (best == null || t.missingTotal() + t.unseen() < best.missingTotal() + best.unseen()) best = t;
        }
        return best;
    }

    /** A pending structure's candidates; built and checked on the spot when they are not tracked. */
    private List<Tracker<S>> trackersForRanking(Instance<S> inst) {
        List<Tracker<S>> options = inst.candidates;
        if (options == null) {
            options = new ArrayList<>();
            for (int[] p : inst.def.paramCombos()) {
                RoleField<S> f = inst.def.field(p);
                for (Transform t : options(inst, f)) {
                    Tracker<S> tr = new Tracker<>(inst.def, f, t, inst.anchor, p, inst.def.fingerprint());
                    fullEvaluate(inst, tr, false);
                    options.add(tr);
                }
            }
        }
        return options;
    }

    /**
     * Forms a complete pending structure now, for definitions that form by hand. False when it is not
     * complete (see {@link #problems}).
     */
    public boolean tryForm(long anchor) {
        Instance<S> inst = byAnchor.get(anchor);
        if (inst == null || inst.state != Instance.State.PENDING) return false;
        if (inst.candidates == null) {
            scan(inst);
        } else {
            inst.ready = firstComplete(inst.candidates);
        }
        if (inst.ready == null) return false;
        form(inst, inst.ready);
        fireEvents();
        return inst.state == Instance.State.FORMED;
    }

    // ---- saving

    public List<Record> save() {
        List<Record> out = new ArrayList<>(orphans);
        for (Instance<S> inst : byAnchor.values()) {
            if (inst.state == Instance.State.PENDING) {
                out.add(new Record(inst.def.id(), inst.anchor, inst.state, -1, new int[0], inst.def.fingerprint(), Map.of()));
            } else {
                out.add(new Record(inst.def.id(), inst.anchor, inst.state, inst.tracker.transform.id(), inst.tracker.params.clone(),
                        inst.fingerprint, Map.copyOf(inst.savedVars)));
            }
        }
        return out;
    }

    /** Loads saved structures into an empty engine. Their cells are checked as their chunks load. */
    public void restore(List<Record> records) {
        for (Record r : records) {
            Definition<S> def = definitions.get(r.definition());
            if (def == null) {
                orphans.add(r);
                continue;
            }
            if (byAnchor.containsKey(r.anchor())) continue;
            Instance<S> inst = new Instance<>(def, r.anchor());
            boolean pending = r.state() == Instance.State.PENDING;
            Shape<S> shape = pending ? null : def.shapeFor(r.fingerprint());
            boolean trusted = shape != null;
            if (shape == null) shape = def.shape();
            boolean placeable = !pending && r.transform() >= 0 && r.transform() < 48 && Definition.validParams(shape, r.params())
                    && (trusted || def.transforms().contains(Transform.get(r.transform())));
            if (!placeable) {
                // still being built, or its parameters no longer exist: look for it from scratch
                inst.fingerprint = def.fingerprint();
                startSearch(inst);
            } else {
                // a changed definition: checked again, damaged until every cell passes
                RoleField<S> f = def.field(shape, r.params());
                Tracker<S> t = new Tracker<>(def, f, Transform.get(r.transform()), r.anchor(), r.params().clone(),
                        trusted ? r.fingerprint() : def.fingerprint());
                inst.tracker = t;
                inst.state = trusted ? r.state() : Instance.State.DAMAGED;
                inst.trusted = trusted;
                inst.fingerprint = r.fingerprint();
                inst.savedVars.putAll(r.vars());
                index(inst, new int[]{t.minX, t.minY, t.minZ, t.maxX, t.maxY, t.maxZ});
            }
            byAnchor.put(r.anchor(), inst);
            // some of its chunks may be loaded already (spawn chunks); the others are added as they load
            forChunks(inst.bounds, dirtyChunks::add);
        }
    }

    // ---- processing

    private Definition<S> anchorDefinition(S state) {
        for (Definition<S> d : definitions.values()) {
            if (d.anchorMatches(state)) return d;
        }
        return null;
    }

    private void processChunks() {
        if (dirtyChunks.isEmpty()) return;
        List<Long> chunks = new ArrayList<>(dirtyChunks);
        dirtyChunks.clear();
        for (long c : chunks) {
            int cx = Pos.chunkX(c), cz = Pos.chunkZ(c);
            if (!world.isLoaded(cx, cz)) continue;
            List<Instance<S>> list = index.get(c);
            if (list == null) continue;
            for (Instance<S> inst : new ArrayList<>(list)) {
                if (inst.removed) continue;
                if (Pos.chunkOf(inst.anchor) == c) {
                    S a = world.get(inst.anchor);
                    if (a != null && !inst.def.anchorMatches(a)) {
                        remove(inst);
                        continue;
                    }
                }
                refreshChunk(inst, cx, cz);
            }
        }
    }

    private void refreshChunk(Instance<S> inst, int cx, int cz) {
        if (inst.state == Instance.State.PENDING && inst.candidates == null) {
            inst.rescan = true;
            return;
        }
        int x0 = cx << 4, z0 = cz << 4;
        for (Tracker<S> t : trackers(inst)) {
            int xa = Math.max(t.minX, x0), xb = Math.min(t.maxX, x0 + 15);
            int za = Math.max(t.minZ, z0), zb = Math.min(t.maxZ, z0 + 15);
            for (int y = t.minY; y <= t.maxY; y++) {
                for (int z = za; z <= zb; z++) {
                    for (int x = xa; x <= xb; x++) {
                        long pos = Pos.pack(x, y, z);
                        int i = t.indexOf(pos);
                        if (i < 0) continue;
                        S s = world.get(pos);
                        if (s != null) evaluate(inst, t, i, pos, s);
                    }
                }
            }
        }
    }

    private List<Tracker<S>> trackers(Instance<S> inst) {
        if (inst.tracker != null) return List.of(inst.tracker);
        return inst.candidates == null ? List.of() : inst.candidates;
    }

    private void processDirty() {
        while (!dirty.isEmpty()) {
            long pos = dirty.poll();
            dirtySet.remove(pos);
            S s = world.get(pos);
            if (s == null) continue;
            Instance<S> at = byAnchor.get(pos);
            if (at != null && !at.def.anchorMatches(s)) {
                remove(at);
                at = null;
            }
            if (at == null) {
                Definition<S> d = anchorDefinition(s);
                if (d != null) create(d, pos);
            }
            List<Instance<S>> list = index.get(Pos.chunkOf(pos));
            if (list == null) continue;
            for (Instance<S> inst : new ArrayList<>(list)) {
                if (!inst.removed) cellChanged(inst, pos, s);
            }
        }
    }

    private void cellChanged(Instance<S> inst, long pos, S s) {
        if (inst.state == Instance.State.PENDING && inst.candidates == null) {
            if (inBounds(inst.bounds, pos)) inst.rescan = true;
            return;
        }
        for (Tracker<S> t : trackers(inst)) {
            int i = t.indexOf(pos);
            if (i >= 0) evaluate(inst, t, i, pos, s);
        }
    }

    private void evaluate(Instance<S> inst, Tracker<S> t, int i, long pos, S s) {
        Match m = t.test(i, s);
        boolean owned = false;
        if (m != null) {
            Instance<S> owner = claims.get(pos);
            owned = owner != null && owner != inst && world.exclusive(pos, s);
        }
        if (t.set(i, m, owned)) touched.add(inst);
        if (t == inst.tracker) updateClaim(inst, pos, s, m != null && !owned);
    }

    /** A formed structure takes the exclusive blocks it holds; others then see them as owned. */
    private void updateClaim(Instance<S> inst, long pos, S s, boolean matched) {
        boolean want = matched && world.exclusive(pos, s);
        Instance<S> owner = claims.get(pos);
        if (want && owner == null) {
            claims.put(pos, inst);
            inst.claimed.add(pos);
            markDirty(pos);
        } else if (!want && owner == inst) {
            claims.remove(pos);
            inst.claimed.remove(pos);
            markDirty(pos);
        }
    }

    /** Checks every cell; with {@code earlyExit}, stops at the first cell that is not right. */
    private boolean fullEvaluate(Instance<S> inst, Tracker<S> t, boolean earlyExit) {
        for (int i = 0; i < t.field.volume(); i++) {
            if (t.field.roleId(i) == 0) continue;
            long pos = t.worldPos(i);
            S s = world.get(pos);
            if (s != null) evaluate(inst, t, i, pos, s);
            if (earlyExit && t.code(i) < Tracker.MATCHED) return false;
        }
        return true;
    }

    private void create(Definition<S> def, long anchor) {
        Instance<S> inst = new Instance<>(def, anchor);
        inst.fingerprint = def.fingerprint();
        startSearch(inst);
        byAnchor.put(anchor, inst);
        touched.add(inst);
        onSave.run();
    }

    /** The turns a structure may take, as far as its controller says. */
    private java.util.List<Transform> options(Instance<S> inst, RoleField<S> f) {
        return inst.def.orientations(f, world.get(inst.anchor));
    }

    /** Sets up the candidates of a pending structure and indexes it over all of them. */
    private void startSearch(Instance<S> inst) {
        Definition<S> def = inst.def;
        long cells = 0;
        for (int[] p : def.paramCombos()) {
            RoleField<S> f = def.field(p);
            cells += (long) f.count() * options(inst, f).size();
        }
        int[] box = null;
        if (cells <= trackBudget) {
            List<Tracker<S>> list = new ArrayList<>();
            for (int[] p : def.paramCombos()) {
                RoleField<S> f = def.field(p);
                for (Transform t : options(inst, f)) {
                    Tracker<S> tr = new Tracker<>(def, f, t, inst.anchor, p, def.fingerprint());
                    fullEvaluate(inst, tr, false);
                    list.add(tr);
                    box = union(box, new int[]{tr.minX, tr.minY, tr.minZ, tr.maxX, tr.maxY, tr.maxZ});
                }
            }
            inst.candidates = list;
        } else {
            inst.candidates = null;
            inst.rescan = true;
            inst.nextScan = tick;
            for (int[] p : def.paramCombos()) {
                RoleField<S> f = def.field(p);
                for (Transform t : options(inst, f)) box = union(box, Tracker.bounds(f, t, inst.anchor));
            }
        }
        index(inst, box);
    }

    private void rescanPending() {
        for (Instance<S> inst : new ArrayList<>(byAnchor.values())) {
            if (inst.state == Instance.State.PENDING && inst.candidates == null && inst.rescan && tick >= inst.nextScan) scan(inst);
        }
    }

    /** Checks every candidate of an untracked pending structure, stopping at the first complete one. */
    private void scan(Instance<S> inst) {
        inst.rescan = false;
        inst.nextScan = tick + RESCAN_INTERVAL;
        inst.ready = null;
        outer:
        for (int[] p : inst.def.paramCombos()) {
            RoleField<S> f = inst.def.field(p);
            for (Transform t : options(inst, f)) {
                Tracker<S> tr = new Tracker<>(inst.def, f, t, inst.anchor, p, inst.def.fingerprint());
                if (fullEvaluate(inst, tr, true) && tr.complete()) {
                    inst.ready = tr;
                    break outer;
                }
            }
        }
        touched.add(inst);
    }

    private Tracker<S> firstComplete(List<Tracker<S>> candidates) {
        for (Tracker<S> t : candidates) {
            if (t.complete()) return t;
        }
        return null;
    }

    private void settle() {
        while (!touched.isEmpty()) {
            List<Instance<S>> list = new ArrayList<>(touched);
            touched.clear();
            for (Instance<S> inst : list) {
                if (!inst.removed) settle(inst);
            }
        }
    }

    private void settle(Instance<S> inst) {
        if (inst.state == Instance.State.PENDING) {
            if (inst.candidates != null) inst.ready = firstComplete(inst.candidates);
            if (inst.ready != null && inst.def.autoForm()) form(inst, inst.ready);
            return;
        }
        Tracker<S> t = inst.tracker;
        boolean ok = t.holds(false, inst.trusted);
        if (ok && !inst.trusted) {
            // every cell checked under the new definition
            inst.trusted = true;
            inst.fingerprint = t.fingerprint;
            onSave.run();
        }
        boolean membersChanged = t.consumeMembersChanged();
        if (inst.state == Instance.State.FORMED && !ok) {
            inst.state = Instance.State.DAMAGED;
            event(inst, l -> l.damaged(inst));
        } else if (inst.state == Instance.State.DAMAGED && ok) {
            inst.state = Instance.State.FORMED;
            event(inst, l -> l.recovered(inst));
        } else if (inst.state == Instance.State.FORMED && membersChanged) {
            event(inst, l -> l.membersChanged(inst));
        }
        for (String name : t.variableNames()) {
            String v = t.variable(name);
            if (v != null && !v.equals(inst.savedVars.put(name, v))) onSave.run();
        }
    }

    private void form(Instance<S> inst, Tracker<S> t) {
        // an exclusive block another structure took this tick: not yet
        for (int i = 0; i < t.field.volume(); i++) {
            if (t.code(i) < Tracker.MATCHED) continue;
            long pos = t.worldPos(i);
            Instance<S> owner = claims.get(pos);
            if (owner != null && owner != inst) {
                S s = world.get(pos);
                if (s != null && world.exclusive(pos, s)) {
                    markDirty(pos);
                    return;
                }
            }
        }
        unindex(inst);
        inst.tracker = t;
        inst.candidates = null;
        inst.ready = null;
        inst.rescan = false;
        inst.state = Instance.State.FORMED;
        inst.trusted = true;
        inst.fingerprint = t.fingerprint;
        for (int i = 0; i < t.field.volume(); i++) {
            if (t.code(i) < Tracker.MATCHED) continue;
            long pos = t.worldPos(i);
            S s = world.get(pos);
            if (s != null) updateClaim(inst, pos, s, true);
        }
        t.consumeMembersChanged();
        inst.savedVars.clear();
        for (String name : t.variableNames()) {
            String v = t.variable(name);
            if (v != null) inst.savedVars.put(name, v);
        }
        index(inst, new int[]{t.minX, t.minY, t.minZ, t.maxX, t.maxY, t.maxZ});
        event(inst, l -> l.formed(inst));
        onSave.run();
    }

    private void remove(Instance<S> inst) {
        inst.removed = true;
        unindex(inst);
        byAnchor.remove(inst.anchor);
        for (long pos : inst.claimed) {
            claims.remove(pos);
            markDirty(pos);
        }
        inst.claimed.clear();
        if (inst.state != Instance.State.PENDING) event(inst, l -> l.removed(inst));
        onSave.run();
    }

    private void event(Instance<S> inst, Consumer<StructureListener<S>> call) {
        events.add(() -> {
            call.accept(inst.def.listener());
            for (StructureListener<S> o : observers) call.accept(o);
        });
    }

    private void fireEvents() {
        while (!events.isEmpty()) {
            List<Runnable> list = new ArrayList<>(events);
            events.clear();
            list.forEach(Runnable::run);
        }
    }

    // ---- chunk index

    private void index(Instance<S> inst, int[] box) {
        inst.bounds = box;
        forChunks(box, c -> index.computeIfAbsent(c, k -> new ArrayList<>()).add(inst));
    }

    private void unindex(Instance<S> inst) {
        if (inst.bounds == null) return;
        forChunks(inst.bounds, c -> {
            List<Instance<S>> list = index.get(c);
            if (list == null) return;
            list.remove(inst);
            if (list.isEmpty()) index.remove(c);
        });
        inst.bounds = null;
    }

    private static void forChunks(int[] box, Consumer<Long> action) {
        if (box == null) return;
        for (int cx = box[0] >> 4; cx <= box[3] >> 4; cx++) {
            for (int cz = box[2] >> 4; cz <= box[5] >> 4; cz++) action.accept(Pos.chunk(cx, cz));
        }
    }

    private static boolean inBounds(int[] box, long pos) {
        int x = Pos.x(pos), y = Pos.y(pos), z = Pos.z(pos);
        return box != null && x >= box[0] && x <= box[3] && y >= box[1] && y <= box[4] && z >= box[2] && z <= box[5];
    }

    private static int[] union(int[] a, int[] b) {
        if (a == null) return b.clone();
        return new int[]{Math.min(a[0], b[0]), Math.min(a[1], b[1]), Math.min(a[2], b[2]),
                Math.max(a[3], b[3]), Math.max(a[4], b[4]), Math.max(a[5], b[5])};
    }
}
