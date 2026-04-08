package com.recipe.ai.controller;

import com.recipe.ai.service.RecipeService;
import com.recipe.ai.service.InstructionRefinementService;
import com.recipe.ai.model.InstructionRefinementRequest;
import com.recipe.ai.model.InstructionRefinementResponse;
import com.recipe.ai.model.Units;
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
            super(WebClient.builder(), new com.fasterxml.jackson.databind.ObjectMapper());
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

    static class TestInstructionRefinementService extends InstructionRefinementService {
        public TestInstructionRefinementService() {
            super(WebClient.builder(), new com.recipe.ai.service.GeminiApiKeyResolver(), new com.fasterxml.jackson.databind.ObjectMapper());
        }

        @Override
        public InstructionRefinementResponse refineInstructions(InstructionRefinementRequest request) {
            return new InstructionRefinementResponse(List.of());
        }
    }

    @BeforeEach
    void setup() {
        this.controller = new RecipeController(
            new TestRecipeService(),
            new TestInstructionRefinementService()
        );
    }

    @Test
    void generateRecipe_allowsEmptyPrompt_andDelegatesToService() throws Exception {
        RecipeGenerationRequest request = new RecipeGenerationRequest();
        request.setPrompt("");
        request.setPantryItems(List.of());

        ResponseEntity<Recipe> resp = controller.generateRecipe(request);

        assertThat(resp.getStatusCode().is2xxSuccessful()).isTrue();
        assertThat(resp.getBody()).isNotNull();
        assertThat(resp.getBody().getRecipeName()).isEqualTo("Test Recipe");
    }

    @Test
    void generateImage_allowsEmptyPrompt_andDelegatesToService() throws Exception {
        ImageGenerationRequest request = new ImageGenerationRequest();
        request.setPrompt("");

        ResponseEntity<Map<String, Object>> resp = controller.generateImage(request, false);

        assertThat(resp.getStatusCode().is2xxSuccessful()).isTrue();
        assertThat(resp.getBody()).containsEntry("status", "skipped");
    }


}
