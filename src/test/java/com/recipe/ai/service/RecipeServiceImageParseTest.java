package com.recipe.ai.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

public class RecipeServiceImageParseTest {

    @Test
    public void parseForcedRawJson_shouldFindImage() throws Exception {
        ObjectMapper om = new ObjectMapper();
        // instantiate a minimal WebClient.Builder using the factory method
        org.springframework.web.reactive.function.client.WebClient.Builder builder = org.springframework.web.reactive.function.client.WebClient.builder();
        RecipeService svc = new RecipeService(builder, om);

    // Load a stable test fixture packaged with the tests (no reliance on /tmp)
    Path p = Path.of("src/test/resources/fixtures/gemini_image_direct_sample.json");
    assertTrue(Files.exists(p), "fixture src/test/resources/fixtures/gemini_image_direct_sample.json must exist for this test");
    String raw = Files.readString(p);
        assertNotNull(raw);
        assertTrue(raw.length() > 100, "fixture seems too small to contain base64 payload");

        // parse into Map
        Map<?,?> parsed = om.readValue(raw, Map.class);
        assertNotNull(parsed);

        // Use reflection to call the private parseImageResponseMap method
        Method m = RecipeService.class.getDeclaredMethod("parseImageResponseMap", Map.class);
        m.setAccessible(true);
        Object result = m.invoke(svc, parsed);

        assertNotNull(result, "parseImageResponseMap should return a data URL or external URL when inline data exists");
        assertTrue(result instanceof String);
        String s = (String) result;
        assertFalse(s.isBlank());
        // either a data: URI or an http(s) URL
        assertTrue(s.startsWith("data:") || s.startsWith("http://") || s.startsWith("https://"), "returned string should be a data URI or URL");
    }

    @Test
    public void testTryExtractBase64FromInline_camelCase() throws Exception {
        Path p = Path.of("src/test/resources/fixtures/gemini_image_direct_sample.json");
        String raw = Files.readString(p);
        ObjectMapper om = new ObjectMapper();
        Map<String, Object> parsed = om.readValue(raw, Map.class);
        Object candidates = parsed.get("candidates");
        Map<?,?> cand0 = (Map<?,?>) ((java.util.List<?>) candidates).get(0);
        Map<?,?> content = (Map<?,?>) cand0.get("content");
        java.util.List<?> parts = (java.util.List<?>) content.get("parts");
        Map<?,?> part0 = (Map<?,?>) parts.get(0);
        Object inline = part0.get("inlineData");

        RecipeService svc = new RecipeService(null, new ObjectMapper());
        java.lang.reflect.Method m = RecipeService.class.getDeclaredMethod("tryExtractBase64FromInline", Object.class);
        m.setAccessible(true);
        Object res = m.invoke(svc, inline);
        org.assertj.core.api.Assertions.assertThat(res).isInstanceOf(String.class);
        org.assertj.core.api.Assertions.assertThat((String) res).startsWith("iVBORw0K");
    }
}
