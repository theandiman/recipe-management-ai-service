package com.recipe.ai.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;
import java.util.ArrayList;

@JsonIgnoreProperties(ignoreUnknown = true)
public class GeminiResponse {
    @JsonProperty("candidates")
    private List<Candidate> candidates;

    public List<Candidate> getCandidates() {
    // Return a defensive copy so callers can't modify our internal list reference.
    return candidates == null ? null : new ArrayList<>(candidates);
    }

    public void setCandidates(List<Candidate> candidates) {
        if (candidates == null) {
            this.candidates = null;
            return;
        }
        List<Candidate> internal = new ArrayList<>(candidates.size());
        for (Candidate c : candidates) {
            internal.add(c == null ? null : new Candidate(c));
        }
        // store an unmodifiable copy so internal representation can't be mutated externally
        this.candidates = List.copyOf(internal);
    }
}
