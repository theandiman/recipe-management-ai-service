package com.recipe.ai.model;

import java.util.Collections;
import java.util.List;

/**
 * Response envelope returned by POST /api/recipes/refine-instructions.
 * Contains only the steps that were actually improved; unchanged steps are omitted.
 */
public class InstructionRefinementResponse {

    private List<InstructionRefinement> refinements;

    public InstructionRefinementResponse() {}

    public InstructionRefinementResponse(List<InstructionRefinement> refinements) {
        this.refinements = refinements;
    }

    public List<InstructionRefinement> getRefinements() {
        return refinements == null ? Collections.emptyList() : Collections.unmodifiableList(refinements);
    }
    public void setRefinements(List<InstructionRefinement> refinements) { this.refinements = refinements; }
}
