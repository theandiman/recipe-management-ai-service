package com.recipe.ai.model;

/**
 * A single AI-generated suggestion for one recipe field.
 */
public class FieldSuggestion {

    private String field;
    private String suggestedValue;
    private String reason;

    public FieldSuggestion() {}

    public FieldSuggestion(String field, String suggestedValue, String reason) {
        this.field = field;
        this.suggestedValue = suggestedValue;
        this.reason = reason;
    }

    public String getField() { return field; }
    public void setField(String field) { this.field = field; }

    public String getSuggestedValue() { return suggestedValue; }
    public void setSuggestedValue(String suggestedValue) { this.suggestedValue = suggestedValue; }

    public String getReason() { return reason; }
    public void setReason(String reason) { this.reason = reason; }
}
