package com.recipe.ai.model;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * A single normalization suggestion for one ingredient line.
 */
public class IngredientNormalization {

    private final int index;
    private final String original;
    private final String normalized;
    private final String reason;
    private final double confidence;

    @JsonCreator
    public IngredientNormalization(
            @JsonProperty("index") int index,
            @JsonProperty("original") String original,
            @JsonProperty("normalized") String normalized,
            @JsonProperty("reason") String reason,
            @JsonProperty("confidence") double confidence) {
        this.index = index;
        this.original = original;
        this.normalized = normalized;
        this.reason = reason;
        this.confidence = confidence;
    }

    public int getIndex() { return index; }
    public String getOriginal() { return original; }
    public String getNormalized() { return normalized; }
    public String getReason() { return reason; }
    public double getConfidence() { return confidence; }
}
