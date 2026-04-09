package com.recipe.ai.model;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.Collections;
import java.util.List;

/**
 * Nutrition estimate (per-serving or whole-recipe).
 */
public class NutritionEstimate {

    private final NutrientValue calories;
    private final NutrientValue protein;
    private final NutrientValue carbs;
    private final NutrientValue fat;
    private final NutrientValue fiber;
    private final List<String> warnings;
    private final boolean isPartial;

    @JsonCreator
    public NutritionEstimate(
            @JsonProperty("calories") NutrientValue calories,
            @JsonProperty("protein") NutrientValue protein,
            @JsonProperty("carbs") NutrientValue carbs,
            @JsonProperty("fat") NutrientValue fat,
            @JsonProperty("fiber") NutrientValue fiber,
            @JsonProperty("warnings") List<String> warnings,
            @JsonProperty("isPartial") boolean isPartial) {
        this.calories = calories;
        this.protein = protein;
        this.carbs = carbs;
        this.fat = fat;
        this.fiber = fiber;
        this.warnings = warnings == null ? List.of() : Collections.unmodifiableList(warnings);
        this.isPartial = isPartial;
    }

    public NutrientValue getCalories() { return calories; }
    public NutrientValue getProtein() { return protein; }
    public NutrientValue getCarbs() { return carbs; }
    public NutrientValue getFat() { return fat; }
    public NutrientValue getFiber() { return fiber; }
    public List<String> getWarnings() { return warnings; }
    public boolean isPartial() { return isPartial; }
}
