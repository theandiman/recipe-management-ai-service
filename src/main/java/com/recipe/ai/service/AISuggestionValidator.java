package com.recipe.ai.service;

import com.recipe.shared.model.Recipe;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

/**
 * Validates AI-generated recipe suggestions against field constraints and sanitizes text content.
 *
 * Concerns addressed:
 * - Schema validation: required fields, length limits, numeric range checks
 * - Sanitization: strip HTML/script tags from all text fields
 * - Observability: all failures logged with [AI_AUTHORING] prefix for separate monitoring
 *
 * This validator is intentionally separate from RecipeSafetyValidator, which handles
 * allergen/dietary concerns — a different concern.
 */
@Component
public class AISuggestionValidator {

    private static final Logger log = LoggerFactory.getLogger(AISuggestionValidator.class);

    // Log marker for separate observability of AI validation failures
    static final String AI_AUTHORING_TAG = "[AI_AUTHORING]";

    static final int MAX_RECIPE_NAME_LENGTH = 200;
    static final int MAX_DESCRIPTION_LENGTH = 2000;
    static final int MAX_TAG_LENGTH = 50;
    static final int MAX_TAG_COUNT = 20;
    static final int MIN_SERVINGS = 1;
    static final int MAX_SERVINGS = 100;
    static final int MAX_INGREDIENT_LENGTH = 500;
    static final int MAX_INGREDIENTS_COUNT = 100;
    static final int MAX_INSTRUCTION_LENGTH = 2000;
    static final int MAX_INSTRUCTIONS_COUNT = 100;

    // Pattern stripping script/style elements including their content
    private static final Pattern SCRIPT_STYLE_PATTERN =
            Pattern.compile("<(script|style)[^>]*>[\\s\\S]*?</\\1>", Pattern.CASE_INSENSITIVE | Pattern.DOTALL);
    // Pattern matching HTML tags and common injection vectors
    private static final Pattern HTML_TAG_PATTERN = Pattern.compile("<[^>]+>", Pattern.DOTALL);
    // Dangerous chars: null bytes and ASCII control chars except tab/newline/CR
    private static final Pattern CONTROL_CHAR_PATTERN = Pattern.compile("[\\x00-\\x08\\x0B\\x0C\\x0E-\\x1F\\x7F]");

    /**
     * Validates a Recipe object produced by AI and returns a list of violation messages.
     * An empty list means no violations.
     *
     * @param recipe the AI-generated Recipe to validate
     * @return list of human-readable violation messages
     */
    public List<String> validate(Recipe recipe) {
        List<String> violations = new ArrayList<>();

        if (recipe == null) {
            violations.add("AI suggestion produced a null recipe");
            log.warn("{} AI suggestion produced a null recipe", AI_AUTHORING_TAG);
            return violations;
        }

        // Required field: recipeName
        if (recipe.getRecipeName() == null || recipe.getRecipeName().isBlank()) {
            violations.add("recipeName is required and must not be empty");
        } else if (recipe.getRecipeName().length() > MAX_RECIPE_NAME_LENGTH) {
            violations.add(String.format("recipeName exceeds maximum length of %d characters (got %d)",
                    MAX_RECIPE_NAME_LENGTH, recipe.getRecipeName().length()));
        }

        // Optional field: description
        if (recipe.getDescription() != null && recipe.getDescription().length() > MAX_DESCRIPTION_LENGTH) {
            violations.add(String.format("description exceeds maximum length of %d characters (got %d)",
                    MAX_DESCRIPTION_LENGTH, recipe.getDescription().length()));
        }

        // servings: must be in valid range
        if (recipe.getServings() != null) {
            int s = recipe.getServings();
            if (s < MIN_SERVINGS || s > MAX_SERVINGS) {
                violations.add(String.format("servings must be between %d and %d (got %d)",
                        MIN_SERVINGS, MAX_SERVINGS, s));
            }
        }

        // tags: count and individual length
        if (recipe.getTags() != null) {
            if (recipe.getTags().size() > MAX_TAG_COUNT) {
                violations.add(String.format("tags list exceeds maximum of %d tags (got %d)",
                        MAX_TAG_COUNT, recipe.getTags().size()));
            }
            for (String tag : recipe.getTags()) {
                if (tag != null && tag.length() > MAX_TAG_LENGTH) {
                    violations.add(String.format("tag '%s' exceeds maximum length of %d characters",
                            truncateForLog(tag, 30), MAX_TAG_LENGTH));
                }
            }
        }

        // ingredients: count and individual length
        if (recipe.getIngredients() != null) {
            if (recipe.getIngredients().size() > MAX_INGREDIENTS_COUNT) {
                violations.add(String.format("ingredients list exceeds maximum of %d items (got %d)",
                        MAX_INGREDIENTS_COUNT, recipe.getIngredients().size()));
            }
            for (String ing : recipe.getIngredients()) {
                if (ing != null && ing.length() > MAX_INGREDIENT_LENGTH) {
                    violations.add(String.format("ingredient entry exceeds maximum length of %d characters",
                            MAX_INGREDIENT_LENGTH));
                }
            }
        }

        // instructions: count and individual length
        if (recipe.getInstructions() != null) {
            if (recipe.getInstructions().size() > MAX_INSTRUCTIONS_COUNT) {
                violations.add(String.format("instructions list exceeds maximum of %d items (got %d)",
                        MAX_INSTRUCTIONS_COUNT, recipe.getInstructions().size()));
            }
            for (String inst : recipe.getInstructions()) {
                if (inst != null && inst.length() > MAX_INSTRUCTION_LENGTH) {
                    violations.add(String.format("instruction step exceeds maximum length of %d characters",
                            MAX_INSTRUCTION_LENGTH));
                }
            }
        }

        if (!violations.isEmpty()) {
            log.warn("{} AI suggestion failed schema validation — {} violation(s): {}",
                    AI_AUTHORING_TAG, violations.size(), violations);
        }

        return violations;
    }

    /**
     * Sanitizes all text fields in the recipe by stripping HTML tags and control characters.
     * Returns a new Recipe with sanitized content; the original is not mutated.
     *
     * @param recipe the recipe to sanitize
     * @return sanitized Recipe instance
     */
    public Recipe sanitize(Recipe recipe) {
        if (recipe == null) {
            return null;
        }

        recipe.setRecipeName(sanitizeText(recipe.getRecipeName()));
        recipe.setDescription(sanitizeText(recipe.getDescription()));

        if (recipe.getIngredients() != null) {
            recipe.setIngredients(
                    recipe.getIngredients().stream()
                            .map(this::sanitizeText)
                            .toList()
            );
        }

        if (recipe.getInstructions() != null) {
            recipe.setInstructions(
                    recipe.getInstructions().stream()
                            .map(this::sanitizeText)
                            .toList()
            );
        }

        if (recipe.getTags() != null) {
            recipe.setTags(
                    recipe.getTags().stream()
                            .map(this::sanitizeText)
                            .toList()
            );
        }

        return recipe;
    }

    /**
     * Strips HTML tags and control characters from a single text value.
     * Returns null if input is null.
     */
    String sanitizeText(String text) {
        if (text == null) {
            return null;
        }
        // First remove script/style elements including their inner content
        String result = SCRIPT_STYLE_PATTERN.matcher(text).replaceAll("");
        // Then strip any remaining HTML tags
        result = HTML_TAG_PATTERN.matcher(result).replaceAll("");
        result = CONTROL_CHAR_PATTERN.matcher(result).replaceAll("");
        return result;
    }

    private String truncateForLog(String s, int maxLen) {
        if (s == null) return "null";
        return s.length() <= maxLen ? s : s.substring(0, maxLen) + "...";
    }
}
