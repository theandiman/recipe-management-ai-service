package com.recipe.ai.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.recipe.ai.model.IngredientNormalization;
import com.recipe.ai.model.IngredientNormalizationRequest;
import com.recipe.ai.model.IngredientNormalizationResponse;
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
 * Service that calls Gemini to detect ambiguous or unclear ingredient lines
 * and suggest normalized alternatives.
 *
 * BDD Scenarios:
 *   Scenario 1: Ambiguous ingredient → normalization suggestion returned
 *   Scenario 2: Low-confidence suggestion (< 0.6) → filtered out
 *   Scenario 3: Empty ingredient list → returns empty normalizations (graceful)
 *   Scenario 4: API failure → returns empty list (graceful degradation)
 */
@Service
public class IngredientNormalizationService {

    private static final Logger log = LoggerFactory.getLogger(IngredientNormalizationService.class);
    private static final double MIN_CONFIDENCE = 0.6;
    private static final String API_KEY_HEADER = "x-goog-api-key";

    @Value("${gemini.api.url:https://generativelanguage.googleapis.com/v1beta/models/gemini-2.5-flash:generateContent}")
    private String geminiApiUrl;

    private final WebClient.Builder webClientBuilder;
    private final GeminiApiKeyResolver apiKeyResolver;
    private final ObjectMapper objectMapper;

    public IngredientNormalizationService(WebClient.Builder webClientBuilder,
                                          GeminiApiKeyResolver apiKeyResolver,
                                          ObjectMapper objectMapper) {
        this.webClientBuilder = webClientBuilder;
        this.apiKeyResolver = apiKeyResolver;
        this.objectMapper = objectMapper;
    }

    /**
     * Normalizes a list of ingredient strings.
     * Returns only ingredients that have a normalization suggestion with confidence >= 0.6.
     * Returns empty list on any error (graceful degradation).
     */
    public IngredientNormalizationResponse normalizeIngredients(IngredientNormalizationRequest request) {
        List<String> ingredients = request.getIngredients();
        if (ingredients == null || ingredients.isEmpty()) {
            return new IngredientNormalizationResponse(List.of());
        }

        if (!apiKeyResolver.hasValidApiKey()) {
            log.warn("No valid Gemini API key — skipping ingredient normalization.");
            return new IngredientNormalizationResponse(List.of());
        }

        String effectiveApiKey = apiKeyResolver.resolveEffectiveApiKey();
        String prompt = buildPrompt(ingredients, request.getRecipeName());
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

            return parseGeminiResponse(responseBody, ingredients);
        } catch (Exception e) {
            log.error("Ingredient normalization call to Gemini failed: {}", e.getMessage(), e);
            return new IngredientNormalizationResponse(List.of());
        }
    }

    String buildPrompt(List<String> ingredients, String recipeName) {
        StringBuilder sb = new StringBuilder();
        sb.append("You are a culinary editor. Analyze the following ingredient list");
        if (recipeName != null && !recipeName.isBlank()) {
            sb.append(" for the recipe \"").append(recipeName).append("\"");
        }
        sb.append(".\n\n");
        sb.append("For each ingredient line that is AMBIGUOUS, VAGUE, or INCOMPLETE, suggest a clearer normalized version.\n");
        sb.append("Only return suggestions for ingredients that genuinely need improvement.\n");
        sb.append("Do NOT suggest changes to ingredients that are already clear and specific.\n\n");
        sb.append("Rules:\n");
        sb.append("- Preserve the original meaning and quantity intent\n");
        sb.append("- Add missing units where a typical quantity is assumed\n");
        sb.append("- Clarify vague qualifiers (e.g. 'some', 'a bit of', 'handful')\n");
        sb.append("- confidence: 0.0–1.0. Use >= 0.6 only for clear improvements.\n\n");
        sb.append("Ingredients (0-indexed):\n");
        for (int i = 0; i < ingredients.size(); i++) {
            sb.append(i).append(": ").append(ingredients.get(i)).append("\n");
        }
        return sb.toString();
    }

    String buildResponseSchema() {
        return """
            {
              "type": "object",
              "properties": {
                "normalizations": {
                  "type": "array",
                  "items": {
                    "type": "object",
                    "properties": {
                      "index":      { "type": "integer" },
                      "original":   { "type": "string" },
                      "normalized": { "type": "string" },
                      "reason":     { "type": "string" },
                      "confidence": { "type": "number" }
                    },
                    "required": ["index", "original", "normalized", "reason", "confidence"]
                  }
                }
              },
              "required": ["normalizations"]
            }
            """;
    }

    IngredientNormalizationResponse parseGeminiResponse(String body, List<String> ingredients) {
        try {
            JsonNode root = objectMapper.readTree(body);
            JsonNode candidates = root.path("candidates");
            if (!candidates.isArray() || candidates.isEmpty()) {
                log.warn("normalizeIngredients: no candidates in Gemini response");
                return new IngredientNormalizationResponse(List.of());
            }
            String text = candidates.get(0).path("content").path("parts").get(0).path("text").asText();
            JsonNode parsed = objectMapper.readTree(text);
            JsonNode items = parsed.path("normalizations");
            if (!items.isArray()) {
                return new IngredientNormalizationResponse(List.of());
            }

            List<IngredientNormalization> result = new ArrayList<>();
            for (JsonNode item : items) {
                int idx = item.path("index").asInt(-1);
                if (idx < 0 || idx >= ingredients.size()) continue;
                double confidence = item.path("confidence").asDouble(0.0);
                if (confidence < MIN_CONFIDENCE) continue;

                result.add(new IngredientNormalization(
                    idx,
                    item.path("original").asText(ingredients.get(idx)),
                    item.path("normalized").asText(""),
                    item.path("reason").asText(""),
                    confidence
                ));
            }
            return new IngredientNormalizationResponse(result);
        } catch (Exception e) {
            log.error("Failed to parse ingredient normalization response: {}", e.getMessage(), e);
            return new IngredientNormalizationResponse(List.of());
        }
    }

    private Map<String, Object> parseSchema(String jsonSchema) {
        try {
            return objectMapper.readValue(jsonSchema, new TypeReference<Map<String, Object>>() {});
        } catch (Exception e) {
            log.warn("Failed to parse ingredient normalization schema: {}", e.getMessage());
            return Map.of();
        }
    }
}
