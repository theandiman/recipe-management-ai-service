package com.recipe.ai.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertTrue;

public class RecipeServiceTimeConstraintTest {

    @Test
    public void testEstimatedTimeViolation() throws Exception {
        RecipeService svc = new RecipeService(null, new ObjectMapper());

        Map<String, Object> recipe = Map.of(
            "recipeName", "Slow Stew",
            "ingredients", List.of("1 kg beef", "2 carrots"),
            "instructions", List.of("Stew for a long time."),
            "estimatedTimeMinutes", 120
        );

        java.util.List<String> violations = svc.runTimeConstraintChecks(recipe, 30);
        assertTrue(violations.size() >= 1, "Expected a time violation when estimatedTimeMinutes exceeds max");
    }

    @Test
    public void testPrepTimeStringParsingViolation() throws Exception {
        RecipeService svc = new RecipeService(null, new ObjectMapper());

        Map<String, Object> recipe = Map.of(
            "recipeName", "Baked Casserole",
            "ingredients", List.of("potatoes"),
            "instructions", List.of("Bake until tender."),
            "prepTime", "1 hour 20 minutes"
        );

        java.util.List<String> violations = svc.runTimeConstraintChecks(recipe, 45);
        assertTrue(violations.size() >= 1, "Expected a time violation when parsed prepTime exceeds max");
    }
}
