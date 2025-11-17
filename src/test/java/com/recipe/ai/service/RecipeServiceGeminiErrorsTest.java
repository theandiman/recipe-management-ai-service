package com.recipe.ai.service;

import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import static org.junit.jupiter.api.Assertions.*;

import org.springframework.beans.factory.annotation.Autowired;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
@ActiveProfiles("test")
public class RecipeServiceGeminiErrorsTest {

    private static MockWebServer mockWebServer;

    @Autowired
    private RecipeService recipeService;

    @BeforeAll
    public static void startServer() throws Exception {
        mockWebServer = new MockWebServer();
        mockWebServer.start();
        System.setProperty("GEMINI_API_KEY", "TEST_API_KEY");
    }

    @AfterAll
    public static void stopServer() throws Exception {
        if (mockWebServer != null) {
            mockWebServer.shutdown();
        }
        System.clearProperty("GEMINI_API_KEY");
    }

    @DynamicPropertySource
    static void registerProps(DynamicPropertyRegistry registry) {
        registry.add("gemini.api.url", () -> "http://localhost:" + mockWebServer.getPort());
        registry.add("gemini.dev.fallback", () -> "false");
    }

    @Test
    public void testGenerateRecipe_403_devFallbackFalse_returnsNull() throws Exception {
        MockResponse resp = new MockResponse().setResponseCode(403).setBody("Forbidden");
        mockWebServer.enqueue(resp);

        // Ensure devFallback is explicitly false for this test (set private field directly)
        java.lang.reflect.Field df = RecipeService.class.getDeclaredField("devFallback");
        df.setAccessible(true);
        df.set(recipeService, false);

        String out = recipeService.generateRecipe("Make a test", java.util.List.of("egg"));
        // When 403 and devFallback=false we expect null result (no fallback)
        assertNull(out);
    }

    @Test
    public void testGenerateRecipe_403_devFallbackTrue_returnsMock() throws Exception {
        // Set devFallback true on the RecipeService instance for the test via reflection
        java.lang.reflect.Field df = RecipeService.class.getDeclaredField("devFallback");
        df.setAccessible(true);
        df.set(recipeService, true);
        MockResponse resp = new MockResponse().setResponseCode(403).setBody("Forbidden");
        mockWebServer.enqueue(resp);

        String out = recipeService.generateRecipe("Make a test", java.util.List.of("egg"));
        assertNotNull(out);
    // Reset the devFallback field to its default false so other tests are unaffected
    df.set(recipeService, false);
    }

    @Test
    public void testGenerateRecipe_503_retries_thenNull() throws Exception {
        // Return repeated 503s to trigger retry and eventual failure
        mockWebServer.enqueue(new MockResponse().setResponseCode(503).setBody("Service Unavailable"));
        mockWebServer.enqueue(new MockResponse().setResponseCode(503).setBody("Service Unavailable"));
        mockWebServer.enqueue(new MockResponse().setResponseCode(503).setBody("Service Unavailable"));
        mockWebServer.enqueue(new MockResponse().setResponseCode(503).setBody("Service Unavailable"));
        mockWebServer.enqueue(new MockResponse().setResponseCode(503).setBody("Service Unavailable"));

        String out = recipeService.generateRecipe("Make a test", java.util.List.of("egg"));
        assertNull(out);
    }
}
