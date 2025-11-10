package com.recipe.ai.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.springframework.web.reactive.function.client.WebClient;


import java.lang.reflect.Method;
import java.util.Map;

public class RecipeServiceParseForcedRawTest {

    @Test
    public void parseForcedRaw_shouldFindBase64() throws Exception {
        ObjectMapper mapper = new ObjectMapper();
        WebClient.Builder builder = WebClient.builder();
        RecipeService svc = new RecipeService(builder, mapper);

    // Use the packaged fixture instead of relying on /tmp being populated by manual runs
    java.nio.file.Path p = java.nio.file.Path.of("src/test/resources/fixtures/gemini_image_direct_sample.json");
    Assertions.assertTrue(java.nio.file.Files.exists(p), "fixture src/test/resources/fixtures/gemini_image_direct_sample.json must exist for this test");
    @SuppressWarnings("unchecked")
    Map<String, Object> resp = mapper.readValue(java.nio.file.Files.readString(p), Map.class);

    // First try the dedicated recursive scanner directly to see if it can find the base64 blob
    Method scan = RecipeService.class.getDeclaredMethod("recursiveFindBase64AndMime", Object.class);
    scan.setAccessible(true);
    @SuppressWarnings("unchecked")
    Map<String, String> found = (Map<String, String>) scan.invoke(svc, resp);
    Assertions.assertNotNull(found, "recursiveFindBase64AndMime should find the inline base64 in forced raw response");
    Assertions.assertTrue(found.containsKey("base64"), "Found map should contain 'base64' key");

    // Also ensure parseImageResponseMap returns a data URL when given the same map
    Method m = RecipeService.class.getDeclaredMethod("parseImageResponseMap", Map.class);
    m.setAccessible(true);
    String parsed = (String) m.invoke(svc, resp);
    Assertions.assertNotNull(parsed, "parseImageResponseMap should return a data URL for the forced raw response");
    Assertions.assertTrue(parsed.startsWith("data:"), "Parsed value should be a data URL");
    }
}
