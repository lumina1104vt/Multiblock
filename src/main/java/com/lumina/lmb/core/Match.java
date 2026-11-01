package com.lumina.lmb.core;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * What a block counts as in its cell. {@code kind} groups members (the machine asks for "every input
 * hatch") and is what count limits count; {@code vars} are values read from the block that must agree
 * across the whole structure (a blast furnace's coil tier).
 */
public record Match(String kind, Map<String, String> vars) {
    public Match {
        vars = Map.copyOf(vars);
    }

    public static Match of(String kind) {
        return new Match(kind, Map.of());
    }

    public Match with(String var, String value) {
        Map<String, String> v = new LinkedHashMap<>(vars);
        v.put(var, value);
        return new Match(kind, v);
    }
}
