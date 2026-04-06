package dev.sbs.serverapi.security;

import org.jetbrains.annotations.NotNull;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Conditional configuration that registers the API key security beans.
 *
 * <p>Only active when {@code api.key.authentication.enabled} is {@code true} (the default).
 * Disabling allows running the server without any configured {@link ApiKeyStore} during
 * local development.
 *
 * <p>No default {@link ApiKeyStore} bean is declared here: consuming applications must
 * supply their own, otherwise Spring will fail at startup with a clear
 * {@code NoSuchBeanDefinitionException}. Silently falling back to an empty store would
 * boot cleanly and reject every request with 401, which is an extremely confusing failure
 * mode to debug.
 *
 * @see ApiKeyStore
 * @see InMemoryApiKeyStore
 * @see ApiKeyWebMvcConfig
 */
@Configuration
@ConditionalOnProperty(name = "api.key.authentication.enabled", havingValue = "true")
public class ApiKeyConfig {

    @Bean
    public @NotNull ApiKeyRoleHierarchy roleHierarchyService() {
        return new ApiKeyRoleHierarchy();
    }

    @Bean
    public @NotNull ApiKeyService apiKeyService(
        @NotNull ApiKeyRoleHierarchy roleHierarchyService,
        @NotNull ApiKeyStore apiKeyStore) {
        return new ApiKeyService(roleHierarchyService, apiKeyStore);
    }

    @Bean
    public @NotNull ApiKeyAuthenticationInterceptor apiKeyAuthenticationInterceptor(@NotNull ApiKeyService apiKeyService) {
        return new ApiKeyAuthenticationInterceptor(apiKeyService);
    }

}
