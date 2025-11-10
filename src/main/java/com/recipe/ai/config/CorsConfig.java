package com.recipe.ai.config;

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

    @Override
    public void addCorsMappings(CorsRegistry registry) {
        registry.addMapping("/api/**")
                .allowedOrigins(
                    "http://localhost:5173", 
                    "http://127.0.0.1:5173",
                    "https://recipe-mgmt-dev.web.app",
                    "https://recipe-mgmt-dev.firebaseapp.com"
                )
                .allowedMethods("GET", "POST", "PUT", "DELETE", "OPTIONS")
                .allowedHeaders("Authorization", "Content-Type", "Accept")
                .allowCredentials(true)
                .maxAge(3600);
    }
}
