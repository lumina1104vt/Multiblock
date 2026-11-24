package com.lumina.lmb.core;

/** "At least one energy hatch", "at most one maintenance hatch": counts blocks by {@link Match#kind()}. */
public record CountLimit(String kind, int min, int max) {
}
