package com.recipe.ai.controller;

import com.recipe.ai.model.FieldSuggestionRequest;
import com.recipe.ai.model.FieldSuggestionsResponse;
import com.recipe.ai.model.InstructionRefinementRequest;
import com.recipe.ai.model.InstructionRefinementResponse;
import com.recipe.ai.service.FieldSuggestionService;
import com.recipe.ai.service.InstructionRefinementService;
import com.recipe.ai.service.RecipeService;
import com.recipe.ai.service.AISuggestionValidationException;
import com.recipe.shared.model.Recipe;
import com.recipe.ai.model.RecipeGenerationRequest;
import com.recipe.ai.model.ImageGenerationRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * REST Controller to expose the AI recipe generation endpoint.
 */
@RestController
@RequestMapping("/api/recipes")
@CrossOrigin
public class RecipeController {

    private final RecipeService recipeService;
    private final FieldSuggestionService fieldSuggestionService;
    private final InstructionRefinementService instructionRefinementService;
    private static final Logger log = LoggerFactory.getLogger(RecipeController.class);

    public RecipeController(RecipeService recipeService,
                            FieldSuggestionService fieldSuggestionService,
                            InstructionRefinementService instructionRefinementService) {
        this.recipeService = recipeService;
        this.fieldSuggestionService = fieldSuggestionService;
        this.instructionRefinementService = instructionRefinementService;
    }

    @PostMapping("/generate")
    public ResponseEntity<Object> generateRecipe(@RequestBody RecipeGenerationRequest request) {
        try {
            Recipe recipe = recipeService.generateRecipeModel(request);
            if (recipe != null) {
                return new ResponseEntity<>(recipe, HttpStatus.OK);
            } else {
                return new ResponseEntity<>(HttpStatus.INTERNAL_SERVER_ERROR);
            }
        } catch (AISuggestionValidationException e) {
            log.warn("AI suggestion failed schema validation: {}", e.getViolations());
            return ResponseEntity.badRequest()
                    .body(Map.of("error", "AI suggestion failed validation", "violations", e.getViolations()));
        } catch (Exception e) {
            log.error("Error generating recipe: {}", e.getMessage(), e);
            return new ResponseEntity<>(HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }

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

    /**
     * POST /api/recipes/suggest-fields
     * Accepts a partial recipe and returns AI-generated suggestions for
     * missing or low-quality fields.
     */
    @PostMapping("/suggest-fields")
    public ResponseEntity<FieldSuggestionsResponse> suggestFields(@RequestBody FieldSuggestionRequest request) {
        try {
            long start = System.currentTimeMillis();
            FieldSuggestionsResponse response = fieldSuggestionService.suggestFields(request);
            long latencyMs = System.currentTimeMillis() - start;
            log.info("suggest-fields: returned {} suggestion(s) in {}ms",
                    response.getSuggestions().size(), latencyMs);
            return new ResponseEntity<>(response, HttpStatus.OK);
        } catch (Exception e) {
            log.error("Error in suggest-fields: {}", e.getMessage(), e);
            return new ResponseEntity<>(new FieldSuggestionsResponse(List.of()), HttpStatus.OK);
        }
    }

    /**
     * POST /api/recipes/refine-instructions
     * Accepts a list of instruction steps and returns AI-refined versions for
     * steps that were actually improved.
     */
    @PostMapping("/refine-instructions")
    public ResponseEntity<InstructionRefinementResponse> refineInstructions(
            @RequestBody InstructionRefinementRequest request) {
        try {
            long start = System.currentTimeMillis();
            InstructionRefinementResponse response = instructionRefinementService.refineInstructions(request);
            long latencyMs = System.currentTimeMillis() - start;
            log.info("refine-instructions: returned {} refinement(s) in {}ms",
                    response.getRefinements().size(), latencyMs);
            return new ResponseEntity<>(response, HttpStatus.OK);
        } catch (Exception e) {
            log.error("Error in refine-instructions: {}", e.getMessage(), e);
            return new ResponseEntity<>(new InstructionRefinementResponse(List.of()), HttpStatus.OK);
        }
    }
}
