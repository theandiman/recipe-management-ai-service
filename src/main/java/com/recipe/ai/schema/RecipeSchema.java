package com.recipe.ai.schema;

import static com.recipe.ai.schema.GeminiSchemaBuilder.*;

/**
 * Schema helper for Recipe objects used by the Gemini model.
 * Previously this lived in RecipeDTO.getSchema(); the schema is kept local to AI service
 * to define how we want the model to emit JSON for subsequent parsing into shared Recipe.
 */
public final class RecipeSchema {

    private RecipeSchema() { /* static utility */ }

    public static JsonSchema getSchema() {
        // Build small reusable sub-schemas for nutrition and tips
        // Inline nutrition values are created where needed - no top-level builder required here

        GeminiSchemaBuilder nutritionalInfoBuilder = object()
            .property("perServing", object().property("calories", number()).property("protein", number()).property("carbohydrates", number()).property("fat", number()).property("fiber", number()).property("sodium", number()))
            .property("total", object().property("calories", number()).property("protein", number()).property("carbohydrates", number()).property("fat", number()).property("fiber", number()).property("sodium", number()));

        GeminiSchemaBuilder tipsBuilder = object()
            .property("substitutions", array().items(string()))
            .property("makeAhead", string())
            .property("storage", string())
            .property("reheating", string())
            .property("variations", array().items(string()));

        return object()
            .property("recipeName", string()
                .description("The creative name of the recipe."))
            .property("description", string()
                .description("A brief, appealing description of the dish."))
            .property("ingredients", array()
                .description("A list of ingredients with quantities.")
                .items(string()))
            .property("instructions", array()
                .description("Step-by-step instructions for preparation.")
                .items(string()))
            .property("prepTime", string()
                .description("Estimated preparation time (e.g., '15 minutes')."))
            .property("cookTime", string()
                .description("Estimated cooking time (e.g., '20 minutes')."))
            .property("estimatedTime", string()
                .description("Human-readable total time estimate (e.g., '35 minutes' or '1 hour')."))
            .property("estimatedTimeMinutes", number()
                .description("Total estimated time in minutes as an integer."))
            .property("servings", string()
                .description("Number of servings the recipe yields (e.g., '4')."))
            .property("nutritionalInfo", nutritionalInfoBuilder)
            .property("tips", tipsBuilder)
            .required("recipeName", "ingredients", "instructions", "servings")
            .build();
    }
}
