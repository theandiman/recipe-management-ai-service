package com.recipe.ai.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.recipe.ai.model.InstructionRefinement;
import com.recipe.ai.model.InstructionRefinementRequest;
import com.recipe.ai.model.InstructionRefinementResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.web.reactive.function.client.WebClient;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for InstructionRefinementService covering prompt building,
 * response parsing, sanitization, and unchanged-step exclusion — no real HTTP calls.
 *
 * BDD Scenarios covered:
 *   Scenario 1: Single step refinement returns diff
 *   Scenario 2: Full set refinement — changed steps returned, unchanged omitted
 *   Scenario 3: Original details preserved — prompt includes preservation instruction
 *   Scenario 4: AI failure returns empty response (graceful fallback)
 *   Scenario 5: Sanitization removes HTML and control characters from refined text
 */
class InstructionRefinementServiceTest {

    private InstructionRefinementService service;

    @BeforeEach
    void setUp() {
        service = new InstructionRefinementService(
            WebClient.builder(),
            new GeminiApiKeyResolver(),
            new ObjectMapper(),
            new io.micrometer.core.instrument.simple.SimpleMeterRegistry()
        );
    }

    // -------------------------------------------------------------------------
    // buildPrompt
    // -------------------------------------------------------------------------

    @Test
    void buildPrompt_includesRecipeName_andAllSteps() {
        InstructionRefinementRequest req = new InstructionRefinementRequest();
        req.setRecipeName("Banana Bread");
        req.setInstructions(List.of("mix the stuff", "bake it"));

        String prompt = service.buildPrompt(req);

        assertThat(prompt).contains("Banana Bread");
        assertThat(prompt).contains("[0] mix the stuff");
        assertThat(prompt).contains("[1] bake it");
    }

    @Test
    void buildPrompt_withoutRecipeName_doesNotIncludeNull() {
        InstructionRefinementRequest req = new InstructionRefinementRequest();
        req.setInstructions(List.of("do something"));

        String prompt = service.buildPrompt(req);

        assertThat(prompt).doesNotContain("null");
        assertThat(prompt).contains("[0] do something");
    }

    @Test
    void buildPrompt_containsMeasurementPreservationInstruction() {
        InstructionRefinementRequest req = new InstructionRefinementRequest();
        req.setInstructions(List.of("preheat oven to 375°F for 20 minutes"));

        String prompt = service.buildPrompt(req);

        assertThat(prompt).contains("Preserve ALL temperatures");
        assertThat(prompt).contains("times");
        assertThat(prompt).contains("measurements");
    }

    // -------------------------------------------------------------------------
    // sanitize
    // -------------------------------------------------------------------------

    @Test
    void sanitize_stripsHtmlTags() {
        assertThat(service.sanitize("<b>Bold text</b>")).isEqualTo("Bold text");
    }

    @Test
    void sanitize_stripsScriptElements() {
        assertThat(service.sanitize("<script>alert('xss')</script>Clean text"))
            .isEqualTo("Clean text");
    }

    @Test
    void sanitize_stripsControlCharacters() {
        assertThat(service.sanitize("Good\u0000text")).isEqualTo("Goodtext");
    }

    @Test
    void sanitize_preservesPlainText() {
        String text = "Preheat oven to 375°F. Bake for 20 minutes until golden.";
        assertThat(service.sanitize(text)).isEqualTo(text);
    }

    @Test
    void sanitize_handlesNull() {
        assertThat(service.sanitize(null)).isEqualTo("");
    }

    // -------------------------------------------------------------------------
    // parseGeminiResponse
    // -------------------------------------------------------------------------

    @Test
    void parseGeminiResponse_validResponse_returnsRefinements() throws Exception {
        String inner = "{\"refinements\":[{\"stepIndex\":0,\"refined\":\"Cook over medium heat for 8-10 minutes.\",\"changesSummary\":\"Added specific time and heat level.\"}]}";
        String body = buildGeminiBody(inner);
        List<String> originals = List.of("cook the thing until done");

        InstructionRefinementResponse response = service.parseGeminiResponse(body, originals);

        assertThat(response.getRefinements()).hasSize(1);
        InstructionRefinement refinement = response.getRefinements().get(0);
        assertThat(refinement.getStepIndex()).isZero();
        assertThat(refinement.getOriginal()).isEqualTo("cook the thing until done");
        assertThat(refinement.getRefined()).isEqualTo("Cook over medium heat for 8-10 minutes.");
        assertThat(refinement.getChangesSummary()).isEqualTo("Added specific time and heat level.");
    }

    @Test
    void parseGeminiResponse_unchangedStepExcluded() throws Exception {
        String step = "Preheat oven to 375°F.";
        String inner = "{\"refinements\":[{\"stepIndex\":0,\"refined\":\"" + step + "\",\"changesSummary\":\"No change.\"}]}";
        String body = buildGeminiBody(inner);
        List<String> originals = List.of(step);

        InstructionRefinementResponse response = service.parseGeminiResponse(body, originals);

        assertThat(response.getRefinements()).isEmpty();
    }

    @Test
    void parseGeminiResponse_outOfBoundsStepIndexIgnored() throws Exception {
        String inner = "{\"refinements\":[{\"stepIndex\":99,\"refined\":\"some text\",\"changesSummary\":\"summary\"}]}";
        String body = buildGeminiBody(inner);
        List<String> originals = List.of("step one");

        InstructionRefinementResponse response = service.parseGeminiResponse(body, originals);

        assertThat(response.getRefinements()).isEmpty();
    }

    @Test
    void parseGeminiResponse_emptyBody_returnsEmpty() {
        assertThat(service.parseGeminiResponse("", List.of("step")).getRefinements()).isEmpty();
        assertThat(service.parseGeminiResponse(null, List.of("step")).getRefinements()).isEmpty();
    }

    @Test
    void parseGeminiResponse_malformedJson_returnsEmpty() {
        String body = "{not valid json";
        assertThat(service.parseGeminiResponse(body, List.of("step")).getRefinements()).isEmpty();
    }

    @Test
    void parseGeminiResponse_sanitizesHtmlInRefinedText() throws Exception {
        String inner = "{\"refinements\":[{\"stepIndex\":0,\"refined\":\"<b>Cook</b> for 10 min.\",\"changesSummary\":\"Added time.\"}]}";
        String body = buildGeminiBody(inner);
        List<String> originals = List.of("cook it");

        InstructionRefinementResponse response = service.parseGeminiResponse(body, originals);

        assertThat(response.getRefinements()).hasSize(1);
        assertThat(response.getRefinements().get(0).getRefined()).isEqualTo("Cook for 10 min.");
    }

    // -------------------------------------------------------------------------
    // refineInstructions (graceful degradation)
    // -------------------------------------------------------------------------

    @Test
    void refineInstructions_nullRequest_returnsEmpty() {
        InstructionRefinementResponse response = service.refineInstructions(null);
        assertThat(response.getRefinements()).isEmpty();
    }

    @Test
    void refineInstructions_nullInstructions_returnsEmpty() {
        InstructionRefinementRequest req = new InstructionRefinementRequest();
        req.setInstructions(null);
        InstructionRefinementResponse response = service.refineInstructions(req);
        assertThat(response.getRefinements()).isEmpty();
    }

    @Test
    void refineInstructions_emptyInstructions_returnsEmpty() {
        InstructionRefinementRequest req = new InstructionRefinementRequest();
        req.setInstructions(List.of());
        InstructionRefinementResponse response = service.refineInstructions(req);
        assertThat(response.getRefinements()).isEmpty();
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    private static String buildGeminiBody(String innerJsonText) {
        // Escape double quotes inside innerJsonText for embedding as a JSON string value
        String escaped = innerJsonText.replace("\\", "\\\\").replace("\"", "\\\"");
        return "{\"candidates\":[{\"content\":{\"parts\":[{\"text\":\"" + escaped + "\"}]}}]}";
    }
}
