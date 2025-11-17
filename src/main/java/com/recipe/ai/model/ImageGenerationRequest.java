package com.recipe.ai.model;

import com.recipe.shared.model.Recipe;

/**
 * Request DTO for image generation endpoint.
 * Can accept either a simple prompt or a full recipe context for richer image generation.
 */
public class ImageGenerationRequest {
    private String prompt;
    private Recipe recipe; // Optional: full recipe context for better image generation

    public ImageGenerationRequest() {}

    // Getters and setters
    public String getPrompt() { return prompt; }
    public void setPrompt(String prompt) { this.prompt = prompt; }
    
    public Recipe getRecipe() { return recipe; }
    public void setRecipe(Recipe recipe) { this.recipe = recipe; }
}
