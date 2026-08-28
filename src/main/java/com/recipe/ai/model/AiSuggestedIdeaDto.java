package com.recipe.ai.model;

public class AiSuggestedIdeaDto {
    private String title;
    private String prompt;
    private String reason;

    public AiSuggestedIdeaDto() {}

    public AiSuggestedIdeaDto(String title, String prompt, String reason) {
        this.title = title;
        this.prompt = prompt;
        this.reason = reason;
    }

    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }

    public String getPrompt() { return prompt; }
    public void setPrompt(String prompt) { this.prompt = prompt; }

    public String getReason() { return reason; }
    public void setReason(String reason) { this.reason = reason; }
}
