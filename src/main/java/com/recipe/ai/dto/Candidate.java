package com.recipe.ai.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

@JsonIgnoreProperties(ignoreUnknown = true)
public class Candidate {
    @JsonProperty("content")
    private Content content;

    public Candidate() {}

    // Copy constructor for defensive copying
    public Candidate(Candidate other) {
        if (other != null && other.content != null) {
            this.content = new Content(other.content);
        }
    }

    public Content getContent() {
        return content == null ? null : new Content(content);
    }

    public void setContent(Content content) {
        this.content = content == null ? null : new Content(content);
    }
}
