package com.recipe.ai;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Generates an OpenAPI spec for documentation purposes.
 * Disabled because springdoc-openapi is not yet fully compatible with Spring Boot 4.
 */
@Disabled("springdoc-openapi incompatible with Spring Boot 4 — WebMvcProperties moved")
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@TestPropertySource(properties = "auth.enabled=false")
class OpenApiGeneratorTest {

    @Autowired
    private WebApplicationContext webApplicationContext;

    @Test
    void generateOpenApiJson() throws Exception {
        MockMvc mockMvc = MockMvcBuilders.webAppContextSetup(webApplicationContext).build();
        String openApiJson = mockMvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();

        Path outputPath = Paths.get("target/openapi.json");
        Files.createDirectories(outputPath.getParent());
        Files.write(outputPath, openApiJson.getBytes());
    }
}
