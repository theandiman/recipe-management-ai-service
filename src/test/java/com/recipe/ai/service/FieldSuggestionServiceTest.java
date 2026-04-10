package com.recipe.ai.service;

import com.recipe.ai.model.FieldSuggestion;
import com.recipe.ai.model.FieldSuggestionRequest;
import com.recipe.ai.model.FieldSuggestionsResponse;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.web.reactive.function.client.WebClient;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for FieldSuggestionService covering prompt building,
 * missing-field detection, and response parsing without any real HTTP calls.
 */
class FieldSuggestionServiceTest {

    private FieldSuggestionService service;

    @BeforeEach
    void setUp() {
        service = new FieldSuggestionService(WebClient.builder(), new GeminiApiKeyResolver(), new ObjectMapper(), new io.micrometer.core.instrument.simple.SimpleMeterRegistry());
    }

    // -------------------------------------------------------------------------
    // collectMissingFields
    // -------------------------------------------------------------------------

    @Test
    void collectMissingFields_allEmpty_returnsAllFields() {
        FieldSuggestionRequest req = new FieldSuggestionRequest();
        List<String> missing = service.collectMissingFields(req);
        assertThat(missing).containsExactlyInAnyOrder(
            "recipeName", "description", "prepTime", "cookTime", "servings", "tags"
        );
    }

    @Test
    void collectMissingFields_allPresent_returnsEmpty() {
        FieldSuggestionRequest req = new FieldSuggestionRequest();
        req.setRecipeName("Pasta");
        req.setDescription("Delicious pasta");
        req.setPrepTime("10");
        req.setCookTime("20");
        req.setServings("4");
        req.setTags(List.of("italian", "pasta"));

        List<String> missing = service.collectMissingFields(req);
        assertThat(missing).isEmpty();
    }

    @Test
    void collectMissingFields_blankStrings_areConsideredMissing() {
        FieldSuggestionRequest req = new FieldSuggestionRequest();
        req.setRecipeName("  ");
        req.setDescription("");
        List<String> missing = service.collectMissingFields(req);
        assertThat(missing).contains("recipeName", "description");
    }

    // -------------------------------------------------------------------------
    // buildPrompt
    // -------------------------------------------------------------------------

    @Test
    void buildPrompt_includesRecipeName_andMissingFields() {
        FieldSuggestionRequest req = new FieldSuggestionRequest();
        req.setRecipeName("Spaghetti Bolognese");
        req.setIngredients(List.of("pasta", "ground beef", "tomato"));

        String prompt = service.buildPrompt(req, List.of("description", "prepTime"));

        assertThat(prompt).contains("Spaghetti Bolognese");
        assertThat(prompt).contains("description");
        assertThat(prompt).contains("prepTime");
        assertThat(prompt).contains("pasta, ground beef, tomato");
    }

    @Test
    void buildPrompt_withoutRecipeName_doesNotIncludeNull() {
        FieldSuggestionRequest req = new FieldSuggestionRequest();
        String prompt = service.buildPrompt(req, List.of("recipeName"));
        assertThat(prompt).doesNotContain("null");
        assertThat(prompt).contains("recipeName");
    }

    // -------------------------------------------------------------------------
    // parseGeminiResponse
    // -------------------------------------------------------------------------

    @Test
    void parseGeminiResponse_validResponse_returnsSuggestions() throws Exception {
        String inner = "{\"suggestions\":[{\"field\":\"description\",\"suggestedValue\":\"A rich pasta dish\",\"reason\":\"No description provided\"}]}";
        String body = buildGeminiBody(inner);

        FieldSuggestionsResponse response = service.parseGeminiResponse(body);
        assertThat(response.getSuggestions()).hasSize(1);
        FieldSuggestion s = response.getSuggestions().get(0);
        assertThat(s.getField()).isEqualTo("description");
        assertThat(s.getSuggestedValue()).isEqualTo("A rich pasta dish");
        assertThat(s.getReason()).isEqualTo("No description provided");
    }

    @Test
    void parseGeminiResponse_nullBody_returnsEmpty() {
        FieldSuggestionsResponse response = service.parseGeminiResponse(null);
        assertThat(response.getSuggestions()).isEmpty();
    }

    @Test
    void parseGeminiResponse_blankBody_returnsEmpty() {
        FieldSuggestionsResponse response = service.parseGeminiResponse("   ");
        assertThat(response.getSuggestions()).isEmpty();
    }

    @Test
    void parseGeminiResponse_malformedJson_returnsEmpty() {
        FieldSuggestionsResponse response = service.parseGeminiResponse("{not valid json}");
        assertThat(response.getSuggestions()).isEmpty();
    }

    @Test
    void parseGeminiResponse_emptySuggestionsArray_returnsEmpty() throws Exception {
        String inner = "{\"suggestions\":[]}";
        String body = buildGeminiBody(inner);
        FieldSuggestionsResponse response = service.parseGeminiResponse(body);
        assertThat(response.getSuggestions()).isEmpty();
    }

    // -------------------------------------------------------------------------
    // suggestFields — no valid API key
    // -------------------------------------------------------------------------

    @Test
    void suggestFields_withNoValidApiKey_returnsEmptySuggestions() {
        // GeminiApiKeyResolver with default placeholder key → hasValidApiKey() == false
        FieldSuggestionRequest req = new FieldSuggestionRequest();
        req.setRecipeName("Salad");
        // description, prepTime, etc. all missing → normally would call Gemini

        FieldSuggestionsResponse response = service.suggestFields(req);
        // Should degrade gracefully with empty list
        assertThat(response.getSuggestions()).isEmpty();
    }

    @Test
    void suggestFields_withNullRequest_returnsEmpty() {
        FieldSuggestionsResponse response = service.suggestFields(null);
        assertThat(response.getSuggestions()).isEmpty();
    }

    @Test
    void suggestFields_withAllFieldsPresent_returnsEmpty() {
        FieldSuggestionRequest req = new FieldSuggestionRequest();
        req.setRecipeName("Full Recipe");
        req.setDescription("Complete description");
        req.setPrepTime("15");
        req.setCookTime("30");
        req.setServings("4");
        req.setTags(List.of("italian"));

        FieldSuggestionsResponse response = service.suggestFields(req);
        assertThat(response.getSuggestions()).isEmpty();
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    private String buildGeminiBody(String innerJson) throws Exception {
        ObjectMapper mapper = new ObjectMapper();
        // Escape the inner JSON as a string value in the Gemini response structure
        String escaped = innerJson.replace("\"", "\\\"");
        return "{\"candidates\":[{\"content\":{\"parts\":[{\"text\":\"" + escaped + "\"}]}}]}";
    }
}
