package com.recipe.ai.model;

import java.util.Collections;
import java.util.List;

/**
 * Request DTO for POST /api/recipes/normalize-ingredients.
 * Callers send the current ingredient lines for ambiguity detection and normalization.
 */
public class IngredientNormalizationRequest {

    private List<String> ingredients;
    private String recipeName;

    public IngredientNormalizationRequest() {}

    public List<String> getIngredients() {
        return ingredients == null ? null : Collections.unmodifiableList(ingredients);
    }
    public void setIngredients(List<String> ingredients) { this.ingredients = ingredients; }

    public String getRecipeName() { return recipeName; }
    public void setRecipeName(String recipeName) { this.recipeName = recipeName; }
}
