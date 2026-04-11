package com.recipe.ai.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.recipe.ai.model.InstructionRefinement;
import com.recipe.ai.model.InstructionRefinementRequest;
import com.recipe.ai.model.InstructionRefinementResponse;
import io.micrometer.core.instrument.MeterRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.BodyInserters;
import org.springframework.web.reactive.function.client.WebClient;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.regex.Pattern;

/**
 * Service that calls the Gemini API to refine recipe instruction steps for
 * clarity, imperative tone, and completeness while preserving all temperatures,
 * times, and specific measurements.
 *
 * BDD Scenarios covered:
 *   Scenario 1: Single step refinement — send one-element list, diff returned
 *   Scenario 2: Full set refinement — all steps sent in one call for coherence
 *   Scenario 3: Original details preserved — prompt instructs Gemini to keep measurements
 *   Scenario 4: AI failure graceful fallback — exception caught, empty response returned
 *   Scenario 5: Reject all keeps originals — no mutation; frontend tracks accept/reject
 */
@Service
public class InstructionRefinementService {

    private static final Logger log = LoggerFactory.getLogger(InstructionRefinementService.class);

    private static final String API_KEY_HEADER = "x-goog-api-key";

    /** Matches HTML/script tags and ASCII control chars for sanitization. */
    private static final Pattern SCRIPT_STYLE_PATTERN =
        Pattern.compile("<(script|style)[^>]*>[\\s\\S]*?</\\1>", Pattern.CASE_INSENSITIVE);
    private static final Pattern HTML_TAG_PATTERN = Pattern.compile("<[^>]*>");
    private static final Pattern CONTROL_CHAR_PATTERN = Pattern.compile("[\\x00-\\x08\\x0B\\x0C\\x0E-\\x1F\\x7F]");

    @Value("${gemini.api.url:https://generativelanguage.googleapis.com/v1beta/models/gemini-2.5-flash:generateContent}")
    private String geminiApiUrl;

    private final WebClient.Builder webClientBuilder;
    private final GeminiApiKeyResolver apiKeyResolver;
    private final ObjectMapper objectMapper;
    private final MeterRegistry meterRegistry;

    private static final String ENDPOINT_TAG = "endpoint";
    private static final String ENDPOINT_VALUE = "refine-instructions";

    public InstructionRefinementService(WebClient.Builder webClientBuilder,
                                         GeminiApiKeyResolver apiKeyResolver,
                                         ObjectMapper objectMapper,
                                         MeterRegistry meterRegistry) {
        this.webClientBuilder = webClientBuilder;
        this.apiKeyResolver = apiKeyResolver;
        this.objectMapper = objectMapper == null ? new ObjectMapper() : objectMapper.copy();
        this.meterRegistry = meterRegistry;
    }

    /**
     * Refines the provided instruction steps using Gemini.  Returns only steps
     * that were actually improved; unchanged steps are omitted.  On any error,
     * returns an empty refinements list so callers can degrade gracefully.
     */
    public InstructionRefinementResponse refineInstructions(InstructionRefinementRequest request) {
        long start = System.currentTimeMillis();
        try {
            meterRegistry.counter("ai.suggestion.requests", ENDPOINT_TAG, ENDPOINT_VALUE).increment();
            InstructionRefinementResponse result = doRefineInstructions(request);
            return result;
        } catch (Exception e) {
            meterRegistry.counter("ai.suggestion.errors", ENDPOINT_TAG, ENDPOINT_VALUE).increment();
            log.error("Unhandled error in refineInstructions: {}", e.getMessage(), e);
            return new InstructionRefinementResponse(List.of());
        } finally {
            meterRegistry.timer("ai.suggestion.latency", ENDPOINT_TAG, ENDPOINT_VALUE)
                    .record(System.currentTimeMillis() - start, TimeUnit.MILLISECONDS);
        }
    }

    private InstructionRefinementResponse doRefineInstructions(InstructionRefinementRequest request) {
        if (request == null || request.getInstructions() == null || request.getInstructions().isEmpty()) {
            return new InstructionRefinementResponse(List.of());
        }

        List<String> instructions = request.getInstructions();

        if (!apiKeyResolver.hasValidApiKey()) {
            log.warn("No valid Gemini API key — skipping instruction refinement.");
            return new InstructionRefinementResponse(List.of());
        }

        String effectiveApiKey = apiKeyResolver.resolveEffectiveApiKey();
        String prompt = buildPrompt(request);
        String jsonSchema = buildResponseSchema();

        Map<String, Object> payload = Map.of(
            "contents", List.of(Map.of("parts", List.of(Map.of("text", prompt)))),
            "generationConfig", Map.of(
                "responseMimeType", "application/json",
                "responseSchema", parseSchema(jsonSchema)
            )
        );

        try {
            WebClient client = webClientBuilder
                .baseUrl(geminiApiUrl)
                .defaultHeader(API_KEY_HEADER, effectiveApiKey)
                .build();

            String responseBody = client.post()
                .contentType(MediaType.APPLICATION_JSON)
                .body(BodyInserters.fromValue(payload))
                .retrieve()
                .bodyToMono(String.class)
                .block();

            InstructionRefinementResponse parsed = parseGeminiResponse(responseBody, instructions);
            return parsed;
        } catch (Exception e) {
            log.error("Instruction refinement call to Gemini failed: {}", e.getMessage(), e);
            return new InstructionRefinementResponse(List.of());
        }
    }

    // -------------------------------------------------------------------------
    // Package-private for unit testing
    // -------------------------------------------------------------------------

    String buildPrompt(InstructionRefinementRequest request) {
        List<String> instructions = request.getInstructions();
        StringBuilder sb = new StringBuilder();

        sb.append("You are a professional recipe editor. ");
        if (request.getRecipeName() != null && !request.getRecipeName().isBlank()) {
            sb.append("The recipe is called \"").append(request.getRecipeName()).append("\". ");
        }
        sb.append("Refine the following recipe instruction steps for clarity, imperative tone, and completeness.\n\n");
        sb.append("CRITICAL RULES:\n");
        sb.append("- Preserve ALL temperatures (e.g. 375°F, 200°C), times (e.g. 20 minutes), and specific measurements exactly as given.\n");
        sb.append("- Use imperative tone (e.g. \"Heat the pan\" not \"You should heat the pan\").\n");
        sb.append("- Keep each step concise and action-focused.\n");
        sb.append("- If a step is already clear and well-written, return it unchanged.\n\n");
        sb.append("Return a JSON array named \"refinements\" where each element has:\n");
        sb.append("  stepIndex (integer, 0-based), refined (string), changesSummary (string, 1 sentence).\n");
        sb.append("Only include steps that were actually changed. Omit unchanged steps entirely.\n\n");
        sb.append("Steps to refine:\n");

        for (int i = 0; i < instructions.size(); i++) {
            sb.append("[").append(i).append("] ").append(instructions.get(i)).append("\n");
        }

        return sb.toString();
    }

    /** Sanitizes a text string by stripping HTML/script tags and control characters. */
    String sanitize(String text) {
        if (text == null) return "";
        String result = SCRIPT_STYLE_PATTERN.matcher(text).replaceAll("");
        result = HTML_TAG_PATTERN.matcher(result).replaceAll("");
        result = CONTROL_CHAR_PATTERN.matcher(result).replaceAll("");
        return result.trim();
    }

    InstructionRefinementResponse parseGeminiResponse(String body, List<String> originalInstructions) {
        if (body == null || body.isBlank()) {
            return new InstructionRefinementResponse(List.of());
        }
        try {
            Map<String, Object> root = objectMapper.readValue(body, new TypeReference<>() {});
            @SuppressWarnings("unchecked")
            List<Map<String, Object>> candidates = (List<Map<String, Object>>) root.get("candidates");
            if (candidates == null || candidates.isEmpty()) {
                return new InstructionRefinementResponse(List.of());
            }
            @SuppressWarnings("unchecked")
            Map<String, Object> content = (Map<String, Object>) candidates.get(0).get("content");
            @SuppressWarnings("unchecked")
            List<Map<String, Object>> parts = (List<Map<String, Object>>) content.get("parts");
            String text = (String) parts.get(0).get("text");

            Map<String, Object> parsed = objectMapper.readValue(text, new TypeReference<>() {});
            @SuppressWarnings("unchecked")
            List<Map<String, Object>> rawRefinements = (List<Map<String, Object>>) parsed.get("refinements");
            if (rawRefinements == null) {
                return new InstructionRefinementResponse(List.of());
            }

            List<InstructionRefinement> refinements = new ArrayList<>();
            for (Map<String, Object> raw : rawRefinements) {
                Object idxObj = raw.get("stepIndex");
                if (idxObj == null) continue;

                int idx;
                try {
                    idx = ((Number) idxObj).intValue();
                } catch (ClassCastException e) {
                    continue;
                }

                if (idx < 0 || idx >= originalInstructions.size()) continue;

                String refined = sanitize((String) raw.get("refined"));
                String changesSummary = sanitize((String) raw.getOrDefault("changesSummary", ""));
                String original = originalInstructions.get(idx);

                // Only include the step if the refined text actually differs
                if (refined.isBlank() || refined.equals(original)) continue;

                refinements.add(new InstructionRefinement(idx, original, refined, changesSummary));
            }
            return new InstructionRefinementResponse(refinements);
        } catch (Exception e) {
            log.error("Failed to parse Gemini instruction refinement response: {}", e.getMessage(), e);
            return new InstructionRefinementResponse(List.of());
        }
    }

    private String buildResponseSchema() {
        return """
            {
              "type": "object",
              "properties": {
                "refinements": {
                  "type": "array",
                  "items": {
                    "type": "object",
                    "properties": {
                      "stepIndex":      { "type": "integer" },
                      "refined":        { "type": "string" },
                      "changesSummary": { "type": "string" }
                    },
                    "required": ["stepIndex", "refined", "changesSummary"]
                  }
                }
              },
              "required": ["refinements"]
            }
            """;
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> parseSchema(String jsonSchema) {
        try {
            return objectMapper.readValue(jsonSchema, new TypeReference<Map<String, Object>>() {});
        } catch (Exception e) {
            log.warn("Failed to parse instruction refinement schema: {}", e.getMessage());
            return Map.of();
        }
    }
}
