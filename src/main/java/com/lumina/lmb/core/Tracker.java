package com.lumina.lmb.core;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * One placement of a role field (anchor, turn, parameters) and what is currently known about each of its
 * cells. Every cell change adjusts a few counters, so whether the structure holds is known at any time
 * without looking at the other cells: the cost of a block change does not grow with the structure.
 *
 * <p>A cell is unseen (its chunk has not been loaded since this placement was made), wrong, owned by
 * another structure, or matched. A cell keeps its last state while its chunk is unloaded: nothing can
 * change a block in an unloaded chunk during normal play.
 */
final class Tracker<S> extends Placement<S> {
    static final int UNSEEN = 0, WRONG = 1, OWNED = 2, MATCHED = 3;

    /** Of the shape this placement uses (the current one, or a legacy one). */
    final long fingerprint;

    private final int[] codes;
    private final List<Match> matches = new ArrayList<>();
    private final Map<Match, Integer> matchIds = new HashMap<>();
    private int unseen;
    private final int[] missing;   // per palette id: wrong + owned cells
    private final Map<String, Integer> kinds = new HashMap<>();
    private final Map<String, Set<Long>> members = new HashMap<>();
    private final Map<String, Map<String, Integer>> vars = new LinkedHashMap<>();
    private boolean membersChanged;

    Tracker(Definition<S> def, RoleField<S> field, Transform transform, long anchor, int[] params, long fingerprint) {
        super(def, field, transform, anchor, params);
        this.fingerprint = fingerprint;
        this.codes = new int[field.volume()];
        this.unseen = field.count();
        this.missing = new int[field.paletteSize()];
    }

    Match test(int index, S state) {
        return field.role(index).matcher().test(state);
    }

    int code(int index) {
        return codes[index];
    }

    Match match(int index) {
        return codes[index] >= MATCHED ? matches.get(codes[index] - MATCHED) : null;
    }

    /** Records what the cell now holds; false when nothing changed. */
    boolean set(int index, Match m, boolean owned) {
        int code;
        if (m == null) {
            code = WRONG;
        } else if (owned) {
            code = OWNED;
        } else {
            Integer id = matchIds.get(m);
            if (id == null) {
                id = matches.size();
                matches.add(m);
                matchIds.put(m, id);
            }
            code = MATCHED + id;
        }
        int old = codes[index];
        if (code == old) return false;
        long pos = worldPos(index);
        contribute(index, old, pos, -1);
        codes[index] = code;
        contribute(index, code, pos, 1);
        if (!Objects.equals(kindOf(old), kindOf(code))) membersChanged = true;
        return true;
    }

    private String kindOf(int code) {
        return code >= MATCHED ? matches.get(code - MATCHED).kind() : null;
    }

    private void contribute(int index, int code, long pos, int sign) {
        if (code == UNSEEN) {
            unseen += sign;
        } else if (code < MATCHED) {
            missing[field.roleId(index)] += sign;
        } else {
            Match m = matches.get(code - MATCHED);
            if (kinds.merge(m.kind(), sign, Integer::sum) == 0) kinds.remove(m.kind());
            if (sign > 0) {
                members.computeIfAbsent(m.kind(), k -> new LinkedHashSet<>()).add(pos);
            } else {
                Set<Long> s = members.get(m.kind());
                s.remove(pos);
                if (s.isEmpty()) members.remove(m.kind());
            }
            for (Map.Entry<String, String> e : m.vars().entrySet()) {
                Map<String, Integer> values = vars.computeIfAbsent(e.getKey(), k -> new LinkedHashMap<>());
                if (values.merge(e.getValue(), sign, Integer::sum) == 0) values.remove(e.getValue());
                if (values.isEmpty()) vars.remove(e.getKey());
            }
        }
    }

    /**
     * Whether the structure holds. {@code strict}: every cell right (forming); otherwise each role may miss
     * up to its tolerance. {@code trustUnseen}: unseen cells count as they were when last checked (a formed
     * structure after a restart); otherwise an unseen cell means it does not hold.
     */
    boolean holds(boolean strict, boolean trustUnseen) {
        for (int r = 1; r < missing.length; r++) {
            if (missing[r] > (strict ? 0 : field.paletteRole(r).tolerance())) return false;
        }
        for (Map<String, Integer> values : vars.values()) {
            if (values.size() > 1) return false;
        }
        for (CountLimit l : def.limits()) {
            if (kindCount(l.kind()) > l.max()) return false;
        }
        if (unseen > 0) return !strict && trustUnseen;
        for (CountLimit l : def.limits()) {
            if (kindCount(l.kind()) < l.min()) return false;
        }
        return true;
    }

    boolean complete() {
        return holds(true, false);
    }

    int unseen() {
        return unseen;
    }

    int missingTotal() {
        int n = 0;
        for (int m : missing) n += m;
        return n;
    }

    /** Wrong cells by role name. */
    Map<String, Integer> missingByRole() {
        Map<String, Integer> out = new LinkedHashMap<>();
        for (int r = 1; r < missing.length; r++) {
            if (missing[r] > 0) out.merge(field.paletteRole(r).name(), missing[r], Integer::sum);
        }
        return out;
    }

    int kindCount(String kind) {
        return kinds.getOrDefault(kind, 0);
    }

    Map<String, Integer> kindCounts() {
        return Collections.unmodifiableMap(kinds);
    }

    Set<Long> members(String kind) {
        Set<Long> s = members.get(kind);
        return s == null ? Set.of() : Collections.unmodifiableSet(s);
    }

    /** The value every seen cell agrees on, or null when none has it or they disagree. */
    String variable(String name) {
        Map<String, Integer> values = vars.get(name);
        return values != null && values.size() == 1 ? values.keySet().iterator().next() : null;
    }

    Set<String> variableNames() {
        return vars.keySet();
    }

    boolean consumeMembersChanged() {
        boolean c = membersChanged;
        membersChanged = false;
        return c;
    }

    List<Problem> problems(int limit) {
        List<Problem> out = new ArrayList<>();
        for (CountLimit l : def.limits()) {
            int n = kindCount(l.kind());
            if (n > l.max()) out.add(new Problem(Problem.Type.TOO_MANY, l.kind(), anchor, n + " / max " + l.max()));
            else if (unseen == 0 && n < l.min()) out.add(new Problem(Problem.Type.TOO_FEW, l.kind(), anchor, n + " / min " + l.min()));
        }
        for (Map.Entry<String, Map<String, Integer>> e : vars.entrySet()) {
            if (e.getValue().size() > 1) {
                out.add(new Problem(Problem.Type.VARIABLE_CONFLICT, e.getKey(), anchor, String.join(", ", e.getValue().keySet())));
            }
        }
        Map<Long, int[]> unloaded = new LinkedHashMap<>();
        Map<Long, Long> unloadedAt = new HashMap<>();
        for (int i = 0; i < codes.length; i++) {
            if (field.roleId(i) == 0) continue;
            int c = codes[i];
            if (c == UNSEEN) {
                long pos = worldPos(i);
                long chunk = Pos.chunkOf(pos);
                unloaded.computeIfAbsent(chunk, k -> new int[1])[0]++;
                unloadedAt.putIfAbsent(chunk, pos);
            } else if (c < MATCHED && out.size() < limit) {
                out.add(new Problem(c == WRONG ? Problem.Type.WRONG_BLOCK : Problem.Type.OWNED_BY_OTHER,
                        field.role(i).name(), worldPos(i), ""));
            }
        }
        for (Map.Entry<Long, int[]> e : unloaded.entrySet()) {
            out.add(new Problem(Problem.Type.NOT_LOADED, "", unloadedAt.get(e.getKey()), Integer.toString(e.getValue()[0])));
        }
        return out;
    }
}
