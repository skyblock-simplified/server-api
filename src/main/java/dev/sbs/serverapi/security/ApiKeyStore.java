package dev.sbs.serverapi.security;

import org.jetbrains.annotations.NotNull;

import java.util.Optional;

/**
 * Source of {@link ApiKey} instances used by {@link ApiKeyService} for
 * authentication, rate limiting, and permission checks.
 *
 * <p><b>Identity contract.</b> Implementations must return the same {@code ApiKey}
 * instance for repeated lookups of the same key value for as long as that key is
 * considered current. Sliding-window rate-limit state is held on the {@code ApiKey}
 * itself, so returning fresh instances would silently reset counters on every
 * request. When a key is invalidated or reloaded from a backing store, dropping
 * the old instance (and its counter state) is acceptable - but it must not
 * happen between two consecutive requests for the same key under normal operation.
 *
 * <p>Hibernate second-level caches (including Hazelcast as an L2 cache) may
 * materialize fresh entity instances on cache hits, so JPA-backed implementations
 * must mediate with their own identity map to satisfy this contract.
 */
public interface ApiKeyStore {

    /**
     * Looks up an API key by its string value.
     *
     * @param keyValue the key string from the {@code X-API-Key} header
     * @return the matching {@code ApiKey}, or empty if the key is unknown
     */
    @NotNull Optional<ApiKey> findByKey(@NotNull String keyValue);

}
