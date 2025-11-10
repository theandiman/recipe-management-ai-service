package com.recipe.ai.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.lang.NonNull;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Arrays;
import java.util.Collections;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Filter that validates API keys from the X-API-Key header.
 * Supports multiple API keys configured via application properties.
 * This is an alternative authentication method to Firebase tokens.
 * Uses constant-time comparison to prevent timing attacks.
 * 
 * Disabled when auth.enabled=false.
 */
@Component
@ConditionalOnProperty(
    name = "auth.enabled",
    havingValue = "true",
    matchIfMissing = true
)
public class ApiKeyAuthenticationFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(ApiKeyAuthenticationFilter.class);
    private static final String API_KEY_HEADER = "X-API-Key";
    private static final String API_KEY_PRINCIPAL = "api-key-user";
    private static final String ROLE_API_KEY = "ROLE_API_KEY";

    private final Set<String> validApiKeys;

    public ApiKeyAuthenticationFilter(@Value("${api.keys:}") String apiKeys) {
        // Parse comma-separated API keys from configuration and make immutable
        this.validApiKeys = apiKeys.isEmpty() 
            ? Collections.emptySet()
            : Collections.unmodifiableSet(
                Arrays.stream(apiKeys.split(","))
                        .map(String::trim)
                        .filter(key -> !key.isEmpty())
                        .collect(Collectors.toSet())
            );
        
        if (this.validApiKeys.isEmpty()) {
            log.warn("No API keys configured. API key authentication will be disabled.");
        } else {
            log.info("API key authentication enabled with {} key(s)", this.validApiKeys.size());
        }
    }

    @Override
    protected void doFilterInternal(
            @NonNull HttpServletRequest request,
            @NonNull HttpServletResponse response,
            @NonNull FilterChain filterChain) throws ServletException, IOException {

        // Skip if already authenticated (Firebase auth took precedence)
        if (SecurityContextHolder.getContext().getAuthentication() != null) {
            filterChain.doFilter(request, response);
            return;
        }

        String apiKey = request.getHeader(API_KEY_HEADER);

        if (apiKey != null) {
            apiKey = apiKey.trim();
            if (!apiKey.isEmpty()) {
                if (isValidApiKey(apiKey)) {
                    log.debug("Valid API key authenticated");

                    // Create authentication object for API key
                    UsernamePasswordAuthenticationToken authentication =
                            new UsernamePasswordAuthenticationToken(
                                    API_KEY_PRINCIPAL,
                                    null,
                                    Collections.singletonList(new SimpleGrantedAuthority(ROLE_API_KEY))
                            );

                    // Set authentication in security context
                    SecurityContextHolder.getContext().setAuthentication(authentication);
                } else {
                    log.debug("Authentication failed");
                    // Don't set authentication - request will be rejected by security config
                }
            }
        }

        filterChain.doFilter(request, response);
    }

    /**
     * Validates the provided API key using constant-time comparison to prevent timing attacks.
     * Iterates through all valid keys and compares each one without short-circuiting.
     *
     * @param providedKey the API key to validate
     * @return true if the key is valid, false otherwise
     */
    private boolean isValidApiKey(String providedKey) {
        boolean isValid = false;
        for (String validKey : validApiKeys) {
            if (constantTimeEquals(providedKey, validKey)) {
                isValid = true;
            }
        }
        return isValid;
    }

    /**
     * Compares two strings in constant time to prevent timing attacks.
     * Always compares all characters regardless of early mismatches.
     *
     * @param a first string
     * @param b second string
     * @return true if strings are equal, false otherwise
     */
    private boolean constantTimeEquals(String a, String b) {
        if (a.length() != b.length()) {
            return false;
        }

        int result = 0;
        for (int i = 0; i < a.length(); i++) {
            result |= a.charAt(i) ^ b.charAt(i);
        }
        return result == 0;
    }
}
