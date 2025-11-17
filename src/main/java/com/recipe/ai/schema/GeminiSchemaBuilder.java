package com.recipe.ai.schema;

/**
 * Deprecated compatibility wrapper that delegates to shared version of GeminiSchemaBuilder.
 */
@Deprecated
public final class GeminiSchemaBuilder {
    private GeminiSchemaBuilder() {}

    public static com.recipe.shared.schema.GeminiSchemaBuilder string() { return com.recipe.shared.schema.GeminiSchemaBuilder.string(); }
    public static com.recipe.shared.schema.GeminiSchemaBuilder number() { return com.recipe.shared.schema.GeminiSchemaBuilder.number(); }
    public static com.recipe.shared.schema.GeminiSchemaBuilder integer() { return com.recipe.shared.schema.GeminiSchemaBuilder.integer(); }
    public static com.recipe.shared.schema.GeminiSchemaBuilder bool() { return com.recipe.shared.schema.GeminiSchemaBuilder.bool(); }
    public static com.recipe.shared.schema.GeminiSchemaBuilder array() { return com.recipe.shared.schema.GeminiSchemaBuilder.array(); }
    public static com.recipe.shared.schema.GeminiSchemaBuilder object() { return com.recipe.shared.schema.GeminiSchemaBuilder.object(); }
}
