package com.recipe.ai.controller;

import com.recipe.ai.service.RecipeService;
import com.recipe.ai.service.InstructionRefinementService;
import com.recipe.ai.service.IngredientNormalizationService;
import com.recipe.ai.service.NutritionEstimateService;
import com.recipe.ai.model.IngredientNormalizationRequest;
import com.recipe.ai.model.IngredientNormalizationResponse;
import com.recipe.ai.model.NutritionEstimateRequest;
import com.recipe.ai.model.NutritionEstimateResponse;
import com.recipe.ai.service.FieldSuggestionService;
import com.recipe.ai.model.InstructionRefinementRequest;
import com.recipe.ai.model.InstructionRefinementResponse;
import com.recipe.ai.model.Units;
import com.recipe.ai.model.FieldSuggestion;
import com.recipe.ai.model.FieldSuggestionRequest;
import com.recipe.ai.model.FieldSuggestionsResponse;
import com.recipe.shared.model.Recipe;
import com.recipe.ai.model.RecipeGenerationRequest;
import com.recipe.ai.model.ImageGenerationRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.ResponseEntity;
import org.springframework.web.reactive.function.client.WebClient;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

public class RecipeControllerTest {

    private RecipeController controller;

    static class TestRecipeService extends RecipeService {
        public TestRecipeService() {
            super(WebClient.builder(), new com.fasterxml.jackson.databind.ObjectMapper(), new com.recipe.ai.service.AISuggestionValidator());
        }

        @Override
        public String generateRecipe(String prompt, List<String> pantryItems, Units units, List<String> dietaryPreferences, List<String> allergies, Integer maxTotalMinutes) {
            return "{}";
        }

        @Override
        public Recipe generateRecipeModel(RecipeGenerationRequest request) {
            return Recipe.builder().recipeName("Test Recipe").servings(4).build();
        }

        @Override
        public Map<String, Object> generateImageForPrompt(String prompt, boolean forceCurl) {
            return Map.of("status", "skipped");
        }

        @Override
        public Map<String, Object> generateImageFromRequest(ImageGenerationRequest request, boolean forceCurl) {
            return Map.of("status", "skipped");
        }
    }

    static class TestFieldSuggestionService extends FieldSuggestionService {
        public TestFieldSuggestionService() {
            super(WebClient.builder(),
                  new com.recipe.ai.service.GeminiApiKeyResolver(),
                  new com.fasterxml.jackson.databind.ObjectMapper(),
                  new io.micrometer.core.instrument.simple.SimpleMeterRegistry());
        }

        @Override
        public FieldSuggestionsResponse suggestFields(FieldSuggestionRequest request) {
            return new FieldSuggestionsResponse(List.of(
                new FieldSuggestion("description", "A delicious test recipe", "Test reason")
            ));
        }
    }

    static class TestInstructionRefinementService extends InstructionRefinementService {
        public TestInstructionRefinementService() {
            super(WebClient.builder(), new com.recipe.ai.service.GeminiApiKeyResolver(), new com.fasterxml.jackson.databind.ObjectMapper(),
                  new io.micrometer.core.instrument.simple.SimpleMeterRegistry());
        }

        @Override
        public InstructionRefinementResponse refineInstructions(InstructionRefinementRequest request) {
            return new InstructionRefinementResponse(List.of());
        }
    }

    static class NoOpIngredientNormalizationService extends IngredientNormalizationService {
        public NoOpIngredientNormalizationService() {
            super(WebClient.builder(), new com.recipe.ai.service.GeminiApiKeyResolver(), new com.fasterxml.jackson.databind.ObjectMapper(),
                  new io.micrometer.core.instrument.simple.SimpleMeterRegistry());
        }
        @Override
        public IngredientNormalizationResponse normalizeIngredients(IngredientNormalizationRequest request) {
            return new IngredientNormalizationResponse(List.of());
        }
    }

    static class NoOpNutritionEstimateService extends NutritionEstimateService {
        public NoOpNutritionEstimateService() {
            super(WebClient.builder(), new com.recipe.ai.service.GeminiApiKeyResolver(), new com.fasterxml.jackson.databind.ObjectMapper());
        }
        @Override
        public NutritionEstimateResponse estimateNutrition(NutritionEstimateRequest request) {
            return new NutritionEstimateResponse(null, null);
        }
    }

    @BeforeEach
    void setup() {
        this.controller = new RecipeController(
            new TestRecipeService(),
            new TestFieldSuggestionService(),
            new TestInstructionRefinementService(),
            new NoOpIngredientNormalizationService(),
            new NoOpNutritionEstimateService()
        );
    }

    @Test
    void generateRecipe_allowsEmptyPrompt_andDelegatesToService() {
        RecipeGenerationRequest request = new RecipeGenerationRequest();
        request.setPrompt("");
        request.setPantryItems(List.of());

        ResponseEntity<?> resp = controller.generateRecipe(request);

        assertThat(resp.getStatusCode().is2xxSuccessful()).isTrue();
        assertThat(resp.getBody()).isNotNull();
        assertThat(resp.getBody()).isInstanceOf(Recipe.class);
        assertThat(((Recipe) resp.getBody()).getRecipeName()).isEqualTo("Test Recipe");
    }

    @Test
    void generateImage_allowsEmptyPrompt_andDelegatesToService() {
        ImageGenerationRequest request = new ImageGenerationRequest();
        request.setPrompt("");

        ResponseEntity<Map<String, Object>> resp = controller.generateImage(request, false);

        assertThat(resp.getStatusCode().is2xxSuccessful()).isTrue();
        assertThat(resp.getBody()).containsEntry("status", "skipped");
    }

    @Test
    void suggestFields_delegatesToService_andReturnsOk() {
        FieldSuggestionRequest request = new FieldSuggestionRequest();
        request.setRecipeName("Carbonara");

        ResponseEntity<FieldSuggestionsResponse> resp = controller.suggestFields(request);

        assertThat(resp.getStatusCode().is2xxSuccessful()).isTrue();
        assertThat(resp.getBody()).isNotNull();
        assertThat(resp.getBody().getSuggestions()).isNotEmpty();
        assertThat(resp.getBody().getSuggestions().get(0).getField()).isEqualTo("description");
    }

    @Test
    void refineInstructions_delegatesToService_andReturnsOk() {
        InstructionRefinementRequest request = new InstructionRefinementRequest();
        request.setInstructions(List.of("Boil water", "Add pasta"));

        ResponseEntity<InstructionRefinementResponse> resp = controller.refineInstructions(request);

        assertThat(resp.getStatusCode().is2xxSuccessful()).isTrue();
        assertThat(resp.getBody()).isNotNull();
        assertThat(resp.getBody().getRefinements()).isEmpty();
    }
}
