package com.recipe.ai.model;

public class AiRecipeMatchDto {
    private String recipeId;
    private double matchScore;
    private String matchReason;

    public AiRecipeMatchDto() {}

    public AiRecipeMatchDto(String recipeId, double matchScore, String matchReason) {
        this.recipeId = recipeId;
        this.matchScore = matchScore;
        this.matchReason = matchReason;
    }

    public String getRecipeId() { return recipeId; }
    public void setRecipeId(String recipeId) { this.recipeId = recipeId; }

    public double getMatchScore() { return matchScore; }
    public void setMatchScore(double matchScore) { this.matchScore = matchScore; }

    public String getMatchReason() { return matchReason; }
    public void setMatchReason(String matchReason) { this.matchReason = matchReason; }
}
