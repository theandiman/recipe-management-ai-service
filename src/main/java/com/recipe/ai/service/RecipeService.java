package com.recipe.ai.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.recipe.ai.model.RecipeDTO;
import com.recipe.ai.schema.JsonSchema;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.http.HttpStatus;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.BodyInserters;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.ExchangeStrategies;
import org.springframework.http.client.reactive.ReactorClientHttpConnector;
import reactor.netty.http.client.HttpClient;

import java.util.List;
import java.util.Map;
import java.util.Base64;
import java.net.URLEncoder;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import com.recipe.ai.dto.GeminiResponse;
import com.recipe.ai.dto.Candidate;
import com.recipe.ai.dto.Content;
import com.recipe.ai.dto.Part;
import com.recipe.ai.model.RecipeGenerationRequest;
import com.recipe.ai.model.ImageGenerationRequest;

/**
 * Service to handle communication with the Gemini API for recipe generation.
 * This service is responsible for constructing the API payload (including the JSON schema)
 * and handling the structured response.
 */
@Service
public class RecipeService {

    private static final Logger log = LoggerFactory.getLogger(RecipeService.class);

    // In a real Spring Boot app, this would be loaded from application.properties/yml
    @Value("${gemini.api.url:https://generativelanguage.googleapis.com/v1beta/models/gemini-2.5-flash:generateContent}")
    private String geminiApiUrl;

    // Use a placeholder value; the actual key must be configured in your environment
    @Value("${gemini.api.key:YOUR_SECURE_API_KEY_HERE}")
    private String geminiApiKey;

    private final ObjectMapper objectMapper;

    // Defines the strict JSON schema for the recipe output
    private static final JsonSchema RECIPE_SCHEMA = RecipeDTO.getSchema();

    // System instructions guide the model's persona and output format
    // Externalized via application properties: gemini.system.prompt
    @Value("${gemini.system.prompt:You are a world-class chef. Based on the user's request, generate a unique, appealing, and easy-to-follow recipe. Use common metric or imperial units as appropriate. Ensure your response strictly follows the provided JSON schema.}")
    private String systemPrompt;

    // When true, any failure contacting the remote API will return a local mock recipe
    @Value("${gemini.dev.fallback:false}")
    private boolean devFallback;

    // Image generation controls: enable and optional endpoint URL
    @Value("${gemini.image.enabled:false}")
    private boolean geminiImageEnabled;

    @Value("${gemini.image.url:}")
    private String geminiImageUrl;

    // Allow selecting a different image model (fallback default kept conservative)
    @Value("${gemini.image.model:gemini-2.5-flash-image}")
    private String geminiImageModel;



    // Keep the builder and create a client per-call after @Value injection
    private final WebClient.Builder webClientBuilder;

    public RecipeService(WebClient.Builder webClientBuilder, ObjectMapper objectMapper) {
        // Do not build the WebClient here because @Value fields may not be populated yet.
        this.webClientBuilder = webClientBuilder;
        // Make a defensive copy of the provided ObjectMapper to avoid retaining a mutable external reference
        this.objectMapper = objectMapper == null ? new ObjectMapper() : objectMapper.copy();
    }

    /**
     * Minimal time-constraint checks. Returns an empty list when no violations.
     * If maxTotalMinutes is null, this is a no-op (empty list returned).
     */
    java.util.List<String> runTimeConstraintChecks(Map<String, Object> recipeObj, Integer maxTotalMinutes) {
        java.util.List<String> violations = new java.util.ArrayList<>();
        if (maxTotalMinutes == null) return violations;

        // 1) Prefer an explicit numeric field returned by the model
        Object etm = recipeObj.get("estimatedTimeMinutes");
        if (etm instanceof Number) {
            int est = ((Number) etm).intValue();
            if (est > maxTotalMinutes) {
                violations.add(String.format("Estimated total time %d minutes exceeds maximum allowed %d minutes", est, maxTotalMinutes));
                return violations;
            }
            return violations;
        }

        // 2) Fallback: try to parse prepTime string like '45 minutes' or '1 hour 30 minutes'
        Object pt = recipeObj.get("prepTime");
        if (pt instanceof String) {
            Integer parsed = parseMinutesFromString((String) pt);
            if (parsed != null && parsed > maxTotalMinutes) {
                violations.add(String.format("Parsed prepTime %d minutes exceeds maximum allowed %d minutes", parsed, maxTotalMinutes));
                return violations;
            }
        }

        // No explicit violation found
        return violations;
    }

    // Very small helper: extract first reasonable minute estimate from a human string.
    private Integer parseMinutesFromString(String s) {
        if (s == null) return null;
        try {
            String low = s.toLowerCase();
            // handle patterns like '1 hour 30 minutes' or '45 minutes' or 'about 20-30 mins'
            int minutes = 0;
            java.util.regex.Matcher mHour = java.util.regex.Pattern.compile("(\\d+)\\s*hour").matcher(low);
            if (mHour.find()) {
                minutes += Integer.parseInt(mHour.group(1)) * 60;
            }
            java.util.regex.Matcher mMin = java.util.regex.Pattern.compile("(\\d+)\\s*(?:minute|min)s?").matcher(low);
            if (mMin.find()) {
                minutes += Integer.parseInt(mMin.group(1));
            }
            if (minutes > 0) return minutes;
            // fallback: capture a lone number and assume minutes
            java.util.regex.Matcher mNum = java.util.regex.Pattern.compile("(\\d+)").matcher(low);
            if (mNum.find()) {
                return Integer.parseInt(mNum.group(1));
            }
        } catch (Exception e) {
            log.debug("parseMinutesFromString failed for '{}': {}", s, e.getMessage());
        }
        return null;
    }

    // Helper: parse a numeric minute value from a map field which may be a Number or a human string
    private Integer parseMinutesFromField(Map<String, Object> m, String key) {
        if (m == null || key == null) return null;
        try {
            Object v = m.get(key);
            if (v == null) return null;
            if (v instanceof Number) return ((Number) v).intValue();
            if (v instanceof String) return parseMinutesFromString((String) v);
        } catch (Exception e) {
            log.debug("parseMinutesFromField failed for key='{}': {}", key, e.getMessage());
        }
        return null;
    }

    // Exposed for unit tests to verify key resolution logic
    public String resolveEffectiveApiKey() {
        String apiKeyFromSysProp = System.getProperty("GEMINI_API_KEY");
        String apiKeyFromEnv = System.getenv("GEMINI_API_KEY");
        if (apiKeyFromSysProp != null && !apiKeyFromSysProp.isBlank()) {
            return apiKeyFromSysProp;
        } else if (apiKeyFromEnv != null && !apiKeyFromEnv.isBlank()) {
            return apiKeyFromEnv;
        } else {
            // If not set via system property or environment, try a local .env file (common in dev setups)
            try {
                java.nio.file.Path envPath = java.nio.file.Paths.get(".env");
                if (java.nio.file.Files.exists(envPath)) {
                    java.util.List<String> lines = java.nio.file.Files.readAllLines(envPath, java.nio.charset.StandardCharsets.UTF_8);
                    for (String line : lines) {
                        if (line == null) continue;
                        String trimmed = line.trim();
                        if (trimmed.isEmpty() || trimmed.startsWith("#") || !trimmed.contains("=")) continue;
                        String[] parts = trimmed.split("=", 2);
                        if (parts.length == 2) {
                            String k = parts[0].trim();
                            String v = parts[1].trim();
                            if ("GEMINI_API_KEY".equals(k) && v.length() > 0) {
                                return v;
                            }
                        }
                    }
                }
            } catch (Exception e) {
                // Log reading errors for dev visibility but continue to fall back to configured property
                log.debug("Failed to read .env for GEMINI_API_KEY: {}", e == null ? "null" : e.getMessage(), e);
            }
            return geminiApiKey;
        }
    }

    /**
     * Generates a recipe by calling the Gemini API with a structured prompt and schema.
     * @param prompt The user's recipe request.
     * @param pantryItems A list of ingredients to prioritize.
     * @return A JSON String representing the generated recipe object, or null on failure.
     */
    public String generateRecipe(String prompt, List<String> pantryItems) {
    // Default to metric for backward compatibility
    return generateRecipe(prompt, pantryItems, com.recipe.ai.model.Units.METRIC);
    }

    /**
     * Generate a recipe using the requested units system.
     * @param prompt The user's recipe request.
     * @param pantryItems Ingredients to prioritize.
     * @param units The Units enum value (METRIC or IMPERIAL)
     * @return JSON recipe string or null on failure
     */
    public String generateRecipe(String prompt, List<String> pantryItems, com.recipe.ai.model.Units units) {
        // Delegate to the new overload that accepts dietary constraints (backwards compatible)
        return generateRecipe(prompt, pantryItems, units, List.of(), List.of());
    }

    /**
     * Generates a recipe with dietary preferences and allergen constraints.
     * 
     * @param prompt The user's recipe request
     * @param pantryItems A list of ingredients to prioritize
     * @param units The measurement system to use (METRIC or IMPERIAL)
     * @param dietaryPreferences List of dietary requirements (e.g., "vegan", "vegetarian", "gluten-free")
     * @param allergies List of allergens to avoid (e.g., "peanuts", "dairy", "shellfish")
     * @return JSON string representing the generated recipe, or null on failure
     */
    public String generateRecipe(String prompt, List<String> pantryItems, com.recipe.ai.model.Units units, List<String> dietaryPreferences, List<String> allergies) {
        // Delegate to the 6-arg overload with no time limit for backward compatibility
        return generateRecipe(prompt, pantryItems, units, dietaryPreferences, allergies, null);
    }

    /**
     * Generates a recipe with dietary preferences, allergen constraints, and time limit.
     * This is the primary implementation method that all other overloads delegate to.
     * 
     * @param prompt The user's recipe request
     * @param pantryItems A list of ingredients to prioritize
     * @param units The measurement system to use (METRIC or IMPERIAL)
     * @param dietaryPreferences List of dietary requirements (e.g., "vegan", "vegetarian", "gluten-free")
     * @param allergies List of allergens to avoid (e.g., "peanuts", "dairy", "shellfish")
     * @param maxTotalMinutes Maximum total cooking time in minutes, or null for no time constraint
     * @return JSON string representing the generated recipe, or null on failure
     */
    public String generateRecipe(String prompt, List<String> pantryItems, com.recipe.ai.model.Units units, List<String> dietaryPreferences, List<String> allergies, Integer maxTotalMinutes) {
        String basePrompt = buildPrompt(prompt, pantryItems, units);
        // Append dietary and allergy constraints to the user-visible prompt so the model honors them
        StringBuilder sb = new StringBuilder(basePrompt);
        if (dietaryPreferences != null && !dietaryPreferences.isEmpty()) {
            sb.append(" Ensure the recipe conforms to the following dietary preferences: ");
            sb.append(String.join(", ", dietaryPreferences));
            sb.append('.');
        }
        if (allergies != null && !allergies.isEmpty()) {
            sb.append(" Avoid any ingredients or common substitutes that contain: ");
            sb.append(String.join(", ", allergies));
            sb.append('.');
        }
        String finalPrompt = sb.toString();

        // Resolve the effective API key (system property, env var, .env file, then configured property)
        String effectiveApiKey = resolveEffectiveApiKey();
    log.info("gemini.image.enabled={} (property), effectiveApiKeyPresent={}", geminiImageEnabled, effectiveApiKey != null && !effectiveApiKey.isBlank());

        if (effectiveApiKey == null || effectiveApiKey.isBlank() || effectiveApiKey.contains("YOUR_SECURE_API_KEY_HERE")) {
            log.warn("No Gemini API key configured (system prop GEMINI_API_KEY, env GEMINI_API_KEY, .env, or gemini.api.key). Calls will fail until an API key is set.");
        }

        // Conservative guard: if there's no effective API key and devFallback is false, return a local mock
        // instead of attempting outbound calls (avoids repeated 403s during local development).
        if ((effectiveApiKey == null || effectiveApiKey.isBlank() || effectiveApiKey.contains("YOUR_SECURE_API_KEY_HERE")) && !devFallback) {
            log.info("No effective Gemini API key found and devFallback disabled — returning development mock to avoid outbound calls.");
            return createMockRecipe(pantryItems);
        }

        // If developer fallback is explicitly enabled and no API key is present, return the local mock
        // immediately to avoid attempting outbound calls during local development.
        if (devFallback && (effectiveApiKey == null || effectiveApiKey.isBlank() || effectiveApiKey.contains("YOUR_SECURE_API_KEY_HERE"))) {
            log.info("devFallback enabled and no API key present — returning development mock recipe.");
            return createMockRecipe(pantryItems);
        }

        // 1. Construct the API Payload
        Map<String, Object> payload = Map.of(
            "contents", List.of(
                Map.of("parts", List.of(Map.of("text", finalPrompt)))
            ),
            "systemInstruction", Map.of("parts", List.of(Map.of("text", systemPrompt))),
            "generationConfig", Map.of(
                "responseMimeType", "application/json",
                "responseSchema", RECIPE_SCHEMA
            )
        );

        try {
            // 2. Execute the HTTP POST request (using WebClient) with a small retry policy
            final int maxAttempts = 3;
            String responseBody = null;

            for (int attempt = 1; attempt <= maxAttempts; attempt++) {
                try {
                    WebClient client = webClientBuilder
                        .clientConnector(new ReactorClientHttpConnector(HttpClient.create().wiretap(true)))
                        .baseUrl(geminiApiUrl)
                        .defaultHeader("x-goog-api-key", effectiveApiKey)
                        .build();

                    responseBody = client.post()
                        .contentType(MediaType.APPLICATION_JSON)
                        .body(BodyInserters.fromValue(payload))
                        .retrieve()
                        .bodyToMono(String.class)
                        .block();

                    // if we got a non-null response body, break out and parse it
                    if (responseBody != null) {
                        break;
                    }

                } catch (WebClientResponseException wcre) {
                    // Handle HTTP status codes. For 403, honor the devFallback immediately.
                    log.error("Gemini API returned HTTP {} on attempt {}: {}", wcre.getStatusCode().value(), attempt, wcre.getMessage());
                    if (wcre.getStatusCode() == HttpStatus.FORBIDDEN) {
                        if (devFallback) {
                            log.warn("Received 403 Forbidden from Gemini API; devFallback enabled — returning development mock response.");
                            return createMockRecipe(pantryItems);
                        } else {
                            log.warn("Received 403 Forbidden from Gemini API; devFallback disabled — not returning mock.");
                            return null;
                        }
                    }
                    // For other 5xx/429 style errors, retry unless we've exhausted attempts
                    if (attempt >= maxAttempts) {
                        log.error("Gemini API returned HTTP {} on final attempt: {}", wcre.getStatusCode().value(), wcre.getMessage(), wcre);
                        return null;
                    } else {
                        long backoffMs = 300L * attempt;
                        log.warn("Attempt {} failed with HTTP {} — retrying after {}ms", attempt, wcre.getStatusCode().value(), backoffMs);
                        try { Thread.sleep(backoffMs); } catch (InterruptedException ie) { Thread.currentThread().interrupt(); }
                        continue;
                    }

                } catch (org.springframework.web.reactive.function.client.WebClientRequestException we) {
                    // Transport errors (connectivity/timeouts) — retry a few times
                    log.error("Transport/WebClient error on attempt {}: {}", attempt, we.getMessage());
                    if (attempt >= maxAttempts) {
                        log.error("Transport/WebClient error on final attempt: {}", we.getMessage(), we);
                        return null;
                    } else {
                        long backoffMs = 300L * attempt;
                        log.warn("Attempt {} transport error — retrying after {}ms", attempt, backoffMs);
                        try { Thread.sleep(backoffMs); } catch (InterruptedException ie) { Thread.currentThread().interrupt(); }
                        continue;
                    }
                }
            }

            if (responseBody == null) {
                // No response obtained after retries
                log.error("No response body obtained from Gemini API after {} attempts", maxAttempts);
                return null;
            }

            // 3. Parse the result into DTOs to avoid unchecked casts
            GeminiResponse geminiResponse = objectMapper.readValue(responseBody, GeminiResponse.class);
            List<Candidate> candidates = geminiResponse.getCandidates();
            if (candidates != null && !candidates.isEmpty()) {
                Content content = candidates.get(0).getContent();
                if (content != null) {
                    List<Part> parts = content.getParts();
                    if (parts != null && !parts.isEmpty() && parts.get(0).getText() != null) {
                        String recipeJson = parts.get(0).getText(); // Final structured JSON recipe string
                        try {
                            // parse and run safety checks before any further processing
                            @SuppressWarnings("unchecked")
                            Map<String, Object> obj = objectMapper.readValue(recipeJson, Map.class);
                            // Ensure the response includes a numeric estimatedTimeMinutes when possible.
                            // Prefer an existing numeric field; otherwise derive from estimatedTime string,
                            // or sum prepTime + cookTime (when cookTime is present).
                            try {
                                Object existing = obj.get("estimatedTimeMinutes");
                                Integer computed = null;
                                if (existing instanceof Number) {
                                    computed = ((Number) existing).intValue();
                                } else {
                                    // Try an alternative explicit string field
                                    // Prefer explicit estimatedTime string if present
                                    Integer parsedEst = parseMinutesFromField(obj, "estimatedTime");
                                    if (parsedEst != null) {
                                        computed = parsedEst;
                                    } else {
                                        // Parse prepTime and cookTime and only compute a total when cookTime exists
                                        Integer p = parseMinutesFromField(obj, "prepTime");
                                        Integer c = parseMinutesFromField(obj, "cookTime");
                                        if (c != null && c > 0) {
                                            computed = (p == null ? 0 : p) + c;
                                        }
                                    }
                                    // NOTE: deliberately do NOT fall back to using only prepTime as the
                                    // estimated total. Many recipes report prepTime alone and that value
                                    // often represents the bulk of work (or may already include cook time).
                                    // We require either an explicit estimatedTime/estimatedTimeMinutes
                                    // or presence of cookTime so that a total is meaningful.
                                }

                                if (computed != null && !(obj.get("estimatedTimeMinutes") instanceof Number)) {
                                    // Only inject if not already present as a numeric value
                                    obj.put("estimatedTimeMinutes", computed);
                                    try {
                                        recipeJson = objectMapper.writeValueAsString(obj);
                                    } catch (Exception ex) {
                                        // Log at debug level; non-fatal — we'll return the original recipe JSON if serialization fails
                                        log.debug("Failed to serialize recipe after injecting estimatedTimeMinutes: {}", ex.getMessage());
                                    }
                                }
                            } catch (Exception e) {
                                // non-fatal; continue without estimatedTimeMinutes
                                log.debug("Failed to compute estimatedTimeMinutes: {}", e.getMessage());
                            }

                            // Time constraint check: if the caller passed a maxTotalMinutes to the
                            // 6-arg overload, enforce it conservatively using model-provided
                            // 'estimatedTimeMinutes' or parsed prepTime strings.
                            List<String> timeViolations = runTimeConstraintChecks(obj, maxTotalMinutes);
                            if (timeViolations != null && !timeViolations.isEmpty()) {
                                Map<String, Object> err = new java.util.LinkedHashMap<>();
                                err.put("error", "constraint_violation");
                                err.put("message", "Generated recipe violates requested time constraints.");
                                err.put("details", Map.of("violations", timeViolations));
                                return objectMapper.writeValueAsString(err);
                            }
                            if (!obj.containsKey("imageUrl")) {
                                String title = obj.getOrDefault("recipeName", "Recipe").toString();
                                // Prepare imageGeneration metadata to help frontend show status/errors
                                Map<String, Object> imageGeneration = new java.util.LinkedHashMap<>();
                                imageGeneration.put("status", "not_attempted");
                                imageGeneration.put("source", "");
                                imageGeneration.put("errorMessage", "");

                                // Try to generate an image when enabled (use same Gemini API URL & key)
                                String generatedImage = null;
                                if (geminiImageEnabled) {
                                    imageGeneration.put("status", "attempting");
                                    try {
                                        // Convert recipe Map to RecipeDTO for richer image prompts
                                        RecipeDTO recipeForImage = objectMapper.convertValue(obj, RecipeDTO.class);
                                        ImageGenerationRequest imageRequest = new ImageGenerationRequest();
                                        imageRequest.setRecipe(recipeForImage);
                                        
                                        // Use generateImageFromRequest for full recipe context
                                        Map<String, Object> imageResult = generateImageFromRequest(imageRequest);
                                        
                                        if ("success".equals(imageResult.get("status"))) {
                                            generatedImage = (String) imageResult.get("imageUrl");
                                            imageGeneration.put("status", "success");
                                            imageGeneration.put("source", imageResult.getOrDefault("source", ""));
                                        } else {
                                            imageGeneration.put("status", "failed");
                                            imageGeneration.put("errorMessage", imageResult.getOrDefault("errorMessage", "no_image_returned"));
                                        }
                                    } catch (Exception e) {
                                        // record failure status and message for frontend visibility
                                        log.debug("Image generation failed for recipe='{}': {}", title, e.getMessage());
                                        imageGeneration.put("status", "failed");
                                        imageGeneration.put("errorMessage", e.getMessage() == null ? "unknown_error" : e.getMessage());
                                    }
                                } else {
                                    imageGeneration.put("status", "skipped");
                                    imageGeneration.put("errorMessage", "image_generation_disabled");
                                }

                                if (generatedImage != null) {
                                    obj.put("imageUrl", generatedImage);
                                } else {
                                    // If images are disabled, do not inject the placeholder — only set metadata
                                    if (geminiImageEnabled) {
                                        obj.put("imageUrl", generatePlaceholderDataUrl(title));
                                        if (!imageGeneration.containsKey("status") || "failed".equals(imageGeneration.get("status"))) {
                                            imageGeneration.put("source", "placeholder");
                                        }
                                    } else {
                                        // indicate skipped and leave out imageUrl so frontend can detect absence
                                        imageGeneration.put("status", "skipped");
                                        imageGeneration.put("source", "");
                                        imageGeneration.put("errorMessage", "image_generation_disabled");
                                    }
                                }

                                obj.put("imageGeneration", imageGeneration);

                                return objectMapper.writeValueAsString(obj);
                            }
                        } catch (Exception e) {
                            // if parsing fails, just return the original recipe JSON
                            log.debug("Failed to parse or inject imageUrl into recipe JSON: {}", e.getMessage());
                        }
                        return recipeJson;
                    }
                }
            }
            return null;

        } catch (com.fasterxml.jackson.core.JsonProcessingException e) {
            // JSON parsing issues
            log.error("Failed to parse Gemini API response: {}", e.getMessage(), e);
            return null;
        } catch (RuntimeException e) {
            // Catch any unexpected runtime exceptions to avoid leaking internal state
            log.error("Unexpected error in generateRecipe: {}", e.getMessage(), e);
            // If dev fallback is enabled or no API key present, return a local mock so dev smoke tests succeed
            if (devFallback || effectiveApiKey == null || effectiveApiKey.isBlank() || effectiveApiKey.contains("YOUR_SECURE_API_KEY_HERE")) {
                log.warn("Falling back to development mock recipe (devFallback={}, keyPresent={}).", devFallback, effectiveApiKey != null && !effectiveApiKey.isBlank());
                return createMockRecipe(pantryItems);
            }
            return null;
        }
    }

    /**
     * Generate a recipe using a RecipeGenerationRequest DTO and return a RecipeDTO.
     * This is the new recommended method for type-safe recipe generation.
     * 
     * @param request The recipe generation request containing all parameters
     * @return A RecipeDTO object, or null on failure
     */
    public RecipeDTO generateRecipeDTO(RecipeGenerationRequest request) {
        // Null check for request parameter
        if (request == null) {
            log.warn("generateRecipeDTO called with null request");
            return null;
        }

        // Extract parameters from request with defaults
        String prompt = request.getPrompt() != null ? request.getPrompt() : "";
        List<String> pantryItems = request.getPantryItems() != null ? new java.util.ArrayList<>(request.getPantryItems()) : List.of();
        // Handle Units enum directly, defaulting to METRIC if null
        com.recipe.ai.model.Units units = request.getUnits() != null ? request.getUnits() : com.recipe.ai.model.Units.METRIC;
        List<String> dietaryPreferences = request.getDietaryPreferences() != null ? new java.util.ArrayList<>(request.getDietaryPreferences()) : List.of();
        List<String> allergies = request.getAllergies() != null ? new java.util.ArrayList<>(request.getAllergies()) : List.of();
        Integer maxTotalMinutes = request.getMaxTotalMinutes();

        // Delegate to existing implementation
        String recipeJson = generateRecipe(prompt, pantryItems, units, dietaryPreferences, allergies, maxTotalMinutes);
        
        if (recipeJson == null) {
            return null;
        }

        // Parse JSON string into RecipeDTO
        try {
            return objectMapper.readValue(recipeJson, RecipeDTO.class);
        } catch (Exception e) {
            log.error("Failed to parse recipe JSON into DTO: {}", e.getMessage(), e);
            return null;
        }
    }

    private String createMockRecipe(List<String> pantryItems) {
        Map<String, Object> base = new java.util.LinkedHashMap<>();
        base.put("recipeName", "Simple Toast");
        base.put("description", "A quick and tasty toast using available pantry items.");
        base.put("ingredients", pantryItems == null || pantryItems.isEmpty()
                ? List.of("bread", "butter")
                : pantryItems.stream().map(i -> "1 x " + i).toList());
        base.put("instructions", List.of("Toast the bread.", "Spread butter on the toast.", "Serve immediately."));
        base.put("prepTime", "5 minutes");
        base.put("servings", "1");
        // Only add a placeholder image if image generation is enabled or we explicitly want a placeholder
        if (geminiImageEnabled) {
            base.put("imageUrl", generatePlaceholderDataUrl("Simple Toast"));
        }
        // Add imageGeneration metadata so the frontend can see this is a mocked response and whether images are enabled
        Map<String, Object> imageGeneration = new java.util.LinkedHashMap<>();
        if (geminiImageEnabled) {
            imageGeneration.put("status", "mock_placeholder");
            imageGeneration.put("source", "placeholder");
            imageGeneration.put("errorMessage", "");
        } else {
            imageGeneration.put("status", "skipped");
            imageGeneration.put("source", "");
            imageGeneration.put("errorMessage", "image_generation_disabled");
        }
        base.put("imageGeneration", imageGeneration);
        Map<String, Object> mock = base;
        try {
            return objectMapper.writeValueAsString(mock);
        } catch (com.fasterxml.jackson.core.JsonProcessingException jpe) {
            log.error("Failed to serialize mock recipe: {}", jpe.getMessage(), jpe);
            return null;
        }
    }

    /**
     * Generates a simple SVG data URL placeholder with the provided title.
     */
    private String generatePlaceholderDataUrl(String title) {
        try {
            String safe = title == null ? "Recipe" : title.replaceAll("[<>]", "");
            String svg = String.format("<svg xmlns='http://www.w3.org/2000/svg' width='640' height='400'><defs><linearGradient id='g' x1='0' x2='1'><stop offset='0' stop-color='%s'/><stop offset='1' stop-color='%s'/></linearGradient></defs><rect width='100%%' height='100%%' fill='url(#g)'/><text x='50%%' y='50%%' dominant-baseline='middle' text-anchor='middle' font-family='Arial' font-size='36' fill='white'>%s</text></svg>", "#f59e0b", "#f97316", safe);
            String encoded = URLEncoder.encode(svg, StandardCharsets.UTF_8.toString());
            return "data:image/svg+xml;utf8," + encoded;
        } catch (Exception e) {
            log.debug("Failed to generate placeholder SVG: {}", e.getMessage());
            return null;
        }
    }

    /**
     * Attempt to generate an image via the configured Gemini image endpoint.
     * The endpoint is expected to return JSON containing either an 'imageData' (base64) field
     * or an 'imageUrl' field. Returns a data:image URI or external URL, or null on failure.
     */
    // Public wrapper for testing and external invocation from controllers
    public Map<String, Object> generateImageForPrompt(String prompt) {
        return generateImageForPrompt(prompt, false);
    }

    /**
     * Generate image metadata for the given prompt. If forceCurl is true, the service will
     * issue an HTTP POST using the JDK HttpClient with the exact JSON shape used by the
     * successful curl probe and persist the full response to /tmp/gemini_image_raw_forced.json.
     */
    public Map<String, Object> generateImageForPrompt(String prompt, boolean forceCurl) {
        Map<String, Object> meta = new java.util.LinkedHashMap<>();
        try {
            String data = null;
            if (forceCurl) {
                data = generateImageDataUrlForced(prompt);
            } else {
                data = generateImageDataUrl(prompt);
            }
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
     * This method supports both simple prompts and full recipe context for richer image generation.
     * 
     * @param request The image generation request
     * @return A map containing image generation metadata (status, imageUrl, source, errorMessage)
     */
    public Map<String, Object> generateImageFromRequest(ImageGenerationRequest request) {
        return generateImageFromRequest(request, false);
    }

    /**
     * Generate image using an ImageGenerationRequest DTO with optional forceCurl flag.
     * 
     * @param request The image generation request
     * @param forceCurl If true, use JDK HttpClient with exact curl-like JSON shape
     * @return A map containing image generation metadata (status, imageUrl, source, errorMessage)
     */
    public Map<String, Object> generateImageFromRequest(ImageGenerationRequest request, boolean forceCurl) {
        // Null check for request parameter
        if (request == null) {
            log.warn("generateImageFromRequest called with null request");
            Map<String, Object> meta = new java.util.LinkedHashMap<>();
            meta.put("status", "failed");
            meta.put("imageUrl", null);
            meta.put("source", "");
            meta.put("errorMessage", "Request cannot be null");
            return meta;
        }

        // Build the image generation prompt
        String prompt = buildImagePrompt(request);
        if (prompt == null || prompt.isBlank()) {
            Map<String, Object> meta = new java.util.LinkedHashMap<>();
            meta.put("status", "failed");
            meta.put("imageUrl", null);
            meta.put("source", "");
            meta.put("errorMessage", "No prompt or recipe context provided");
            return meta;
        }

        // Delegate to existing implementation
        return generateImageForPrompt(prompt, forceCurl);
    }

    /**
     * Build a rich image generation prompt from the request.
     * Prioritizes recipe context over simple prompt for better image quality.
     * 
     * @param request The image generation request
     * @return A well-formatted prompt for image generation, or null if no content available
     */
    private String buildImagePrompt(ImageGenerationRequest request) {
        if (request == null) {
            log.debug("buildImagePrompt: request is null");
            return null;
        }

        // If recipe context is provided, create a detailed, descriptive prompt
        if (request.getRecipe() != null) {
            RecipeDTO recipe = request.getRecipe();
            log.debug("buildImagePrompt: Using recipe context - name: {}, ingredients: {}", 
                     recipe.getRecipeName(), 
                     recipe.getIngredients() != null ? recipe.getIngredients().size() : 0);
            StringBuilder sb = new StringBuilder();
            
            // Start with a clear image generation instruction
            sb.append("Create a professional, appetizing food photography image of ");
            
            // Recipe name and description
            if (recipe.getRecipeName() != null && !recipe.getRecipeName().isBlank()) {
                sb.append(recipe.getRecipeName());
            } else {
                sb.append("a delicious dish");
            }
            
            if (recipe.getDescription() != null && !recipe.getDescription().isBlank()) {
                sb.append(": ").append(recipe.getDescription());
            }
            
            sb.append(". ");
            
            // Key visual ingredients (limit to most prominent ones)
            if (recipe.getIngredients() != null && !recipe.getIngredients().isEmpty()) {
                // Extract ingredient names (remove quantities)
                java.util.List<String> visualIngredients = recipe.getIngredients().stream()
                    .limit(6) // Top 6 ingredients for visual context
                    .map(this::extractIngredientName)
                    .filter(name -> name != null && !name.isBlank())
                    .toList();
                
                if (!visualIngredients.isEmpty()) {
                    sb.append("The dish prominently features: ");
                    sb.append(String.join(", ", visualIngredients));
                    sb.append(". ");
                }
            }
            
            // Add styling guidance for appealing food photography
            sb.append("Style: professional food photography, ");
            sb.append("well-lit, appetizing presentation, ");
            sb.append("garnished and plated beautifully, ");
            sb.append("shallow depth of field, ");
            sb.append("natural lighting, ");
            sb.append("rustic wooden table or clean white background");
            
            String generatedPrompt = sb.toString();
            log.info("Generated rich image prompt: {}", generatedPrompt);
            return generatedPrompt;
        } 
        
        // Fall back to simple prompt if no recipe provided
        if (request.getPrompt() != null && !request.getPrompt().isBlank()) {
            log.debug("buildImagePrompt: Using simple prompt fallback");
            // Enhance the simple prompt with photography style guidance
            return "Create a professional food photography image: " + request.getPrompt() + 
                   ". Style: well-lit, appetizing, professional presentation";
        }
        
        log.warn("buildImagePrompt: No recipe or prompt provided");
        return null;
    }

    /**
     * Extract the ingredient name from a full ingredient string (e.g., "2 cups flour" -> "flour").
     * Uses simple heuristics to remove quantities and focus on the ingredient itself.
     * 
     * @param ingredientLine The full ingredient line with quantity
     * @return The extracted ingredient name, or the original string if extraction fails
     */
    private String extractIngredientName(String ingredientLine) {
        if (ingredientLine == null || ingredientLine.isBlank()) {
            return ingredientLine;
        }
        
        try {
            // Remove common quantity patterns:
            // - Leading numbers: "2 cups", "1/2 teaspoon", "3-4", "1.5"
            // - Common units: cup, cups, tablespoon, teaspoon, oz, lb, gram, ml, etc.
            String cleaned = ingredientLine
                .replaceAll("^\\d+(\\.\\d+)?\\s*", "") // Remove leading numbers
                .replaceAll("^\\d+/\\d+\\s*", "") // Remove fractions like 1/2
                .replaceAll("^\\d+-\\d+\\s*", "") // Remove ranges like 2-3
                .replaceAll("(?i)\\b(cup|cups|tablespoon|tablespoons|tbsp|teaspoon|teaspoons|tsp|ounce|ounces|oz|pound|pounds|lb|lbs|gram|grams|g|kilogram|kilograms|kg|milliliter|milliliters|ml|liter|liters|l|pinch|dash|handful|bunch)\\b", "")
                .replaceAll("\\s+", " ") // Normalize whitespace
                .trim();
            
            // If we reduced it to nothing, return the original
            if (cleaned.isBlank()) {
                return ingredientLine;
            }
            
            // Take first few words (usually the ingredient name)
            String[] words = cleaned.split("\\s+");
            if (words.length > 3) {
                // Too many words, likely has extra description - take first 3
                return String.join(" ", java.util.Arrays.copyOf(words, 3));
            }
            
            return cleaned;
        } catch (Exception e) {
            // If any processing fails, return original
            log.debug("Failed to extract ingredient name from '{}': {}", ingredientLine, e.getMessage());
            return ingredientLine;
        }
    }

    private String generateImageDataUrl(String prompt) {
        // Use the same Gemini API URL and resolve the effective key as used for text generation
        String effectiveApiKey = resolveEffectiveApiKey();
        // If an explicit image URL isn't configured, try to derive a reasonable one from the
        // configured text API URL (best-effort). This allows environments that only set a
        // single base text endpoint to still attempt image generation.
        String imageEndpoint = geminiImageUrl;
        if ((imageEndpoint == null || imageEndpoint.isBlank()) && geminiApiUrl != null && !geminiApiUrl.isBlank()) {
            imageEndpoint = deriveImageEndpointFromTextApi(geminiApiUrl);
        }

        if (imageEndpoint == null || imageEndpoint.isBlank() || effectiveApiKey == null || effectiveApiKey.isBlank()) {
            log.debug("Skipping image generation because gemini.image.url or API key is not configured");
            return null;
        }

        try {
        // Increase in-memory buffer to allow very large JSON responses (base64 image payloads)
        ExchangeStrategies strategies = ExchangeStrategies.builder()
            .codecs(configurer -> configurer.defaultCodecs().maxInMemorySize(32 * 1024 * 1024))
            .build();

        Map<String, Object> generationConfig = Map.of("responseModalities", List.of("Image"));
        Map<String, Object> payload = Map.of("contents", List.of(Map.of("parts", List.of(Map.of("text", "Generate image for: " + prompt)))) , "generationConfig", generationConfig);

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
                        .map(b -> {
                            try {
                                if (b == null || b.isBlank()) {
                                    String hdrs = objectMapper.writeValueAsString(resp.headers().asHttpHeaders());
                                    log.warn("Image endpoint returned HTTP {} with empty body on attempt {} — headers: {}", resp.statusCode().value(), currentAttempt, hdrs);
                                } else {
                                    try {
                                        Map<String, Object> tmpResp = objectMapper.readValue(b, new com.fasterxml.jackson.core.type.TypeReference<Map<String,Object>>(){});
                                        Map<String, Object> san = sanitizeResponseForDebug(tmpResp);
                                        String sanJson = objectMapper.writeValueAsString(san);
                                        String snip = sanJson.length() > 1000 ? sanJson.substring(0, 1000) + "..." : sanJson;
                                        log.info("Sanitized image response (truncated) : {}", snip);
                                    } catch (Exception ex) {
                                        String snippet = b.length() > 200 ? b.substring(0, 200) + "..." : b;
                                        log.info("Raw image response snippet: {}", snippet.replaceAll("\n", "\\n"));
                                    }
                                }
                            } catch (Exception ex) {
                                log.debug("Failed to persist image response debug info: {}", ex.getMessage());
                            }
                            return b;
                        })
                    ).block();

                // If we got a non-blank body, break early
                if (body != null && !body.isBlank()) {
                    log.info("Received non-empty image response on WebClient attempt {} ({} bytes)", attempt, body.length());
                    break;
                }

                log.warn("WebClient attempt {} returned empty body — will retry after {}ms if attempts remain", attempt, WEBCLIENT_BACKOFF_MS);
            } catch (Exception ex) {
                log.warn("WebClient attempt {} failed with exception: {}", attempt, ex.getMessage());
                // fall through to retry
            }

            // Backoff before next attempt (unless it's the last)
            if (attempt < MAX_WEBCLIENT_ATTEMPTS) {
                try { Thread.sleep(WEBCLIENT_BACKOFF_MS * attempt); } catch (InterruptedException ie) { Thread.currentThread().interrupt(); }
            }
        }

        if (body == null || body.isBlank()) {
            // After exhausting WebClient attempts, try the JDK HttpClient fallback (as diagnostic and potential fix)
            try {
                log.warn("Exhausted WebClient attempts ({}). Attempting java.net.HttpClient fallback to diagnose/obtain body.", MAX_WEBCLIENT_ATTEMPTS);
                java.net.http.HttpClient jclient = java.net.http.HttpClient.newHttpClient();
                String altPayload = objectMapper.writeValueAsString(payload);
                java.net.http.HttpRequest req = java.net.http.HttpRequest.newBuilder()
                    .uri(new URI(imageEndpoint))
                    .header("Content-Type", "application/json")
                    .header("x-goog-api-key", effectiveApiKey)
                    .POST(java.net.http.HttpRequest.BodyPublishers.ofString(altPayload))
                    .build();
                java.net.http.HttpResponse<String> altResp = jclient.send(req, java.net.http.HttpResponse.BodyHandlers.ofString());
                int sc = altResp.statusCode();
                String altBody = altResp.body();
                if (altBody != null && !altBody.isBlank()) {
                    try {
                        try {
                            Map<String, Object> tmpResp = objectMapper.readValue(altBody, new com.fasterxml.jackson.core.type.TypeReference<Map<String,Object>>(){});
                            Map<String, Object> san = sanitizeResponseForDebug(tmpResp);
                            String sanJson = objectMapper.writeValueAsString(san);
                            String snip = sanJson.length() > 1000 ? sanJson.substring(0, 1000) + "..." : sanJson;
                            log.info("JDK HttpClient received alt image response (status={}): {}", sc, snip);
                        } catch (Exception ex) {
                            String snippet = altBody.length() > 200 ? altBody.substring(0, 200) + "..." : altBody;
                            log.info("JDK HttpClient alt response snippet (status={}): {}", sc, snippet.replaceAll("\n", "\\n"));
                        }
                    } catch (Exception ex) { log.debug("Failed to process alt raw response: {}", ex.getMessage()); }
                    body = altBody; // let the normal parsing handle it
                } else {
                    log.warn("JDK HttpClient also returned empty body — status={}", sc);
                }
            } catch (Exception ex) {
                log.debug("JDK HttpClient fallback failed: {}", ex.getMessage());
            }
        }

    if (body == null) return null;

    Map<String, Object> resp = objectMapper.readValue(body, new com.fasterxml.jackson.core.type.TypeReference<Map<String,Object>>(){});

            // Simplified assumption: image will be in candidates[0].content.parts[1].inlineData.data
            List<?> candidates = (List<?>) resp.get("candidates");
            if (candidates != null && !candidates.isEmpty()) {
                Object cand0 = candidates.get(0);
                if (cand0 instanceof Map) {
                    Map<?,?> candMap = (Map<?,?>) cand0;
                    Map<?,?> content = (Map<?,?>) candMap.get("content");
                    if (content != null) {
                        List<?> parts = (List<?>) content.get("parts");
                        if (parts != null && parts.size() > 1) {
                            Object part1 = parts.get(1);
                            if (part1 instanceof Map) {
                                Map<?,?> p1 = (Map<?,?>) part1;
                                // prefer inlineData / inline_data
                                Object inline = p1.get("inlineData");
                                    if (inline == null) inline = p1.get("inline_data");
                                    String base64 = tryExtractBase64FromInline(inline);
                                    if (base64 != null) {
                                        Object mm = (inline instanceof Map) ? ((Map<?,?>) inline).get("mimeType") : null;
                                        if (mm == null && inline instanceof Map) mm = ((Map<?,?>) inline).get("mime_type");
                                        String mime = mm instanceof String ? (String) mm : "image/png";
                                        log.info("Parsed inline image at expected path mime={} size={}", mime, base64.length());
                                        return "data:" + mime + ";base64," + base64;
                                    }

                                // fallback: external URL on the same part
                                Object imageUrl = p1.get("imageUrl");
                                if (imageUrl instanceof String) {
                                    log.info("Found external image URL at expected path: {}", imageUrl);
                                    return (String) imageUrl;
                                }
                            }
                        }
                    }
                }
            }

            // Try a tolerant recursive scan for base64 inline blobs anywhere in the response
            try {
                Map<String, String> found = recursiveFindBase64AndMime(resp);
                if (found != null && found.get("base64") != null) {
                    String mime = found.getOrDefault("mime", "image/png");
                    log.info("Parsed inline image via recursive scan mime={} size={}", mime, found.get("base64").length());
                    return "data:" + mime + ";base64," + found.get("base64");
                }
            } catch (Exception ignore) {
                log.debug("Ignored exception during recursive scan: {}", ignore == null ? "null" : ignore.getMessage());
            }

            // Nothing found at the expected location — write a sanitized copy for debugging
                try {
                    Map<String, Object> sanitized = sanitizeResponseForDebug(resp);
                    String sanJson = objectMapper.writeValueAsString(sanitized);
                    String snip = sanJson.length() > 1000 ? sanJson.substring(0, 1000) + "..." : sanJson;
                    log.warn("Image response did not contain the expected inline payload; sanitized (truncated): {}", snip);
                } catch (Exception ex) {
                    log.warn("Failed to produce sanitized image response for logs: {}", ex.getMessage());
                }

        } catch (Exception e) {
            // If the server returned an HTTP error, try to persist the response body for debugging
            if (e instanceof WebClientResponseException) {
                WebClientResponseException wcre = (WebClientResponseException) e;
                String respBody = null;
                try {
                    respBody = wcre.getResponseBodyAsString();
                } catch (Exception ex) {
                    // Log rather than silently swallowing — helps diagnostics in CI and production
                    log.debug("Failed to read error response body from WebClientResponseException: {}", ex == null ? "null" : ex.getMessage(), ex);
                }
                try {
                    String snippet = respBody == null ? "" : (respBody.length() > 200 ? respBody.substring(0, 200) + "..." : respBody);
                    log.warn("Image endpoint returned HTTP {} — response snippet: {}", wcre.getStatusCode().value(), snippet);
                } catch (Exception ex) {
                    log.warn("Failed to write image error body: {}", ex.getMessage());
                }
            }
            log.warn("Image generation/parse failed: {}", e.getMessage());
        }
        return null;
    }

    /**
     * Force the exact-curl style POST to the image endpoint using java.net.HttpClient.
     * Persists the raw body to /tmp/gemini_image_raw_forced.json and tries to parse it.
     */
    private String generateImageDataUrlForced(String prompt) {
        try {
            String effectiveApiKey = resolveEffectiveApiKey();
            if (geminiImageUrl == null || geminiImageUrl.isBlank() || effectiveApiKey == null || effectiveApiKey.isBlank()) {
                log.debug("Skipping forced image generation because gemini.image.url or API key is not configured");
                return null;
            }

            Map<String, Object> generationConfig = Map.of("responseModalities", List.of("Image"));
            Map<String, Object> payloadMap = Map.of("contents", List.of(Map.of("parts", List.of(Map.of("text", "Generate image for: " + prompt)))) , "generationConfig", generationConfig);
            String payload = objectMapper.writeValueAsString(payloadMap);

        java.net.http.HttpClient jclient = java.net.http.HttpClient.newHttpClient();
        java.net.http.HttpRequest req = java.net.http.HttpRequest.newBuilder()
            .uri(new URI(geminiImageUrl))
            .header("Content-Type", "application/json")
            .header("x-goog-api-key", effectiveApiKey)
            .POST(java.net.http.HttpRequest.BodyPublishers.ofString(payload))
            .build();

        java.net.http.HttpResponse<String> resp = jclient.send(req, java.net.http.HttpResponse.BodyHandlers.ofString());
            int sc = resp.statusCode();
            String body = resp.body();
            if (body != null && !body.isBlank()) {
                try {
                    try {
                        Map<String, Object> tmpResp = objectMapper.readValue(body, new com.fasterxml.jackson.core.type.TypeReference<Map<String,Object>>(){});
                        Map<String, Object> san = sanitizeResponseForDebug(tmpResp);
                        String sanJson = objectMapper.writeValueAsString(san);
                        String snip = sanJson.length() > 1000 ? sanJson.substring(0, 1000) + "..." : sanJson;
                        log.info("Forced JDK HttpClient response (status={}): {}", sc, snip);
                    } catch (Exception ex) {
                        String snippet = body.length() > 200 ? body.substring(0, 200) + "..." : body;
                        log.info("Forced JDK HttpClient response snippet (status={}): {}", sc, snippet.replaceAll("\n", "\\n"));
                    }
                } catch (Exception ex) { log.debug("Failed to process forced raw response: {}", ex.getMessage()); }
                Map<String, Object> respMap = objectMapper.readValue(body, new com.fasterxml.jackson.core.type.TypeReference<Map<String,Object>>(){});
                String parsed = parseImageResponseMap(respMap);
                if (parsed != null) return parsed;
            } else {
                log.warn("Forced JDK HttpClient returned empty body status={}", sc);
            }
        } catch (Exception e) {
            log.warn("Forced image generation failed: {}", e.getMessage());
        }
        return null;
    }

    /**
     * Extract image data URL or external image URL from a parsed Gemini response map.
     */
    private String parseImageResponseMap(Map<String, Object> resp) {
        if (resp == null) return null;
        try {
            List<?> candidates = (List<?>) resp.get("candidates");
            if (candidates != null && !candidates.isEmpty()) {
                Object cand0 = candidates.get(0);
                if (cand0 instanceof Map) {
                    Map<?,?> candMap = (Map<?,?>) cand0;
                    Map<?,?> content = (Map<?,?>) candMap.get("content");
                    if (content != null) {
                        List<?> parts = (List<?>) content.get("parts");
                        if (parts != null && !parts.isEmpty()) {
                            // Prefer the originally expected path when there are multiple parts
                            java.util.List<Integer> indicesToTry = new java.util.ArrayList<>();
                            if (parts.size() > 1) indicesToTry.add(1);
                            // Always try part 0 as a sensible fallback (many responses put inline data there)
                            indicesToTry.add(0);
                            // Also try any other parts if present
                            for (int i = 0; i < parts.size(); i++) {
                                if (!indicesToTry.contains(i)) indicesToTry.add(i);
                            }

                            for (int idx : indicesToTry) {
                                try {
                                    Object partObj = parts.get(idx);
                                    if (!(partObj instanceof Map)) continue;
                                    Map<?,?> p = (Map<?,?>) partObj;
                                    Object inline = p.get("inlineData");
                                    if (inline == null) inline = p.get("inline_data");
                                    if (inline instanceof Map) {
                                        Object data = ((Map<?,?>) inline).get("data");
                                        if (data instanceof String) {
                                            String base64 = (String) data;
                                            Object mm = ((Map<?,?>) inline).get("mimeType");
                                            if (mm == null) mm = ((Map<?,?>) inline).get("mime_type");
                                            String mime = mm instanceof String ? (String) mm : "image/png";
                                            log.info("Parsed inline image at part[{}] mime={} size={}", idx, mime, base64.length());
                                            return "data:" + mime + ";base64," + base64;
                                        }
                                    }
                                    Object imageUrl = p.get("imageUrl");
                                    if (imageUrl instanceof String) {
                                        log.info("Found external image URL at part[{}]: {}", idx, imageUrl);
                                        return (String) imageUrl;
                                    }
                                } catch (IndexOutOfBoundsException ioob) {
                                    // Index out of bounds while trying part indices — log at debug and continue
                                    log.debug("IndexOutOfBounds while scanning parts: {}", ioob.getMessage());
                                }
                            }
                        }
                    }
                }
            }
        } catch (Exception ignored) {
            log.debug("Ignored exception in recursiveFindBase64AndMime: {}", ignored == null ? "null" : ignored.getMessage());
        }
        return null;
    }

    /**
     * Derive a reasonable image-model endpoint from the configured text-model URL.
     * If the URL contains a model identifier like 'models/<model>:generateContent',
     * replace the model with 'gemini-2.5-flash-image'. Otherwise return the original URL.
     */
    private String deriveImageEndpointFromTextApi(String textApiUrl) {
        if (textApiUrl == null) return null;
        try {
            // naive replacement: find '/models/' then the next ':' and replace the model name
            int modelsIdx = textApiUrl.indexOf("/models/");
            if (modelsIdx >= 0) {
                int start = modelsIdx + "/models/".length();
                int colon = textApiUrl.indexOf(':', start);
                if (colon > start) {
                    String before = textApiUrl.substring(0, start);
                    String after = textApiUrl.substring(colon);
                    String modelToUse = (geminiImageModel != null && !geminiImageModel.isBlank()) ? geminiImageModel : "gemini-2.5-flash-image";
                    return before + modelToUse + after;
                }
            }
        } catch (Exception e) {
            // Log and return the original URL when derivation fails — this method is best-effort
            log.debug("deriveImageEndpointFromTextApi failed for url {}: {}", textApiUrl, e == null ? "null" : e.getMessage(), e);
        }
        return textApiUrl;
    }

    /**
     * Helper that accepts various inline data shapes and returns a base64 string if possible.
     */
    private String tryExtractBase64FromInline(Object inline) {
        if (inline == null) return null;
        try {
            // If it's a Map with a 'data' field
            if (inline instanceof Map) {
                Map<?, ?> m = (Map<?, ?>) inline;
                Object data = m.get("data");
                if (data instanceof String) {
                    // assume already base64
                    return (String) data;
                } else if (data instanceof List) {
                    // list of numeric bytes
                    List<?> arr = (List<?>) data;
                    byte[] bytes = new byte[arr.size()];
                    for (int i = 0; i < arr.size(); i++) {
                        Object n = arr.get(i);
                        int val = 0;
                        if (n instanceof Number) val = ((Number) n).intValue();
                        else if (n instanceof String) val = Integer.parseInt((String) n);
                        bytes[i] = (byte) val;
                    }
                    return Base64.getEncoder().encodeToString(bytes);
                }
            }
        } catch (Exception ex) {
            // Log exception for diagnostics and return null (best-effort extraction)
            log.debug("tryExtractBase64FromInline failed: {}", ex == null ? "null" : ex.getMessage(), ex);
        }
        return null;
    }

    /**
     * Recursively scan an object tree (maps/lists) for a large base64-like string and optional mimeType.
     * Returns a map with keys 'mime' and 'base64' when found, otherwise null.
     */
    private Map<String, String> recursiveFindBase64AndMime(Object node) {
        if (node == null) return null;
        try {
            if (node instanceof Map) {
                Map<?, ?> m = (Map<?, ?>) node;
                // Common inline fields
                if (m.containsKey("data") && m.get("data") instanceof String) {
                    String s = ((String) m.get("data")).trim();
                    if (looksLikeBase64Image(s)) {
                        String mime = null;
                        Object mt = m.get("mimeType");
                        if (mt == null) mt = m.get("mime_type");
                        if (mt instanceof String) mime = (String) mt;
                        Map<String, String> out = new java.util.HashMap<>();
                        out.put("base64", s);
                        out.put("mime", mime == null ? "image/png" : mime);
                        return out;
                    }
                }
                // Check string fields that may *be* the base64 payload
                for (Map.Entry<?, ?> e : m.entrySet()) {
                    Object v = e.getValue();
                    if (v instanceof String) {
                        String s = ((String) v).trim();
                        if (looksLikeBase64Image(s)) {
                            Map<String, String> out = new java.util.HashMap<>();
                            out.put("base64", s);
                            out.put("mime", "image/png");
                            return out;
                        }
                    } else {
                        Map<String, String> found = recursiveFindBase64AndMime(v);
                        if (found != null) return found;
                    }
                }
            } else if (node instanceof List) {
                for (Object item : (List<?>) node) {
                    Map<String, String> found = recursiveFindBase64AndMime(item);
                    if (found != null) return found;
                }
            } else if (node instanceof String) {
                String s = ((String) node).trim();
                if (looksLikeBase64Image(s)) {
                    Map<String, String> out = new java.util.HashMap<>();
                    out.put("base64", s);
                    out.put("mime", "image/png");
                    return out;
                }
            }
        } catch (Exception ignored) {
            log.debug("recursiveFindBase64AndMime exception: {}", ignored == null ? "null" : ignored.getMessage(), ignored);
        }
        return null;
    }

    private boolean looksLikeBase64Image(String s) {
        if (s == null) return false;
        // Quick acceptance for known image signatures (PNG/JPEG) which can be short in fixtures
        if (s.startsWith("iVBOR") || s.startsWith("/9j/")) return true; // PNG or JPEG signatures in base64

        // Heuristic for longer payloads: must be reasonably long and only contain base64 chars
        if (s.length() < 200) return false;
        // only base64 chars + possible newlines/equals
        if (!s.matches("^[A-Za-z0-9+/=\\n\\r]+$")) return false;
        // fallback: if it's long and looks base64-ish, accept it
        return s.length() > 500;
    }

    /**
     * Sanitize a Gemini image response for debug logging.
     * This will redact large/binary fields (inline data) and truncate very long strings to avoid
     * logging secrets or heavy payloads.
     */
    @SuppressWarnings("unchecked")
    private Map<String, Object> sanitizeResponseForDebug(Map<String, Object> resp) {
        if (resp == null) return Map.of();
        Map<String, Object> out = new java.util.LinkedHashMap<>();
        // copy top-level keys except we will specially handle candidates
        for (Map.Entry<String, Object> e : resp.entrySet()) {
            String k = e.getKey();
            if ("candidates".equals(k) && e.getValue() instanceof List) {
                List<?> candidates = (List<?>) e.getValue();
                List<Object> sanCandidates = new java.util.ArrayList<>();
                for (Object cobj : candidates) {
                    if (!(cobj instanceof Map)) {
                        sanCandidates.add(cobj);
                        continue;
                    }
                    Map<String, Object> c = (Map<String, Object>) cobj;
                    Map<String, Object> sanC = new java.util.LinkedHashMap<>();
                    // copy non-content keys as-is (truncate strings)
                    for (Map.Entry<String, Object> ce : c.entrySet()) {
                        String ck = ce.getKey();
                        Object cv = ce.getValue();
                        if ("content".equals(ck) && cv instanceof Map) {
                            Map<String, Object> content = (Map<String, Object>) cv;
                            Map<String, Object> sanContent = new java.util.LinkedHashMap<>();
                            for (Map.Entry<String, Object> contEntry : content.entrySet()) {
                                String contK = contEntry.getKey();
                                Object contV = contEntry.getValue();
                                if ("parts".equals(contK) && contV instanceof List) {
                                    List<?> parts = (List<?>) contV;
                                    List<Object> sanParts = new java.util.ArrayList<>();
                                    for (Object partObj : parts) {
                                        if (!(partObj instanceof Map)) {
                                            sanParts.add(partObj);
                                            continue;
                                        }
                                        Map<String, Object> part = (Map<String, Object>) partObj;
                                        Map<String, Object> sanPart = new java.util.LinkedHashMap<>();
                                        for (Map.Entry<String, Object> p : part.entrySet()) {
                                            String pk = p.getKey();
                                            Object pv = p.getValue();
                                            // redact inline binary fields
                                            if ("inlineData".equals(pk) || "inline_data".equals(pk)) {
                                                if (pv instanceof Map) {
                                                    Object data = ((Map<?, ?>) pv).get("data");
                                                    if (data instanceof List) {
                                                        sanPart.put(pk, Map.of("data", String.format("[bytes:%d]", ((List<?>) data).size())));
                                                    } else if (data instanceof String) {
                                                        sanPart.put(pk, Map.of("data", String.format("[base64:%d]", ((String) data).length())));
                                                    } else {
                                                        sanPart.put(pk, "[REDACTED_BINARY]");
                                                    }
                                                } else if (pv instanceof List) {
                                                    sanPart.put(pk, String.format("[bytes:%d]", ((List<?>) pv).size()));
                                                } else if (pv instanceof String) {
                                                    sanPart.put(pk, String.format("[base64:%d]", ((String) pv).length()));
                                                } else {
                                                    sanPart.put(pk, "[REDACTED_BINARY]");
                                                }
                                            } else if ("data".equals(pk) && pv instanceof List) {
                                                sanPart.put(pk, String.format("[bytes:%d]", ((List<?>) pv).size()));
                                            } else if (pv instanceof String) {
                                                String s = (String) pv;
                                                sanPart.put(pk, s.length() > 200 ? s.substring(0, 200) + "..." : s);
                                            } else {
                                                sanPart.put(pk, pv);
                                            }
                                        }
                                        sanParts.add(sanPart);
                                    }
                                    sanContent.put(contK, sanParts);
                                } else if (contV instanceof String) {
                                    String s = (String) contV;
                                    sanContent.put(contK, s.length() > 200 ? s.substring(0, 200) + "..." : s);
                                } else {
                                    sanContent.put(contK, contV);
                                }
                            }
                            sanC.put(ck, sanContent);
                        } else if (cv instanceof String) {
                            String s = (String) cv;
                            sanC.put(ck, s.length() > 200 ? s.substring(0, 200) + "..." : s);
                        } else {
                            sanC.put(ck, cv);
                        }
                    }
                    sanCandidates.add(sanC);
                }
                out.put("candidates", sanCandidates);
            } else if (e.getValue() instanceof String) {
                String s = (String) e.getValue();
                out.put(k, s.length() > 200 ? s.substring(0, 200) + "..." : s);
            } else {
                out.put(k, e.getValue());
            }
        }
        return out;
    }

    private String buildPrompt(String prompt, List<String> pantryItems, com.recipe.ai.model.Units units) {
        String unitInstruction = "Use metric units (grams, liters, Celsius)";
        if (units != null && units == com.recipe.ai.model.Units.IMPERIAL) {
            unitInstruction = "Use imperial units (ounces, cups, Fahrenheit)";
        }
        if (pantryItems == null || pantryItems.isEmpty()) {
            // Keep unit instruction immediately before the user prompt for consistent ordering
            return String.format("%s. %s", unitInstruction, prompt);
        }
        String pantryString = String.join(", ", pantryItems);
        // Order: pantry constraints, unit instruction, then the user's prompt
        // Changed from "ONLY" to allow common pantry staples (salt, pepper, oil, etc.)
        return String.format("Prioritize using these available ingredients: [%s]. You may also include common pantry staples like salt, pepper, oil, butter, sugar, flour, and spices as needed. %s. %s", pantryString, unitInstruction, prompt);
    }
}
