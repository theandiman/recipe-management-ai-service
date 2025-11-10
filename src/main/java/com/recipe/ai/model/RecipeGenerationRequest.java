package com.recipe.ai.model;

import java.util.List;
import java.util.Collections;

/**
 * Request DTO for recipe generation endpoint.
 * Encapsulates all parameters needed to generate a recipe.
 */
public class RecipeGenerationRequest {
    private String prompt;
    private List<String> pantryItems;
    private Units units; // METRIC or IMPERIAL enum
    private List<String> dietaryPreferences;
    private List<String> allergies;
    private Integer maxTotalMinutes;

    public RecipeGenerationRequest() {}

    // Getters and setters
    public String getPrompt() { return prompt; }
    public void setPrompt(String prompt) { this.prompt = prompt; }
    
    public List<String> getPantryItems() { 
        return pantryItems == null ? null : Collections.unmodifiableList(pantryItems); 
    }
    public void setPantryItems(List<String> pantryItems) { this.pantryItems = pantryItems; }
    
    public Units getUnits() { return units; }
    public void setUnits(Units units) { this.units = units; }
    
    public List<String> getDietaryPreferences() { 
        return dietaryPreferences == null ? null : Collections.unmodifiableList(dietaryPreferences); 
    }
    public void setDietaryPreferences(List<String> dietaryPreferences) { this.dietaryPreferences = dietaryPreferences; }
    
    public List<String> getAllergies() { 
        return allergies == null ? null : Collections.unmodifiableList(allergies); 
    }
    public void setAllergies(List<String> allergies) { this.allergies = allergies; }
    
    public Integer getMaxTotalMinutes() { return maxTotalMinutes; }
    public void setMaxTotalMinutes(Integer maxTotalMinutes) { this.maxTotalMinutes = maxTotalMinutes; }
}
