package com.recipe.ai.model;

import java.util.List;

public class AiSearchParseResponse {
    private String queryKeywords;
    private List<String> dietaryTags;
    private Integer maxPrepTime;
    private Integer maxCalories;
    private String explanation;

    public AiSearchParseResponse() {}

    public AiSearchParseResponse(String queryKeywords, List<String> dietaryTags, Integer maxPrepTime, Integer maxCalories, String explanation) {
        this.queryKeywords = queryKeywords;
        this.dietaryTags = dietaryTags;
        this.maxPrepTime = maxPrepTime;
        this.maxCalories = maxCalories;
        this.explanation = explanation;
    }

    public String getQueryKeywords() {
        return queryKeywords;
    }

    public void setQueryKeywords(String queryKeywords) {
        this.queryKeywords = queryKeywords;
    }

    public List<String> getDietaryTags() {
        return dietaryTags;
    }

    public void setDietaryTags(List<String> dietaryTags) {
        this.dietaryTags = dietaryTags;
    }

    public Integer getMaxPrepTime() {
        return maxPrepTime;
    }

    public void setMaxPrepTime(Integer maxPrepTime) {
        this.maxPrepTime = maxPrepTime;
    }

    public Integer getMaxCalories() {
        return maxCalories;
    }

    public void setMaxCalories(Integer maxCalories) {
        this.maxCalories = maxCalories;
    }

    public String getExplanation() {
        return explanation;
    }

    public void setExplanation(String explanation) {
        this.explanation = explanation;
    }
}
