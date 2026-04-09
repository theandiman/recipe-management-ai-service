package com.recipe.ai.controller;

import com.recipe.ai.model.InstructionRefinement;
import com.recipe.ai.model.InstructionRefinementRequest;
import com.recipe.ai.model.InstructionRefinementResponse;
import com.recipe.ai.model.FieldSuggestionRequest;
import com.recipe.ai.model.FieldSuggestionsResponse;
import com.recipe.ai.service.InstructionRefinementService;
import com.recipe.ai.service.FieldSuggestionService;
import com.recipe.ai.service.GeminiApiKeyResolver;
import com.recipe.ai.service.AISuggestionValidator;
import com.recipe.ai.service.RecipeService;
import com.recipe.ai.service.IngredientNormalizationService;
import com.recipe.ai.service.NutritionEstimateService;
import com.recipe.ai.model.NutritionEstimateRequest;
import com.recipe.ai.model.NutritionEstimateResponse;
import com.recipe.ai.model.IngredientNormalizationRequest;
import com.recipe.ai.model.IngredientNormalizationResponse;
import com.recipe.ai.model.Units;
import com.recipe.ai.model.RecipeGenerationRequest;
import com.recipe.ai.model.ImageGenerationRequest;
import com.recipe.shared.model.Recipe;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.ResponseEntity;
import org.springframework.web.reactive.function.client.WebClient;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class RefineInstructionsControllerTest {

    private RecipeController controller;

    static class StubInstructionRefinementService extends InstructionRefinementService {
        private final InstructionRefinementResponse stubResponse;
        StubInstructionRefinementService(InstructionRefinementResponse stubResponse) {
            super(WebClient.builder(), new GeminiApiKeyResolver(), new ObjectMapper());
            this.stubResponse = stubResponse;
        }
        @Override
        public InstructionRefinementResponse refineInstructions(InstructionRefinementRequest request) {
            return stubResponse;
        }
    }

    static class ThrowingInstructionRefinementService extends InstructionRefinementService {
        ThrowingInstructionRefinementService() {
            super(WebClient.builder(), new GeminiApiKeyResolver(), new ObjectMapper());
        }
        @Override
        public InstructionRefinementResponse refineInstructions(InstructionRefinementRequest request) {
            throw new RuntimeException("Simulated Gemini failure");
        }
    }

    static class NoOpRecipeService extends RecipeService {
        NoOpRecipeService() { super(WebClient.builder(), new ObjectMapper(), new AISuggestionValidator()); }
        @Override
        public Recipe generateRecipeModel(RecipeGenerationRequest request) {
            return Recipe.builder().recipeName("Test").servings(2).build();
        }
        @Override
        public Map<String, Object> generateImageFromRequest(ImageGenerationRequest request, boolean forceCurl) {
            return Map.of("status", "skipped");
        }
        @Override
        public Map<String, Object> generateImageForPrompt(String prompt, boolean forceCurl) {
            return Map.of("status", "skipped");
        }
        @Override
        public String generateRecipe(String prompt, List<String> pantryItems, Units units, List<String> dietaryPreferences, List<String> allergies, Integer maxTotalMinutes) {
            return "{}";
        }
    }

    static class NoOpFieldSuggestionService extends FieldSuggestionService {
        NoOpFieldSuggestionService() {
            super(WebClient.builder(), new GeminiApiKeyResolver(), new ObjectMapper());
        }
        @Override
        public FieldSuggestionsResponse suggestFields(FieldSuggestionRequest request) {
            return new FieldSuggestionsResponse(List.of());
        }
    }

    static class NoOpIngredientNormalizationService extends IngredientNormalizationService {
        NoOpIngredientNormalizationService() {
            super(WebClient.builder(), new GeminiApiKeyResolver(), new ObjectMapper());
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
        InstructionRefinementResponse stubResp = new InstructionRefinementResponse(List.of(
            new InstructionRefinement(0, "cook the thing", "Cook over medium heat for 8 minutes.", "Added heat level and time.")
        ));
        controller = new RecipeController(
            new NoOpRecipeService(),
            new NoOpFieldSuggestionService(),
            new StubInstructionRefinementService(stubResp),
            new NoOpIngredientNormalizationService(),
            new NoOpNutritionEstimateService()
        );
    }

    @Test
    void refineInstructions_returnsOkWithRefinements() {
        InstructionRefinementRequest req = new InstructionRefinementRequest();
        req.setInstructions(List.of("cook the thing"));
        req.setRecipeName("Pasta Dish");
        ResponseEntity<InstructionRefinementResponse> resp = controller.refineInstructions(req);
        assertThat(resp.getStatusCode().is2xxSuccessful()).isTrue();
        assertThat(resp.getBody()).isNotNull();
        assertThat(resp.getBody().getRefinements()).hasSize(1);
        assertThat(resp.getBody().getRefinements().get(0).getStepIndex()).isZero();
        assertThat(resp.getBody().getRefinements().get(0).getRefined())
            .isEqualTo("Cook over medium heat for 8 minutes.");
    }

    @Test
    void refineInstructions_emptyResponse_returnsOkWithEmptyList() {
        RecipeController ctrl = new RecipeController(
            new NoOpRecipeService(),
            new NoOpFieldSuggestionService(),
            new StubInstructionRefinementService(new InstructionRefinementResponse(List.of())),
            new NoOpIngredientNormalizationService(),
            new NoOpNutritionEstimateService()
        );
        InstructionRefinementRequest req = new InstructionRefinementRequest();
        req.setInstructions(List.of("Preheat oven to 375°F."));
        ResponseEntity<InstructionRefinementResponse> resp = ctrl.refineInstructions(req);
        assertThat(resp.getStatusCode().is2xxSuccessful()).isTrue();
        assertThat(resp.getBody().getRefinements()).isEmpty();
    }

    @Test
    void refineInstructions_serviceThrows_returnsOkWithEmptyList() {
        RecipeController ctrl = new RecipeController(
            new NoOpRecipeService(),
            new NoOpFieldSuggestionService(),
            new ThrowingInstructionRefinementService(),
            new NoOpIngredientNormalizationService(),
            new NoOpNutritionEstimateService()
        );
        InstructionRefinementRequest req = new InstructionRefinementRequest();
        req.setInstructions(List.of("some step"));
        ResponseEntity<InstructionRefinementResponse> resp = ctrl.refineInstructions(req);
        assertThat(resp.getStatusCode().is2xxSuccessful()).isTrue();
        assertThat(resp.getBody().getRefinements()).isEmpty();
    }
}
