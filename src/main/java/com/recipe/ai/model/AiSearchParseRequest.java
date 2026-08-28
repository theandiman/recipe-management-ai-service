package com.recipe.ai.model;

public class AiSearchParseRequest {
    private String prompt;

    public AiSearchParseRequest() {}

    public AiSearchParseRequest(String prompt) {
        this.prompt = prompt;
    }

    public String getPrompt() {
        return prompt;
    }

    public void setPrompt(String prompt) {
        this.prompt = prompt;
    }
}
