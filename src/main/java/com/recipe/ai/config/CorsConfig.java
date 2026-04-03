package com.recipe.ai.config;

import java.util.ArrayList;
import java.util.List;
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

    @Value("${storage.service.url:}")
    private String storageServiceUrl;

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
