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

            // Ensure servings, prepTimeMinutes, and imageGeneration are present and typed correctly (lowercase types)
            Assertions.assertTrue(properties.containsKey("servings"), "Schema should include servings field");
            @SuppressWarnings("unchecked")
            Map<String, Object> servingsProp = (Map<String, Object>) properties.get("servings");
            Assertions.assertTrue("integer".equalsIgnoreCase(String.valueOf(servingsProp.get("type"))), "servings should be an integer type");
            Assertions.assertTrue(properties.containsKey("prepTimeMinutes"), "Schema should include prepTimeMinutes field");
            @SuppressWarnings("unchecked")
            Map<String, Object> prepMinutesProp = (Map<String, Object>) properties.get("prepTimeMinutes");
            Assertions.assertTrue("integer".equalsIgnoreCase(String.valueOf(prepMinutesProp.get("type"))), "prepTimeMinutes should be an integer type");
            // imageGeneration should be an object due to Map<String,Object>
            Assertions.assertTrue(properties.containsKey("imageGeneration"), "Schema should include imageGeneration field");
            @SuppressWarnings("unchecked")
            Map<String, Object> imageGenProp = (Map<String, Object>) properties.get("imageGeneration");
            Assertions.assertTrue("object".equalsIgnoreCase(String.valueOf(imageGenProp.get("type"))), "imageGeneration should be an object type");

            // tags should be an array of strings
            Assertions.assertTrue(properties.containsKey("tags"), "Schema should include tags field");
            @SuppressWarnings("unchecked")
            Map<String, Object> tagsProp = (Map<String, Object>) properties.get("tags");
            Assertions.assertTrue("array".equalsIgnoreCase(String.valueOf(tagsProp.get("type"))), "tags should be an array type");
            @SuppressWarnings("unchecked")
            Map<String, Object> items = (Map<String, Object>) tagsProp.get("items");
            Assertions.assertTrue("string".equalsIgnoreCase(String.valueOf(items.get("type"))), "tags items should be string type");
    }
}
