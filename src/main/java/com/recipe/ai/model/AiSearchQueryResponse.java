package com.recipe.ai.model;

import java.util.List;

public class AiSearchQueryResponse {
    private List<AiRecipeMatchDto> matches;
    private AiSuggestedIdeaDto suggestedIdea;

    public AiSearchQueryResponse() {}

    public AiSearchQueryResponse(List<AiRecipeMatchDto> matches, AiSuggestedIdeaDto suggestedIdea) {
        this.matches = matches;
        this.suggestedIdea = suggestedIdea;
    }

    public List<AiRecipeMatchDto> getMatches() { return matches; }
    public void setMatches(List<AiRecipeMatchDto> matches) { this.matches = matches; }

    public AiSuggestedIdeaDto getSuggestedIdea() { return suggestedIdea; }
    public void setSuggestedIdea(AiSuggestedIdeaDto suggestedIdea) { this.suggestedIdea = suggestedIdea; }
}
