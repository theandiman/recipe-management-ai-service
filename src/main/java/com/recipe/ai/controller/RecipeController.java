package com.recipe.ai.controller;

import com.recipe.ai.service.RecipeService;
import com.recipe.shared.model.Recipe;
import com.recipe.ai.model.RecipeGenerationRequest;
import com.recipe.ai.model.ImageGenerationRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * REST Controller to expose the AI recipe generation endpoint.
 * REST endpoint for recipe generation requests.
 */
@RestController
@RequestMapping("/api/recipes")
// Allow CORS from local dev origins if needed during development
@CrossOrigin
public class RecipeController {

    private final RecipeService recipeService;
    private static final Logger log = LoggerFactory.getLogger(RecipeController.class);

    public RecipeController(RecipeService recipeService) {
        this.recipeService = recipeService;
    }

    /**
     * Handles the POST request to generate a recipe.
     * Accepts a RecipeGenerationRequest DTO for type-safe input handling.
     *
     * @param request The recipe generation request DTO
    * @return The generated shared Recipe, or an error response
     */
    @PostMapping("/generate")
    public ResponseEntity<Recipe> generateRecipe(@RequestBody RecipeGenerationRequest request) {
        try {
            Recipe recipe = recipeService.generateRecipeModel(request);
            
            if (recipe != null) {
                return new ResponseEntity<>(recipe, HttpStatus.OK);
            } else {
                return new ResponseEntity<>(HttpStatus.INTERNAL_SERVER_ERROR);
            }
        } catch (Exception e) {
            log.error("Error generating recipe: {}", e.getMessage(), e);
            return new ResponseEntity<>(HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }

    /**
     * POST /api/recipes/image/generate
     * Accepts an ImageGenerationRequest DTO that can contain either a simple prompt
     * or a full recipe context for richer image generation.
     */
    @PostMapping("/image/generate")
    public ResponseEntity<Map<String, Object>> generateImage(@RequestBody ImageGenerationRequest request,
                                                             @RequestParam(name = "forceCurl", required = false, defaultValue = "false") boolean forceCurl) {
        try {
            log.info("Image generation request received - hasRecipe: {}, hasPrompt: {}", 
                    request.getRecipe() != null, 
                    request.getPrompt() != null && !request.getPrompt().isBlank());
            if (request.getRecipe() != null) {
                log.info("Recipe context: name='{}', ingredients={}", 
                        request.getRecipe().getRecipeName(),
                        request.getRecipe().getIngredients() != null ? request.getRecipe().getIngredients().size() : 0);
            }
            Map<String, Object> meta = recipeService.generateImageFromRequest(request, forceCurl);
            return new ResponseEntity<>(meta, HttpStatus.OK);
        } catch (Exception e) {
            log.error("Error generating image: {}", e.getMessage(), e);
            return new ResponseEntity<>(Map.of("status", "failed", "errorMessage", e.getMessage()), HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }
}
