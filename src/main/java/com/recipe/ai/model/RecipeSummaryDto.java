package com.recipe.ai.model;

import java.util.List;

public class RecipeSummaryDto {
    private String id;
    private String recipeName;
    private String description;
    private List<String> tags;
    private List<String> ingredients;
    private Integer prepTimeMinutes;
    private Integer calories;

    public RecipeSummaryDto() {}

    public RecipeSummaryDto(String id, String recipeName, String description, List<String> tags, List<String> ingredients, Integer prepTimeMinutes) {
        this(id, recipeName, description, tags, ingredients, prepTimeMinutes, null);
    }

    public RecipeSummaryDto(String id, String recipeName, String description, List<String> tags, List<String> ingredients, Integer prepTimeMinutes, Integer calories) {
        this.id = id;
        this.recipeName = recipeName;
        this.description = description;
        this.tags = tags;
        this.ingredients = ingredients;
        this.prepTimeMinutes = prepTimeMinutes;
        this.calories = calories;
    }

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getRecipeName() { return recipeName; }
    public void setRecipeName(String recipeName) { this.recipeName = recipeName; }

    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }

    public List<String> getTags() { return tags; }
    public void setTags(List<String> tags) { this.tags = tags; }

    public List<String> getIngredients() { return ingredients; }
    public void setIngredients(List<String> ingredients) { this.ingredients = ingredients; }

    public Integer getPrepTimeMinutes() { return prepTimeMinutes; }
    public void setPrepTimeMinutes(Integer prepTimeMinutes) { this.prepTimeMinutes = prepTimeMinutes; }

    public Integer getCalories() { return calories; }
    public void setCalories(Integer calories) { this.calories = calories; }
}
