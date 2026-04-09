package com.recipe.ai.model;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * A single nutrient measurement, optionally estimated by AI.
 */
public class NutrientValue {

    private final double value;
    private final String unit;
    private final boolean estimated;

    @JsonCreator
    public NutrientValue(
            @JsonProperty("value") double value,
            @JsonProperty("unit") String unit,
            @JsonProperty("estimated") boolean estimated) {
        this.value = value;
        this.unit = unit;
        this.estimated = estimated;
    }

    public double getValue() { return value; }
    public String getUnit() { return unit; }
    public boolean isEstimated() { return estimated; }
}
