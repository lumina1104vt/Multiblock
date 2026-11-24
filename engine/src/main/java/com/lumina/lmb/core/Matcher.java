package com.lumina.lmb.core;

import java.util.function.Function;

/** Decides whether a block belongs in a cell. {@code S} is the block-state type (BlockState in game). */
@FunctionalInterface
public interface Matcher<S> {
    /** What {@code state} counts as in this cell, or null when it does not belong there. */
    Match test(S state);

    /** Also reads a variable from the block, e.g. {@code withVar("tier", coil -> tierOf(coil))}. */
    default Matcher<S> withVar(String var, Function<S, String> value) {
        return s -> {
            Match m = test(s);
            return m == null ? null : m.with(var, value.apply(s));
        };
    }

    /** The first option that accepts the block. */
    @SafeVarargs
    static <S> Matcher<S> anyOf(Matcher<S>... options) {
        return s -> {
            for (Matcher<S> o : options) {
                Match m = o.test(s);
                if (m != null) return m;
            }
            return null;
        };
    }
}
