package com.recipe.ai.model;

import java.util.Collections;
import java.util.List;

/**
 * Response DTO for POST /api/recipes/normalize-ingredients.
 * Contains only ingredients that have normalization suggestions.
 * Clear ingredients are omitted.
 */
public class IngredientNormalizationResponse {

    private final List<IngredientNormalization> normalizations;

    public IngredientNormalizationResponse(List<IngredientNormalization> normalizations) {
        this.normalizations = normalizations == null ? List.of() : Collections.unmodifiableList(normalizations);
    }

    public List<IngredientNormalization> getNormalizations() { return normalizations; }
}
