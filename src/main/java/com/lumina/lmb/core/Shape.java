package com.lumina.lmb.core;

import java.util.List;
import java.util.function.Function;

/**
 * A structure's role field, possibly depending on parameters (a tank from 3×3×3 to 9×9×9 has one
 * parameter, its size). The engine finds the parameters from the world by trying every value.
 */
public interface Shape<S> {
    /** A parameter runs from {@code min} to {@code max} in steps of {@code step} (sizes 3, 5, 7, 9). */
    record Param(String name, int min, int max, int step) {
        public Param {
            if (step <= 0 || max < min) throw new IllegalArgumentException(name + ": " + min + ".." + max + " step " + step);
        }

        public int count() {
            return (max - min) / step + 1;
        }
    }

    List<Param> params();

    /** {@code values} has one entry per parameter, each a valid value. */
    RoleField<S> build(int[] values);

    static <S> Shape<S> fixed(RoleField<S> field) {
        return of(List.of(), v -> field);
    }

    static <S> Shape<S> of(List<Param> params, Function<int[], RoleField<S>> build) {
        List<Param> copy = List.copyOf(params);
        return new Shape<>() {
            @Override
            public List<Param> params() {
                return copy;
            }

            @Override
            public RoleField<S> build(int[] values) {
                return build.apply(values);
            }
        };
    }
}
