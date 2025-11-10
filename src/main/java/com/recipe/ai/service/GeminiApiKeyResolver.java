package com.recipe.ai.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.charset.StandardCharsets;
import java.util.List;

/**
 * Resolves the Gemini API key from multiple sources in priority order:
 * 1. System property GEMINI_API_KEY
 * 2. Environment variable GEMINI_API_KEY
 * 3. .env file in project root
 * 4. Configured property value (gemini.api.key)
 */
@Component
public class GeminiApiKeyResolver {

    private static final Logger log = LoggerFactory.getLogger(GeminiApiKeyResolver.class);

    @Value("${gemini.api.key:YOUR_SECURE_API_KEY_HERE}")
    private String configuredApiKey;

    /**
     * Resolves the effective API key from all available sources.
     * @return The resolved API key, or the configured property value if no other source is found
     */
    public String resolveEffectiveApiKey() {
        String apiKeyFromSysProp = System.getProperty("GEMINI_API_KEY");
        String apiKeyFromEnv = System.getenv("GEMINI_API_KEY");
        
        if (apiKeyFromSysProp != null && !apiKeyFromSysProp.isBlank()) {
            return apiKeyFromSysProp;
        } else if (apiKeyFromEnv != null && !apiKeyFromEnv.isBlank()) {
            return apiKeyFromEnv;
        } else {
            // If not set via system property or environment, try a local .env file (common in dev setups)
            String envFileKey = readApiKeyFromEnvFile();
            if (envFileKey != null) {
                return envFileKey;
            }
            return configuredApiKey;
        }
    }

    /**
     * Reads the GEMINI_API_KEY from a .env file in the project root.
     * @return The API key if found, null otherwise
     */
    private String readApiKeyFromEnvFile() {
        try {
            Path envPath = Paths.get(".env");
            if (Files.exists(envPath)) {
                List<String> lines = Files.readAllLines(envPath, StandardCharsets.UTF_8);
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
            log.debug("Failed to read .env for GEMINI_API_KEY: {}", e.getMessage(), e);
        }
        return null;
    }

    /**
     * Checks if the effective API key is valid (present and not a placeholder).
     * @return true if a valid API key is available, false otherwise
     */
    public boolean hasValidApiKey() {
        String effectiveKey = resolveEffectiveApiKey();
        return effectiveKey != null 
            && !effectiveKey.isBlank() 
            && !effectiveKey.contains("YOUR_SECURE_API_KEY_HERE");
    }
}
