package com.lumina.lmb.core;

/** What a machine hears about its structure. Called on the server thread at the end of the tick. */
public interface StructureListener<S> {
    /** Built completely (or formed by hand, for a definition that forms manually). */
    default void formed(Instance<S> structure) {}

    /** More cells wrong than a role tolerates, a count limit broken or a variable disagreeing. */
    default void damaged(Instance<S> structure) {}

    /** Back within tolerance after being damaged. */
    default void recovered(Instance<S> structure) {}

    /** The controller is gone; the record is deleted. Only for structures that had formed. */
    default void removed(Instance<S> structure) {}

    /** A formed structure still holds, but its members changed (an input hatch swapped for casing). */
    default void membersChanged(Instance<S> structure) {}
}
