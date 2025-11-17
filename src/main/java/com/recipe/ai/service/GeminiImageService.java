package com.recipe.ai.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.recipe.shared.model.Recipe;
import com.recipe.ai.model.ImageGenerationRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.BodyInserters;
import org.springframework.web.reactive.function.client.ExchangeStrategies;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import org.springframework.http.client.reactive.ReactorClientHttpConnector;
import reactor.netty.http.client.HttpClient;

import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Service dedicated to generating recipe images using Gemini's image generation API.
 */
@Service
public class GeminiImageService {

    private static final Logger log = LoggerFactory.getLogger(GeminiImageService.class);

    @Value("${gemini.image.enabled:false}")
    private boolean geminiImageEnabled;

    @Value("${gemini.image.url:}")
    private String geminiImageUrl;

    @Value("${gemini.image.model:gemini-2.5-flash-image}")
    private String geminiImageModel;

    @Value("${gemini.api.url:https://generativelanguage.googleapis.com/v1beta/models/gemini-2.5-flash:generateContent}")
    private String geminiApiUrl;

    private final WebClient.Builder webClientBuilder;
    private final ObjectMapper objectMapper;
    private final GeminiApiKeyResolver apiKeyResolver;
    private final GeminiResponseParser responseParser;

    public GeminiImageService(
            WebClient.Builder webClientBuilder,
            ObjectMapper objectMapper,
            GeminiApiKeyResolver apiKeyResolver,
            GeminiResponseParser responseParser) {
        this.webClientBuilder = webClientBuilder;
        this.objectMapper = objectMapper;
        this.apiKeyResolver = apiKeyResolver;
        this.responseParser = responseParser;
    }

    /**
     * Generate image metadata for the given prompt.
     */
    public Map<String, Object> generateImageForPrompt(String prompt) {
        return generateImageForPrompt(prompt, false);
    }

    /**
     * Generate image metadata for the given prompt. If forceCurl is true, uses JDK HttpClient.
     */
    public Map<String, Object> generateImageForPrompt(String prompt, boolean forceCurl) {
        Map<String, Object> meta = new LinkedHashMap<>();
        try {
            String data = forceCurl ? 
                generateImageDataUrlForced(prompt) : 
                generateImageDataUrl(prompt);
            
            if (data != null) {
                meta.put("status", "success");
                meta.put("imageUrl", data);
                meta.put("source", data.startsWith("data:") ? "inline" : "external");
            } else {
                meta.put("status", "failed");
                meta.put("imageUrl", null);
                meta.put("source", "");
            }
        } catch (Exception e) {
            meta.put("status", "failed");
            meta.put("imageUrl", null);
            meta.put("errorMessage", e.getMessage());
        }
        return meta;
    }

    /**
     * Generate image using an ImageGenerationRequest DTO.
     */
    public Map<String, Object> generateImageFromRequest(ImageGenerationRequest request) {
        return generateImageFromRequest(request, false);
    }

    /**
     * Generate image using an ImageGenerationRequest DTO with optional forceCurl flag.
     */
    public Map<String, Object> generateImageFromRequest(ImageGenerationRequest request, boolean forceCurl) {
        if (request == null) {
            log.warn("generateImageFromRequest called with null request");
            Map<String, Object> meta = new LinkedHashMap<>();
            meta.put("status", "failed");
            meta.put("imageUrl", null);
            meta.put("source", "");
            meta.put("errorMessage", "Request cannot be null");
            return meta;
        }

        String prompt = buildPromptFromRequest(request);
        if (prompt == null) {
            Map<String, Object> meta = new LinkedHashMap<>();
            meta.put("status", "failed");
            meta.put("imageUrl", null);
            meta.put("source", "");
            meta.put("errorMessage", "No prompt or recipe context provided");
            return meta;
        }

        return generateImageForPrompt(prompt, forceCurl);
    }

    private String buildPromptFromRequest(ImageGenerationRequest request) {
        if (request.getRecipe() != null) {
            Recipe recipe = request.getRecipe();
            StringBuilder sb = new StringBuilder("Generate an illustrative image for this recipe: ");
            if (recipe.getRecipeName() != null) {
                sb.append(recipe.getRecipeName());
            }
            if (recipe.getDescription() != null) {
                sb.append(". ").append(recipe.getDescription());
            }
            if (recipe.getIngredients() != null && !recipe.getIngredients().isEmpty()) {
                sb.append(". Key ingredients: ");
                sb.append(String.join(", ", recipe.getIngredients().stream().limit(5).toList()));
            }
            return sb.toString();
        } else if (request.getPrompt() != null && !request.getPrompt().isBlank()) {
            return request.getPrompt();
        }
        return null;
    }

    private String generateImageDataUrl(String prompt) {
        String effectiveApiKey = apiKeyResolver.resolveEffectiveApiKey();
        String imageEndpoint = resolveImageEndpoint();

        if (imageEndpoint == null || imageEndpoint.isBlank() || 
            effectiveApiKey == null || effectiveApiKey.isBlank()) {
            log.debug("Skipping image generation because gemini.image.url or API key is not configured");
            return null;
        }

        try {
            ExchangeStrategies strategies = ExchangeStrategies.builder()
                .codecs(configurer -> configurer.defaultCodecs().maxInMemorySize(32 * 1024 * 1024))
                .build();

            Map<String, Object> generationConfig = Map.of("responseModalities", List.of("Image"));
            Map<String, Object> payload = Map.of(
                "contents", List.of(Map.of("parts", List.of(Map.of("text", "Generate image for: " + prompt)))),
                "generationConfig", generationConfig
            );

            final int MAX_WEBCLIENT_ATTEMPTS = 3;
            final long WEBCLIENT_BACKOFF_MS = 500L;
            String body = null;

            for (int attempt = 1; attempt <= MAX_WEBCLIENT_ATTEMPTS; attempt++) {
                WebClient client = webClientBuilder
                    .exchangeStrategies(strategies)
                    .clientConnector(new ReactorClientHttpConnector(HttpClient.create().wiretap(true)))
                    .baseUrl(imageEndpoint)
                    .defaultHeader("x-goog-api-key", effectiveApiKey)
                    .defaultHeader("Accept", "application/json")
                    .build();

                try {
                    final int currentAttempt = attempt;
                    body = client.post()
                        .contentType(MediaType.APPLICATION_JSON)
                        .body(BodyInserters.fromValue(payload))
                        .exchangeToMono(resp -> resp.bodyToMono(String.class).defaultIfEmpty("")
                            .map(b -> logAndReturn(b, resp.statusCode().value(), currentAttempt))
                        ).block();

                    if (body != null && !body.isBlank()) {
                        log.info("Received non-empty image response on WebClient attempt {} ({} bytes)", 
                            attempt, body.length());
                        break;
                    }
                    log.warn("WebClient attempt {} returned empty body", attempt);
                } catch (Exception ex) {
                    log.warn("WebClient attempt {} failed with exception: {}", attempt, ex.getMessage());
                }

                if (attempt < MAX_WEBCLIENT_ATTEMPTS) {
                    try { Thread.sleep(WEBCLIENT_BACKOFF_MS * attempt); } 
                    catch (InterruptedException ie) { Thread.currentThread().interrupt(); }
                }
            }

            // Fallback to JDK HttpClient if WebClient failed
            if (body == null || body.isBlank()) {
                body = tryJdkHttpClientFallback(imageEndpoint, effectiveApiKey, payload);
            }

            if (body == null) return null;

            Map<String, Object> resp = objectMapper.readValue(body, new TypeReference<Map<String, Object>>(){});
            return parseImageResponse(resp);

        } catch (WebClientResponseException wcre) {
            logWebClientError(wcre);
        } catch (Exception e) {
            log.warn("Image generation/parse failed: {}", e.getMessage());
        }
        return null;
    }

    private String generateImageDataUrlForced(String prompt) {
        try {
            String effectiveApiKey = apiKeyResolver.resolveEffectiveApiKey();
            if (geminiImageUrl == null || geminiImageUrl.isBlank() || 
                effectiveApiKey == null || effectiveApiKey.isBlank()) {
                log.debug("Skipping forced image generation because gemini.image.url or API key is not configured");
                return null;
            }

            Map<String, Object> generationConfig = Map.of("responseModalities", List.of("Image"));
            Map<String, Object> payloadMap = Map.of(
                "contents", List.of(Map.of("parts", List.of(Map.of("text", "Generate image for: " + prompt)))),
                "generationConfig", generationConfig
            );
            String payload = objectMapper.writeValueAsString(payloadMap);

            java.net.http.HttpClient jclient = java.net.http.HttpClient.newHttpClient();
            java.net.http.HttpRequest req = java.net.http.HttpRequest.newBuilder()
                .uri(new URI(geminiImageUrl))
                .header("Content-Type", "application/json")
                .header("x-goog-api-key", effectiveApiKey)
                .POST(java.net.http.HttpRequest.BodyPublishers.ofString(payload))
                .build();

            java.net.http.HttpResponse<String> resp = jclient.send(req, 
                java.net.http.HttpResponse.BodyHandlers.ofString());
            
            String body = resp.body();
            if (body != null && !body.isBlank()) {
                logForcedResponse(body, resp.statusCode());
                Map<String, Object> respMap = objectMapper.readValue(body, 
                    new TypeReference<Map<String, Object>>(){});
                return responseParser.parseImageResponseMap(respMap);
            } else {
                log.warn("Forced JDK HttpClient returned empty body status={}", resp.statusCode());
            }
        } catch (Exception e) {
            log.warn("Forced image generation failed: {}", e.getMessage());
        }
        return null;
    }

    private String parseImageResponse(Map<String, Object> resp) {
        // Try expected path first
        String parsed = responseParser.parseImageResponseMap(resp);
        if (parsed != null) return parsed;

        // Fallback: recursive scan
        try {
            Map<String, String> found = responseParser.recursiveFindBase64AndMime(resp);
            if (found != null && found.get("base64") != null) {
                String mime = found.getOrDefault("mime", "image/png");
                log.info("Parsed inline image via recursive scan mime={} size={}", 
                    mime, found.get("base64").length());
                return "data:" + mime + ";base64," + found.get("base64");
            }
        } catch (Exception ignore) {
            log.debug("Recursive scan failed: {}", ignore.getMessage());
        }

        // Log sanitized response for debugging
        logSanitizedResponse(resp);
        return null;
    }

    private String resolveImageEndpoint() {
        String imageEndpoint = geminiImageUrl;
        if ((imageEndpoint == null || imageEndpoint.isBlank()) && 
            geminiApiUrl != null && !geminiApiUrl.isBlank()) {
            imageEndpoint = deriveImageEndpointFromTextApi(geminiApiUrl);
        }
        return imageEndpoint;
    }

    private String deriveImageEndpointFromTextApi(String textApiUrl) {
        if (textApiUrl == null) return null;
        try {
            int modelsIdx = textApiUrl.indexOf("/models/");
            if (modelsIdx >= 0) {
                int start = modelsIdx + "/models/".length();
                int colon = textApiUrl.indexOf(':', start);
                if (colon > start) {
                    String before = textApiUrl.substring(0, start);
                    String after = textApiUrl.substring(colon);
                    String modelToUse = (geminiImageModel != null && !geminiImageModel.isBlank()) ? 
                        geminiImageModel : "gemini-2.5-flash-image";
                    return before + modelToUse + after;
                }
            }
        } catch (Exception e) {
            log.debug("deriveImageEndpointFromTextApi failed for url {}: {}", textApiUrl, e.getMessage(), e);
        }
        return textApiUrl;
    }

    public String generatePlaceholderDataUrl(String title) {
        try {
            String safe = title == null ? "Recipe" : title.replaceAll("[<>]", "");
            String svg = String.format(
                "<svg xmlns='http://www.w3.org/2000/svg' width='640' height='400'>" +
                "<defs><linearGradient id='g' x1='0' x2='1'>" +
                "<stop offset='0' stop-color='%s'/><stop offset='1' stop-color='%s'/>" +
                "</linearGradient></defs>" +
                "<rect width='100%%' height='100%%' fill='url(#g)'/>" +
                "<text x='50%%' y='50%%' dominant-baseline='middle' text-anchor='middle' " +
                "font-family='Arial' font-size='36' fill='white'>%s</text>" +
                "</svg>", 
                "#f59e0b", "#f97316", safe
            );
            String encoded = URLEncoder.encode(svg, StandardCharsets.UTF_8.toString());
            return "data:image/svg+xml;utf8," + encoded;
        } catch (Exception e) {
            log.debug("Failed to generate placeholder SVG: {}", e.getMessage());
            return null;
        }
    }

    public boolean isImageGenerationEnabled() {
        return geminiImageEnabled;
    }

    private String logAndReturn(String body, int statusCode, int attempt) {
        try {
            if (body == null || body.isBlank()) {
                log.warn("Image endpoint returned HTTP {} with empty body on attempt {}", statusCode, attempt);
            } else {
                try {
                    Map<String, Object> tmpResp = objectMapper.readValue(body, 
                        new TypeReference<Map<String, Object>>(){});
                    Map<String, Object> san = responseParser.sanitizeResponseForDebug(tmpResp);
                    String sanJson = objectMapper.writeValueAsString(san);
                    String snip = sanJson.length() > 1000 ? sanJson.substring(0, 1000) + "..." : sanJson;
                    log.info("Sanitized image response (truncated): {}", snip);
                } catch (Exception ex) {
                    String snippet = body.length() > 200 ? body.substring(0, 200) + "..." : body;
                    log.info("Raw image response snippet: {}", snippet.replaceAll("\n", "\\\\n"));
                }
            }
        } catch (Exception ex) {
            log.debug("Failed to persist image response debug info: {}", ex.getMessage());
        }
        return body;
    }

    private String tryJdkHttpClientFallback(String endpoint, String apiKey, Map<String, Object> payload) {
        try {
            log.warn("Attempting java.net.HttpClient fallback");
            java.net.http.HttpClient jclient = java.net.http.HttpClient.newHttpClient();
            String altPayload = objectMapper.writeValueAsString(payload);
            java.net.http.HttpRequest req = java.net.http.HttpRequest.newBuilder()
                .uri(new URI(endpoint))
                .header("Content-Type", "application/json")
                .header("x-goog-api-key", apiKey)
                .POST(java.net.http.HttpRequest.BodyPublishers.ofString(altPayload))
                .build();
            java.net.http.HttpResponse<String> altResp = jclient.send(req, 
                java.net.http.HttpResponse.BodyHandlers.ofString());
            
            String altBody = altResp.body();
            if (altBody != null && !altBody.isBlank()) {
                logForcedResponse(altBody, altResp.statusCode());
                return altBody;
            } else {
                log.warn("JDK HttpClient also returned empty body — status={}", altResp.statusCode());
            }
        } catch (Exception ex) {
            log.debug("JDK HttpClient fallback failed: {}", ex.getMessage());
        }
        return null;
    }

    private void logForcedResponse(String body, int statusCode) {
        try {
            Map<String, Object> tmpResp = objectMapper.readValue(body, 
                new TypeReference<Map<String, Object>>(){});
            Map<String, Object> san = responseParser.sanitizeResponseForDebug(tmpResp);
            String sanJson = objectMapper.writeValueAsString(san);
            String snip = sanJson.length() > 1000 ? sanJson.substring(0, 1000) + "..." : sanJson;
            log.info("Forced JDK HttpClient response (status={}): {}", statusCode, snip);
        } catch (Exception ex) {
            String snippet = body.length() > 200 ? body.substring(0, 200) + "..." : body;
            log.info("Forced JDK HttpClient response snippet (status={}): {}", statusCode, 
                snippet.replaceAll("\n", "\\\\n"));
        }
    }

    private void logWebClientError(WebClientResponseException wcre) {
        String respBody = null;
        try {
            respBody = wcre.getResponseBodyAsString();
        } catch (Exception ex) {
            log.debug("Failed to read error response body: {}", ex.getMessage(), ex);
        }
        try {
            String snippet = respBody == null ? "" : 
                (respBody.length() > 200 ? respBody.substring(0, 200) + "..." : respBody);
            log.warn("Image endpoint returned HTTP {} — response snippet: {}", 
                wcre.getStatusCode().value(), snippet);
        } catch (Exception ex) {
            log.warn("Failed to write image error body: {}", ex.getMessage());
        }
    }

    private void logSanitizedResponse(Map<String, Object> resp) {
        try {
            Map<String, Object> sanitized = responseParser.sanitizeResponseForDebug(resp);
            String sanJson = objectMapper.writeValueAsString(sanitized);
            String snip = sanJson.length() > 1000 ? sanJson.substring(0, 1000) + "..." : sanJson;
            log.warn("Image response did not contain expected inline payload; sanitized (truncated): {}", snip);
        } catch (Exception ex) {
            log.warn("Failed to produce sanitized image response for logs: {}", ex.getMessage());
        }
    }
}
