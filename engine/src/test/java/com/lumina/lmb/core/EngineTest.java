package com.lumina.lmb.core;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The engine over a fake world of characters: '.' air, 'C' controller, '#' casing, 'H' input hatch
 * (shareable), 'M' maintenance hatch (exclusive), 'a'/'b' casing of two tiers.
 */
class EngineTest {
    static final Role<Character> CONTROLLER = Role.of("controller", c -> c == 'C' ? Match.of("controller") : null);
    static final Role<Character> SHELL = Role.of("shell", c -> switch (c) {
        case '#' -> Match.of("casing");
        case 'H' -> Match.of("hatch");
        case 'M' -> Match.of("maint");
        default -> null;
    });
    static final Role<Character> INSIDE = Role.of("inside", c -> c == '.' ? Match.of("air") : null);
    static final Role<Character> TIERED_SHELL = Role.of("shell",
            ((Matcher<Character>) c -> c == 'a' || c == 'b' ? Match.of("casing") : null).withVar("tier", String::valueOf));

    /** A hollow n-cube with the controller in the middle of its z = 0 face. */
    static RoleField<Character> cube(int n, Role<Character> shell) {
        RoleField.Builder<Character> b = RoleField.builder(n, n, n);
        for (int y = 0; y < n; y++) {
            for (int z = 0; z < n; z++) {
                for (int x = 0; x < n; x++) {
                    boolean edge = x == 0 || y == 0 || z == 0 || x == n - 1 || y == n - 1 || z == n - 1;
                    b.set(x, y, z, edge ? shell : INSIDE);
                }
            }
        }
        int c = n / 2;
        return b.set(c, c, 0, CONTROLLER).anchor(c, c, 0).build();
    }

    static final class World implements WorldView<Character> {
        final Map<Long, Character> blocks = new HashMap<>();
        final Set<Long> unloaded = new HashSet<>();
        final Set<Character> exclusive = new HashSet<>(Set.of('M'));
        final Map<String, Definition<Character>> defs = new LinkedHashMap<>();
        Engine<Character> engine = new Engine<>(this, defs, () -> {});

        @Override
        public Character get(long pos) {
            return unloaded.contains(Pos.chunkOf(pos)) ? null : blocks.getOrDefault(pos, '.');
        }

        @Override
        public boolean isLoaded(int chunkX, int chunkZ) {
            return !unloaded.contains(Pos.chunk(chunkX, chunkZ));
        }

        @Override
        public boolean exclusive(long pos, Character state) {
            return exclusive.contains(state);
        }

        void set(long pos, char c) {
            blocks.put(pos, c);
            engine.blockChanged(pos, c);
        }

        void set(int x, int y, int z, char c) {
            set(Pos.pack(x, y, z), c);
        }

        /** Builds a field (unturned) with its anchor at {@code anchor}; the controller goes last unless asked. */
        void build(RoleField<Character> f, long anchor, char shell, boolean controllerFirst) {
            if (controllerFirst) set(anchor, 'C');
            for (int i = 0; i < f.volume(); i++) {
                Role<Character> r = f.role(i);
                if (r == null || r == CONTROLLER) continue;
                long pos = Pos.pack(Pos.x(anchor) + f.x(i) - f.anchorX(), Pos.y(anchor) + f.y(i) - f.anchorY(),
                        Pos.z(anchor) + f.z(i) - f.anchorZ());
                set(pos, r == INSIDE ? '.' : shell);
            }
            if (!controllerFirst) set(anchor, 'C');
        }

        void tick() {
            engine.tick();
        }
    }

    static final class Log implements StructureListener<Character> {
        final List<String> events = new ArrayList<>();

        @Override
        public void formed(Instance<Character> s) {
            events.add("formed");
        }

        @Override
        public void damaged(Instance<Character> s) {
            events.add("damaged");
        }

        @Override
        public void recovered(Instance<Character> s) {
            events.add("recovered");
        }

        @Override
        public void removed(Instance<Character> s) {
            events.add("removed");
        }

        @Override
        public void membersChanged(Instance<Character> s) {
            events.add("members");
        }
    }

    static Definition<Character> tank(World w, Log log, Role<Character> shell) {
        Definition<Character> d = Definition.builder("test:tank", Shape.fixed(cube(3, shell))).listener(log).build();
        w.defs.put(d.id(), d);
        return d;
    }

    static final long A = Pos.pack(1, 1, 0);

    // ---- transforms and orientations

    @Test
    void transformGroups() {
        assertEquals(4, Transform.YAW.size());
        assertEquals(8, Transform.YAW_MIRROR.size());
        assertEquals(24, Transform.ROTATIONS.size());
        assertEquals(48, Transform.ALL_48.size());
        for (Transform t : Transform.ALL_48) assertEquals(Transform.IDENTITY, t.then(t.inverse()));
        assertTrue(Transform.ROTATIONS.stream().allMatch(Transform::isRotation));
        assertFalse(Transform.MIRROR_X.isRotation());
        Transform r = Transform.ROTATE_Y;
        assertEquals(Transform.IDENTITY, r.then(r).then(r).then(r));
        // (1, 0, 0) turns to (0, 0, 1)
        assertEquals(0, r.x(1, 0, 0));
        assertEquals(1, r.z(1, 0, 0));
    }

    @Test
    void orientationsSkipLookalikes() {
        RoleField<Character> faceAnchor = cube(3, SHELL);
        assertEquals(4, Definition.builder("t:a", Shape.fixed(faceAnchor)).build().orientations(faceAnchor).size());
        // turned about the axis through the controller it looks the same: 24 / 4
        assertEquals(6, Definition.builder("t:b", Shape.fixed(faceAnchor)).transforms(Transform.ROTATIONS).build()
                .orientations(faceAnchor).size());
        RoleField<Character> centred = RoleField.<Character>builder(3, 3, 3).fill(0, 0, 0, 2, 2, 2, SHELL)
                .set(1, 1, 1, CONTROLLER).anchor(1, 1, 1).build();
        assertEquals(1, Definition.builder("t:c", Shape.fixed(centred)).build().orientations(centred).size());
    }

    @Test
    void layersBuildTheSameField() {
        RoleField<Character> f = RoleField.layers(Map.of('#', SHELL, '.', INSIDE, 'C', CONTROLLER), 'C',
                new String[]{"###", "###", "###"},
                new String[]{"#C#", "#.#", "###"},
                new String[]{"###", "###", "###"});
        RoleField<Character> c = cube(3, SHELL);
        assertEquals(c.count(), f.count());
        for (int i = 0; i < c.volume(); i++) assertEquals(c.role(i), f.role(i));
        assertEquals(1, f.anchorX());
        assertEquals(0, f.anchorZ());
    }

    // ---- forming

    @Test
    void formsWhenTheLastBlockGoesIn() {
        World w = new World();
        Log log = new Log();
        tank(w, log, SHELL);
        w.build(cube(3, SHELL), A, '#', true);
        w.set(0, 0, 0, '.');
        w.tick();
        Instance<Character> s = w.engine.at(A);
        assertEquals(Instance.State.PENDING, s.state());
        w.set(0, 0, 0, '#');
        w.tick();
        assertEquals(Instance.State.FORMED, s.state());
        assertEquals(List.of("formed"), log.events);
        assertEquals(Transform.IDENTITY, s.transform());
    }

    @Test
    void formsWhenTheControllerGoesInLast() {
        World w = new World();
        Log log = new Log();
        tank(w, log, SHELL);
        w.build(cube(3, SHELL), A, '#', false);
        w.tick();
        assertEquals(Instance.State.FORMED, w.engine.at(A).state());
    }

    @Test
    void formsTurned() {
        World w = new World();
        tank(w, new Log(), SHELL);
        // the cube behind the controller, turned to face the other way: z from 0 down to -2
        for (int y = 0; y < 3; y++) {
            for (int z = -2; z <= 0; z++) {
                for (int x = 0; x < 3; x++) w.set(x, y, z, x == 1 && y == 1 && z == -1 ? '.' : '#');
            }
        }
        w.set(A, 'C');
        w.tick();
        Instance<Character> s = w.engine.at(A);
        assertEquals(Instance.State.FORMED, s.state());
        assertTrue(s.transform() != Transform.IDENTITY);
    }

    @Test
    void manualFormWaitsForTryForm() {
        World w = new World();
        Definition<Character> d = Definition.builder("test:manual", Shape.fixed(cube(3, SHELL))).manualForm().build();
        w.defs.put(d.id(), d);
        w.build(cube(3, SHELL), A, '#', false);
        w.tick();
        Instance<Character> s = w.engine.at(A);
        assertEquals(Instance.State.PENDING, s.state());
        assertTrue(s.readyToForm());
        assertTrue(w.engine.tryForm(A));
        assertEquals(Instance.State.FORMED, s.state());
    }

    // ---- damage, tolerance, removal

    @Test
    void toleranceKeepsItRunningButFormingNeedsEverything() {
        World w = new World();
        Log log = new Log();
        tank(w, log, SHELL.tolerance(1));
        w.build(cube(3, SHELL), A, '#', false);
        w.tick();
        Instance<Character> s = w.engine.at(A);
        w.set(0, 0, 0, '.');
        w.tick();
        assertEquals(Instance.State.FORMED, s.state());
        assertEquals(Map.of("shell", 1), s.missing());
        w.set(2, 2, 2, '.');
        w.tick();
        assertEquals(Instance.State.DAMAGED, s.state());
        w.set(2, 2, 2, '#');
        w.tick();
        assertEquals(Instance.State.FORMED, s.state());
        // the first missing block is within tolerance: still formed, one casing fewer
        assertEquals(List.of("formed", "members", "damaged", "recovered"), log.events);

        // a new one with one block missing does not form, tolerance or not
        long b = Pos.pack(11, 1, 0);
        w.build(cube(3, SHELL), b, '#', false);
        w.set(10, 0, 0, '.');
        w.tick();
        assertEquals(Instance.State.PENDING, w.engine.at(b).state());
    }

    @Test
    void removingTheControllerDeletesTheRecord() {
        World w = new World();
        Log log = new Log();
        tank(w, log, SHELL);
        w.build(cube(3, SHELL), A, '#', false);
        w.tick();
        w.set(A, '.');
        w.tick();
        assertNull(w.engine.at(A));
        assertEquals(List.of("formed", "removed"), log.events);
        assertTrue(w.engine.save().isEmpty());
    }

    // ---- variables, limits, members

    @Test
    void variablesMustAgree() {
        World w = new World();
        Log log = new Log();
        tank(w, log, TIERED_SHELL);
        w.build(cube(3, TIERED_SHELL), A, 'a', false);
        w.tick();
        Instance<Character> s = w.engine.at(A);
        assertEquals(Instance.State.FORMED, s.state());
        assertEquals("a", s.variable("tier"));
        w.set(0, 0, 0, 'b');
        w.tick();
        assertEquals(Instance.State.DAMAGED, s.state());
        assertTrue(w.engine.problems(s, 10).stream().anyMatch(p -> p.type() == Problem.Type.VARIABLE_CONFLICT));
        w.set(0, 0, 0, 'a');
        w.tick();
        assertEquals(Instance.State.FORMED, s.state());
    }

    @Test
    void countLimits() {
        World w = new World();
        Definition<Character> d = Definition.builder("test:limited", Shape.fixed(cube(3, SHELL)))
                .limit("maint", 0, 1).limit("hatch", 1, 4).build();
        w.defs.put(d.id(), d);
        w.build(cube(3, SHELL), A, '#', false);
        w.set(0, 0, 0, 'M');
        w.set(2, 0, 0, 'M');
        w.tick();
        Instance<Character> s = w.engine.at(A);
        assertEquals(Instance.State.PENDING, s.state());
        List<Problem> problems = w.engine.problems(s, 10);
        assertTrue(problems.stream().anyMatch(p -> p.type() == Problem.Type.TOO_MANY && p.role().equals("maint")));
        assertTrue(problems.stream().anyMatch(p -> p.type() == Problem.Type.TOO_FEW && p.role().equals("hatch")));
        w.set(2, 0, 0, 'H');
        w.tick();
        assertEquals(Instance.State.FORMED, s.state());
        assertEquals(Set.of(Pos.pack(2, 0, 0)), s.members("hatch"));
    }

    @Test
    void membersChangedWhileFormed() {
        World w = new World();
        Log log = new Log();
        tank(w, log, SHELL);
        w.build(cube(3, SHELL), A, '#', false);
        w.tick();
        Instance<Character> s = w.engine.at(A);
        w.set(0, 2, 2, 'H');
        w.tick();
        assertEquals(List.of("formed", "members"), log.events);
        assertEquals(Set.of(Pos.pack(0, 2, 2)), s.members("hatch"));
        assertEquals(1, s.count("hatch"));
    }

    // ---- sharing

    @Test
    void exclusiveBlocksBelongToOneStructure() {
        World w = new World();
        tank(w, new Log(), SHELL);
        RoleField<Character> f = cube(3, SHELL);
        long a = Pos.pack(1, 1, 0), b = Pos.pack(3, 1, 0);   // the wall at x = 2 is shared
        w.build(f, a, '#', false);
        w.set(2, 1, 1, 'M');
        w.tick();
        assertEquals(Instance.State.FORMED, w.engine.at(a).state());
        w.build(f, b, '#', false);
        w.set(2, 1, 1, 'M');
        w.tick();
        Instance<Character> second = w.engine.at(b);
        assertEquals(Instance.State.PENDING, second.state());
        assertTrue(w.engine.problems(second, 10).stream().anyMatch(p -> p.type() == Problem.Type.OWNED_BY_OTHER));
        // the first one goes away: the hatch is free
        w.set(a, '.');
        w.tick();
        w.tick();
        assertEquals(Instance.State.FORMED, second.state());
    }

    @Test
    void shareableBlocksBelongToBoth() {
        World w = new World();
        tank(w, new Log(), SHELL);
        RoleField<Character> f = cube(3, SHELL);
        long a = Pos.pack(1, 1, 0), b = Pos.pack(3, 1, 0);
        w.build(f, a, '#', false);
        w.build(f, b, '#', false);
        w.set(2, 1, 1, 'H');
        w.tick();
        assertEquals(Instance.State.FORMED, w.engine.at(a).state());
        assertEquals(Instance.State.FORMED, w.engine.at(b).state());
        assertEquals(2, w.engine.owners(Pos.pack(2, 1, 1)).size());
    }

    // ---- unloaded chunks and saving

    @Test
    void unseenCellsKeepAPendingStructureFromForming() {
        World w = new World();
        tank(w, new Log(), SHELL);
        long anchor = Pos.pack(15, 1, 0);   // the cube spans x 14..16: chunks 0 and 1
        long chunk1 = Pos.chunk(1, 0);
        w.build(cube(3, SHELL), anchor, '#', true);
        // as if the x = 16 blocks were there all along but their chunk is not loaded
        w.engine = new Engine<>(w, w.defs, () -> {});
        w.unloaded.add(chunk1);
        w.engine.blockChanged(anchor, 'C');
        w.tick();
        Instance<Character> s = w.engine.at(anchor);
        assertEquals(Instance.State.PENDING, s.state());
        assertTrue(w.engine.problems(s, 10).stream().anyMatch(p -> p.type() == Problem.Type.NOT_LOADED));
        w.unloaded.remove(chunk1);
        w.engine.chunkLoaded(1, 0);
        w.tick();
        assertEquals(Instance.State.FORMED, s.state());
        // unloading does not change anything
        w.unloaded.add(chunk1);
        w.tick();
        assertEquals(Instance.State.FORMED, s.state());
    }

    @Test
    void savedStructuresTrustUnseenCellsUntilTheirChunkLoads() {
        World w = new World();
        Log log = new Log();
        tank(w, log, SHELL);
        w.build(cube(3, SHELL), A, '#', false);
        long pending = Pos.pack(21, 1, 0);
        w.set(pending, 'C');
        w.tick();
        List<Record> saved = w.engine.save();
        assertEquals(2, saved.size());

        // restart with every chunk unloaded; a block edited "offline"
        w.unloaded.add(Pos.chunk(0, 0));
        w.unloaded.add(Pos.chunk(1, 0));
        w.blocks.put(Pos.pack(0, 0, 0), '.');
        w.engine = new Engine<>(w, w.defs, () -> {});
        w.engine.restore(saved);
        w.tick();
        Instance<Character> s = w.engine.at(A);
        assertEquals(Instance.State.FORMED, s.state());
        assertEquals(Instance.State.PENDING, w.engine.at(pending).state());
        w.unloaded.clear();
        w.engine.chunkLoaded(0, 0);
        w.engine.chunkLoaded(1, 0);
        w.tick();
        assertEquals(Instance.State.DAMAGED, s.state());
    }

    @Test
    void aChangedDefinitionChecksAgain() {
        World w = new World();
        tank(w, new Log(), SHELL);
        w.build(cube(3, SHELL), A, '#', false);
        w.tick();
        List<Record> saved = w.engine.save();

        // version 1: damaged until every cell has been checked
        Log log = new Log();
        Definition<Character> v1 = Definition.builder("test:tank", Shape.fixed(cube(3, SHELL))).version(1).listener(log).build();
        w.defs.put(v1.id(), v1);
        w.unloaded.add(Pos.chunk(0, 0));
        w.engine = new Engine<>(w, w.defs, () -> {});
        w.engine.restore(saved);
        w.tick();
        Instance<Character> s = w.engine.at(A);
        assertEquals(Instance.State.DAMAGED, s.state());
        w.unloaded.clear();
        w.engine.chunkLoaded(0, 0);
        w.tick();
        assertEquals(Instance.State.FORMED, s.state());
        assertEquals(List.of("recovered"), log.events);
        assertEquals(v1.fingerprint(), w.engine.save().get(0).fingerprint());

        // version 1 listing version 0 as legacy: keeps running at once
        Definition<Character> v1legacy = Definition.builder("test:tank", Shape.fixed(cube(3, SHELL))).version(1)
                .legacy(0, Shape.fixed(cube(3, SHELL))).build();
        w.defs.put(v1legacy.id(), v1legacy);
        w.unloaded.add(Pos.chunk(0, 0));
        w.engine = new Engine<>(w, w.defs, () -> {});
        w.engine.restore(saved);
        w.tick();
        assertEquals(Instance.State.FORMED, w.engine.at(A).state());
    }

    @Test
    void unknownDefinitionsAreKeptAsTheyWere() {
        World w = new World();
        tank(w, new Log(), SHELL);
        w.build(cube(3, SHELL), A, '#', false);
        w.tick();
        List<Record> saved = w.engine.save();
        w.defs.clear();
        w.engine = new Engine<>(w, w.defs, () -> {});
        w.engine.restore(saved);
        w.tick();
        assertNull(w.engine.at(A));
        assertEquals(saved, w.engine.save());
    }

    // ---- sizes

    static Definition<Character> sized(World w) {
        Definition<Character> d = Definition.builder("test:sized",
                Shape.of(List.of(new Shape.Param("size", 3, 7, 2)), v -> cube(v[0], SHELL))).build();
        w.defs.put(d.id(), d);
        return d;
    }

    @Test
    void findsTheSize() {
        World w = new World();
        sized(w);
        long anchor = Pos.pack(2, 2, 0);
        w.build(cube(5, SHELL), anchor, '#', false);
        w.tick();
        Instance<Character> s = w.engine.at(anchor);
        assertEquals(Instance.State.FORMED, s.state());
        assertArrayEquals(new int[]{5}, s.params());
    }

    @Test
    void findsTheSizeWithoutTracking() {
        World w = new World();
        w.engine.trackBudget = 1;
        sized(w);
        long anchor = Pos.pack(2, 2, 0);
        w.set(anchor, 'C');
        w.tick();
        Instance<Character> s = w.engine.at(anchor);
        assertEquals(Instance.State.PENDING, s.state());
        w.build(cube(5, SHELL), anchor, '#', true);
        for (int i = 0; i < Engine.RESCAN_INTERVAL + 1 && s.state() == Instance.State.PENDING; i++) w.tick();
        assertEquals(Instance.State.FORMED, s.state());
        assertArrayEquals(new int[]{5}, s.params());
        assertNotNull(s.transform());
    }
}
