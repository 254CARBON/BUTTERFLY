package com.z254.butterfly.testing.base;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.z254.butterfly.testing.containers.ButterflyTestContainers;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.TestInstance;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;

/**
 * Base class for API controller integration tests in the BUTTERFLY ecosystem.
 * 
 * <p>Provides:</p>
 * <ul>
 *   <li>Pre-configured Testcontainers (PostgreSQL, Kafka, Redis)</li>
 *   <li>MockMvc for HTTP testing</li>
 *   <li>Jackson ObjectMapper for JSON serialization</li>
 *   <li>Convenience methods for common HTTP operations</li>
 *   <li>Test profile activation</li>
 * </ul>
 * 
 * <h2>Usage</h2>
 * <pre>{@code
 * @SpringBootTest
 * class MyControllerIntegrationTest extends AbstractApiIntegrationTest {
 *     
 *     @Test
 *     void shouldCreateResource() throws Exception {
 *         MyRequest request = new MyRequest("test");
 *         
 *         performPost("/api/v1/resources", request)
 *             .andExpect(status().isCreated())
 *             .andExpect(jsonPath("$.id").exists());
 *     }
 * }
 * }</pre>
 * 
 * <h2>Container Configuration</h2>
 * <p>By default, PostgreSQL, Kafka, and Redis containers are started.
 * Override {@link #configureContainerProperties(DynamicPropertyRegistry)} to customize.</p>
 * 
 * <h2>Security</h2>
 * <p>Security is disabled by default for easier testing. Override
 * {@link #configureSecurityProperties(DynamicPropertyRegistry)} to enable.</p>
 * 
 * @since 0.1.0
 */
@SpringBootTest(
    webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
    properties = {
        "spring.main.allow-bean-definition-overriding=true"
    }
)
@AutoConfigureMockMvc
@ActiveProfiles("test")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
public abstract class AbstractApiIntegrationTest {

    // Static initializer ensures containers are started before Spring context
    static {
        ButterflyTestContainers.startCoreContainers();
    }

    @Autowired
    protected MockMvc mockMvc;

    @Autowired
    protected ObjectMapper objectMapper;

    /**
     * Configure container properties for the test.
     * Override to customize container configuration.
     *
     * @param registry the dynamic property registry
     */
    @DynamicPropertySource
    static void registerContainerProperties(DynamicPropertyRegistry registry) {
        configureContainerProperties(registry);
        configureSecurityProperties(registry);
        configureTestDefaults(registry);
    }

    /**
     * Configure container connection properties.
     * Override in subclass to add or modify container properties.
     *
     * @param registry the dynamic property registry
     */
    protected static void configureContainerProperties(DynamicPropertyRegistry registry) {
        ButterflyTestContainers.configureCoreProperties(registry);
    }

    /**
     * Configure security properties for testing.
     * Override to enable security or customize authentication.
     *
     * @param registry the dynamic property registry
     */
    protected static void configureSecurityProperties(DynamicPropertyRegistry registry) {
        // Disable security by default for easier testing
        registry.add("spring.security.enabled", () -> "false");
        registry.add("butterfly.security.enabled", () -> "false");
    }

    /**
     * Configure common test default properties.
     *
     * @param registry the dynamic property registry
     */
    protected static void configureTestDefaults(DynamicPropertyRegistry registry) {
        // Disable unnecessary auto-configurations
        registry.add("spring.autoconfigure.exclude", () -> 
            "org.springframework.boot.autoconfigure.flyway.FlywayAutoConfiguration");
        
        // Enable bean overriding
        registry.add("spring.main.allow-bean-definition-overriding", () -> "true");
    }

    /**
     * Setup method called before all tests in the class.
     * Override to perform custom setup.
     */
    @BeforeAll
    void setUpTestClass() {
        // Override in subclass if needed
    }

    // ==========================================================================
    // HTTP Helper Methods
    // ==========================================================================

    /**
     * Perform a GET request.
     *
     * @param url the URL path
     * @return ResultActions for further assertions
     */
    protected ResultActions performGet(String url) throws Exception {
        return mockMvc.perform(get(url)
                .contentType(MediaType.APPLICATION_JSON)
                .accept(MediaType.APPLICATION_JSON));
    }

    /**
     * Perform a GET request with path variables.
     *
     * @param urlTemplate the URL template with placeholders
     * @param uriVars     the URI variable values
     * @return ResultActions for further assertions
     */
    protected ResultActions performGet(String urlTemplate, Object... uriVars) throws Exception {
        return mockMvc.perform(get(urlTemplate, uriVars)
                .contentType(MediaType.APPLICATION_JSON)
                .accept(MediaType.APPLICATION_JSON));
    }

    /**
     * Perform a POST request with a JSON body.
     *
     * @param url  the URL path
     * @param body the request body object (will be serialized to JSON)
     * @return ResultActions for further assertions
     */
    protected ResultActions performPost(String url, Object body) throws Exception {
        return mockMvc.perform(post(url)
                .contentType(MediaType.APPLICATION_JSON)
                .accept(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(body)));
    }

    /**
     * Perform a PUT request with a JSON body.
     *
     * @param url  the URL path
     * @param body the request body object (will be serialized to JSON)
     * @return ResultActions for further assertions
     */
    protected ResultActions performPut(String url, Object body) throws Exception {
        return mockMvc.perform(put(url)
                .contentType(MediaType.APPLICATION_JSON)
                .accept(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(body)));
    }

    /**
     * Perform a PATCH request with a JSON body.
     *
     * @param url  the URL path
     * @param body the request body object (will be serialized to JSON)
     * @return ResultActions for further assertions
     */
    protected ResultActions performPatch(String url, Object body) throws Exception {
        return mockMvc.perform(patch(url)
                .contentType(MediaType.APPLICATION_JSON)
                .accept(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(body)));
    }

    /**
     * Perform a DELETE request.
     *
     * @param url the URL path
     * @return ResultActions for further assertions
     */
    protected ResultActions performDelete(String url) throws Exception {
        return mockMvc.perform(delete(url)
                .contentType(MediaType.APPLICATION_JSON)
                .accept(MediaType.APPLICATION_JSON));
    }

    /**
     * Perform a DELETE request with path variables.
     *
     * @param urlTemplate the URL template with placeholders
     * @param uriVars     the URI variable values
     * @return ResultActions for further assertions
     */
    protected ResultActions performDelete(String urlTemplate, Object... uriVars) throws Exception {
        return mockMvc.perform(delete(urlTemplate, uriVars)
                .contentType(MediaType.APPLICATION_JSON)
                .accept(MediaType.APPLICATION_JSON));
    }

    /**
     * Perform a custom request with additional configuration.
     *
     * @param requestBuilder the request builder
     * @return ResultActions for further assertions
     */
    protected ResultActions perform(MockHttpServletRequestBuilder requestBuilder) throws Exception {
        return mockMvc.perform(requestBuilder);
    }

    // ==========================================================================
    // Response Parsing Helpers
    // ==========================================================================

    /**
     * Parse the response body as the specified type.
     *
     * @param result      the MvcResult from a performed request
     * @param targetClass the class to deserialize to
     * @param <T>         the target type
     * @return the deserialized response
     */
    protected <T> T parseResponse(MvcResult result, Class<T> targetClass) throws Exception {
        String content = result.getResponse().getContentAsString();
        return objectMapper.readValue(content, targetClass);
    }

    /**
     * Parse the response body as the specified type using a type reference.
     *
     * @param result        the MvcResult from a performed request
     * @param typeReference the type reference for complex types
     * @param <T>           the target type
     * @return the deserialized response
     */
    protected <T> T parseResponse(MvcResult result, 
            com.fasterxml.jackson.core.type.TypeReference<T> typeReference) throws Exception {
        String content = result.getResponse().getContentAsString();
        return objectMapper.readValue(content, typeReference);
    }

    /**
     * Convert an object to JSON string.
     *
     * @param object the object to serialize
     * @return JSON string representation
     */
    protected String toJson(Object object) throws Exception {
        return objectMapper.writeValueAsString(object);
    }

    /**
     * Pretty print JSON for debugging.
     *
     * @param object the object to serialize
     * @return formatted JSON string
     */
    protected String toPrettyJson(Object object) throws Exception {
        return objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(object);
    }
}

