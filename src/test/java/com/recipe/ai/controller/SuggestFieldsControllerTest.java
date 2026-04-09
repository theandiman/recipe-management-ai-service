package com.recipe.ai.controller;

import com.recipe.ai.model.FieldSuggestion;
import com.recipe.ai.model.FieldSuggestionRequest;
import com.recipe.ai.model.FieldSuggestionsResponse;
import com.recipe.ai.service.FieldSuggestionService;
import com.recipe.ai.service.GeminiApiKeyResolver;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.ResponseEntity;
import org.springframework.web.reactive.function.client.WebClient;

import com.fasterxml.jackson.databind.ObjectMapper;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for the suggest-fields endpoint.
 * Uses lightweight test doubles to avoid starting a Spring context.
 */
class SuggestFieldsControllerTest {

    private RecipeController controller;

    static class StubFieldSuggestionService extends FieldSuggestionService {
        private final FieldSuggestionsResponse stubResponse;

        StubFieldSuggestionService(FieldSuggestionsResponse stubResponse) {
            super(WebClient.builder(), new GeminiApiKeyResolver(), new ObjectMapper());
            this.stubResponse = stubResponse;
        }

        @Override
        public FieldSuggestionsResponse suggestFields(FieldSuggestionRequest request) {
            return stubResponse;
        }
    }

    static class NoOpRecipeService extends com.recipe.ai.service.RecipeService {
        NoOpRecipeService() {
            super(WebClient.builder(), new ObjectMapper());
        }
    }

    @BeforeEach
    void setUp() {
        FieldSuggestionsResponse stubResp = new FieldSuggestionsResponse(List.of(
            new FieldSuggestion("description", "A tasty pasta dish", "No description provided")
        ));
        controller = new RecipeController(new NoOpRecipeService(), new StubFieldSuggestionService(stubResp));
    }

    @Test
    void suggestFields_returnsOkWithSuggestions() {
        FieldSuggestionRequest req = new FieldSuggestionRequest();
        req.setRecipeName("Pasta Carbonara");

        ResponseEntity<FieldSuggestionsResponse> resp = controller.suggestFields(req);

        assertThat(resp.getStatusCode().is2xxSuccessful()).isTrue();
        assertThat(resp.getBody()).isNotNull();
        assertThat(resp.getBody().getSuggestions()).hasSize(1);
        assertThat(resp.getBody().getSuggestions().get(0).getField()).isEqualTo("description");
    }

    @Test
    void suggestFields_withEmptyResponse_returnsOkWithEmptyList() {
        FieldSuggestionsResponse empty = new FieldSuggestionsResponse(List.of());
        RecipeController ctrl = new RecipeController(new NoOpRecipeService(), new StubFieldSuggestionService(empty));

        ResponseEntity<FieldSuggestionsResponse> resp = ctrl.suggestFields(new FieldSuggestionRequest());

        assertThat(resp.getStatusCode().is2xxSuccessful()).isTrue();
        assertThat(resp.getBody().getSuggestions()).isEmpty();
    }
}
