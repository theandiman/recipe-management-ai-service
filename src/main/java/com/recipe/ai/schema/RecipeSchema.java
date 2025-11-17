package com.recipe.ai.schema;

import com.recipe.shared.schema.JsonSchema;

/**
 * Deprecated compatibility wrapper. The shared library now provides schema generation.
 */
@Deprecated
public final class RecipeSchema {
    private RecipeSchema() {}

    public static JsonSchema getSchema() {
        return com.recipe.shared.schema.RecipeSchema.getSchema();
    }
}
