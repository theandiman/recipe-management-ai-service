package com.recipe.ai.model;

import java.util.Collections;
import java.util.List;

/**
 * Response envelope returned by POST /api/recipes/suggest-fields.
 */
public class FieldSuggestionsResponse {

    private List<FieldSuggestion> suggestions;

    public FieldSuggestionsResponse() {}

    public FieldSuggestionsResponse(List<FieldSuggestion> suggestions) {
        this.suggestions = suggestions;
    }

    public List<FieldSuggestion> getSuggestions() {
        return suggestions == null ? Collections.emptyList() : Collections.unmodifiableList(suggestions);
    }
    public void setSuggestions(List<FieldSuggestion> suggestions) { this.suggestions = suggestions; }
}
