package com.lumina.lmb.core;

import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * One structure in the world, found from its controller. While pending (not formed yet) the engine
 * keeps every placement the controller could be part of; once formed it keeps the one that matched.
 */
public final class Instance<S> {
    public enum State {
        /** The controller is placed; the structure around it is not complete. */
        PENDING,
        FORMED,
        /** Formed once, now broken beyond its tolerance; repairs bring it back. */
        DAMAGED
    }

    final Definition<S> def;
    final long anchor;
    State state = State.PENDING;

    /** FORMED / DAMAGED: the placement that formed. */
    Tracker<S> tracker;
    /** PENDING: every candidate placement, tracked cell by cell; null when there are too many (see {@link #rescan}). */
    List<Tracker<S>> candidates;
    /** PENDING: a complete candidate, waiting for a manual form (or found by a rescan). */
    Tracker<S> ready;
    /** PENDING without tracked candidates: something changed in range, look again (at most once a second). */
    boolean rescan;
    long nextScan;

    /** False after a definition change, until every cell has been checked under the new definition. */
    boolean trusted = true;
    /** Of the definition version this structure was last verified under. */
    long fingerprint;
    /** Variable values as last seen, for while their cells are unloaded (and across restarts). */
    final Map<String, String> savedVars = new LinkedHashMap<>();
    final Set<Long> claimed = new HashSet<>();
    int[] bounds;
    boolean removed;

    Instance(Definition<S> def, long anchor) {
        this.def = def;
        this.anchor = anchor;
    }

    public Definition<S> definition() {
        return def;
    }

    public long anchor() {
        return anchor;
    }

    public State state() {
        return state;
    }

    public boolean isFormed() {
        return state == State.FORMED;
    }

    /** False once the controller is gone. */
    public boolean exists() {
        return !removed;
    }

    /** Where its cells are; null while pending. */
    public Placement<S> placement() {
        return tracker;
    }

    /** The turn it was built in; null while pending. */
    public Transform transform() {
        return tracker == null ? null : tracker.transform;
    }

    /** Parameter values (sizes); null while pending. */
    public int[] params() {
        return tracker == null ? null : tracker.params.clone();
    }

    /** A pending structure that is complete but forms by hand. */
    public boolean readyToForm() {
        return state == State.PENDING && ready != null;
    }

    /** A variable read from the world (coil tier); null while pending or when no cell has it. */
    public String variable(String name) {
        if (tracker == null) return null;
        String v = tracker.variable(name);
        return v != null ? v : savedVars.get(name);
    }

    /** Positions of every member of a kind ("every input hatch"); empty while pending. */
    public Set<Long> members(String kind) {
        return tracker == null ? Set.of() : tracker.members(kind);
    }

    public int count(String kind) {
        return tracker == null ? 0 : tracker.kindCount(kind);
    }

    /** Matched blocks by kind; empty while pending. */
    public Map<String, Integer> counts() {
        return tracker == null ? Map.of() : tracker.kindCounts();
    }

    /** Wrong cells by role name ("wall is missing 3"); empty while pending. */
    public Map<String, Integer> missing() {
        return tracker == null ? Map.of() : tracker.missingByRole();
    }

    /** Cells not checked since the last restart because their chunk has not loaded. */
    public int unseenCells() {
        return tracker == null ? 0 : tracker.unseen();
    }
}
