package com.recipe.ai.model;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

/**
 * Units system enumeration for recipe generation.
 */
public enum Units {
    METRIC,
    IMPERIAL;

    @JsonCreator
    public static Units fromString(String s) {
        if (s == null) return METRIC;
        String v = s.trim().toUpperCase();
        try {
            return Units.valueOf(v);
        } catch (IllegalArgumentException e) {
            // Default to METRIC if invalid value
            return METRIC;
        }
    }

    @JsonValue
    public String toValue() {
        return this.name();
    }
}
