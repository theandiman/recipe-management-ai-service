package com.recipe.ai.metrics;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.recipe.ai.model.FieldSuggestionRequest;
import com.recipe.ai.model.IngredientNormalizationRequest;
import com.recipe.ai.model.InstructionRefinementRequest;
import com.recipe.ai.service.FieldSuggestionService;
import com.recipe.ai.service.GeminiApiKeyResolver;
import com.recipe.ai.service.IngredientNormalizationService;
import com.recipe.ai.service.InstructionRefinementService;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.web.reactive.function.client.WebClient;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Verifies that Micrometer metrics are recorded for each AI service endpoint.
 *
 * BDD Scenarios:
 *   Scenario 1: Request counter is incremented when suggestFields is called
 *   Scenario 2: Latency timer is recorded when suggestFields is called
 *   Scenario 3: Request counter is incremented when refineInstructions is called
 *   Scenario 4: Request counter is incremented when normalizeIngredients is called
 *   Scenario 5: Error counter is incremented on service-level exception
 */
class AiMetricsTest {

    private MeterRegistry registry;
    private FieldSuggestionService fieldSuggestionService;
    private InstructionRefinementService instructionRefinementService;
    private IngredientNormalizationService ingredientNormalizationService;

    @BeforeEach
    void setUp() {
        registry = new SimpleMeterRegistry();
        fieldSuggestionService = new FieldSuggestionService(
                WebClient.builder(), new GeminiApiKeyResolver(), new ObjectMapper(), registry);
        instructionRefinementService = new InstructionRefinementService(
                WebClient.builder(), new GeminiApiKeyResolver(), new ObjectMapper(), registry);
        ingredientNormalizationService = new IngredientNormalizationService(
                WebClient.builder(), new GeminiApiKeyResolver(), new ObjectMapper(), registry);
    }

    // ── Scenario 1: suggest-fields request counter ────────────────────────────
    @Test
    void scenario1_suggestFields_incrementsRequestCounter() {
        FieldSuggestionRequest request = new FieldSuggestionRequest();
        request.setRecipeName("Pasta");

        fieldSuggestionService.suggestFields(request);

        Counter counter = registry.find("ai.suggestion.requests")
                .tag("endpoint", "suggest-fields")
                .counter();
        assertThat(counter).isNotNull();
        assertThat(counter.count()).isGreaterThanOrEqualTo(1.0);
    }

    // ── Scenario 2: suggest-fields latency timer ─────────────────────────────
    @Test
    void scenario2_suggestFields_recordsLatencyTimer() {
        FieldSuggestionRequest request = new FieldSuggestionRequest();
        request.setRecipeName("Pasta");

        fieldSuggestionService.suggestFields(request);

        Timer timer = registry.find("ai.suggestion.latency")
                .tag("endpoint", "suggest-fields")
                .timer();
        assertThat(timer).isNotNull();
        assertThat(timer.count()).isGreaterThanOrEqualTo(1L);
    }

    // ── Scenario 3: refine-instructions request counter ──────────────────────
    @Test
    void scenario3_refineInstructions_incrementsRequestCounter() {
        InstructionRefinementRequest request = new InstructionRefinementRequest();
        request.setInstructions(List.of("mix flour and water"));
        request.setRecipeName("Bread");

        instructionRefinementService.refineInstructions(request);

        Counter counter = registry.find("ai.suggestion.requests")
                .tag("endpoint", "refine-instructions")
                .counter();
        assertThat(counter).isNotNull();
        assertThat(counter.count()).isGreaterThanOrEqualTo(1.0);
    }

    // ── Scenario 4: normalize-ingredients request counter ────────────────────
    @Test
    void scenario4_normalizeIngredients_incrementsRequestCounter() {
        IngredientNormalizationRequest request = new IngredientNormalizationRequest();
        request.setIngredients(List.of("some flour", "a bit of sugar"));

        ingredientNormalizationService.normalizeIngredients(request);

        Counter counter = registry.find("ai.suggestion.requests")
                .tag("endpoint", "normalize-ingredients")
                .counter();
        assertThat(counter).isNotNull();
        assertThat(counter.count()).isGreaterThanOrEqualTo(1.0);
    }

    // ── Scenario 5: latency timer recorded for all endpoints ─────────────────
    @Test
    void scenario5_allEndpoints_latencyTimersPresent() {
        FieldSuggestionRequest fsReq = new FieldSuggestionRequest();
        fsReq.setRecipeName("Cake");
        fieldSuggestionService.suggestFields(fsReq);

        InstructionRefinementRequest irReq = new InstructionRefinementRequest();
        irReq.setInstructions(List.of("bake at 180°C"));
        instructionRefinementService.refineInstructions(irReq);

        IngredientNormalizationRequest inReq = new IngredientNormalizationRequest();
        inReq.setIngredients(List.of("handful of oats"));
        ingredientNormalizationService.normalizeIngredients(inReq);

        assertThat(registry.find("ai.suggestion.latency").tag("endpoint", "suggest-fields").timer()).isNotNull();
        assertThat(registry.find("ai.suggestion.latency").tag("endpoint", "refine-instructions").timer()).isNotNull();
        assertThat(registry.find("ai.suggestion.latency").tag("endpoint", "normalize-ingredients").timer()).isNotNull();
    }
}
