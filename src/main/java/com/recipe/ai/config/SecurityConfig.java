package com.recipe.ai.config;

import com.recipe.ai.security.ApiKeyAuthenticationFilter;
import com.recipe.ai.security.FirebaseAuthenticationFilter;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

/**
 * Security configuration for the Recipe Generator API.
 * Supports two authentication methods:
 * 1. Firebase ID tokens (Authorization: Bearer <token>)
 * 2. API keys (X-API-Key: <key>)
 * 
 * Can be completely disabled via auth.enabled=false property.
 */
@Configuration
@EnableWebSecurity
@ConditionalOnProperty(
    name = "auth.enabled",
    havingValue = "true",
    matchIfMissing = true
)
public class SecurityConfig {

    private final FirebaseAuthenticationFilter firebaseAuthenticationFilter;
    private final ApiKeyAuthenticationFilter apiKeyAuthenticationFilter;

    public SecurityConfig(FirebaseAuthenticationFilter firebaseAuthenticationFilter,
                         ApiKeyAuthenticationFilter apiKeyAuthenticationFilter) {
        this.firebaseAuthenticationFilter = firebaseAuthenticationFilter;
        this.apiKeyAuthenticationFilter = apiKeyAuthenticationFilter;
    }

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
                // Disable CSRF for stateless API (we use bearer tokens)
                .csrf(csrf -> csrf.disable())
                
                // Stateless session management (no server-side sessions)
                .sessionManagement(session -> 
                        session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                
                // Configure authorization rules
                .authorizeHttpRequests(authz -> authz
                        // Allow health checks without authentication (for load balancers)
                        .requestMatchers("/actuator/health", "/actuator/health/**").permitAll()
                        
                        // Allow OPTIONS requests for CORS preflight
                        .requestMatchers(HttpMethod.OPTIONS, "/**").permitAll()
                        
                        // Require authentication for all API endpoints
                        .requestMatchers("/api/**").authenticated()
                        
                        // Deny all other requests
                        .anyRequest().denyAll()
                )
                
                // Add Firebase authentication filter before Spring Security's default filter
                .addFilterBefore(firebaseAuthenticationFilter, UsernamePasswordAuthenticationFilter.class)
                
                // Add API key filter after Firebase filter (Firebase takes precedence)
                .addFilterAfter(apiKeyAuthenticationFilter, FirebaseAuthenticationFilter.class);

        return http.build();
    }
}
