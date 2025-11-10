package com.recipe.ai.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.recipe.ai.schema.JsonSchema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

import java.util.List;
import java.util.Map;

import static com.recipe.ai.schema.GeminiSchemaBuilder.*;

/**
 * Data Transfer Object for recipe generation and display.
 * Annotations define both validation rules and schema requirements.
 */
public class RecipeDTO {
    
    @NotBlank(message = "Recipe name is required")
    @JsonProperty("recipeName")
    private String recipeName;
    
    @JsonProperty("description")
    private String description;
    
    @NotEmpty(message = "Ingredients list cannot be empty")
    @JsonProperty("ingredients")
    private List<String> ingredients;
    
    @NotEmpty(message = "Instructions list cannot be empty")
    @JsonProperty("instructions")
    private List<String> instructions;
    
    @JsonProperty("prepTime")
    private String prepTime;
    
    @JsonProperty("cookTime")
    private String cookTime;
    
    @JsonProperty("estimatedTime")
    private String estimatedTime;
    
    @JsonProperty("estimatedTimeMinutes")
    private Integer estimatedTimeMinutes;
    
    @NotBlank(message = "Servings is required")
    @JsonProperty("servings")
    private String servings;
    
    @JsonProperty("nutritionalInfo")
    private NutritionalInfo nutritionalInfo;
    
    @JsonProperty("imageUrl")
    private String imageUrl;
    
    @JsonProperty("imageGeneration")
    private Map<String, Object> imageGeneration;
    
    @JsonProperty("tips")
    private RecipeTips tips;

    public RecipeDTO() {}

    // Getters and setters
    public String getRecipeName() { return recipeName; }
    public void setRecipeName(String recipeName) { this.recipeName = recipeName; }
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
    public List<String> getIngredients() { return ingredients; }
    public void setIngredients(List<String> ingredients) { this.ingredients = ingredients; }
    public List<String> getInstructions() { return instructions; }
    public void setInstructions(List<String> instructions) { this.instructions = instructions; }
    public String getPrepTime() { return prepTime; }
    public void setPrepTime(String prepTime) { this.prepTime = prepTime; }
    public String getCookTime() { return cookTime; }
    public void setCookTime(String cookTime) { this.cookTime = cookTime; }
    public String getEstimatedTime() { return estimatedTime; }
    public void setEstimatedTime(String estimatedTime) { this.estimatedTime = estimatedTime; }
    public Integer getEstimatedTimeMinutes() { return estimatedTimeMinutes; }
    public void setEstimatedTimeMinutes(Integer estimatedTimeMinutes) { this.estimatedTimeMinutes = estimatedTimeMinutes; }
    public String getServings() { return servings; }
    public void setServings(String servings) { this.servings = servings; }
    public NutritionalInfo getNutritionalInfo() { return nutritionalInfo; }
    public void setNutritionalInfo(NutritionalInfo nutritionalInfo) { this.nutritionalInfo = nutritionalInfo; }
    public String getImageUrl() { return imageUrl; }
    public void setImageUrl(String imageUrl) { this.imageUrl = imageUrl; }
    public Map<String, Object> getImageGeneration() { return imageGeneration; }
    public void setImageGeneration(Map<String, Object> imageGeneration) { this.imageGeneration = imageGeneration; }
    public RecipeTips getTips() { return tips; }
    public void setTips(RecipeTips tips) { this.tips = tips; }

    /**
     * Generates the Gemini API schema for recipe generation.
     * This schema defines the structure that the AI model should follow.
     * 
     * @return JsonSchema object for recipe generation
     */
    public static JsonSchema getSchema() {
        return object()
            .property("recipeName", string()
                .description("The creative name of the recipe."))
            .property("description", string()
                .description("A brief, appealing description of the dish."))
            .property("ingredients", array()
                .description("A list of ingredients with quantities.")
                .items(string()))
            .property("instructions", array()
                .description("Step-by-step instructions for preparation.")
                .items(string()))
            .property("prepTime", string()
                .description("Estimated preparation time (e.g., '15 minutes')."))
            .property("cookTime", string()
                .description("Estimated cooking time (e.g., '20 minutes')."))
            .property("estimatedTime", string()
                .description("Human-readable total time estimate (e.g., '35 minutes' or '1 hour')."))
            .property("estimatedTimeMinutes", number()
                .description("Total estimated time in minutes as an integer."))
            .property("servings", string()
                .description("Number of servings the recipe yields (e.g., '4')."))
            .property("nutritionalInfo", NutritionalInfo.getSchemaAsMap())
            .property("tips", RecipeTips.getSchemaAsMap())
            .required("recipeName", "ingredients", "instructions", "servings")
            .build();
    }
}
