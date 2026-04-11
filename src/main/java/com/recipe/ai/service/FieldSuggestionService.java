package com.recipe.ai.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.recipe.ai.model.FieldSuggestion;
import com.recipe.ai.model.FieldSuggestionRequest;
import com.recipe.ai.model.FieldSuggestionsResponse;
import io.micrometer.core.instrument.MeterRegistry;
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
import java.util.concurrent.TimeUnit;

/**
 * Service that calls the Gemini API to suggest values for missing or
 * low-quality recipe fields.  Only fields that are null/blank/empty in the
 * request are included in the prompt so the model focuses on genuinely missing
 * data.
 */
@Service
public class FieldSuggestionService {

    private static final Logger log = LoggerFactory.getLogger(FieldSuggestionService.class);

    private static final String API_KEY_HEADER = "x-goog-api-key";

    @Value("${gemini.api.url:https://generativelanguage.googleapis.com/v1beta/models/gemini-2.5-flash:generateContent}")
    private String geminiApiUrl;

    private final WebClient.Builder webClientBuilder;
    private final GeminiApiKeyResolver apiKeyResolver;
    private final ObjectMapper objectMapper;
    private final MeterRegistry meterRegistry;

    private static final String ENDPOINT_TAG = "endpoint";
    private static final String ENDPOINT_VALUE = "suggest-fields";

    public FieldSuggestionService(WebClient.Builder webClientBuilder,
                                   GeminiApiKeyResolver apiKeyResolver,
                                   ObjectMapper objectMapper,
                                   MeterRegistry meterRegistry) {
        this.webClientBuilder = webClientBuilder;
        this.apiKeyResolver = apiKeyResolver;
        this.objectMapper = objectMapper == null ? new ObjectMapper() : objectMapper.copy();
        this.meterRegistry = meterRegistry;
    }

    /**
     * Produces AI suggestions for recipe fields that are missing or have
     * low-quality content.  Never returns null — on error, returns an empty
     * suggestions list so callers can degrade gracefully.
     */
    public FieldSuggestionsResponse suggestFields(FieldSuggestionRequest request) {
        long start = System.currentTimeMillis();
        try {
            meterRegistry.counter("ai.suggestion.requests", ENDPOINT_TAG, ENDPOINT_VALUE).increment();
            FieldSuggestionsResponse result = doSuggestFields(request);
            meterRegistry.counter("ai.suggestion.acceptance",
                    ENDPOINT_TAG, ENDPOINT_VALUE,
                    "count", String.valueOf(result.getSuggestions().size())).increment();
            return result;
        } catch (Exception e) {
            meterRegistry.counter("ai.suggestion.errors", ENDPOINT_TAG, ENDPOINT_VALUE).increment();
            log.error("Unhandled error in suggestFields: {}", e.getMessage(), e);
            return new FieldSuggestionsResponse(List.of());
        } finally {
            meterRegistry.timer("ai.suggestion.latency", ENDPOINT_TAG, ENDPOINT_VALUE)
                    .record(System.currentTimeMillis() - start, TimeUnit.MILLISECONDS);
        }
    }

    private FieldSuggestionsResponse doSuggestFields(FieldSuggestionRequest request) {
        if (request == null) {
            return new FieldSuggestionsResponse(List.of());
        }

        List<String> missingFields = collectMissingFields(request);
        if (missingFields.isEmpty()) {
            log.debug("No missing/weak fields detected — returning empty suggestions.");
            return new FieldSuggestionsResponse(List.of());
        }

        String effectiveApiKey = apiKeyResolver.resolveEffectiveApiKey();
        if (!apiKeyResolver.hasValidApiKey()) {
            log.warn("No valid Gemini API key — skipping field suggestions.");
            return new FieldSuggestionsResponse(List.of());
        }

        String prompt = buildPrompt(request, missingFields);
        String jsonSchema = buildResponseSchema();

        Map<String, Object> payload = Map.of(
            "contents", List.of(Map.of("parts", List.of(Map.of("text", prompt)))),
            "generationConfig", Map.of(
                "responseMimeType", "application/json",
                "responseSchema", objectMapper.convertValue(parseSchema(jsonSchema), Object.class)
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
            log.error("Field suggestion call to Gemini failed: {}", e.getMessage(), e);
            return new FieldSuggestionsResponse(List.of());
        }
    }

    // -------------------------------------------------------------------------
    // Private helpers
    // -------------------------------------------------------------------------

    /** Returns the names of fields that are null, blank, or empty. */
    List<String> collectMissingFields(FieldSuggestionRequest req) {
        List<String> missing = new ArrayList<>();
        if (isBlank(req.getRecipeName())) missing.add("recipeName");
        if (isBlank(req.getDescription()))  missing.add("description");
        if (isBlank(req.getPrepTime()))     missing.add("prepTime");
        if (isBlank(req.getCookTime()))     missing.add("cookTime");
        if (isBlank(req.getServings()))     missing.add("servings");
        if (isEmpty(req.getTags()))         missing.add("tags");
        return missing;
    }

    private static boolean isBlank(String s) {
        return s == null || s.isBlank();
    }

    private static boolean isEmpty(List<?> l) {
        return l == null || l.isEmpty();
    }

    /** Builds the natural-language prompt sent to Gemini. */
    String buildPrompt(FieldSuggestionRequest req, List<String> missingFields) {
        StringBuilder sb = new StringBuilder();
        sb.append("You are a helpful recipe assistant. A user is creating a recipe");
        if (!isBlank(req.getRecipeName())) {
            sb.append(" called \"").append(req.getRecipeName()).append("\"");
        }
        sb.append(".\n\n");

        // Provide available context
        if (!isBlank(req.getDescription())) {
            sb.append("Description: ").append(req.getDescription()).append("\n");
        }
        if (req.getIngredients() != null && !req.getIngredients().isEmpty()) {
            sb.append("Ingredients: ").append(String.join(", ", req.getIngredients())).append("\n");
        }
        if (req.getInstructions() != null && !req.getInstructions().isEmpty()) {
            sb.append("Instructions: ").append(req.getInstructions().size()).append(" steps\n");
        }
        sb.append("\n");

        sb.append("The following fields are missing or incomplete: ")
          .append(String.join(", ", missingFields))
          .append(".\n\n");
        sb.append("For each missing field, suggest a realistic value and provide a brief reason (1 sentence). ");
        sb.append("Return ONLY the JSON array described by the provided schema. ");
        sb.append("Use sensible defaults: e.g. prepTime/cookTime in minutes as strings like \"15\", ");
        sb.append("servings as a number string like \"4\", tags as comma-separated suggestions.");

        return sb.toString();
    }

    /** Inline JSON Schema for the Gemini structured response. */
    private String buildResponseSchema() {
        return """
            {
              "type": "object",
              "properties": {
                "suggestions": {
                  "type": "array",
                  "items": {
                    "type": "object",
                    "properties": {
                      "field":          { "type": "string" },
                      "suggestedValue": { "type": "string" },
                      "reason":         { "type": "string" }
                    },
                    "required": ["field", "suggestedValue", "reason"]
                  }
                }
              },
              "required": ["suggestions"]
            }
            """;
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> parseSchema(String jsonSchema) {
        try {
            return objectMapper.readValue(jsonSchema, new TypeReference<Map<String, Object>>() {});
        } catch (Exception e) {
            log.warn("Failed to parse field suggestion schema: {}", e.getMessage());
            return Map.of();
        }
    }

    /** Extracts the suggestions array from the raw Gemini API response. */
    @SuppressWarnings("unchecked")
    FieldSuggestionsResponse parseGeminiResponse(String body) {
        if (body == null || body.isBlank()) {
            return new FieldSuggestionsResponse(List.of());
        }
        try {
            Map<String, Object> root = objectMapper.readValue(body, new TypeReference<Map<String, Object>>() {});
            List<Map<String, Object>> candidates = (List<Map<String, Object>>) root.get("candidates");
            if (candidates == null || candidates.isEmpty()) {
                return new FieldSuggestionsResponse(List.of());
            }
            Map<String, Object> firstCandidate = candidates.get(0);
            Map<String, Object> content = (Map<String, Object>) firstCandidate.get("content");
            List<Map<String, Object>> parts = (List<Map<String, Object>>) content.get("parts");
            String text = (String) parts.get(0).get("text");

            Map<String, Object> parsed = objectMapper.readValue(text, new TypeReference<Map<String, Object>>() {});
            List<Map<String, Object>> rawSuggestions = (List<Map<String, Object>>) parsed.get("suggestions");
            if (rawSuggestions == null) {
                return new FieldSuggestionsResponse(List.of());
            }

            List<FieldSuggestion> suggestions = new ArrayList<>();
            for (Map<String, Object> raw : rawSuggestions) {
                String field          = (String) raw.get("field");
                String suggestedValue = (String) raw.get("suggestedValue");
                String reason         = (String) raw.get("reason");
                if (field != null && suggestedValue != null) {
                    suggestions.add(new FieldSuggestion(field, suggestedValue, reason != null ? reason : ""));
                }
            }
            return new FieldSuggestionsResponse(suggestions);
        } catch (Exception e) {
            log.error("Failed to parse Gemini field suggestion response: {}", e.getMessage(), e);
            return new FieldSuggestionsResponse(List.of());
        }
    }
}
