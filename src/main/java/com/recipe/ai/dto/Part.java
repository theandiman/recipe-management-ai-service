package com.recipe.ai.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

@JsonIgnoreProperties(ignoreUnknown = true)
public class Part {
    @JsonProperty("text")
    private String text;

    // No-arg constructor for Jackson and other frameworks
    public Part() {}

    public String getText() {
        return text;
    }

    public void setText(String text) {
        this.text = text;
    }

    // Copy constructor used for defensive copies
    public Part(Part other) {
        if (other != null) {
            this.text = other.text;
        }
    }
}
