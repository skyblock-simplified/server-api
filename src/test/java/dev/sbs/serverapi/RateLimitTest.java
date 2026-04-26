package dev.sbs.serverapi;

import dev.sbs.serverapi.ratelimit.RateLimitFilter;
import dev.sbs.serverapi.security.ApiKey;
import dev.sbs.serverapi.security.ApiKeyRole;
import dev.sbs.serverapi.security.ApiKeyStore;
import dev.sbs.serverapi.security.InMemoryApiKeyStore;
import dev.simplified.collection.Concurrent;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Integration tests for {@link RateLimitFilter} with a Bucket4j-backed bucket per
 * {@link ApiKey}.
 *
 * <p>Tests assume {@code api.key.authentication.enabled=true} (the default in
 * {@code src/test/resources/application.properties}). When the property is overridden
 * to {@code false} - for example, by a developer running the suite without an
 * {@link ApiKeyStore} configured - every test in this class self-skips via
 * {@link Assumptions#assumeTrue}, so the suite still passes without rate-limit assertions.</p>
 */
@SpringBootTest(classes = TestServer.class)
@AutoConfigureMockMvc
@Import(RateLimitTest.RateLimitTestStoreConfig.class)
class RateLimitTest {

    private static final int CAPACITY = 3;
    private static final long WINDOW_SECONDS = 60;

    /**
     * Test fixture exposing three independent keys at low rate-limit capacity so
     * exhaustion is reachable in a few requests.
     */
    @TestConfiguration
    static class RateLimitTestStoreConfig {

        @Bean
        public @NotNull ApiKeyStore rateLimitTestApiKeyStore() {
            return new InMemoryApiKeyStore()
                .put(new ApiKey("rate-key-A",
                    Concurrent.newSet(ApiKeyRole.USER), CAPACITY, WINDOW_SECONDS))
                .put(new ApiKey("rate-key-B",
                    Concurrent.newSet(ApiKeyRole.USER), CAPACITY, WINDOW_SECONDS))
                .put(new ApiKey("rate-key-unlimited",
                    Concurrent.newSet(ApiKeyRole.USER), Integer.MAX_VALUE, WINDOW_SECONDS));
        }

    }

    @Value("${api.key.authentication.enabled:false}")
    private boolean apiKeyAuthEnabled;

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectProvider<RateLimitFilter> rateLimitFilterProvider;

    /**
     * Skip every test in this class when API key authentication is disabled - the
     * {@link RateLimitFilter} bean is conditional on the same property, so without it
     * there is nothing to assert against.
     */
    @BeforeEach
    void requireApiKeyAuthEnabled() {
        Assumptions.assumeTrue(this.apiKeyAuthEnabled,
            "api.key.authentication.enabled is false - skipping rate-limit tests");

        // Reset state so each test starts with a fresh bucket per key.
        RateLimitFilter filter = this.rateLimitFilterProvider.getIfAvailable();
        if (filter != null) {
            filter.clearBucket("rate-key-A");
            filter.clearBucket("rate-key-B");
            filter.clearBucket("rate-key-unlimited");
        }
    }

    @Test
    void belowCapacity_allRequestsSucceed() throws Exception {
        for (int i = 0; i < CAPACITY; i++) {
            mockMvc.perform(get("/api/basic").header("X-API-Key", "rate-key-A"))
                .andExpect(status().isOk());
        }
    }

    @Test
    void overCapacity_throttledWith429() throws Exception {
        for (int i = 0; i < CAPACITY; i++) {
            mockMvc.perform(get("/api/basic").header("X-API-Key", "rate-key-A"))
                .andExpect(status().isOk());
        }

        mockMvc.perform(get("/api/basic").header("X-API-Key", "rate-key-A"))
            .andExpect(status().isTooManyRequests());
    }

    @Test
    void separateKeys_haveIndependentBuckets() throws Exception {
        for (int i = 0; i < CAPACITY; i++) {
            mockMvc.perform(get("/api/basic").header("X-API-Key", "rate-key-A"))
                .andExpect(status().isOk());
        }

        mockMvc.perform(get("/api/basic").header("X-API-Key", "rate-key-A"))
            .andExpect(status().isTooManyRequests());

        mockMvc.perform(get("/api/basic").header("X-API-Key", "rate-key-B"))
            .andExpect(status().isOk());
    }

    @Test
    void unauthenticatedRequest_notRateLimited() throws Exception {
        for (int i = 0; i < CAPACITY * 5; i++) {
            mockMvc.perform(get("/v1/hello"))
                .andExpect(status().isOk());
        }
    }

    @Test
    void highCapacityKey_neverThrottled() throws Exception {
        for (int i = 0; i < CAPACITY * 10; i++) {
            mockMvc.perform(get("/api/basic").header("X-API-Key", "rate-key-unlimited"))
                .andExpect(status().isOk());
        }
    }

}
