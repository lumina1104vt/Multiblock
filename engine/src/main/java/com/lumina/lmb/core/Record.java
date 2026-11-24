package com.lumina.lmb.core;

import java.util.Map;

/**
 * What is saved of a structure: its placement, never its cells (they follow from the definition).
 *
 * @param transform −1 and {@code params} empty while pending
 */
public record Record(String definition, long anchor, Instance.State state, int transform, int[] params, long fingerprint,
                     Map<String, String> vars) {
}
