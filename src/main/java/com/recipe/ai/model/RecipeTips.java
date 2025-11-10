package com.recipe.ai.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.recipe.ai.schema.JsonSchema;

import java.util.List;
import java.util.Map;

import static com.recipe.ai.schema.GeminiSchemaBuilder.*;

/**
 * Recipe tips including substitutions, make-ahead instructions, and storage advice.
 */
public class RecipeTips {
    
    @JsonProperty("substitutions")
    private List<String> substitutions;
    
    @JsonProperty("makeAhead")
    private String makeAhead;
    
    @JsonProperty("storage")
    private String storage;
    
    @JsonProperty("reheating")
    private String reheating;
    
    @JsonProperty("variations")
    private List<String> variations;

    public RecipeTips() {}

    // Getters and setters
    public List<String> getSubstitutions() { return substitutions; }
    public void setSubstitutions(List<String> substitutions) { this.substitutions = substitutions; }
    public String getMakeAhead() { return makeAhead; }
    public void setMakeAhead(String makeAhead) { this.makeAhead = makeAhead; }
    public String getStorage() { return storage; }
    public void setStorage(String storage) { this.storage = storage; }
    public String getReheating() { return reheating; }
    public void setReheating(String reheating) { this.reheating = reheating; }
    public List<String> getVariations() { return variations; }
    public void setVariations(List<String> variations) { this.variations = variations; }

    /**
     * Generates the schema for recipe tips.
     * 
     * @return JsonSchema object for recipe tips
     */
    public static JsonSchema getSchema() {
        return object()
            .property("substitutions", array()
                .description("Common ingredient substitutions (e.g., 'Greek yogurt can replace sour cream').")
                .items(string()))
            .property("makeAhead", string()
                .description("Instructions for preparing the recipe in advance."))
            .property("storage", string()
                .description("How to store leftovers and for how long."))
            .property("reheating", string()
                .description("Best method to reheat the dish."))
            .property("variations", array()
                .description("Recipe variations or customization ideas.")
                .items(string()))
            .build();
    }
    
    /**
     * Generates the schema as a Map for embedding in parent schemas.
     * 
     * @return Schema map for recipe tips
     */
    public static Map<String, Object> getSchemaAsMap() {
        return getSchema().asMap();
    }
}
