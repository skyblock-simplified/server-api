package dev.sbs.serverapi.security;

import org.jetbrains.annotations.NotNull;

import java.util.Optional;

/**
 * Source of {@link ApiKey} instances used by {@link ApiKeyService} for
 * authentication, rate limiting, and permission checks.
 *
 * <p>Implementations are free to return fresh {@code ApiKey} instances on each
 * lookup. Sliding-window rate-limit state is held on {@link ApiKeyService}, keyed
 * by {@link ApiKey#getKeyValue()}, so instance identity does not affect counter
 * continuity.</p>
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
