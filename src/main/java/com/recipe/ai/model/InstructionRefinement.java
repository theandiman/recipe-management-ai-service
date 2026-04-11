package com.recipe.ai.model;

/**
 * A single refined instruction step returned by the AI.
 * Only steps that were actually improved are included in the response.
 */
public class InstructionRefinement {

    /** Zero-based index of the step in the original instructions list. */
    private int stepIndex;

    /** The original instruction text as submitted. */
    private String original;

    /** The AI-refined instruction text. */
    private String refined;

    /** One-sentence summary of what was changed. */
    private String changesSummary;

    public InstructionRefinement() {}

    public InstructionRefinement(int stepIndex, String original, String refined, String changesSummary) {
        this.stepIndex = stepIndex;
        this.original = original;
        this.refined = refined;
        this.changesSummary = changesSummary;
    }

    public int getStepIndex() { return stepIndex; }
    public void setStepIndex(int stepIndex) { this.stepIndex = stepIndex; }

    public String getOriginal() { return original; }
    public void setOriginal(String original) { this.original = original; }

    public String getRefined() { return refined; }
    public void setRefined(String refined) { this.refined = refined; }

    public String getChangesSummary() { return changesSummary; }
    public void setChangesSummary(String changesSummary) { this.changesSummary = changesSummary; }
}
