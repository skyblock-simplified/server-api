package dev.sbs.serverapi.security;

import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.jetbrains.annotations.NotNull;

import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

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
    private final @NotNull ConcurrentHashMap<String, ConcurrentHashMap<Long, AtomicLong>> rateLimitState = new ConcurrentHashMap<>();

    @PostConstruct
    private void logStore() {
        log.info("ApiKeyService initialized with store: {}", store.getClass().getSimpleName());
    }

    /**
     * Resolves the {@link ApiKey} registered under the given key string.
     *
     * <p>This is the only point in the request path that touches the {@link ApiKeyStore},
     * so JPA-backed stores incur a single hit per request when callers retain the returned
     * instance and pass it to subsequent rate-limit and permission checks.</p>
     *
     * @param apiKey the key string to look up
     * @return the matching {@link ApiKey}, or empty if the key is unregistered
     */
    public @NotNull Optional<ApiKey> resolve(@NotNull String apiKey) {
        return store.findByKey(apiKey);
    }

    /**
     * Checks whether the given {@link ApiKey} holds at least one of the required roles,
     * accounting for role hierarchy expansion.
     *
     * @param key the resolved API key
     * @param requiredPermissions the roles required (any-match semantics)
     * @return {@code true} if {@code requiredPermissions} is empty or the key holds at
     *         least one of the required roles
     */
    public boolean hasPermission(@NotNull ApiKey key, @NotNull ApiKeyRole[] requiredPermissions) {
        if (requiredPermissions.length == 0)
            return true;

        Set<ApiKeyRole> reachable = hierarchyService.getReachablePermissions(key.getPermissions());

        for (ApiKeyRole required : requiredPermissions) {
            if (reachable.contains(required))
                return true;
        }

        return false;
    }

    /**
     * Checks whether a request from the given {@link ApiKey} is allowed under its
     * sliding-window rate limit, registering the request against the counter if so.
     *
     * <p>Rate-limit state is keyed by {@link ApiKey#getKeyValue()} on this service rather
     * than held on the {@link ApiKey} instance, so {@link ApiKeyStore} implementations are
     * free to return fresh instances on each lookup without resetting counters. The
     * read-modify-write sequence is serialized per-key by synchronizing on the inner
     * counter map; different keys never contend.</p>
     *
     * @param key the resolved API key
     * @return {@code true} if the request is allowed
     */
    public boolean allowRequest(@NotNull ApiKey key) {
        ConcurrentHashMap<Long, AtomicLong> windowCounts = rateLimitState.computeIfAbsent(
            key.getKeyValue(), k -> new ConcurrentHashMap<>()
        );

        synchronized (windowCounts) {
            long windowInSeconds = key.getWindowInSeconds();
            long now = System.currentTimeMillis() / 1000;
            long currentWindowStart = (now / windowInSeconds) * windowInSeconds;
            long previousWindowStart = currentWindowStart - windowInSeconds;

            windowCounts.keySet().removeIf(t -> t < previousWindowStart);

            long currentCount = windowCounts
                .computeIfAbsent(currentWindowStart, k -> new AtomicLong(0))
                .incrementAndGet();
            AtomicLong prevCounter = windowCounts.get(previousWindowStart);
            long previousCount = prevCounter != null ? prevCounter.get() : 0;

            double elapsedInCurrentWindow = (double) (now % windowInSeconds) / windowInSeconds;
            double estimatedCount = (previousCount * (1.0 - elapsedInCurrentWindow)) + currentCount;

            if (estimatedCount > key.getMaxRequests()) {
                windowCounts.get(currentWindowStart).decrementAndGet();
                return false;
            }

            return true;
        }
    }

    /**
     * Clears the sliding-window rate-limit state tracked for the given key value.
     *
     * <p>Intended for consumers that revoke or rotate keys and want the per-key counter
     * map released for garbage collection.</p>
     *
     * @param keyValue the key string whose state should be cleared
     */
    public void clearRateLimitState(@NotNull String keyValue) {
        rateLimitState.remove(keyValue);
    }

}
