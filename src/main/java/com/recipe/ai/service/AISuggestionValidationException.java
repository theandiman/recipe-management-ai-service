package com.recipe.ai.service;

import java.util.List;

/**
 * Thrown when an AI-generated recipe suggestion fails schema validation.
 * Carries the full list of violation messages for structured error responses.
 */
public class AISuggestionValidationException extends RuntimeException {

    private final List<String> violations;

    public AISuggestionValidationException(List<String> violations) {
        super("AI suggestion failed validation: " + violations);
        this.violations = List.copyOf(violations);
    }

    public List<String> getViolations() {
        return violations;
    }
}
