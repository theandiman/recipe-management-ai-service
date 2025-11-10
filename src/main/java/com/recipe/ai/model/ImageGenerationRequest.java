package com.recipe.ai.model;

/**
 * Request DTO for image generation endpoint.
 * Can accept either a simple prompt or a full recipe context for richer image generation.
 */
public class ImageGenerationRequest {
    private String prompt;
    private RecipeDTO recipe; // Optional: full recipe context for better image generation

    public ImageGenerationRequest() {}

    // Getters and setters
    public String getPrompt() { return prompt; }
    public void setPrompt(String prompt) { this.prompt = prompt; }
    
    public RecipeDTO getRecipe() { return recipe; }
    public void setRecipe(RecipeDTO recipe) { this.recipe = recipe; }
}
