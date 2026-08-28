package com.recipe.ai.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.recipe.ai.model.AiSearchParseRequest;
import com.recipe.ai.model.AiSearchParseResponse;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.web.reactive.function.client.WebClient;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.when;

class SearchAiServiceTest {

    private SearchAiService searchAiService;
    private GeminiApiKeyResolver apiKeyResolver;

    @BeforeEach
    void setUp() {
        WebClient.Builder webClientBuilder = WebClient.builder();
        apiKeyResolver = Mockito.mock(GeminiApiKeyResolver.class);
        ObjectMapper objectMapper = new ObjectMapper();
        SimpleMeterRegistry meterRegistry = new SimpleMeterRegistry();

        searchAiService = new SearchAiService(webClientBuilder, apiKeyResolver, objectMapper, meterRegistry);
    }

    @Test
    void testParseSearchIntent_NullOrBlankRequest() {
        AiSearchParseResponse response = searchAiService.parseSearchIntent(null);
        assertNotNull(response);
        assertEquals("", response.getQueryKeywords());

        AiSearchParseResponse responseBlank = searchAiService.parseSearchIntent(new AiSearchParseRequest("   "));
        assertNotNull(responseBlank);
        assertEquals("", responseBlank.getQueryKeywords());
    }

    @Test
    void testParseSearchIntent_NoApiKeyFallback() {
        when(apiKeyResolver.hasValidApiKey()).thenReturn(false);
        when(apiKeyResolver.resolveEffectiveApiKey()).thenReturn("");

        AiSearchParseRequest request = new AiSearchParseRequest("Quick 30 min pasta under 500 calories");
        AiSearchParseResponse response = searchAiService.parseSearchIntent(request);

        assertNotNull(response);
        assertEquals("Quick 30 min pasta under 500 calories", response.getQueryKeywords());
        assertTrue(response.getDietaryTags().isEmpty());
        assertNull(response.getMaxPrepTime());
        assertNull(response.getMaxCalories());
        assertEquals("Using raw search keywords.", response.getExplanation());
    }

    @Test
    void testBuildPrompt() {
        String prompt = searchAiService.buildPrompt("Comforting winter soup");
        assertNotNull(prompt);
        assertTrue(prompt.contains("Comforting winter soup"));
        assertTrue(prompt.contains("dietaryTags"));
    }

    @Test
    void testParseGeminiResponse_ValidJson() {
        String mockResponse = """
            {
              "candidates": [
                {
                  "content": {
                    "parts": [
                      {
                        "text": "{\\"queryKeywords\\": \\"pasta\\", \\"dietaryTags\\": [\\"Quick & Easy\\"], \\"maxPrepTime\\": 30, \\"maxCalories\\": 500, \\"explanation\\": \\"Filtered for quick 30-min pasta under 500 kcal.\\"}"
                      }
                    ]
                  }
                }
              ]
            }
            """;

        AiSearchParseResponse response = searchAiService.parseGeminiResponse(mockResponse, "default");
        assertNotNull(response);
        assertEquals("pasta", response.getQueryKeywords());
        assertEquals(List.of("Quick & Easy"), response.getDietaryTags());
        assertEquals(30, response.getMaxPrepTime());
        assertEquals(500, response.getMaxCalories());
        assertEquals("Filtered for quick 30-min pasta under 500 kcal.", response.getExplanation());
    }

    @Test
    void testParseGeminiResponse_SanitizesAttributeKeywords() {
        String mockResponse = """
            {
              "candidates": [
                {
                  "content": {
                    "parts": [
                      {
                        "text": "{\\"queryKeywords\\": \\"quick\\", \\"dietaryTags\\": [\\"Quick & Easy\\"], \\"explanation\\": \\"Filtered for Quick & Easy recipes.\\"}"
                      }
                    ]
                  }
                }
              ]
            }
            """;

        AiSearchParseResponse response = searchAiService.parseGeminiResponse(mockResponse, "quick");
        assertNotNull(response);
        assertEquals("", response.getQueryKeywords());
        assertEquals(List.of("Quick & Easy"), response.getDietaryTags());
    }
}
