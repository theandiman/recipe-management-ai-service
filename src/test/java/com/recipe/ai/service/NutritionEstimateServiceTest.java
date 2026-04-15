package com.recipe.ai.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.recipe.ai.model.NutritionEstimateRequest;
import com.recipe.ai.model.NutritionEstimateResponse;
import org.junit.jupiter.api.Test;
import org.springframework.web.reactive.function.client.WebClient;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for NutritionEstimateService prompt building and response parsing.
 *
 * BDD Scenarios:
 *   Scenario 1: All ingredients known → full estimate with no warnings
 *   Scenario 2: Unknown ingredient → partial estimate with warning
 *   Scenario 3: Empty ingredient list → returns empty response
 *   Scenario 4: API error → graceful empty response
 */
class NutritionEstimateServiceTest {

    static class StubNutritionEstimateService extends NutritionEstimateService {
        private final String stubJson;

        StubNutritionEstimateService(String stubJson) {
            super(WebClient.builder(), new GeminiApiKeyResolver(), new ObjectMapper());
            this.stubJson = stubJson;
        }

        @Override
        public NutritionEstimateResponse estimateNutrition(NutritionEstimateRequest request) {
            List<String> ingredients = request.getIngredients();
            if (ingredients == null || ingredients.isEmpty()) {
                return new NutritionEstimateResponse(null, null);
            }
            return parseGeminiResponse(buildFakeGeminiWrapper(stubJson));
        }

        private String buildFakeGeminiWrapper(String innerJson) {
            return "{\"candidates\":[{\"content\":{\"parts\":[{\"text\":" +
                   new com.fasterxml.jackson.databind.ObjectMapper()
                       .valueToTree(innerJson).toString() +
                   "}]}}]}";
        }
    }

    @Test
    void estimateNutrition_happyPath_returnsBothEstimates() {
        String innerJson = """
            {
              "perServing": {
                "calories": {"value": 350, "unit": "kcal", "estimated": false},
                "protein":  {"value": 12, "unit": "g", "estimated": false},
                "carbs":    {"value": 45, "unit": "g", "estimated": false},
                "fat":      {"value": 10, "unit": "g", "estimated": false},
                "fiber":    {"value": 4, "unit": "g", "estimated": false},
                "warnings": [],
                "isPartial": false
              },
              "wholeRecipe": {
                "calories": {"value": 700, "unit": "kcal", "estimated": false},
                "protein":  {"value": 24, "unit": "g", "estimated": false},
                "carbs":    {"value": 90, "unit": "g", "estimated": false},
                "fat":      {"value": 20, "unit": "g", "estimated": false},
                "fiber":    {"value": 8, "unit": "g", "estimated": false},
                "warnings": [],
                "isPartial": false
              }
            }
            """;
        StubNutritionEstimateService service = new StubNutritionEstimateService(innerJson);
        NutritionEstimateRequest req = new NutritionEstimateRequest();
        req.setIngredients(List.of("2 cups flour", "1 cup sugar"));
        req.setServings(2);

        NutritionEstimateResponse resp = service.estimateNutrition(req);

        assertThat(resp.getPerServing()).isNotNull();
        assertThat(resp.getWholeRecipe()).isNotNull();
        assertThat(resp.getPerServing().getCalories().getValue()).isEqualTo(350.0);
        assertThat(resp.getWholeRecipe().getCalories().getValue()).isEqualTo(700.0);
        assertThat(resp.getPerServing().isPartial()).isFalse();
        assertThat(resp.getPerServing().getWarnings()).isEmpty();
    }

    @Test
    void estimateNutrition_unknownIngredient_returnsPartialWithWarning() {
        String innerJson = """
            {
              "perServing": {
                "calories": {"value": 200, "unit": "kcal", "estimated": true},
                "protein":  {"value": 5, "unit": "g", "estimated": true},
                "carbs":    {"value": 30, "unit": "g", "estimated": true},
                "fat":      {"value": 8, "unit": "g", "estimated": true},
                "fiber":    {"value": 2, "unit": "g", "estimated": true},
                "warnings": ["Unknown ingredient: 'mystery powder'"],
                "isPartial": true
              },
              "wholeRecipe": {
                "calories": {"value": 400, "unit": "kcal", "estimated": true},
                "protein":  {"value": 10, "unit": "g", "estimated": true},
                "carbs":    {"value": 60, "unit": "g", "estimated": true},
                "fat":      {"value": 16, "unit": "g", "estimated": true},
                "fiber":    {"value": 4, "unit": "g", "estimated": true},
                "warnings": ["Unknown ingredient: 'mystery powder'"],
                "isPartial": true
              }
            }
            """;
        StubNutritionEstimateService service = new StubNutritionEstimateService(innerJson);
        NutritionEstimateRequest req = new NutritionEstimateRequest();
        req.setIngredients(List.of("1 cup flour", "2 tsp mystery powder"));
        req.setServings(2);

        NutritionEstimateResponse resp = service.estimateNutrition(req);

        assertThat(resp.getPerServing()).isNotNull();
        assertThat(resp.getPerServing().isPartial()).isTrue();
        assertThat(resp.getPerServing().getWarnings()).hasSize(1);
        assertThat(resp.getPerServing().getWarnings().get(0)).contains("mystery powder");
        assertThat(resp.getPerServing().getCalories().isEstimated()).isTrue();
    }

    @Test
    void estimateNutrition_emptyIngredients_returnsEmptyResponse() {
        NutritionEstimateService service = new NutritionEstimateService(
            WebClient.builder(), new GeminiApiKeyResolver(), new ObjectMapper());
        NutritionEstimateRequest req = new NutritionEstimateRequest();
        req.setIngredients(List.of());

        NutritionEstimateResponse resp = service.estimateNutrition(req);

        assertThat(resp.getPerServing()).isNull();
        assertThat(resp.getWholeRecipe()).isNull();
    }

    @Test
    void estimateNutrition_nullIngredients_returnsEmptyResponse() {
        NutritionEstimateService service = new NutritionEstimateService(
            WebClient.builder(), new GeminiApiKeyResolver(), new ObjectMapper());
        NutritionEstimateRequest req = new NutritionEstimateRequest();
        req.setIngredients(null);

        NutritionEstimateResponse resp = service.estimateNutrition(req);

        assertThat(resp.getPerServing()).isNull();
        assertThat(resp.getWholeRecipe()).isNull();
    }
}
