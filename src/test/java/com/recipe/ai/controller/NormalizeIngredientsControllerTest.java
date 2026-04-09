package com.recipe.ai.controller;

import com.recipe.ai.model.FieldSuggestionRequest;
import com.recipe.ai.model.FieldSuggestionsResponse;
import com.recipe.ai.model.IngredientNormalization;
import com.recipe.ai.model.IngredientNormalizationRequest;
import com.recipe.ai.model.IngredientNormalizationResponse;
import com.recipe.ai.model.InstructionRefinementRequest;
import com.recipe.ai.model.InstructionRefinementResponse;
import com.recipe.ai.service.FieldSuggestionService;
import com.recipe.ai.service.GeminiApiKeyResolver;
import com.recipe.ai.service.IngredientNormalizationService;
import com.recipe.ai.service.InstructionRefinementService;
import com.recipe.ai.service.AISuggestionValidator;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.ResponseEntity;
import org.springframework.web.reactive.function.client.WebClient;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * BDD controller tests for POST /api/recipes/normalize-ingredients
 *
 * Scenario 1: Ambiguous ingredients are normalized
 *   Given a recipe with ambiguous ingredient lines
 *   When POST /normalize-ingredients is called
 *   Then a 200 OK response with normalization suggestions is returned
 *
 * Scenario 2: Empty ingredient list returns empty normalizations
 *   Given an empty ingredient list
 *   When POST /normalize-ingredients is called
 *   Then a 200 OK response with an empty normalizations list is returned
 *
 * Scenario 3: Service failure gracefully returns empty list
 *   Given the normalization service throws an exception
 *   When POST /normalize-ingredients is called
 *   Then a 200 OK response with an empty normalizations list is returned
 */
class NormalizeIngredientsControllerTest {

    private RecipeController controller;

    static class StubIngredientNormalizationService extends IngredientNormalizationService {
        private final IngredientNormalizationResponse stubResponse;
        StubIngredientNormalizationService(IngredientNormalizationResponse stubResponse) {
            super(WebClient.builder(), new GeminiApiKeyResolver(), new ObjectMapper(), new io.micrometer.core.instrument.simple.SimpleMeterRegistry());
            this.stubResponse = stubResponse;
        }
        @Override
        public IngredientNormalizationResponse normalizeIngredients(IngredientNormalizationRequest request) {
            return stubResponse;
        }
    }

    static class ThrowingIngredientNormalizationService extends IngredientNormalizationService {
        ThrowingIngredientNormalizationService() {
            super(WebClient.builder(), new GeminiApiKeyResolver(), new ObjectMapper(), new io.micrometer.core.instrument.simple.SimpleMeterRegistry());
        }
        @Override
        public IngredientNormalizationResponse normalizeIngredients(IngredientNormalizationRequest request) {
            throw new RuntimeException("Simulated Gemini failure");
        }
    }

    static class NoOpRecipeService extends com.recipe.ai.service.RecipeService {
        NoOpRecipeService() {
            super(WebClient.builder(), new ObjectMapper(), new AISuggestionValidator());
        }
    }

    static class NoOpFieldSuggestionService extends FieldSuggestionService {
        NoOpFieldSuggestionService() {
            super(WebClient.builder(), new GeminiApiKeyResolver(), new ObjectMapper(), new io.micrometer.core.instrument.simple.SimpleMeterRegistry());
        }
        @Override
        public FieldSuggestionsResponse suggestFields(FieldSuggestionRequest request) {
            return new FieldSuggestionsResponse(List.of());
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

    @BeforeEach
    void setUp() {
        IngredientNormalizationResponse stubResp = new IngredientNormalizationResponse(List.of(
            new IngredientNormalization(0, "some salt", "1/4 tsp fine sea salt", "Quantity unspecified", 0.9)
        ));
        controller = new RecipeController(
            new NoOpRecipeService(),
            new NoOpFieldSuggestionService(),
            new NoOpInstructionRefinementService(),
            new StubIngredientNormalizationService(stubResp)
        );
    }

    @Test
    void normalizeIngredients_ambiguousIngredients_returnsNormalizations() {
        IngredientNormalizationRequest req = new IngredientNormalizationRequest();
        req.setIngredients(List.of("some salt", "2 cups flour"));
        req.setRecipeName("Bread");

        ResponseEntity<IngredientNormalizationResponse> resp = controller.normalizeIngredients(req);

        assertThat(resp.getStatusCode().is2xxSuccessful()).isTrue();
        assertThat(resp.getBody()).isNotNull();
        assertThat(resp.getBody().getNormalizations()).hasSize(1);
        assertThat(resp.getBody().getNormalizations().get(0).getIndex()).isZero();
        assertThat(resp.getBody().getNormalizations().get(0).getNormalized()).isEqualTo("1/4 tsp fine sea salt");
        assertThat(resp.getBody().getNormalizations().get(0).getConfidence()).isGreaterThanOrEqualTo(0.6);
    }

    @Test
    void normalizeIngredients_emptyIngredients_returnsEmptyList() {
        RecipeController ctrl = new RecipeController(
            new NoOpRecipeService(),
            new NoOpFieldSuggestionService(),
            new NoOpInstructionRefinementService(),
            new StubIngredientNormalizationService(new IngredientNormalizationResponse(List.of()))
        );
        IngredientNormalizationRequest req = new IngredientNormalizationRequest();
        req.setIngredients(List.of());

        ResponseEntity<IngredientNormalizationResponse> resp = ctrl.normalizeIngredients(req);

        assertThat(resp.getStatusCode().is2xxSuccessful()).isTrue();
        assertThat(resp.getBody().getNormalizations()).isEmpty();
    }

    @Test
    void normalizeIngredients_serviceThrows_returnsOkWithEmptyList() {
        RecipeController ctrl = new RecipeController(
            new NoOpRecipeService(),
            new NoOpFieldSuggestionService(),
            new NoOpInstructionRefinementService(),
            new ThrowingIngredientNormalizationService()
        );
        IngredientNormalizationRequest req = new IngredientNormalizationRequest();
        req.setIngredients(List.of("some ingredient"));

        ResponseEntity<IngredientNormalizationResponse> resp = ctrl.normalizeIngredients(req);

        assertThat(resp.getStatusCode().is2xxSuccessful()).isTrue();
        assertThat(resp.getBody().getNormalizations()).isEmpty();
    }
}
