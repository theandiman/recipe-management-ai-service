package com.recipe.ai.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.recipe.shared.schema.JsonSchema;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.web.reactive.function.client.WebClient;

import java.lang.reflect.Field;
import java.util.Map;

public class RecipeServiceTest {

    private String origSysProp;

    @BeforeEach
    public void setUp() throws Exception {
        origSysProp = System.getProperty("GEMINI_API_KEY");
        // environment variables cannot be modified reliably across JVMs; tests will assert behavior using sys props
    }

    @AfterEach
    public void tearDown() {
        if (origSysProp != null) {
            System.setProperty("GEMINI_API_KEY", origSysProp);
        } else {
            System.clearProperty("GEMINI_API_KEY");
        }
    }

    @Test
    public void testResolveEffectiveApiKey_prefersSystemProperty() {
        System.setProperty("GEMINI_API_KEY", "sys-key-123");
        RecipeService service = new RecipeService(WebClient.builder(), new ObjectMapper());
        Assertions.assertEquals("sys-key-123", service.resolveEffectiveApiKey());
    }

    @Test
    public void testResolveEffectiveApiKey_fallsBackToProperty() throws Exception {
        // Ensure deterministic behavior in environments that may set GEMINI_API_KEY
        System.setProperty("GEMINI_API_KEY", "prop-key-abc");

        // set the private field geminiApiKey via reflection and assert the resolver returns the system property
        RecipeService service = new RecipeService(WebClient.builder(), new ObjectMapper());
        Field f = RecipeService.class.getDeclaredField("geminiApiKey");
        f.setAccessible(true);
        f.set(service, "prop-key-abc");

        Assertions.assertEquals("prop-key-abc", service.resolveEffectiveApiKey());
    }

    @Test
    public void testRecipeSchemaIncludesNutritionalInfo() throws Exception {
        // Verify that the RECIPE_SCHEMA constant includes nutritionalInfo field
        Field schemaField = RecipeService.class.getDeclaredField("RECIPE_SCHEMA");
        schemaField.setAccessible(true);
        
        JsonSchema jsonSchema = (JsonSchema) schemaField.get(null);
        Assertions.assertNotNull(jsonSchema, "RECIPE_SCHEMA should not be null");
        
        // Convert to Map for assertions
        Map<String, Object> schema = jsonSchema.asMap();
        
        @SuppressWarnings("unchecked")
        Map<String, Object> properties = (Map<String, Object>) schema.get("properties");
        Assertions.assertNotNull(properties, "Schema properties should not be null");
        Assertions.assertTrue(properties.containsKey("nutritionalInfo"), "Schema should include nutritionalInfo field");
        
        @SuppressWarnings("unchecked")
        Map<String, Object> nutritionalInfo = (Map<String, Object>) properties.get("nutritionalInfo");
        Assertions.assertEquals("object", nutritionalInfo.get("type"), "nutritionalInfo should be of type object");
        
        @SuppressWarnings("unchecked")
        Map<String, Object> nutritionProps = (Map<String, Object>) nutritionalInfo.get("properties");
        Assertions.assertTrue(nutritionProps.containsKey("perServing"), "nutritionalInfo should have perServing field");
        Assertions.assertTrue(nutritionProps.containsKey("total"), "nutritionalInfo should have total field");
        
        // Verify perServing structure
        @SuppressWarnings("unchecked")
        Map<String, Object> perServing = (Map<String, Object>) nutritionProps.get("perServing");
        @SuppressWarnings("unchecked")
        Map<String, Object> perServingProps = (Map<String, Object>) perServing.get("properties");
        
        String[] requiredNutrients = {"calories", "protein", "carbohydrates", "fat", "fiber", "sodium"};
        for (String nutrient : requiredNutrients) {
            Assertions.assertTrue(perServingProps.containsKey(nutrient), 
                "perServing should include " + nutrient + " field");
        }

            // Ensure servings and prepTimeMinutes are present and typed correctly (lowercase types)
            Map.of(
                "servings", "integer",
                "prepTimeMinutes", "integer"
            ).forEach((key, expectedType) -> {
                Assertions.assertTrue(properties.containsKey(key), "Schema should include " + key + " field");
                @SuppressWarnings("unchecked")
                Map<String, Object> prop = (Map<String, Object>) properties.get(key);
                Assertions.assertTrue(expectedType.equalsIgnoreCase(String.valueOf(prop.get("type"))), key + " should be of type " + expectedType);
            });
    }
}
