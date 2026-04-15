package com.recipe.ai.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.recipe.ai.model.FieldSuggestionRequest;
import com.recipe.ai.model.FieldSuggestionsResponse;
import com.recipe.ai.model.IngredientNormalizationRequest;
import com.recipe.ai.model.IngredientNormalizationResponse;
import com.recipe.ai.model.InstructionRefinementRequest;
import com.recipe.ai.model.InstructionRefinementResponse;
import com.recipe.ai.model.NutrientValue;
import com.recipe.ai.model.NutritionEstimate;
import com.recipe.ai.model.NutritionEstimateRequest;
import com.recipe.ai.model.NutritionEstimateResponse;
import com.recipe.ai.service.AISuggestionValidator;
import com.recipe.ai.service.FieldSuggestionService;
import com.recipe.ai.service.GeminiApiKeyResolver;
import com.recipe.ai.service.IngredientNormalizationService;
import com.recipe.ai.service.InstructionRefinementService;
import com.recipe.ai.service.NutritionEstimateService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.ResponseEntity;
import org.springframework.web.reactive.function.client.WebClient;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * BDD controller tests for POST /api/recipes/estimate-nutrition
 */
class EstimateNutritionControllerTest {

    private RecipeController controller;

    static class StubNutritionEstimateService extends NutritionEstimateService {
        private final NutritionEstimateResponse stubResponse;
        StubNutritionEstimateService(NutritionEstimateResponse stubResponse) {
            super(WebClient.builder(), new GeminiApiKeyResolver(), new ObjectMapper());
            this.stubResponse = stubResponse;
        }
        @Override
        public NutritionEstimateResponse estimateNutrition(NutritionEstimateRequest request) {
            return stubResponse;
        }
    }

    static class ThrowingNutritionEstimateService extends NutritionEstimateService {
        ThrowingNutritionEstimateService() {
            super(WebClient.builder(), new GeminiApiKeyResolver(), new ObjectMapper());
        }
        @Override
        public NutritionEstimateResponse estimateNutrition(NutritionEstimateRequest request) {
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

    static class NoOpIngredientNormalizationService extends IngredientNormalizationService {
        NoOpIngredientNormalizationService() {
            super(WebClient.builder(), new GeminiApiKeyResolver(), new ObjectMapper(), new io.micrometer.core.instrument.simple.SimpleMeterRegistry());
        }
        @Override
        public IngredientNormalizationResponse normalizeIngredients(IngredientNormalizationRequest request) {
            return new IngredientNormalizationResponse(List.of());
        }
    }

    @BeforeEach
    void setUp() {
        NutrientValue cal = new NutrientValue(350, "kcal", false);
        NutrientValue prot = new NutrientValue(12, "g", false);
        NutrientValue carb = new NutrientValue(45, "g", false);
        NutrientValue fat = new NutrientValue(10, "g", false);
        NutrientValue fiber = new NutrientValue(4, "g", false);
        NutritionEstimate perServing = new NutritionEstimate(cal, prot, carb, fat, fiber, List.of(), false);
        NutritionEstimate wholeRecipe = new NutritionEstimate(
            new NutrientValue(700, "kcal", false),
            new NutrientValue(24, "g", false),
            new NutrientValue(90, "g", false),
            new NutrientValue(20, "g", false),
            new NutrientValue(8, "g", false),
            List.of(), false);
        NutritionEstimateResponse stubResp = new NutritionEstimateResponse(perServing, wholeRecipe);

        controller = new RecipeController(
            new NoOpRecipeService(),
            new NoOpFieldSuggestionService(),
            new NoOpInstructionRefinementService(),
            new NoOpIngredientNormalizationService(),
            new StubNutritionEstimateService(stubResp)
        );
    }

    @Test
    void estimateNutrition_validIngredients_returnsBothEstimates() {
        NutritionEstimateRequest req = new NutritionEstimateRequest();
        req.setIngredients(List.of("2 cups flour", "1 cup sugar", "2 eggs"));
        req.setServings(2);
        req.setRecipeName("Cake");

        ResponseEntity<NutritionEstimateResponse> resp = controller.estimateNutrition(req);

        assertThat(resp.getStatusCode().is2xxSuccessful()).isTrue();
        assertThat(resp.getBody()).isNotNull();
        assertThat(resp.getBody().getPerServing()).isNotNull();
        assertThat(resp.getBody().getWholeRecipe()).isNotNull();
        assertThat(resp.getBody().getPerServing().getCalories().getValue()).isEqualTo(350.0);
        assertThat(resp.getBody().getWholeRecipe().getCalories().getValue()).isEqualTo(700.0);
    }

    @Test
    void estimateNutrition_emptyIngredients_returnsNullEstimates() {
        RecipeController ctrl = new RecipeController(
            new NoOpRecipeService(),
            new NoOpFieldSuggestionService(),
            new NoOpInstructionRefinementService(),
            new NoOpIngredientNormalizationService(),
            new StubNutritionEstimateService(new NutritionEstimateResponse(null, null))
        );
        NutritionEstimateRequest req = new NutritionEstimateRequest();
        req.setIngredients(List.of());

        ResponseEntity<NutritionEstimateResponse> resp = ctrl.estimateNutrition(req);

        assertThat(resp.getStatusCode().is2xxSuccessful()).isTrue();
        assertThat(resp.getBody().getPerServing()).isNull();
        assertThat(resp.getBody().getWholeRecipe()).isNull();
    }

    @Test
    void estimateNutrition_serviceThrows_returnsOkWithNullEstimates() {
        RecipeController ctrl = new RecipeController(
            new NoOpRecipeService(),
            new NoOpFieldSuggestionService(),
            new NoOpInstructionRefinementService(),
            new NoOpIngredientNormalizationService(),
            new ThrowingNutritionEstimateService()
        );
        NutritionEstimateRequest req = new NutritionEstimateRequest();
        req.setIngredients(List.of("2 cups flour"));

        ResponseEntity<NutritionEstimateResponse> resp = ctrl.estimateNutrition(req);

        assertThat(resp.getStatusCode().is2xxSuccessful()).isTrue();
        assertThat(resp.getBody().getPerServing()).isNull();
        assertThat(resp.getBody().getWholeRecipe()).isNull();
    }
}
