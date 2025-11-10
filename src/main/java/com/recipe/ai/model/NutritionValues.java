package com.recipe.ai.model;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.Map;

import static com.recipe.ai.schema.GeminiSchemaBuilder.*;

/**
 * Represents nutritional values for a recipe or serving.
 */
public class NutritionValues {
    
    @JsonProperty("calories")
    private Double calories;
    
    @JsonProperty("protein")
    private Double protein;
    
    @JsonProperty("carbohydrates")
    private Double carbohydrates;
    
    @JsonProperty("fat")
    private Double fat;
    
    @JsonProperty("fiber")
    private Double fiber;
    
    @JsonProperty("sodium")
    private Double sodium;

    public NutritionValues() {}

    // Getters and setters
    public Double getCalories() { return calories; }
    public void setCalories(Double calories) { this.calories = calories; }
    
    public Double getProtein() { return protein; }
    public void setProtein(Double protein) { this.protein = protein; }
    
    public Double getCarbohydrates() { return carbohydrates; }
    public void setCarbohydrates(Double carbohydrates) { this.carbohydrates = carbohydrates; }
    
    public Double getFat() { return fat; }
    public void setFat(Double fat) { this.fat = fat; }
    
    public Double getFiber() { return fiber; }
    public void setFiber(Double fiber) { this.fiber = fiber; }
    
    public Double getSodium() { return sodium; }
    public void setSodium(Double sodium) { this.sodium = sodium; }

    /**
     * Generates the Gemini API schema for nutrition values.
     * 
     * @param description Description for this nutrition values object
     * @return Schema map for nutrition values (for compatibility with property() methods)
     */
    public static Map<String, Object> getSchema(String description) {
        return object()
            .description(description)
            .property("calories", number().description("Calories (kcal)."))
            .property("protein", number().description("Protein (grams)."))
            .property("carbohydrates", number().description("Total carbohydrates (grams)."))
            .property("fat", number().description("Total fat (grams)."))
            .property("fiber", number().description("Dietary fiber (grams)."))
            .property("sodium", number().description("Sodium (milligrams)."))
            .buildAsMap();
    }
}

