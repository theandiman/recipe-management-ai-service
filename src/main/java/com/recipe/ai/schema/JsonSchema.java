package com.recipe.ai.schema;

/**
 * Deprecated compatibility wrapper that delegates to `com.recipe.shared.schema.JsonSchema`.
 */
@Deprecated
public class JsonSchema extends com.recipe.shared.schema.JsonSchema {
    public JsonSchema(java.util.Map<String, Object> schema) {
        super(schema);
    }
}
