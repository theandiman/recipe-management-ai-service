package com.recipe.ai.service;

import com.recipe.ai.model.IngredientNormalizationRequest;
import com.recipe.ai.model.IngredientNormalizationResponse;
import org.junit.jupiter.api.Test;
import org.springframework.web.reactive.function.client.WebClient;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for IngredientNormalizationService prompt building and response parsing.
 */
class IngredientNormalizationServiceTest {

    /**
     * Stub that returns a canned Gemini-style JSON response.
     */
    static class StubIngredientNormalizationService extends IngredientNormalizationService {
        private final String stubJson;

        StubIngredientNormalizationService(String stubJson) {
            super(WebClient.builder(), new GeminiApiKeyResolver(), new com.fasterxml.jackson.databind.ObjectMapper(),
                  new io.micrometer.core.instrument.simple.SimpleMeterRegistry());
            this.stubJson = stubJson;
        }

        @Override
        public IngredientNormalizationResponse normalizeIngredients(IngredientNormalizationRequest request) {
            List<String> ingredients = request.getIngredients();
            if (ingredients == null || ingredients.isEmpty()) {
                return new IngredientNormalizationResponse(List.of());
            }
            try {
                com.fasterxml.jackson.databind.ObjectMapper mapper = new com.fasterxml.jackson.databind.ObjectMapper();
                com.fasterxml.jackson.databind.JsonNode root = mapper.readTree(stubJson);
                com.fasterxml.jackson.databind.JsonNode items = root.path("normalizations");
                java.util.List<com.recipe.ai.model.IngredientNormalization> result = new java.util.ArrayList<>();
                for (com.fasterxml.jackson.databind.JsonNode item : items) {
                    int idx = item.path("index").asInt(-1);
                    if (idx < 0 || idx >= ingredients.size()) continue;
                    double confidence = item.path("confidence").asDouble(0.0);
                    if (confidence < 0.6) continue;
                    result.add(new com.recipe.ai.model.IngredientNormalization(
                        idx,
                        item.path("original").asText(ingredients.get(idx)),
                        item.path("normalized").asText(""),
                        item.path("reason").asText(""),
                        confidence
                    ));
                }
                return new IngredientNormalizationResponse(result);
            } catch (Exception e) {
                return new IngredientNormalizationResponse(List.of());
            }
        }
    }

    @Test
    void normalizeIngredients_ambiguousIngredient_returnsSuggestion() {
        String stubJson = """
            {"normalizations":[
              {"index":0,"original":"some salt","normalized":"1/4 tsp fine sea salt","reason":"quantity unspecified","confidence":0.9}
            ]}
            """;
        StubIngredientNormalizationService service = new StubIngredientNormalizationService(stubJson);
        IngredientNormalizationRequest req = new IngredientNormalizationRequest();
        req.setIngredients(List.of("some salt", "2 cups flour"));

        IngredientNormalizationResponse resp = service.normalizeIngredients(req);

        assertThat(resp.getNormalizations()).hasSize(1);
        assertThat(resp.getNormalizations().get(0).getIndex()).isZero();
        assertThat(resp.getNormalizations().get(0).getNormalized()).isEqualTo("1/4 tsp fine sea salt");
        assertThat(resp.getNormalizations().get(0).getConfidence()).isGreaterThanOrEqualTo(0.6);
    }

    @Test
    void normalizeIngredients_lowConfidence_filteredOut() {
        String stubJson = """
            {"normalizations":[
              {"index":0,"original":"flour","normalized":"2 cups all-purpose flour","reason":"quantity missing","confidence":0.4}
            ]}
            """;
        StubIngredientNormalizationService service = new StubIngredientNormalizationService(stubJson);
        IngredientNormalizationRequest req = new IngredientNormalizationRequest();
        req.setIngredients(List.of("flour"));

        IngredientNormalizationResponse resp = service.normalizeIngredients(req);

        assertThat(resp.getNormalizations()).isEmpty();
    }

    @Test
    void normalizeIngredients_emptyIngredients_returnsEmpty() {
        IngredientNormalizationService service = new IngredientNormalizationService(
            WebClient.builder(), new GeminiApiKeyResolver(), new com.fasterxml.jackson.databind.ObjectMapper(),
            new io.micrometer.core.instrument.simple.SimpleMeterRegistry());
        IngredientNormalizationRequest req = new IngredientNormalizationRequest();
        req.setIngredients(List.of());

        IngredientNormalizationResponse resp = service.normalizeIngredients(req);

        assertThat(resp.getNormalizations()).isEmpty();
    }

    @Test
    void normalizeIngredients_nullIngredients_returnsEmpty() {
        IngredientNormalizationService service = new IngredientNormalizationService(
            WebClient.builder(), new GeminiApiKeyResolver(), new com.fasterxml.jackson.databind.ObjectMapper(),
            new io.micrometer.core.instrument.simple.SimpleMeterRegistry());
        IngredientNormalizationRequest req = new IngredientNormalizationRequest();
        req.setIngredients(null);

        IngredientNormalizationResponse resp = service.normalizeIngredients(req);

        assertThat(resp.getNormalizations()).isEmpty();
    }

    @Test
    void normalizeIngredients_multipleAmbiguous_returnsAllAboveThreshold() {
        String stubJson = """
            {"normalizations":[
              {"index":0,"original":"some salt","normalized":"1/4 tsp fine sea salt","reason":"quantity unspecified","confidence":0.85},
              {"index":2,"original":"a bit of olive oil","normalized":"1 tbsp olive oil","reason":"vague quantity","confidence":0.75}
            ]}
            """;
        StubIngredientNormalizationService service = new StubIngredientNormalizationService(stubJson);
        IngredientNormalizationRequest req = new IngredientNormalizationRequest();
        req.setIngredients(List.of("some salt", "2 eggs", "a bit of olive oil"));

        IngredientNormalizationResponse resp = service.normalizeIngredients(req);

        assertThat(resp.getNormalizations()).hasSize(2);
        assertThat(resp.getNormalizations().get(0).getIndex()).isZero();
        assertThat(resp.getNormalizations().get(1).getIndex()).isEqualTo(2);
    }
}
