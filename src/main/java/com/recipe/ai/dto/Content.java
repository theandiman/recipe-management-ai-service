package com.recipe.ai.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;
import java.util.ArrayList;

@JsonIgnoreProperties(ignoreUnknown = true)
public class Content {
    @JsonProperty("parts")
    private List<Part> parts;

    // Default constructor
    public Content() {}

    // Copy constructor for defensive copies
    public Content(Content other) {
        if (other == null || other.getParts() == null) {
            this.parts = null;
            return;
        }
        setParts(other.getParts());
    }

    public List<Part> getParts() {
        if (parts == null) return null;
        List<Part> copy = new ArrayList<>(parts.size());
        for (Part p : parts) {
            copy.add(p == null ? null : new Part(p));
        }
        return copy;
    }

    public void setParts(List<Part> parts) {
        if (parts == null) {
            this.parts = null;
            return;
        }
        this.parts = new ArrayList<>(parts.size());
        for (Part p : parts) {
            this.parts.add(p == null ? null : new Part(p));
        }
    }
}
