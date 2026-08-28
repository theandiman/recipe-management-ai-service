package com.recipe.ai.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.recipe.ai.model.AiSearchParseRequest;
import com.recipe.ai.model.AiSearchParseResponse;
import io.micrometer.core.instrument.MeterRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.BodyInserters;
import org.springframework.web.reactive.function.client.WebClient;

import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

@Service
public class SearchAiService {

    private static final Logger log = LoggerFactory.getLogger(SearchAiService.class);
    private static final String API_KEY_HEADER = "x-goog-api-key";

    @Value("${gemini.api.url:https://generativelanguage.googleapis.com/v1beta/models/gemini-2.5-flash:generateContent}")
    private String geminiApiUrl;

    private final WebClient.Builder webClientBuilder;
    private final GeminiApiKeyResolver apiKeyResolver;
    private final ObjectMapper objectMapper;
    private final MeterRegistry meterRegistry;

    private static final String ENDPOINT_TAG = "endpoint";
    private static final String ENDPOINT_VALUE = "parse-search-intent";

    public SearchAiService(WebClient.Builder webClientBuilder,
                           GeminiApiKeyResolver apiKeyResolver,
                           ObjectMapper objectMapper,
                           MeterRegistry meterRegistry) {
        this.webClientBuilder = webClientBuilder;
        this.apiKeyResolver = apiKeyResolver;
        this.objectMapper = objectMapper == null ? new ObjectMapper() : objectMapper.copy();
        this.meterRegistry = meterRegistry;
    }

    public AiSearchParseResponse parseSearchIntent(AiSearchParseRequest request) {
        long start = System.currentTimeMillis();
        try {
            if (meterRegistry != null) {
                meterRegistry.counter("ai.search.requests", ENDPOINT_TAG, ENDPOINT_VALUE).increment();
            }
            return doParseSearchIntent(request);
        } catch (Exception e) {
            if (meterRegistry != null) {
                meterRegistry.counter("ai.search.errors", ENDPOINT_TAG, ENDPOINT_VALUE).increment();
            }
            log.error("Unhandled error in parseSearchIntent: {}", e.getMessage(), e);
            String prompt = request != null ? request.getPrompt() : "";
            return new AiSearchParseResponse(prompt, List.of(), null, null, "Standard fallback search.");
        } finally {
            if (meterRegistry != null) {
                meterRegistry.timer("ai.search.latency", ENDPOINT_TAG, ENDPOINT_VALUE)
                        .record(System.currentTimeMillis() - start, TimeUnit.MILLISECONDS);
            }
        }
    }

    private AiSearchParseResponse doParseSearchIntent(AiSearchParseRequest request) {
        if (request == null || request.getPrompt() == null || request.getPrompt().isBlank()) {
            return new AiSearchParseResponse("", List.of(), null, null, "Empty search prompt.");
        }

        String rawPrompt = request.getPrompt().trim();
        String effectiveApiKey = apiKeyResolver.resolveEffectiveApiKey();
        if (!apiKeyResolver.hasValidApiKey()) {
            log.warn("No valid Gemini API key — falling back to standard prompt keywords.");
            return new AiSearchParseResponse(rawPrompt, List.of(), null, null, "Using raw search keywords.");
        }

        String prompt = buildPrompt(rawPrompt);
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

            return parseGeminiResponse(responseBody, rawPrompt);
        } catch (Exception e) {
            log.error("Search intent call to Gemini failed: {}", e.getMessage(), e);
            return new AiSearchParseResponse(rawPrompt, List.of(), null, null, "Standard fallback search.");
        }
    }

    String buildPrompt(String userPrompt) {
        return "You are a precise culinary search intent parser. Analyze the user's search request: \"" 
               + userPrompt + "\"\n\n"
               + "RULES:\n"
               + "1. queryKeywords: Core ingredient or dish name ONLY (e.g. \"pasta\", \"chicken\"). Return empty string \"\" if the request only describes attributes like \"quick\", \"healthy\", \"low carb\", \"vegetarian\", or prep times!\n"
               + "2. dietaryTags: Extract ONLY tags explicitly requested or directly implied, choosing from: [Quick & Easy, Vegetarian, Vegan, Gluten-Free, Low Carb, High Protein, Dairy-Free, Keto, Breakfast, Lunch, Dinner, Dessert]. Return empty [] if none.\n"
               + "3. maxPrepTime: Extract maximum minutes as integer ONLY if explicitly stated (e.g. \"under 30 mins\" -> 30). Leave as null if unspecified.\n"
               + "4. maxCalories: Extract calorie limit per serving as integer ONLY if explicitly specified. Leave as null if unspecified.\n"
               + "5. explanation: Brief 1-sentence friendly explanation of the parsed search intent.\n\n"
               + "CRITICAL: Be minimal and conservative. Do NOT add unnecessary filters or infer time/calorie constraints that were not explicitly stated.";
    }

    private String buildResponseSchema() {
        return """
            {
              "type": "object",
              "properties": {
                "queryKeywords": { "type": "string" },
                "dietaryTags": {
                  "type": "array",
                  "items": { "type": "string" }
                },
                "maxPrepTime": { "type": "integer" },
                "maxCalories": { "type": "integer" },
                "explanation": { "type": "string" }
              },
              "required": ["queryKeywords", "dietaryTags", "explanation"]
            }
            """;
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> parseSchema(String jsonSchema) {
        try {
            return objectMapper.readValue(jsonSchema, new TypeReference<Map<String, Object>>() {});
        } catch (Exception e) {
            log.warn("Failed to parse search schema: {}", e.getMessage());
            return Map.of();
        }
    }

    @SuppressWarnings("unchecked")
    AiSearchParseResponse parseGeminiResponse(String body, String defaultPrompt) {
        if (body == null || body.isBlank()) {
            return new AiSearchParseResponse(defaultPrompt, List.of(), null, null, "Standard fallback search.");
        }
        try {
            Map<String, Object> root = objectMapper.readValue(body, new TypeReference<Map<String, Object>>() {});
            List<Map<String, Object>> candidates = (List<Map<String, Object>>) root.get("candidates");
            if (candidates == null || candidates.isEmpty()) {
                return new AiSearchParseResponse(defaultPrompt, List.of(), null, null, "Standard fallback search.");
            }
            Map<String, Object> firstCandidate = candidates.get(0);
            Map<String, Object> content = (Map<String, Object>) firstCandidate.get("content");
            List<Map<String, Object>> parts = (List<Map<String, Object>>) content.get("parts");
            String text = (String) parts.get(0).get("text");

            Map<String, Object> parsed = objectMapper.readValue(text, new TypeReference<Map<String, Object>>() {});
            String queryKeywords = (String) parsed.getOrDefault("queryKeywords", "");
            List<String> dietaryTags = (List<String>) parsed.getOrDefault("dietaryTags", List.of());
            Number maxPrepTimeNum = (Number) parsed.get("maxPrepTime");
            Number maxCaloriesNum = (Number) parsed.get("maxCalories");
            String explanation = (String) parsed.getOrDefault("explanation", "AI parsed search intent.");

            Integer maxPrepTime = maxPrepTimeNum != null ? maxPrepTimeNum.intValue() : null;
            Integer maxCalories = maxCaloriesNum != null ? maxCaloriesNum.intValue() : null;

            // Sanitize queryKeywords: if it's redundant with an extracted dietary tag or attribute word, clear it
            if (queryKeywords != null && !queryKeywords.isBlank()) {
                String kwLower = queryKeywords.trim().toLowerCase();
                boolean matchesAttribute = List.of("quick", "easy", "quick & easy", "healthy", "low carb", "vegetarian", "vegan", "keto", "dinner", "lunch", "breakfast")
                        .contains(kwLower);
                if (matchesAttribute || (dietaryTags != null && dietaryTags.stream().anyMatch(t -> t.toLowerCase().contains(kwLower)))) {
                    queryKeywords = "";
                }
            }

            return new AiSearchParseResponse(queryKeywords != null ? queryKeywords : "", dietaryTags != null ? dietaryTags : List.of(), maxPrepTime, maxCalories, explanation);
        } catch (Exception e) {
            log.error("Failed to parse Gemini search intent response: {}", e.getMessage(), e);
            return new AiSearchParseResponse(defaultPrompt, List.of(), null, null, "Standard fallback search.");
        }
    }
}
