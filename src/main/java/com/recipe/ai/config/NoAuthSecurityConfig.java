package com.recipe.ai.config;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.web.SecurityFilterChain;

/**
 * Security configuration that disables all authentication.
 * Used for local development and testing environments.
 * Activated when auth.enabled=false.
 */
@Configuration
@EnableWebSecurity
@ConditionalOnProperty(
    name = "auth.enabled",
    havingValue = "false"
)
public class NoAuthSecurityConfig {

    @Bean
    public SecurityFilterChain noAuthSecurityFilterChain(HttpSecurity http) throws Exception {
        http
                // Disable CSRF for stateless API
                .csrf(csrf -> csrf.disable())
                
                // Allow all requests without authentication
                .authorizeHttpRequests(authz -> authz
                        .anyRequest().permitAll()
                );

        return http.build();
    }
}
