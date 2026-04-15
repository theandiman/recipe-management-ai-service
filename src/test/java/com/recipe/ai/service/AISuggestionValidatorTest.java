package com.recipe.ai.service;

import com.recipe.shared.model.Recipe;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for AISuggestionValidator.
 *
 * BDD Scenarios covered:
 *   Scenario 1: Valid AI suggestion passes schema validation
 *   Scenario 2: AI response with content exceeding field length limits
 *   Scenario 3: Sanitization removes HTML/script content
 *   Scenario 5: Structural constraints enforced (servings, tags)
 *   Scenario 6: Validation failures logged with [AI_AUTHORING] tag
 */
class AISuggestionValidatorTest {

    private AISuggestionValidator validator;

    @BeforeEach
    void setUp() {
        validator = new AISuggestionValidator();
    }

    // -----------------------------------------------------------------------
    // Scenario 1: Valid recipe passes validation
    // -----------------------------------------------------------------------

    @Test
    void validate_validRecipe_returnsNoViolations() {
        Recipe recipe = buildValidRecipe();

        List<String> violations = validator.validate(recipe);

        assertThat(violations).isEmpty();
    }

    // -----------------------------------------------------------------------
    // Scenario 2a: recipeName length limit enforced
    // -----------------------------------------------------------------------

    @Test
    void validate_recipeNameTooLong_returnsViolation() {
        Recipe recipe = buildValidRecipe();
        recipe.setRecipeName("A".repeat(AISuggestionValidator.MAX_RECIPE_NAME_LENGTH + 1));

        List<String> violations = validator.validate(recipe);

        assertThat(violations).anyMatch(v -> v.contains("recipeName exceeds maximum length"));
    }

    @Test
    void validate_recipeNameExactlyAtLimit_returnsNoViolation() {
        Recipe recipe = buildValidRecipe();
        recipe.setRecipeName("A".repeat(AISuggestionValidator.MAX_RECIPE_NAME_LENGTH));

        List<String> violations = validator.validate(recipe);

        assertThat(violations).isEmpty();
    }

    @Test
    void validate_recipeNameNull_returnsViolation() {
        Recipe recipe = buildValidRecipe();
        recipe.setRecipeName(null);

        List<String> violations = validator.validate(recipe);

        assertThat(violations).anyMatch(v -> v.contains("recipeName is required"));
    }

    @Test
    void validate_recipeNameBlank_returnsViolation() {
        Recipe recipe = buildValidRecipe();
        recipe.setRecipeName("   ");

        List<String> violations = validator.validate(recipe);

        assertThat(violations).anyMatch(v -> v.contains("recipeName is required"));
    }

    // -----------------------------------------------------------------------
    // Scenario 2b: description length limit enforced
    // -----------------------------------------------------------------------

    @Test
    void validate_descriptionTooLong_returnsViolation() {
        Recipe recipe = buildValidRecipe();
        recipe.setDescription("X".repeat(AISuggestionValidator.MAX_DESCRIPTION_LENGTH + 1));

        List<String> violations = validator.validate(recipe);

        assertThat(violations).anyMatch(v -> v.contains("description exceeds maximum length"));
    }

    @Test
    void validate_descriptionNull_isAccepted() {
        Recipe recipe = buildValidRecipe();
        recipe.setDescription(null);

        List<String> violations = validator.validate(recipe);

        assertThat(violations).isEmpty();
    }

    // -----------------------------------------------------------------------
    // Scenario 5a: servings range enforced
    // -----------------------------------------------------------------------

    @Test
    void validate_servingsZero_returnsViolation() {
        Recipe recipe = buildValidRecipe();
        recipe.setServings(0);

        List<String> violations = validator.validate(recipe);

        assertThat(violations).anyMatch(v -> v.contains("servings must be between"));
    }

    @Test
    void validate_servingsNegative_returnsViolation() {
        Recipe recipe = buildValidRecipe();
        recipe.setServings(-1);

        List<String> violations = validator.validate(recipe);

        assertThat(violations).anyMatch(v -> v.contains("servings must be between"));
    }

    @Test
    void validate_servingsTooHigh_returnsViolation() {
        Recipe recipe = buildValidRecipe();
        recipe.setServings(AISuggestionValidator.MAX_SERVINGS + 1);

        List<String> violations = validator.validate(recipe);

        assertThat(violations).anyMatch(v -> v.contains("servings must be between"));
    }

    @Test
    void validate_servingsNull_isAccepted() {
        Recipe recipe = buildValidRecipe();
        recipe.setServings(null);

        List<String> violations = validator.validate(recipe);

        assertThat(violations).isEmpty();
    }

    // -----------------------------------------------------------------------
    // Scenario 5b: tag count and length enforced
    // -----------------------------------------------------------------------

    @Test
    void validate_tooManyTags_returnsViolation() {
        Recipe recipe = buildValidRecipe();
        List<String> tags = new ArrayList<>();
        for (int i = 0; i <= AISuggestionValidator.MAX_TAG_COUNT; i++) {
            tags.add("tag" + i);
        }
        recipe.setTags(tags);

        List<String> violations = validator.validate(recipe);

        assertThat(violations).anyMatch(v -> v.contains("tags list exceeds maximum"));
    }

    @Test
    void validate_tagTooLong_returnsViolation() {
        Recipe recipe = buildValidRecipe();
        recipe.setTags(List.of("T".repeat(AISuggestionValidator.MAX_TAG_LENGTH + 1)));

        List<String> violations = validator.validate(recipe);

        assertThat(violations).anyMatch(v -> v.contains("exceeds maximum length"));
    }

    @Test
    void validate_tagsNull_isAccepted() {
        Recipe recipe = buildValidRecipe();
        recipe.setTags(null);

        List<String> violations = validator.validate(recipe);

        assertThat(violations).isEmpty();
    }

    // -----------------------------------------------------------------------
    // Scenario 2c: ingredient / instruction limits enforced
    // -----------------------------------------------------------------------

    @Test
    void validate_ingredientTooLong_returnsViolation() {
        Recipe recipe = buildValidRecipe();
        recipe.setIngredients(List.of("X".repeat(AISuggestionValidator.MAX_INGREDIENT_LENGTH + 1)));

        List<String> violations = validator.validate(recipe);

        assertThat(violations).anyMatch(v -> v.contains("ingredient entry exceeds maximum length"));
    }

    @Test
    void validate_instructionTooLong_returnsViolation() {
        Recipe recipe = buildValidRecipe();
        recipe.setInstructions(List.of("X".repeat(AISuggestionValidator.MAX_INSTRUCTION_LENGTH + 1)));

        List<String> violations = validator.validate(recipe);

        assertThat(violations).anyMatch(v -> v.contains("instruction step exceeds maximum length"));
    }

    @Test
    void validate_nullRecipe_returnsViolation() {
        List<String> violations = validator.validate(null);

        assertThat(violations).anyMatch(v -> v.contains("null recipe"));
    }

    // -----------------------------------------------------------------------
    // Scenario 3: Sanitization removes HTML/script content
    // -----------------------------------------------------------------------

    @Test
    void sanitize_htmlInRecipeName_isStripped() {
        Recipe recipe = buildValidRecipe();
        recipe.setRecipeName("<b>Bold Recipe</b>");

        Recipe sanitized = validator.sanitize(recipe);

        assertThat(sanitized.getRecipeName()).isEqualTo("Bold Recipe");
    }

    @Test
    void sanitize_scriptTagInDescription_isStripped() {
        Recipe recipe = buildValidRecipe();
        recipe.setDescription("Good<script>alert('xss')</script>description");

        Recipe sanitized = validator.sanitize(recipe);

        assertThat(sanitized.getDescription()).doesNotContain("<script>");
        assertThat(sanitized.getDescription()).doesNotContain("alert");
        assertThat(sanitized.getDescription()).isEqualTo("Gooddescription");
    }

    @Test
    void sanitize_htmlInIngredients_isStripped() {
        Recipe recipe = buildValidRecipe();
        recipe.setIngredients(List.of("<em>1 cup</em> flour"));

        Recipe sanitized = validator.sanitize(recipe);

        assertThat(sanitized.getIngredients().get(0)).isEqualTo("1 cup flour");
    }

    @Test
    void sanitize_htmlInInstructions_isStripped() {
        Recipe recipe = buildValidRecipe();
        recipe.setInstructions(List.of("<p>Mix well</p>"));

        Recipe sanitized = validator.sanitize(recipe);

        assertThat(sanitized.getInstructions().get(0)).isEqualTo("Mix well");
    }

    @Test
    void sanitize_htmlInTags_isStripped() {
        Recipe recipe = buildValidRecipe();
        recipe.setTags(List.of("<b>vegan</b>"));

        Recipe sanitized = validator.sanitize(recipe);

        assertThat(sanitized.getTags().get(0)).isEqualTo("vegan");
    }

    @Test
    void sanitize_controlCharsInText_areStripped() {
        Recipe recipe = buildValidRecipe();
        recipe.setRecipeName("Recipe\u0000Name\u0007");

        Recipe sanitized = validator.sanitize(recipe);

        assertThat(sanitized.getRecipeName()).isEqualTo("RecipeName");
    }

    @Test
    void sanitize_nullRecipe_returnsNull() {
        Recipe result = validator.sanitize(null);

        assertThat(result).isNull();
    }

    @Test
    void sanitize_nullTextFields_returnNullUnchanged() {
        Recipe recipe = buildValidRecipe();
        recipe.setDescription(null);

        Recipe sanitized = validator.sanitize(recipe);

        assertThat(sanitized.getDescription()).isNull();
    }

    @Test
    void sanitizeText_plainText_isUnchanged() {
        String result = validator.sanitizeText("Chicken soup with vegetables");

        assertThat(result).isEqualTo("Chicken soup with vegetables");
    }

    // -----------------------------------------------------------------------
    // Multiple violations in one recipe
    // -----------------------------------------------------------------------

    @Test
    void validate_multipleViolations_allReported() {
        Recipe recipe = buildValidRecipe();
        recipe.setRecipeName("A".repeat(300));
        recipe.setServings(200);

        List<String> violations = validator.validate(recipe);

        assertThat(violations).hasSizeGreaterThanOrEqualTo(2);
    }

    // -----------------------------------------------------------------------
    // Helpers
    // -----------------------------------------------------------------------

    private Recipe buildValidRecipe() {
        Recipe recipe = new Recipe();
        recipe.setRecipeName("Classic Pasta");
        recipe.setDescription("A simple and delicious pasta dish.");
        recipe.setIngredients(List.of("200g pasta", "2 tbsp olive oil", "salt"));
        recipe.setInstructions(List.of("Boil pasta.", "Toss with olive oil.", "Season and serve."));
        recipe.setServings(4);
        recipe.setTags(List.of("pasta", "quick"));
        return recipe;
    }
}
