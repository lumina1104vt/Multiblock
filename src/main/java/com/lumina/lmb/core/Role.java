package com.lumina.lmb.core;

import java.util.Arrays;
import java.util.List;

/**
 * What a cell is for (casing, coil, inside, controller) and which blocks it accepts.
 *
 * @param tolerance   how many cells of this role may be wrong before a formed structure counts as damaged.
 *                    Forming always needs every cell right; tolerance only keeps it running afterwards.
 * @param suggestions what the builder (and the projection) puts here, best first. With "no hatches" the
 *                    builder takes the first one that is not a hatch (see {@link Definition#isHatch}).
 *                    Empty: the builder leaves the cell alone.
 */
public record Role<S>(String name, Matcher<S> matcher, int tolerance, List<S> suggestions) {
    public Role {
        suggestions = List.copyOf(suggestions);
    }

    public static <S> Role<S> of(String name, Matcher<S> matcher) {
        return new Role<>(name, matcher, 0, List.of());
    }

    public Role<S> tolerance(int missing) {
        return new Role<>(name, matcher, missing, suggestions);
    }

    @SafeVarargs
    public final Role<S> places(S... blocks) {
        return new Role<>(name, matcher, tolerance, Arrays.asList(blocks));
    }
}
