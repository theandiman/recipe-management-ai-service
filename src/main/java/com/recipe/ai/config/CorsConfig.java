package com.recipe.ai.config;

import jakarta.annotation.PostConstruct;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * Explicit CORS configuration for local development and Firebase hosting.
 * This class enables CORS for the /api/** endpoints from known origins.
 * Updated for recipe-mgmt-dev Firebase project.
 */
@Configuration
public class CorsConfig implements WebMvcConfigurer {

    private static final Logger logger = LoggerFactory.getLogger(CorsConfig.class);

    @Value("${storage.service.url:}")
    private String storageServiceUrl;

    /**
     * Normalizes {@code storageServiceUrl} to a bare CORS origin (scheme + host [+ port])
     * at startup, so that values copied from a browser address bar (trailing slash, path, etc.)
     * still work correctly. Throws {@link IllegalStateException} on startup if the value is
     * present but cannot be parsed, so misconfiguration is caught early.
     */
    @PostConstruct
    void normalizeStorageServiceUrl() {
        if (storageServiceUrl == null || storageServiceUrl.isBlank()) {
            return;
        }

        String trimmed = storageServiceUrl.trim();
        // Strip any trailing slashes before parsing
        int end = trimmed.length();
        while (end > 0 && trimmed.charAt(end - 1) == '/') {
            end--;
        }
        trimmed = trimmed.substring(0, end);

        URI uri;
        try {
            uri = new URI(trimmed);
        } catch (URISyntaxException e) {
            throw new IllegalStateException(
                    "Invalid storage.service.url '" + storageServiceUrl + "': " + e.getMessage(), e);
        }

        if (uri.getScheme() == null || uri.getHost() == null) {
            throw new IllegalStateException(
                    "Invalid storage.service.url '" + storageServiceUrl
                    + "': must include a scheme and host (e.g. https://example.a.run.app)");
        }

        int port = uri.getPort();
        String origin = uri.getScheme() + "://" + uri.getHost() + (port != -1 ? ":" + port : "");

        if (!origin.equals(storageServiceUrl)) {
            logger.info("Normalized storage.service.url from '{}' to '{}'", storageServiceUrl, origin);
        }
        storageServiceUrl = origin;
    }

    @Override
    public void addCorsMappings(CorsRegistry registry) {
        List<String> origins = new ArrayList<>(List.of(
            "http://localhost:5173",
            "http://127.0.0.1:5173",
            "https://recipe-mgmt-dev.web.app",
            "https://recipe-mgmt-dev.firebaseapp.com",
            "https://recipe-ai-service-htubs7zkna-nw.a.run.app"
        ));
        if (storageServiceUrl != null && !storageServiceUrl.isBlank()) {
            origins.add(storageServiceUrl);
        }
        registry.addMapping("/api/**")
                .allowedOrigins(origins.toArray(new String[0]))
                .allowedMethods("GET", "POST", "PUT", "DELETE", "OPTIONS")
                .allowedHeaders("Authorization", "Content-Type", "Accept")
                .allowCredentials(true)
                .maxAge(3600);
    }
}
