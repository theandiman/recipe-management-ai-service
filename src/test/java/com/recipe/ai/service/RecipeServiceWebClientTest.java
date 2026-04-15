package com.recipe.ai.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class RecipeServiceWebClientTest {

    @Test
    public void testGenerateRecipe_parsesGeminiResponse() throws Exception {
        // Sample Gemini response JSON that matches DTOs
        String sample = "{\"candidates\":[{\"content\":{\"parts\":[{\"text\":\"Test recipe text from Gemini.\"}]}}]}";

        // Create a real WebClient with an ExchangeFunction that returns a ClientResponse containing our sample JSON.
        org.springframework.web.reactive.function.client.ExchangeFunction exchange = req -> {
            org.springframework.web.reactive.function.client.ClientResponse resp =
                org.springframework.web.reactive.function.client.ClientResponse
                    .create(org.springframework.http.HttpStatus.OK)
                    .header("Content-Type", "application/json")
                    .body(sample)
                    .build();
            return Mono.just(resp);
        };

        WebClient webClient = WebClient.builder().exchangeFunction(exchange).build();
        WebClient.Builder builder = Mockito.mock(WebClient.Builder.class);
        Mockito.when(builder.baseUrl(Mockito.any())).thenReturn(builder);
    // Ensure builder methods used in production code are stubbed in tests as well
    Mockito.when(builder.clientConnector(Mockito.any(org.springframework.http.client.reactive.ClientHttpConnector.class))).thenReturn(builder);
    Mockito.when(builder.exchangeStrategies(Mockito.any(org.springframework.web.reactive.function.client.ExchangeStrategies.class))).thenReturn(builder);
        Mockito.when(builder.defaultHeader(Mockito.any(), Mockito.any())).thenReturn(builder);
        Mockito.when(builder.build()).thenReturn(webClient);

    // Create the service with mocked builder
    RecipeService service = new RecipeService(builder, new ObjectMapper(), new AISuggestionValidator());

    // The service normally gets these from @Value injection; set them via reflection for the unit test
    java.lang.reflect.Field urlField = RecipeService.class.getDeclaredField("geminiApiUrl");
    urlField.setAccessible(true);
    urlField.set(service, "https://example.com/mock");

    java.lang.reflect.Field promptField = RecipeService.class.getDeclaredField("systemPrompt");
    promptField.setAccessible(true);
    promptField.set(service, "You are a test chef.");

    // Ensure an API key is present so the service doesn't short-circuit to the dev mock during unit tests
    java.lang.reflect.Field keyField = RecipeService.class.getDeclaredField("geminiApiKey");
    keyField.setAccessible(true);
    keyField.set(service, "TEST_API_KEY");

        String out = service.generateRecipe("Make a test", List.of("egg"));
        assertEquals("Test recipe text from Gemini.", out.trim());
    }

    @Test
    public void testGenerateRecipe_stripsMarkdownFencesFromGeminiResponse() throws Exception {
        String recipeJson = """
            {
              "recipeName": "Fence Test Recipe",
              "ingredients": ["egg"],
              "instructions": ["Cook it."],
              "servings": 1
            }
            """.trim();
        String sample = new ObjectMapper().writeValueAsString(
            java.util.Map.of(
                "candidates", java.util.List.of(
                    java.util.Map.of(
                        "content", java.util.Map.of(
                            "parts", java.util.List.of(
                                java.util.Map.of("text", "```json\n" + recipeJson + "\n```")
                            )
                        )
                    )
                )
            )
        );

        org.springframework.web.reactive.function.client.ExchangeFunction exchange = req -> {
            org.springframework.web.reactive.function.client.ClientResponse resp =
                org.springframework.web.reactive.function.client.ClientResponse
                    .create(org.springframework.http.HttpStatus.OK)
                    .header("Content-Type", "application/json")
                    .body(sample)
                    .build();
            return Mono.just(resp);
        };

        WebClient webClient = WebClient.builder().exchangeFunction(exchange).build();
        WebClient.Builder builder = Mockito.mock(WebClient.Builder.class);
        Mockito.when(builder.baseUrl(Mockito.any())).thenReturn(builder);
        Mockito.when(builder.clientConnector(Mockito.any(org.springframework.http.client.reactive.ClientHttpConnector.class))).thenReturn(builder);
        Mockito.when(builder.exchangeStrategies(Mockito.any(org.springframework.web.reactive.function.client.ExchangeStrategies.class))).thenReturn(builder);
        Mockito.when(builder.defaultHeader(Mockito.any(), Mockito.any())).thenReturn(builder);
        Mockito.when(builder.build()).thenReturn(webClient);

        RecipeService service = new RecipeService(builder, new ObjectMapper(), new AISuggestionValidator());

        java.lang.reflect.Field urlField = RecipeService.class.getDeclaredField("geminiApiUrl");
        urlField.setAccessible(true);
        urlField.set(service, "https://example.com/mock");

        java.lang.reflect.Field promptField = RecipeService.class.getDeclaredField("systemPrompt");
        promptField.setAccessible(true);
        promptField.set(service, "You are a test chef.");

        java.lang.reflect.Field keyField = RecipeService.class.getDeclaredField("geminiApiKey");
        keyField.setAccessible(true);
        keyField.set(service, "TEST_API_KEY");

        String out = service.generateRecipe("Make a test", List.of("egg"));
        assertTrue(!out.contains("```"));

        JsonNode json = new ObjectMapper().readTree(out);
        assertEquals("Fence Test Recipe", json.get("recipeName").asText());
        assertEquals(1, json.get("ingredients").size());
        assertEquals("egg", json.get("ingredients").get(0).asText());
    }
}
