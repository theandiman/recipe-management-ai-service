package com.recipe.ai.model;

import java.util.Collections;
import java.util.List;

/**
 * Request DTO for the field suggestion endpoint.
 * Fields that are null or blank are treated as missing/weak and are
 * eligible for AI-generated suggestions.
 */
public class FieldSuggestionRequest {

    private String recipeName;
    private String description;
    private String prepTime;
    private String cookTime;
    private String servings;
    private List<String> tags;
    private List<String> ingredients;
    private List<String> instructions;

    public FieldSuggestionRequest() {}

    public String getRecipeName() { return recipeName; }
    public void setRecipeName(String recipeName) { this.recipeName = recipeName; }

    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }

    public String getPrepTime() { return prepTime; }
    public void setPrepTime(String prepTime) { this.prepTime = prepTime; }

    public String getCookTime() { return cookTime; }
    public void setCookTime(String cookTime) { this.cookTime = cookTime; }

    public String getServings() { return servings; }
    public void setServings(String servings) { this.servings = servings; }

    public List<String> getTags() {
        return tags == null ? null : Collections.unmodifiableList(tags);
    }
    public void setTags(List<String> tags) { this.tags = tags; }

    public List<String> getIngredients() {
        return ingredients == null ? null : Collections.unmodifiableList(ingredients);
    }
    public void setIngredients(List<String> ingredients) { this.ingredients = ingredients; }

    public List<String> getInstructions() {
        return instructions == null ? null : Collections.unmodifiableList(instructions);
    }
    public void setInstructions(List<String> instructions) { this.instructions = instructions; }
}
