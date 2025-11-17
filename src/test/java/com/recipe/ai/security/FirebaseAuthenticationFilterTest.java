package com.recipe.ai.security;

// N/A: verified user model is used in tests instead of SDK types
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.context.SecurityContextHolder;

import static org.assertj.core.api.Assertions.assertThat;

public class FirebaseAuthenticationFilterTest {

    @AfterEach
    public void cleanup() {
        SecurityContextHolder.clearContext();
    }

    @Test
    public void validFirebaseToken_setsAuthentication() throws Exception {
    FirebaseVerifier.VerifiedUser mockUser = new FirebaseVerifier.VerifiedUser("uid-123", "user@example.com");

        FirebaseVerifier verifier = new FirebaseVerifier() {
            @Override
            public FirebaseVerifier.VerifiedUser verifyIdToken(String idToken) {
                return mockUser;
            }
        };

        FirebaseAuthenticationFilter filter = new FirebaseAuthenticationFilter(verifier);

        MockHttpServletRequest req = new MockHttpServletRequest();
        MockHttpServletResponse resp = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();
        req.addHeader("Authorization", "Bearer valid-token");

        filter.doFilterInternal(req, resp, chain);

        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNotNull();
        assertThat(SecurityContextHolder.getContext().getAuthentication().getPrincipal()).isEqualTo("uid-123");
    }

    @Test
    public void invalidFirebaseToken_doesNotSetAuthentication() throws Exception {
        FirebaseVerifier verifier = new FirebaseVerifier() {
            @Override
            public FirebaseVerifier.VerifiedUser verifyIdToken(String idToken) {
                // Simulate invalid token by returning null
                return null;
            }
        };

        FirebaseAuthenticationFilter filter = new FirebaseAuthenticationFilter(verifier);

        MockHttpServletRequest req = new MockHttpServletRequest();
        MockHttpServletResponse resp = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();
        req.addHeader("Authorization", "Bearer invalid-token");

        filter.doFilterInternal(req, resp, chain);

        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
    }
}
