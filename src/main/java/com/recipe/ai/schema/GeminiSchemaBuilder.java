package com.recipe.ai.schema;

import java.util.*;

/**
 * Fluent builder for creating Gemini API JSON schemas.
 * Provides a type-safe, readable way to construct schema definitions.
 */
public class GeminiSchemaBuilder {

    private final Map<String, Object> schema = new LinkedHashMap<>();

    private GeminiSchemaBuilder(GeminiSchemaType type) {
        schema.put("type", type.name());
    }

    /**
     * Creates a new schema builder for a STRING type.
     */
    public static GeminiSchemaBuilder string() {
        return new GeminiSchemaBuilder(GeminiSchemaType.STRING);
    }

    /**
     * Creates a new schema builder for a NUMBER type.
     */
    public static GeminiSchemaBuilder number() {
        return new GeminiSchemaBuilder(GeminiSchemaType.NUMBER);
    }

    /**
     * Creates a new schema builder for an INTEGER type.
     */
    public static GeminiSchemaBuilder integer() {
        return new GeminiSchemaBuilder(GeminiSchemaType.INTEGER);
    }

    /**
     * Creates a new schema builder for a BOOLEAN type.
     */
    public static GeminiSchemaBuilder bool() {
        return new GeminiSchemaBuilder(GeminiSchemaType.BOOLEAN);
    }

    /**
     * Creates a new schema builder for an ARRAY type.
     */
    public static GeminiSchemaBuilder array() {
        return new GeminiSchemaBuilder(GeminiSchemaType.ARRAY);
    }

    /**
     * Creates a new schema builder for an OBJECT type.
     */
    public static GeminiSchemaBuilder object() {
        return new GeminiSchemaBuilder(GeminiSchemaType.OBJECT);
    }

    /**
     * Adds a description to this schema element.
     */
    public GeminiSchemaBuilder description(String description) {
        schema.put("description", description);
        return this;
    }

    /**
     * Sets the items schema for an ARRAY type.
     */
    public GeminiSchemaBuilder items(GeminiSchemaBuilder itemsBuilder) {
        schema.put("items", itemsBuilder.buildAsMap());
        return this;
    }

    /**
     * Sets the items schema for an ARRAY type using a pre-built map.
     */
    public GeminiSchemaBuilder items(Map<String, Object> itemsSchema) {
        schema.put("items", itemsSchema);
        return this;
    }

    /**
     * Adds properties to an OBJECT type schema.
     */
    public GeminiSchemaBuilder properties(Map<String, Map<String, Object>> properties) {
        schema.put("properties", properties);
        return this;
    }

    /**
     * Adds a single property to an OBJECT type schema using a fluent builder.
     */
    public GeminiSchemaBuilder property(String name, GeminiSchemaBuilder propertyBuilder) {
        @SuppressWarnings("unchecked")
        Map<String, Map<String, Object>> props = (Map<String, Map<String, Object>>) schema.computeIfAbsent("properties", k -> new LinkedHashMap<>());
        props.put(name, propertyBuilder.buildAsMap());
        return this;
    }

    /**
     * Adds a single property to an OBJECT type schema using a pre-built map.
     */
    public GeminiSchemaBuilder property(String name, Map<String, Object> propertySchema) {
        @SuppressWarnings("unchecked")
        Map<String, Map<String, Object>> props = (Map<String, Map<String, Object>>) schema.computeIfAbsent("properties", k -> new LinkedHashMap<>());
        props.put(name, propertySchema);
        return this;
    }

    /**
     * Sets the required fields for an OBJECT type schema.
     */
    public GeminiSchemaBuilder required(String... fieldNames) {
        schema.put("required", List.of(fieldNames));
        return this;
    }

    /**
     * Sets the required fields for an OBJECT type schema from a list.
     */
    public GeminiSchemaBuilder required(List<String> fieldNames) {
        schema.put("required", new ArrayList<>(fieldNames));
        return this;
    }

    /**
     * Builds and returns the final schema as a JsonSchema object.
     */
    public JsonSchema build() {
        return new JsonSchema(schema);
    }

    /**
     * Builds and returns the schema as a raw map for backwards compatibility.
     * @deprecated Use {@link #build()} instead to get a JsonSchema object.
     */
    @Deprecated
    public Map<String, Object> buildAsMap() {
        return Collections.unmodifiableMap(new LinkedHashMap<>(schema));
    }

    /**
     * Helper method to create a simple property with just type and description.
     */
    public static Map<String, Object> simpleProperty(GeminiSchemaType type, String description) {
        Map<String, Object> prop = new LinkedHashMap<>();
        prop.put("type", type.name());
        prop.put("description", description);
        return Collections.unmodifiableMap(prop);
    }
}
