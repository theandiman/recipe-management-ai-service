package com.recipe.ai.controller;

import com.recipe.ai.model.FieldSuggestion;
import com.recipe.ai.model.FieldSuggestionRequest;
import com.recipe.ai.model.FieldSuggestionsResponse;
import com.recipe.ai.model.InstructionRefinementRequest;
import com.recipe.ai.model.InstructionRefinementResponse;
import com.recipe.ai.service.FieldSuggestionService;
import com.recipe.ai.service.IngredientNormalizationService;
import com.recipe.ai.model.IngredientNormalizationRequest;
import com.recipe.ai.model.IngredientNormalizationResponse;
import com.recipe.ai.service.InstructionRefinementService;
import com.recipe.ai.service.NutritionEstimateService;
import com.recipe.ai.service.GeminiApiKeyResolver;
import com.recipe.ai.service.AISuggestionValidator;
import com.recipe.ai.model.NutritionEstimateRequest;
import com.recipe.ai.model.NutritionEstimateResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.ResponseEntity;
import org.springframework.web.reactive.function.client.WebClient;

import com.fasterxml.jackson.databind.ObjectMapper;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class SuggestFieldsControllerTest {

    private RecipeController controller;

    static class StubFieldSuggestionService extends FieldSuggestionService {
        private final FieldSuggestionsResponse stubResponse;
        StubFieldSuggestionService(FieldSuggestionsResponse stubResponse) {
            super(WebClient.builder(), new GeminiApiKeyResolver(), new ObjectMapper(), new io.micrometer.core.instrument.simple.SimpleMeterRegistry());
            this.stubResponse = stubResponse;
        }
        @Override
        public FieldSuggestionsResponse suggestFields(FieldSuggestionRequest request) {
            return stubResponse;
        }
    }

    static class NoOpRecipeService extends com.recipe.ai.service.RecipeService {
        NoOpRecipeService() {
            super(WebClient.builder(), new ObjectMapper(), new AISuggestionValidator());
        }
    }

    static class NoOpInstructionRefinementService extends InstructionRefinementService {
        NoOpInstructionRefinementService() {
            super(WebClient.builder(), new GeminiApiKeyResolver(), new ObjectMapper(), new io.micrometer.core.instrument.simple.SimpleMeterRegistry());
        }
        @Override
        public InstructionRefinementResponse refineInstructions(InstructionRefinementRequest request) {
            return new InstructionRefinementResponse(List.of());
        }
    }

    static class NoOpIngredientNormalizationService extends IngredientNormalizationService {
        NoOpIngredientNormalizationService() {
            super(WebClient.builder(), new GeminiApiKeyResolver(), new ObjectMapper(), new io.micrometer.core.instrument.simple.SimpleMeterRegistry());
        }
        @Override
        public IngredientNormalizationResponse normalizeIngredients(IngredientNormalizationRequest request) {
            return new IngredientNormalizationResponse(List.of());
        }
    }

    static class NoOpNutritionEstimateService extends NutritionEstimateService {
        NoOpNutritionEstimateService() {
            super(WebClient.builder(), new GeminiApiKeyResolver(), new ObjectMapper());
        }
        @Override
        public NutritionEstimateResponse estimateNutrition(NutritionEstimateRequest request) {
            return new NutritionEstimateResponse(null, null);
        }
    }

    @BeforeEach
    void setUp() {
        FieldSuggestionsResponse stubResp = new FieldSuggestionsResponse(List.of(
            new FieldSuggestion("description", "A tasty pasta dish", "No description provided")
        ));
        controller = new RecipeController(
            new NoOpRecipeService(),
            new StubFieldSuggestionService(stubResp),
            new NoOpInstructionRefinementService(),
            new NoOpIngredientNormalizationService(),
            new NoOpNutritionEstimateService()
        );
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
        RecipeController ctrl = new RecipeController(
            new NoOpRecipeService(),
            new StubFieldSuggestionService(empty),
            new NoOpInstructionRefinementService(),
            new NoOpIngredientNormalizationService(),
            new NoOpNutritionEstimateService()
        );

        ResponseEntity<FieldSuggestionsResponse> resp = ctrl.suggestFields(new FieldSuggestionRequest());

        assertThat(resp.getStatusCode().is2xxSuccessful()).isTrue();
        assertThat(resp.getBody().getSuggestions()).isEmpty();
    }
}
