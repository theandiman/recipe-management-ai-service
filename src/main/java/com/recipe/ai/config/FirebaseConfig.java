package com.recipe.ai.config;

import com.google.auth.oauth2.GoogleCredentials;
import com.google.firebase.FirebaseApp;
import com.google.firebase.FirebaseOptions;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.io.IOException;

/**
 * Configuration for Firebase Admin SDK initialization.
 * Automatically uses Application Default Credentials (ADC) when running on GCP.
 * Disabled when auth.enabled=false.
 */
@Configuration
public class FirebaseConfig {

    private static final Logger log = LoggerFactory.getLogger(FirebaseConfig.class);

    @Value("${firebase.project.id:recipe-gen-dev}")
    private String firebaseProjectId;

    @Bean
    @ConditionalOnProperty(
        name = "auth.enabled",
        havingValue = "true",
        matchIfMissing = true
    )
    public FirebaseApp initializeFirebaseApp() throws IOException {
        if (FirebaseApp.getApps().isEmpty()) {
            log.info("Initializing Firebase Admin SDK with Application Default Credentials for project: {}", firebaseProjectId);
            
            try {
                FirebaseOptions options = FirebaseOptions.builder()
                        .setCredentials(GoogleCredentials.getApplicationDefault())
                        .setProjectId(firebaseProjectId)
                        .build();

                FirebaseApp app = FirebaseApp.initializeApp(options);
                log.info("Firebase Admin SDK initialized successfully for project: {}", firebaseProjectId);
                return app;
            } catch (IOException e) {
                log.error("Failed to initialize Firebase Admin SDK: {}. Firebase authentication will be disabled.", e.getMessage());
                throw e;
            }
        } else {
            log.info("Firebase Admin SDK already initialized");
            return FirebaseApp.getInstance();
        }
    }
}
