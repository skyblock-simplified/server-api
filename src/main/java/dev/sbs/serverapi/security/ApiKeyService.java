package dev.sbs.serverapi.security;

import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.jetbrains.annotations.NotNull;

import java.util.Set;

/**
 * Manages API key validation, rate limiting, and permission resolution.
 *
 * <p>Delegates key lookups to an {@link ApiKeyStore} supplied by the consuming
 * application, so the framework does not own any concrete key source. Permission
 * checks expand the caller's assigned roles through {@link ApiKeyRoleHierarchy}
 * before matching against the required roles.
 */
@Log4j2
@RequiredArgsConstructor
public class ApiKeyService {

    private final @NotNull ApiKeyRoleHierarchy hierarchyService;
    private final @NotNull ApiKeyStore store;

    @PostConstruct
    private void logStore() {
        log.info("ApiKeyService initialized with store: {}", store.getClass().getSimpleName());
    }

    /**
     * Checks whether the given key string corresponds to a registered API key.
     *
     * @param apiKey the key string to validate
     * @return {@code true} if the key is registered
     */
    public boolean isValidApiKey(@NotNull String apiKey) {
        return store.findByKey(apiKey).isPresent();
    }

    /**
     * Checks whether the given API key has exceeded its rate limit.
     *
     * @param apiKey the key string to check
     * @return {@code true} if the key is rate-limited and the request should be rejected
     */
    public boolean isRateLimited(@NotNull String apiKey) {
        return store.findByKey(apiKey).map(key -> !key.allowRequest()).orElse(false);
    }

    /**
     * Checks whether the given API key holds at least one of the required roles,
     * accounting for role hierarchy expansion.
     *
     * @param apiKey the key string to check
     * @param requiredPermissions the roles required (any-match semantics)
     * @return {@code true} if the key holds at least one of the required roles
     */
    public boolean hasPermission(@NotNull String apiKey, @NotNull ApiKeyRole[] requiredPermissions) {
        ApiKey key = store.findByKey(apiKey).orElse(null);
        if (key == null) return false;

        if (requiredPermissions.length == 0)
            return true;

        Set<ApiKeyRole> reachable = hierarchyService.getReachablePermissions(key.getPermissions());

        for (ApiKeyRole required : requiredPermissions) {
            if (reachable.contains(required))
                return true;
        }

        return false;
    }

}
