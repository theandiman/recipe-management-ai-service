package com.recipe.ai.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.recipe.ai.model.NutrientValue;
import com.recipe.ai.model.NutritionEstimate;
import com.recipe.ai.model.NutritionEstimateRequest;
import com.recipe.ai.model.NutritionEstimateResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.BodyInserters;
import org.springframework.web.reactive.function.client.WebClient;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Service that calls Gemini to estimate nutritional values for a recipe.
 *
 * BDD Scenarios:
 *   Scenario 1: All ingredients known → full estimate returned
 *   Scenario 2: Some ingredients unknown → partial estimate with warnings
 *   Scenario 3: Empty ingredient list → returns empty response (graceful)
 *   Scenario 4: API failure → returns empty response (graceful degradation)
 */
@Service
public class NutritionEstimateService {

    private static final Logger log = LoggerFactory.getLogger(NutritionEstimateService.class);
    private static final String API_KEY_HEADER = "x-goog-api-key";

    @Value("${gemini.api.url:https://generativelanguage.googleapis.com/v1beta/models/gemini-2.5-flash:generateContent}")
    private String geminiApiUrl;

    private final WebClient.Builder webClientBuilder;
    private final GeminiApiKeyResolver apiKeyResolver;
    private final ObjectMapper objectMapper;

    public NutritionEstimateService(WebClient.Builder webClientBuilder,
                                    GeminiApiKeyResolver apiKeyResolver,
                                    ObjectMapper objectMapper) {
        this.webClientBuilder = webClientBuilder;
        this.apiKeyResolver = apiKeyResolver;
        this.objectMapper = objectMapper;
    }

    /**
     * Estimates nutrition for the given recipe ingredients.
     * Returns empty response on any error (graceful degradation).
     */
    public NutritionEstimateResponse estimateNutrition(NutritionEstimateRequest request) {
        List<String> ingredients = request.getIngredients();
        if (ingredients == null || ingredients.isEmpty()) {
            return new NutritionEstimateResponse(null, null);
        }

        if (!apiKeyResolver.hasValidApiKey()) {
            log.warn("No valid Gemini API key — skipping nutrition estimation.");
            return new NutritionEstimateResponse(null, null);
        }

        String effectiveApiKey = apiKeyResolver.resolveEffectiveApiKey();
        int servings = request.getServings() > 0 ? request.getServings() : 1;
        String prompt = buildPrompt(ingredients, servings, request.getRecipeName());
        String jsonSchema = buildResponseSchema();

        Map<String, Object> payload = Map.of(
            "contents", List.of(Map.of("parts", List.of(Map.of("text", prompt)))),
            "generationConfig", Map.of(
                "responseMimeType", "application/json",
                "responseSchema", parseSchema(jsonSchema)
            )
        );

        try {
            WebClient client = webClientBuilder
                .baseUrl(geminiApiUrl)
                .defaultHeader(API_KEY_HEADER, effectiveApiKey)
                .build();

            String responseBody = client.post()
                .contentType(MediaType.APPLICATION_JSON)
                .body(BodyInserters.fromValue(payload))
                .retrieve()
                .bodyToMono(String.class)
                .block();

            return parseGeminiResponse(responseBody);
        } catch (Exception e) {
            log.error("Nutrition estimation call to Gemini failed: {}", e.getMessage(), e);
            return new NutritionEstimateResponse(null, null);
        }
    }

    String buildPrompt(List<String> ingredients, int servings, String recipeName) {
        StringBuilder sb = new StringBuilder();
        sb.append("You are a nutritionist. Estimate the nutritional values for a recipe");
        if (recipeName != null && !recipeName.isBlank()) {
            sb.append(" named \"").append(recipeName).append("\"");
        }
        sb.append(".\n\n");
        sb.append("Ingredients:\n");
        for (String ingredient : ingredients) {
            sb.append("- ").append(ingredient).append("\n");
        }
        sb.append("\nServings: ").append(servings).append("\n\n");
        sb.append("Provide estimates for the WHOLE recipe and PER SERVING (whole/servings).\n");
        sb.append("For each nutrient (calories, protein, carbs, fat, fiber), provide:\n");
        sb.append("- value: numeric amount\n");
        sb.append("- unit: the unit string (e.g. 'kcal', 'g')\n");
        sb.append("- estimated: true if you had to estimate due to ambiguity or unknown ingredient\n\n");
        sb.append("Include a 'warnings' array listing any ingredients that were unknown or ambiguous.\n");
        sb.append("Set 'isPartial' to true if any ingredient could not be estimated.\n");
        return sb.toString();
    }

    String buildResponseSchema() {
        return """
            {
              "type": "object",
              "properties": {
                "perServing": {
                  "type": "object",
                  "properties": {
                    "calories": { "type": "object", "properties": { "value": { "type": "number" }, "unit": { "type": "string" }, "estimated": { "type": "boolean" } }, "required": ["value", "unit", "estimated"] },
                    "protein":  { "type": "object", "properties": { "value": { "type": "number" }, "unit": { "type": "string" }, "estimated": { "type": "boolean" } }, "required": ["value", "unit", "estimated"] },
                    "carbs":    { "type": "object", "properties": { "value": { "type": "number" }, "unit": { "type": "string" }, "estimated": { "type": "boolean" } }, "required": ["value", "unit", "estimated"] },
                    "fat":      { "type": "object", "properties": { "value": { "type": "number" }, "unit": { "type": "string" }, "estimated": { "type": "boolean" } }, "required": ["value", "unit", "estimated"] },
                    "fiber":    { "type": "object", "properties": { "value": { "type": "number" }, "unit": { "type": "string" }, "estimated": { "type": "boolean" } }, "required": ["value", "unit", "estimated"] },
                    "warnings": { "type": "array", "items": { "type": "string" } },
                    "isPartial": { "type": "boolean" }
                  },
                  "required": ["calories", "protein", "carbs", "fat", "fiber", "warnings", "isPartial"]
                },
                "wholeRecipe": {
                  "type": "object",
                  "properties": {
                    "calories": { "type": "object", "properties": { "value": { "type": "number" }, "unit": { "type": "string" }, "estimated": { "type": "boolean" } }, "required": ["value", "unit", "estimated"] },
                    "protein":  { "type": "object", "properties": { "value": { "type": "number" }, "unit": { "type": "string" }, "estimated": { "type": "boolean" } }, "required": ["value", "unit", "estimated"] },
                    "carbs":    { "type": "object", "properties": { "value": { "type": "number" }, "unit": { "type": "string" }, "estimated": { "type": "boolean" } }, "required": ["value", "unit", "estimated"] },
                    "fat":      { "type": "object", "properties": { "value": { "type": "number" }, "unit": { "type": "string" }, "estimated": { "type": "boolean" } }, "required": ["value", "unit", "estimated"] },
                    "fiber":    { "type": "object", "properties": { "value": { "type": "number" }, "unit": { "type": "string" }, "estimated": { "type": "boolean" } }, "required": ["value", "unit", "estimated"] },
                    "warnings": { "type": "array", "items": { "type": "string" } },
                    "isPartial": { "type": "boolean" }
                  },
                  "required": ["calories", "protein", "carbs", "fat", "fiber", "warnings", "isPartial"]
                }
              },
              "required": ["perServing", "wholeRecipe"]
            }
            """;
    }

    NutritionEstimateResponse parseGeminiResponse(String body) {
        try {
            JsonNode root = objectMapper.readTree(body);
            JsonNode candidates = root.path("candidates");
            if (!candidates.isArray() || candidates.isEmpty()) {
                log.warn("estimateNutrition: no candidates in Gemini response");
                return new NutritionEstimateResponse(null, null);
            }
            String text = candidates.get(0).path("content").path("parts").get(0).path("text").asText();
            JsonNode parsed = objectMapper.readTree(text);

            NutritionEstimate perServing = parseEstimate(parsed.path("perServing"));
            NutritionEstimate wholeRecipe = parseEstimate(parsed.path("wholeRecipe"));
            return new NutritionEstimateResponse(perServing, wholeRecipe);
        } catch (Exception e) {
            log.error("Failed to parse nutrition estimation response: {}", e.getMessage(), e);
            return new NutritionEstimateResponse(null, null);
        }
    }

    private NutritionEstimate parseEstimate(JsonNode node) {
        if (node == null || node.isMissingNode()) return null;

        NutrientValue calories = parseNutrient(node.path("calories"));
        NutrientValue protein  = parseNutrient(node.path("protein"));
        NutrientValue carbs    = parseNutrient(node.path("carbs"));
        NutrientValue fat      = parseNutrient(node.path("fat"));
        NutrientValue fiber    = parseNutrient(node.path("fiber"));

        List<String> warnings = new ArrayList<>();
        JsonNode warningsNode = node.path("warnings");
        if (warningsNode.isArray()) {
            for (JsonNode w : warningsNode) {
                warnings.add(w.asText());
            }
        }
        boolean isPartial = node.path("isPartial").asBoolean(false);

        return new NutritionEstimate(calories, protein, carbs, fat, fiber, warnings, isPartial);
    }

    private NutrientValue parseNutrient(JsonNode node) {
        if (node == null || node.isMissingNode()) return null;
        double value = node.path("value").asDouble(0.0);
        String unit = node.path("unit").asText("g");
        boolean estimated = node.path("estimated").asBoolean(false);
        return new NutrientValue(value, unit, estimated);
    }

    private Map<String, Object> parseSchema(String jsonSchema) {
        try {
            return objectMapper.readValue(jsonSchema, new TypeReference<Map<String, Object>>() {});
        } catch (Exception e) {
            log.warn("Failed to parse nutrition estimate schema: {}", e.getMessage());
            return Map.of();
        }
    }
}
