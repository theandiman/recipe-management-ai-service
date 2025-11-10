package com.recipe.ai.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.recipe.ai.schema.JsonSchema;

import java.util.Map;

import static com.recipe.ai.schema.GeminiSchemaBuilder.*;

/**
 * Represents nutritional information for a recipe, including both per-serving and total values.
 */
public class NutritionalInfo {
    
    @JsonProperty("perServing")
    private NutritionValues perServing;
    
    @JsonProperty("total")
    private NutritionValues total;

    public NutritionalInfo() {}

    // Getters and setters
    public NutritionValues getPerServing() { return perServing; }
    public void setPerServing(NutritionValues perServing) { this.perServing = perServing; }
    
    public NutritionValues getTotal() { return total; }
    public void setTotal(NutritionValues total) { this.total = total; }

    /**
     * Generates the Gemini API schema for nutritional information.
     * 
     * @return JsonSchema object for nutritional information
     */
    public static JsonSchema getSchema() {
        return object()
            .description("Nutritional information calculated from ingredients.")
            .property("perServing", NutritionValues.getSchema("Nutritional values per single serving."))
            .property("total", NutritionValues.getSchema("Total nutritional values for entire recipe."))
            .build();
    }

    /**
     * Returns the schema as a Map for compatibility with builder.property() methods.
     * 
     * @return Schema map for nutritional information
     */
    public static Map<String, Object> getSchemaAsMap() {
        return getSchema().asMap();
    }
}

