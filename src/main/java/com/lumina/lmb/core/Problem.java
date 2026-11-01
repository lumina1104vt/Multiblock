package com.lumina.lmb.core;

/**
 * One reason a structure is not formed (or is damaged), for the diagnostics screen.
 *
 * @param role   the role of the cell, or the kind / variable name for the whole-structure problems
 * @param pos    the cell, or the controller for the whole-structure problems
 * @param detail extra text: "2 / max 1", "iron, gold", the number of unloaded cells
 */
public record Problem(Type type, String role, long pos, String detail) {
    public enum Type {
        WRONG_BLOCK,
        OWNED_BY_OTHER,
        NOT_LOADED,
        TOO_FEW,
        TOO_MANY,
        VARIABLE_CONFLICT
    }
}
