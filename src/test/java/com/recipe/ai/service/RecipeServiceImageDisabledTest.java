package com.recipe.ai.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.springframework.web.reactive.function.client.WebClient;

import java.lang.reflect.Field;
import java.util.Map;

/**
 * Smoke test to ensure that when image generation is disabled the service does not
 * inject a placeholder image and sets imageGeneration.status = "skipped".
 */
public class RecipeServiceImageDisabledTest {

    @Test
    public void whenImageDisabled_shouldNotInjectPlaceholder_andMarkSkipped() throws Exception {
        WebClient.Builder builder = WebClient.builder();
        ObjectMapper mapper = new ObjectMapper();
        RecipeService svc = new RecipeService(builder, mapper);

    // Force dev fallback to avoid external calls and ensure no API key is present
    setPrivateField(svc, "devFallback", true);
    setPrivateField(svc, "geminiApiKey", "");

    // Ensure image generation is disabled
    setPrivateField(svc, "geminiImageEnabled", false);

    // Some @Value-injected fields are null in a plain unit test — set minimal defaults
    setPrivateField(svc, "systemPrompt", "You are a chef.");
    setPrivateField(svc, "geminiApiUrl", "https://example.invalid/generateContent");
    setPrivateField(svc, "geminiImageUrl", "https://example.invalid/generateImage");

    // Rather than invoking generateRecipe (which may inspect environment vars), call the
    // private createMockRecipe method directly to obtain the dev fallback output.
    java.lang.reflect.Method m = RecipeService.class.getDeclaredMethod("createMockRecipe", java.util.List.class);
    m.setAccessible(true);
    String json = (String) m.invoke(svc, java.util.List.of());
    Assertions.assertNotNull(json, "Expected a mock recipe JSON from createMockRecipe");

    @SuppressWarnings("unchecked")
    Map<String, Object> obj = mapper.readValue(json, Map.class);

        // imageUrl should not be present when images are disabled
        Assertions.assertFalse(obj.containsKey("imageUrl"), "imageUrl must not be injected when gemini.image.enabled=false");

        // imageGeneration metadata must exist and be marked as skipped
        Assertions.assertTrue(obj.containsKey("imageGeneration"), "imageGeneration metadata must be present");
        @SuppressWarnings("unchecked")
        Map<String, Object> imgMeta = (Map<String, Object>) obj.get("imageGeneration");
        Assertions.assertEquals("skipped", imgMeta.get("status"), "imageGeneration.status must be 'skipped' when images are disabled");
    }

    private void setPrivateField(Object target, String fieldName, Object value) throws Exception {
        Field f = target.getClass().getDeclaredField(fieldName);
        f.setAccessible(true);
        f.set(target, value);
    }
}
