package com.recipe.ai.model;

import com.recipe.shared.model.Recipe;

/**
 * Request DTO for structured recipe modification endpoint.
 */
public class RecipeModificationRequest {
    private Recipe currentRecipe;
    private String instruction;

    public RecipeModificationRequest() {}

    public RecipeModificationRequest(Recipe currentRecipe, String instruction) {
        this.currentRecipe = currentRecipe;
        this.instruction = instruction;
    }

    public Recipe getCurrentRecipe() {
        return currentRecipe;
    }

    public void setCurrentRecipe(Recipe currentRecipe) {
        this.currentRecipe = currentRecipe;
    }

    public String getInstruction() {
        return instruction;
    }

    public void setInstruction(String instruction) {
        this.instruction = instruction;
    }
}
