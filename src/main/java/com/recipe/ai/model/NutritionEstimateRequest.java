package com.recipe.ai.model;

import java.util.Collections;
import java.util.List;

/**
 * Request DTO for POST /api/recipes/estimate-nutrition.
 */
public class NutritionEstimateRequest {

    private List<String> ingredients;
    private int servings;
    private String recipeName;

    public NutritionEstimateRequest() {}

    public List<String> getIngredients() {
        return ingredients == null ? null : Collections.unmodifiableList(ingredients);
    }
    public void setIngredients(List<String> ingredients) { this.ingredients = ingredients; }

    public int getServings() { return servings; }
    public void setServings(int servings) { this.servings = servings; }

    public String getRecipeName() { return recipeName; }
    public void setRecipeName(String recipeName) { this.recipeName = recipeName; }
}
