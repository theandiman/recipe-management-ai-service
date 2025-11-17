package com.recipe.ai.security;

// FirebaseToken import removed; will use VerifiedUser instead
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.lang.NonNull;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Collections;

/**
 * Filter that extracts and verifies Firebase ID tokens from the Authorization header.
 * On successful verification, sets the security context with the authenticated user.
 * Disabled when auth.enabled=false.
 */
@Component
@ConditionalOnProperty(
    name = "auth.enabled",
    havingValue = "true",
    matchIfMissing = true
)
public class FirebaseAuthenticationFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(FirebaseAuthenticationFilter.class);
    private static final String BEARER_PREFIX = "Bearer ";

    private final FirebaseVerifier firebaseVerifier;

    public FirebaseAuthenticationFilter(FirebaseVerifier firebaseVerifier) {
        this.firebaseVerifier = firebaseVerifier;
    }

    @Override
    protected void doFilterInternal(
            @NonNull HttpServletRequest request,
            @NonNull HttpServletResponse response,
            @NonNull FilterChain filterChain) throws ServletException, IOException {

        String authHeader = request.getHeader("Authorization");
        log.info("FirebaseAuthFilter invoked for: {} {}, has auth header: {}", 
                 request.getMethod(), request.getRequestURI(), authHeader != null);

        if (authHeader != null && authHeader.startsWith(BEARER_PREFIX)) {
            String idToken = authHeader.substring(BEARER_PREFIX.length());
            log.info("Attempting to verify Firebase ID token (length: {})", idToken.length());

            try {
                // Verify the Firebase ID token
                var verifiedUser = firebaseVerifier.verifyIdToken(idToken);
                if (verifiedUser == null) {
                    log.warn("Firebase verifier returned null for token - not authenticated");
                } else {
                    String uid = verifiedUser.uid();
                    String email = verifiedUser.email();

                    log.info("Successfully authenticated user: uid={}, email={}", uid, email);

                    // Create authentication object with user details
                    UsernamePasswordAuthenticationToken authentication =
                            new UsernamePasswordAuthenticationToken(
                                    uid,
                                    null,
                                    Collections.singletonList(new SimpleGrantedAuthority("ROLE_USER"))
                            );

                    // Set authentication in security context
                    SecurityContextHolder.getContext().setAuthentication(authentication);
                }

        // (Handled above after verifying user)

            } catch (Exception e) {
                log.error("Failed to verify Firebase token for {} {}: {}", 
                         request.getMethod(), request.getRequestURI(), e.getMessage(), e);
                // Don't set authentication - request will be rejected by security config
            }
        } else {
            log.warn("No valid Authorization header for: {} {}", request.getMethod(), request.getRequestURI());
        }

        filterChain.doFilter(request, response);
    }

    /**
     * Wrapper for FirebaseAuth.getInstance() to allow overriding in tests.
     */
    // getFirebaseAuth removed — replaced by FirebaseVerifier injection
}
