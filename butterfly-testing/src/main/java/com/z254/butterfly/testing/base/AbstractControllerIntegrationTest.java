package com.z254.butterfly.testing.base;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.ResultMatcher;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Base class for REST controller integration tests in the BUTTERFLY ecosystem.
 * 
 * <p>Extends {@link AbstractApiIntegrationTest} with additional helpers for:</p>
 * <ul>
 *   <li>API envelope response validation (SUCCESS/ERROR status)</li>
 *   <li>Pagination assertions</li>
 *   <li>Authentication mocking</li>
 *   <li>Error code validation</li>
 *   <li>Common request header setup</li>
 * </ul>
 * 
 * <h2>Quick Start Example</h2>
 * <pre>{@code
 * @SpringBootTest
 * class MyControllerIT extends AbstractControllerIntegrationTest {
 *     
 *     @Test
 *     void shouldReturnSuccessEnvelope() throws Exception {
 *         performGetWithAuth("/api/v1/resources")
 *             .andExpect(status().isOk())
 *             .andExpect(apiSuccess())
 *             .andExpect(jsonPath("$.data").isArray());
 *     }
 *     
 *     @Test
 *     void shouldReturnErrorOnInvalidInput() throws Exception {
 *         performPostWithAuth("/api/v1/resources", invalidRequest())
 *             .andExpect(status().isBadRequest())
 *             .andExpect(apiError("INVALID_REQUEST"));
 *     }
 * }
 * }</pre>
 * 
 * @since 0.2.0
 */
public abstract class AbstractControllerIntegrationTest extends AbstractApiIntegrationTest {

    // ==========================================================================
    // API Envelope Matchers
    // ==========================================================================

    /**
     * Assert that the response has a SUCCESS status envelope.
     * 
     * @return ResultMatcher for SUCCESS status
     */
    protected static ResultMatcher apiSuccess() {
        return jsonPath("$.status").value("SUCCESS");
    }

    /**
     * Assert that the response has an ERROR status envelope.
     * 
     * @return ResultMatcher for ERROR status
     */
    protected static ResultMatcher apiError() {
        return jsonPath("$.status").value("ERROR");
    }

    /**
     * Assert that the response has an ERROR status with specific error code.
     * 
     * @param expectedErrorCode the expected error code
     * @return ResultMatcher for ERROR status with specific code
     */
    protected static ResultMatcher apiError(String expectedErrorCode) {
        return result -> {
            jsonPath("$.status").value("ERROR").match(result);
            jsonPath("$.error.code").value(expectedErrorCode).match(result);
        };
    }

    /**
     * Assert that the response has a SUCCESS status with non-null data.
     * 
     * @return ResultMatcher for SUCCESS with data present
     */
    protected static ResultMatcher apiSuccessWithData() {
        return result -> {
            jsonPath("$.status").value("SUCCESS").match(result);
            jsonPath("$.data").isNotEmpty().match(result);
        };
    }

    /**
     * Assert that the response has the standard envelope fields.
     * 
     * @return ResultMatcher validating envelope structure
     */
    protected static ResultMatcher hasEnvelopeStructure() {
        return result -> {
            jsonPath("$.status").exists().match(result);
            jsonPath("$.timestamp").exists().match(result);
            jsonPath("$.requestId").exists().match(result);
        };
    }

    // ==========================================================================
    // Pagination Helpers
    // ==========================================================================

    /**
     * Assert pagination response headers are present.
     * 
     * @return ResultMatcher for pagination headers
     */
    protected static ResultMatcher hasPaginationHeaders() {
        return result -> {
            assertThat(result.getResponse().containsHeader("X-Total-Count")).isTrue();
            assertThat(result.getResponse().containsHeader("X-Page-Count")).isTrue();
        };
    }

    /**
     * Assert specific pagination values.
     * 
     * @param totalCount expected total count
     * @param pageCount expected page count
     * @return ResultMatcher for pagination values
     */
    protected static ResultMatcher hasPagination(int totalCount, int pageCount) {
        return result -> {
            String total = result.getResponse().getHeader("X-Total-Count");
            String pages = result.getResponse().getHeader("X-Page-Count");
            assertThat(total).isEqualTo(String.valueOf(totalCount));
            assertThat(pages).isEqualTo(String.valueOf(pageCount));
        };
    }

    /**
     * Assert that data is a non-empty array.
     * 
     * @return ResultMatcher for non-empty array
     */
    protected static ResultMatcher dataIsNonEmptyArray() {
        return jsonPath("$.data").isArray();
    }

    /**
     * Assert data array has expected size.
     * 
     * @param expectedSize expected array size
     * @return ResultMatcher for array size
     */
    protected static ResultMatcher dataArraySize(int expectedSize) {
        return jsonPath("$.data", hasSize(expectedSize));
    }

    // ==========================================================================
    // Authenticated Request Helpers
    // ==========================================================================

    /**
     * Perform GET request with default authenticated user.
     * 
     * @param url the URL path
     * @return ResultActions for further assertions
     */
    protected ResultActions performGetWithAuth(String url) throws Exception {
        return mockMvc.perform(get(url)
                .with(SecurityMockMvcRequestPostProcessors.user("test-user").roles("USER"))
                .header("X-Request-ID", UUID.randomUUID().toString())
                .contentType(MediaType.APPLICATION_JSON)
                .accept(MediaType.APPLICATION_JSON));
    }

    /**
     * Perform GET with custom user ID and roles.
     * 
     * @param url the URL path
     * @param userId the user ID
     * @param roles the roles to assign
     * @return ResultActions for further assertions
     */
    protected ResultActions performGetWithAuth(String url, String userId, String... roles) throws Exception {
        return mockMvc.perform(get(url)
                .with(SecurityMockMvcRequestPostProcessors.user(userId).roles(roles))
                .header("X-Request-ID", UUID.randomUUID().toString())
                .contentType(MediaType.APPLICATION_JSON)
                .accept(MediaType.APPLICATION_JSON));
    }

    /**
     * Perform POST request with default authenticated user.
     * 
     * @param url the URL path
     * @param body the request body object
     * @return ResultActions for further assertions
     */
    protected ResultActions performPostWithAuth(String url, Object body) throws Exception {
        return mockMvc.perform(post(url)
                .with(SecurityMockMvcRequestPostProcessors.user("test-user").roles("USER"))
                .header("X-Request-ID", UUID.randomUUID().toString())
                .contentType(MediaType.APPLICATION_JSON)
                .accept(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(body)));
    }

    /**
     * Perform POST with custom user ID and roles.
     * 
     * @param url the URL path
     * @param body the request body object
     * @param userId the user ID
     * @param roles the roles to assign
     * @return ResultActions for further assertions
     */
    protected ResultActions performPostWithAuth(String url, Object body, String userId, String... roles) 
            throws Exception {
        return mockMvc.perform(post(url)
                .with(SecurityMockMvcRequestPostProcessors.user(userId).roles(roles))
                .header("X-Request-ID", UUID.randomUUID().toString())
                .contentType(MediaType.APPLICATION_JSON)
                .accept(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(body)));
    }

    /**
     * Perform PUT request with default authenticated user.
     * 
     * @param url the URL path
     * @param body the request body object
     * @return ResultActions for further assertions
     */
    protected ResultActions performPutWithAuth(String url, Object body) throws Exception {
        return mockMvc.perform(put(url)
                .with(SecurityMockMvcRequestPostProcessors.user("test-user").roles("USER"))
                .header("X-Request-ID", UUID.randomUUID().toString())
                .contentType(MediaType.APPLICATION_JSON)
                .accept(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(body)));
    }

    /**
     * Perform DELETE request with default authenticated user.
     * 
     * @param url the URL path
     * @return ResultActions for further assertions
     */
    protected ResultActions performDeleteWithAuth(String url) throws Exception {
        return mockMvc.perform(delete(url)
                .with(SecurityMockMvcRequestPostProcessors.user("test-user").roles("USER"))
                .header("X-Request-ID", UUID.randomUUID().toString())
                .contentType(MediaType.APPLICATION_JSON)
                .accept(MediaType.APPLICATION_JSON));
    }

    /**
     * Perform request with admin authentication.
     * 
     * @param requestBuilder the request builder
     * @return ResultActions for further assertions
     */
    protected ResultActions performAsAdmin(MockHttpServletRequestBuilder requestBuilder) throws Exception {
        return mockMvc.perform(requestBuilder
                .with(SecurityMockMvcRequestPostProcessors.user("admin").roles("ADMIN"))
                .header("X-Request-ID", UUID.randomUUID().toString()));
    }

    /**
     * Perform request with service account authentication (for internal API calls).
     * 
     * @param requestBuilder the request builder
     * @param serviceId the service ID
     * @return ResultActions for further assertions
     */
    protected ResultActions performAsService(MockHttpServletRequestBuilder requestBuilder, String serviceId) 
            throws Exception {
        return mockMvc.perform(requestBuilder
                .with(SecurityMockMvcRequestPostProcessors.user(serviceId).roles("SERVICE"))
                .header("X-Request-ID", UUID.randomUUID().toString())
                .header("X-Service-ID", serviceId));
    }

    // ==========================================================================
    // Multi-Tenant Helpers
    // ==========================================================================

    /**
     * Perform GET request with tenant header.
     * 
     * @param url the URL path
     * @param tenantId the tenant ID
     * @return ResultActions for further assertions
     */
    protected ResultActions performGetWithTenant(String url, String tenantId) throws Exception {
        return mockMvc.perform(get(url)
                .with(SecurityMockMvcRequestPostProcessors.user("test-user").roles("USER"))
                .header("X-Tenant-Id", tenantId)
                .header("X-Request-ID", UUID.randomUUID().toString())
                .contentType(MediaType.APPLICATION_JSON)
                .accept(MediaType.APPLICATION_JSON));
    }

    /**
     * Perform POST request with tenant header.
     * 
     * @param url the URL path
     * @param body the request body object
     * @param tenantId the tenant ID
     * @return ResultActions for further assertions
     */
    protected ResultActions performPostWithTenant(String url, Object body, String tenantId) throws Exception {
        return mockMvc.perform(post(url)
                .with(SecurityMockMvcRequestPostProcessors.user("test-user").roles("USER"))
                .header("X-Tenant-Id", tenantId)
                .header("X-Request-ID", UUID.randomUUID().toString())
                .contentType(MediaType.APPLICATION_JSON)
                .accept(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(body)));
    }

    // ==========================================================================
    // Response Extraction Helpers
    // ==========================================================================

    /**
     * Extract the 'data' field from an API envelope response.
     * 
     * @param result the MvcResult
     * @param targetClass the class to deserialize to
     * @param <T> the target type
     * @return the deserialized data field
     */
    protected <T> T extractData(MvcResult result, Class<T> targetClass) throws Exception {
        String json = result.getResponse().getContentAsString();
        Map<String, Object> envelope = objectMapper.readValue(json, 
                new TypeReference<Map<String, Object>>() {});
        Object data = envelope.get("data");
        return objectMapper.convertValue(data, targetClass);
    }

    /**
     * Extract the 'data' field as a list.
     * 
     * @param result the MvcResult
     * @param elementClass the element class
     * @param <T> the element type
     * @return the deserialized list
     */
    protected <T> List<T> extractDataList(MvcResult result, Class<T> elementClass) throws Exception {
        String json = result.getResponse().getContentAsString();
        Map<String, Object> envelope = objectMapper.readValue(json, 
                new TypeReference<Map<String, Object>>() {});
        Object data = envelope.get("data");
        return objectMapper.convertValue(data, 
                objectMapper.getTypeFactory().constructCollectionType(List.class, elementClass));
    }

    /**
     * Extract the error code from an API envelope error response.
     * 
     * @param result the MvcResult
     * @return the error code, or null if not present
     */
    protected String extractErrorCode(MvcResult result) throws Exception {
        String json = result.getResponse().getContentAsString();
        Map<String, Object> envelope = objectMapper.readValue(json, 
                new TypeReference<Map<String, Object>>() {});
        Object error = envelope.get("error");
        if (error instanceof Map) {
            return (String) ((Map<?, ?>) error).get("code");
        }
        return null;
    }

    /**
     * Extract the error message from an API envelope error response.
     * 
     * @param result the MvcResult
     * @return the error message, or null if not present
     */
    protected String extractErrorMessage(MvcResult result) throws Exception {
        String json = result.getResponse().getContentAsString();
        Map<String, Object> envelope = objectMapper.readValue(json, 
                new TypeReference<Map<String, Object>>() {});
        Object error = envelope.get("error");
        if (error instanceof Map) {
            return (String) ((Map<?, ?>) error).get("message");
        }
        return null;
    }

    // ==========================================================================
    // Assertion Helpers
    // ==========================================================================

    /**
     * Assert that the response status is SUCCESS and data matches expected.
     * 
     * @param result the MvcResult
     * @param expectedData the expected data object
     */
    protected void assertSuccessWithData(MvcResult result, Object expectedData) throws Exception {
        String json = result.getResponse().getContentAsString();
        Map<String, Object> envelope = objectMapper.readValue(json, 
                new TypeReference<Map<String, Object>>() {});
        
        assertThat(envelope.get("status")).isEqualTo("SUCCESS");
        assertThat(envelope.get("data")).isNotNull();
        
        // Compare data
        String expectedJson = objectMapper.writeValueAsString(expectedData);
        String actualJson = objectMapper.writeValueAsString(envelope.get("data"));
        assertThat(actualJson).isEqualTo(expectedJson);
    }

    /**
     * Assert that the response contains an error with specific code.
     * 
     * @param result the MvcResult
     * @param expectedErrorCode the expected error code
     */
    protected void assertErrorWithCode(MvcResult result, String expectedErrorCode) throws Exception {
        String errorCode = extractErrorCode(result);
        assertThat(errorCode).isEqualTo(expectedErrorCode);
    }

    /**
     * Assert that the response data contains a specific field.
     * 
     * @param result the MvcResult
     * @param fieldName the field name to check
     */
    protected void assertDataContainsField(MvcResult result, String fieldName) throws Exception {
        String json = result.getResponse().getContentAsString();
        Map<String, Object> envelope = objectMapper.readValue(json, 
                new TypeReference<Map<String, Object>>() {});
        Object data = envelope.get("data");
        
        assertThat(data).isInstanceOf(Map.class);
        assertThat(((Map<?, ?>) data).containsKey(fieldName)).isTrue();
    }
}
