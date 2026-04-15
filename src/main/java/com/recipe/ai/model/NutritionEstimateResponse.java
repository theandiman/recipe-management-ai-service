package com.recipe.ai.model;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Response DTO for POST /api/recipes/estimate-nutrition.
 */
public class NutritionEstimateResponse {

    private final NutritionEstimate perServing;
    private final NutritionEstimate wholeRecipe;

    @JsonCreator
    public NutritionEstimateResponse(
            @JsonProperty("perServing") NutritionEstimate perServing,
            @JsonProperty("wholeRecipe") NutritionEstimate wholeRecipe) {
        this.perServing = perServing;
        this.wholeRecipe = wholeRecipe;
    }

    public NutritionEstimate getPerServing() { return perServing; }
    public NutritionEstimate getWholeRecipe() { return wholeRecipe; }
}
