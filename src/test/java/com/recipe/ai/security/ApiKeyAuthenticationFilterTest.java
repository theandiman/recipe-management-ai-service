package com.recipe.ai.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.context.SecurityContextHolder;

import java.io.IOException;

import static org.assertj.core.api.Assertions.assertThat;

public class ApiKeyAuthenticationFilterTest {

    @BeforeEach
    public void setup() {
        SecurityContextHolder.clearContext();
    }

    @AfterEach
    public void teardown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    public void validApiKey_setsAuthentication() throws ServletException, IOException {
        // Single key configured
        ApiKeyAuthenticationFilter filter = new ApiKeyAuthenticationFilter("key-abc123");
        MockHttpServletRequest req = new MockHttpServletRequest();
        MockHttpServletResponse resp = new MockHttpServletResponse();
        FilterChain chain = new MockFilterChain();

        req.addHeader("X-API-Key", "key-abc123");

        filter.doFilterInternal(req, resp, chain);

        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNotNull();
        assertThat(SecurityContextHolder.getContext().getAuthentication().getPrincipal()).isEqualTo("api-key-user");
    }

    @Test
    public void invalidApiKey_doesNotSetAuthentication() throws ServletException, IOException {
        ApiKeyAuthenticationFilter filter = new ApiKeyAuthenticationFilter("key-abc123");
        MockHttpServletRequest req = new MockHttpServletRequest();
        MockHttpServletResponse resp = new MockHttpServletResponse();
        FilterChain chain = new MockFilterChain();

        req.addHeader("X-API-Key", "wrong-key");

        filter.doFilterInternal(req, resp, chain);

        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
    }

    @Test
    public void noApiKey_doesNotSetAuthentication() throws ServletException, IOException {
        ApiKeyAuthenticationFilter filter = new ApiKeyAuthenticationFilter("key-abc123");
        MockHttpServletRequest req = new MockHttpServletRequest();
        MockHttpServletResponse resp = new MockHttpServletResponse();
        FilterChain chain = new MockFilterChain();

        filter.doFilterInternal(req, resp, chain);

        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
    }

    @Test
    public void constantTimeEquals_lengthMismatch_false() throws Exception {
        ApiKeyAuthenticationFilter filter = new ApiKeyAuthenticationFilter("key-abc123");
        java.lang.reflect.Method m = ApiKeyAuthenticationFilter.class.getDeclaredMethod("constantTimeEquals", String.class, String.class);
        m.setAccessible(true);
        Object res = m.invoke(filter, "abc", "abcd");
        assertThat(res).isInstanceOf(Boolean.class);
        assertThat((Boolean) res).isFalse();
    }

}
