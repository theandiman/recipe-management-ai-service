package com.recipe.ai.schema;

import com.recipe.ai.model.RecipeDTO;

import java.util.Map;

/**
 * Defines the JSON schema for recipe generation using the Gemini API.
 * This schema ensures structured, consistent recipe output.
 * 
 * @deprecated Use {@link RecipeDTO#getSchema()} directly instead.
 * This class is maintained for backwards compatibility.
 */
@Deprecated
public class RecipeSchema {

    /**
     * Returns the complete recipe schema for use with Gemini API.
     * Delegates to RecipeDTO.getSchema() for the actual schema definition.
     * 
     * @return The recipe schema as a JsonSchema object
     */
    public static JsonSchema getSchema() {
        return RecipeDTO.getSchema();
    }

    /**
     * Returns the complete recipe schema as a Map for backwards compatibility.
     * 
     * @return The recipe schema map
     * @deprecated Use {@link #getSchema()} to get JsonSchema instead.
     */
    @Deprecated
    public static Map<String, Object> getSchemaAsMap() {
        return RecipeDTO.getSchema().asMap();
    }
}
