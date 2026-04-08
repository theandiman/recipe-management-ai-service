package com.recipe.ai.controller;

import com.recipe.ai.service.RecipeService;
import com.recipe.ai.model.Units;
import com.recipe.ai.model.FieldSuggestion;
import com.recipe.ai.model.FieldSuggestionRequest;
import com.recipe.ai.model.FieldSuggestionsResponse;
import com.recipe.shared.model.Recipe;
import com.recipe.ai.model.RecipeGenerationRequest;
import com.recipe.ai.model.ImageGenerationRequest;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.ResponseEntity;
import org.springframework.web.reactive.function.client.WebClient;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Lightweight unit tests for the controller that avoid starting a Spring context
 * or using Mockito's inline bytecode instrumentation (which can fail on some JDKs).
 */
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
            Recipe dto = Recipe.builder().recipeName("Test Recipe").servings(4).build();
            return dto;
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

    @BeforeEach
    void setup() {
        this.controller = new RecipeController(new TestRecipeService(), new TestFieldSuggestionService());
    }

    static class TestFieldSuggestionService extends com.recipe.ai.service.FieldSuggestionService {
        public TestFieldSuggestionService() {
            super(WebClient.builder(),
                  new com.recipe.ai.service.GeminiApiKeyResolver(),
                  new com.fasterxml.jackson.databind.ObjectMapper());
        }

        @Override
        public com.recipe.ai.model.FieldSuggestionsResponse suggestFields(com.recipe.ai.model.FieldSuggestionRequest request) {
            return new com.recipe.ai.model.FieldSuggestionsResponse(List.of(
                new com.recipe.ai.model.FieldSuggestion("description", "A delicious test recipe", "Test reason")
            ));
        }
    }

    @Test
    void generateRecipe_allowsEmptyPrompt_andDelegatesToService() throws Exception {
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
    void generateImage_allowsEmptyPrompt_andDelegatesToService() throws Exception {
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
}
