package com.recipe.ai.service;

import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import org.junit.jupiter.api.AfterAll;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.web.client.RestTemplate;

import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
public class RecipeIntegrationTest {

    private static MockWebServer mockWebServer;

    @LocalServerPort
    private int port;

    // Start MockWebServer early so we can register its URL with DynamicPropertySource
    static {
        mockWebServer = new MockWebServer();
        try {
            mockWebServer.start();
            // Ensure tests have an API key so RecipeService will call the configured endpoint
            System.setProperty("GEMINI_API_KEY", "TEST_API_KEY");
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    @DynamicPropertySource
    static void registerProperties(DynamicPropertyRegistry registry) {
        // Point the gemini API URL to the MockWebServer
        registry.add("gemini.api.url", () -> "http://localhost:" + mockWebServer.getPort());
    }

    @AfterAll
    public static void stopMockServer() {
        if (mockWebServer != null) {
            try { mockWebServer.shutdown(); } catch (Exception ignored) {}
        }
    }

    @Test
    public void testEndToEnd_withWireMock() throws Exception {
    // Sample Gemini response body (the service extracts parts[0].text and parses it into shared Recipe)
    // The response must be a valid recipe JSON that can be deserialized into shared Recipe
        String recipeJson = "{\"recipeName\":\"Integration Test Recipe\",\"description\":\"A test recipe\",\"ingredients\":[\"egg\"],\"instructions\":[\"Mix\",\"Cook\"],\"servings\":\"1\"}";
        String geminiResponse = "{\"candidates\":[{\"content\":{\"parts\":[{\"text\":\"" + recipeJson.replace("\"", "\\\"") + "\"}]}}]}";

        // Stub the external Gemini endpoint that RecipeService will call
        mockWebServer.enqueue(new MockResponse()
            .setResponseCode(200)
            .addHeader("Content-Type", "application/json")
            .setBody(geminiResponse)
        );

        // Run the controller endpoint using RestTemplate
        RestTemplate rt = new RestTemplate();
        String url = "http://localhost:" + port + "/api/recipes/generate";

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        String body = "{\"prompt\":\"Make a test\",\"pantryItems\":[\"egg\"]}";
        
    // Controller now returns a shared Recipe JSON object
        ResponseEntity<String> resp = rt.postForEntity(url, new HttpEntity<>(body, headers), String.class);

        assertTrue(resp.getStatusCode().is2xxSuccessful());
        String respBody = resp.getBody();
        assertTrue(respBody != null && respBody.contains("Integration Test Recipe"));
        assertTrue(respBody.contains("egg"));
    }
}
