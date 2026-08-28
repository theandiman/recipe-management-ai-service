package com.recipe.ai.model;

import java.util.List;

public class AiSearchQueryRequest {
    private String prompt;
    private List<RecipeSummaryDto> recipes;

    public AiSearchQueryRequest() {}

    public AiSearchQueryRequest(String prompt, List<RecipeSummaryDto> recipes) {
        this.prompt = prompt;
        this.recipes = recipes;
    }

    public String getPrompt() { return prompt; }
    public void setPrompt(String prompt) { this.prompt = prompt; }

    public List<RecipeSummaryDto> getRecipes() { return recipes; }
    public void setRecipes(List<RecipeSummaryDto> recipes) { this.recipes = recipes; }
}
