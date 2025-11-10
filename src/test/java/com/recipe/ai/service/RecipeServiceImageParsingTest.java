package com.recipe.ai.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.web.reactive.function.client.WebClient;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class RecipeServiceImageParsingTest {

    @Test
    void recursiveScanFindsInlineBase64() throws Exception {
        ObjectMapper om = new ObjectMapper();
        Path p = Path.of("src/test/resources/fixtures/exampleResponse.json");
        String json = Files.readString(p);
        @SuppressWarnings("unchecked")
        Map<?, ?> resp = om.readValue(json, Map.class);

        // Create the service with a real WebClient.Builder and ObjectMapper — we won't perform network calls in this test.
        RecipeService svc = new RecipeService(WebClient.builder(), om);

        // Use reflection to call the private recursiveFindBase64AndMime method
        java.lang.reflect.Method m = RecipeService.class.getDeclaredMethod("recursiveFindBase64AndMime", Object.class);
        m.setAccessible(true);
        @SuppressWarnings("unchecked")
        Map<String, String> found = (Map<String, String>) m.invoke(svc, resp);

        assertNotNull(found, "Expected to find a base64 payload via recursive scan");
        assertTrue(found.containsKey("base64"), "Found result should contain 'base64' key");
        String base64 = found.get("base64");
        assertNotNull(base64);
        assertTrue(base64.length() > 10, "Base64 payload should be non-trivial");
        assertEquals("image/png", found.get("mime"), "Expected mime to be image/png from fixture");
    }
}
