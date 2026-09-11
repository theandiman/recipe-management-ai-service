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
        assertTrue(prompt.contains("<user_search_query>Comforting winter soup</user_search_query>"));
    }

    @Test
    void testBuildQueryPrompt_boundaryTags() {
        com.recipe.ai.model.RecipeSummaryDto recipe = new com.recipe.ai.model.RecipeSummaryDto();
        recipe.setId("rec-1");
        recipe.setRecipeName("Chicken Soup");
        recipe.setDescription("Warm chicken soup");

        String prompt = searchAiService.buildQueryPrompt("soup", List.of(recipe));
        assertNotNull(prompt);
        assertTrue(prompt.contains("<user_search_query>soup</user_search_query>"));
        assertTrue(prompt.contains("<candidate_recipes>"));
        assertTrue(prompt.contains("rec-1"));
        assertTrue(prompt.contains("Chicken Soup"));
        assertTrue(prompt.contains("</candidate_recipes>"));
    }

    @Test
    void testSystemInstructions_securityDirectives() {
        assertTrue(SearchAiService.PARSE_INTENT_SYSTEM_INSTRUCTION.contains("SECURITY DIRECTIVE"));
        assertTrue(SearchAiService.PARSE_INTENT_SYSTEM_INSTRUCTION.contains("<user_search_query>"));
        assertTrue(SearchAiService.PARSE_INTENT_SYSTEM_INSTRUCTION.contains("passive text"));

        assertTrue(SearchAiService.QUERY_SYSTEM_INSTRUCTION.contains("SECURITY DIRECTIVE"));
        assertTrue(SearchAiService.QUERY_SYSTEM_INSTRUCTION.contains("<user_search_query>"));
        assertTrue(SearchAiService.QUERY_SYSTEM_INSTRUCTION.contains("<candidate_recipes>"));
        assertTrue(SearchAiService.QUERY_SYSTEM_INSTRUCTION.contains("passive data"));
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

    @Test
    void testQueryRecipes_EmptyCandidateList_ReturnsSuggestedIdea() {
        com.recipe.ai.model.AiSearchQueryResponse response = searchAiService.queryRecipes(
            new com.recipe.ai.model.AiSearchQueryRequest("cozy winter soup", List.of())
        );
        assertNotNull(response);
        assertTrue(response.getMatches().isEmpty());
        assertNotNull(response.getSuggestedIdea());
        assertEquals("cozy winter soup", response.getSuggestedIdea().getTitle());
    }

    @Test
    void testParseQueryGeminiResponse_ValidJson() {
        String mockResponse = """
            {
              "candidates": [
                {
                  "content": {
                    "parts": [
                      {
                        "text": "{\\"matches\\": [{\\"recipeId\\": \\"rec-1\\", \\"matchScore\\": 0.95, \\"matchReason\\": \\"Warm pasta dish for winter.\\"}], \\"suggestedIdea\\": {\\"title\\": \\"Creamy Tomato Soup\\", \\"prompt\\": \\"Create tomato soup\\", \\"reason\\": \\"Nice winter soup idea.\\"}}"
                      }
                    ]
                  }
                }
              ]
            }
            """;

        com.recipe.ai.model.AiSearchQueryResponse response = searchAiService.parseQueryGeminiResponse(mockResponse, "cozy winter soup");
        assertNotNull(response);
        assertEquals(1, response.getMatches().size());
        assertEquals("rec-1", response.getMatches().get(0).getRecipeId());
        assertEquals(0.95, response.getMatches().get(0).getMatchScore());
        assertEquals("Warm pasta dish for winter.", response.getMatches().get(0).getMatchReason());
        assertNotNull(response.getSuggestedIdea());
        assertEquals("Creamy Tomato Soup", response.getSuggestedIdea().getTitle());
    }
}
