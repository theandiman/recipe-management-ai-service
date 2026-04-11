package com.recipe.ai.model;

import java.util.Collections;
import java.util.List;

/**
 * Request DTO for POST /api/recipes/refine-instructions.
 * Callers send the current instruction steps and an optional recipe name
 * for context.  To refine a single step, send a single-element list.
 */
public class InstructionRefinementRequest {

    /** The instruction steps to refine. */
    private List<String> instructions;

    /** Optional recipe name used as context in the Gemini prompt. */
    private String recipeName;

    public InstructionRefinementRequest() {}

    public List<String> getInstructions() {
        return instructions == null ? null : Collections.unmodifiableList(instructions);
    }
    public void setInstructions(List<String> instructions) { this.instructions = instructions; }

    public String getRecipeName() { return recipeName; }
    public void setRecipeName(String recipeName) { this.recipeName = recipeName; }
}
