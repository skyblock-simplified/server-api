package dev.sbs.serverapi.security;

import org.jetbrains.annotations.NotNull;

import java.util.Optional;

/**
 * Source of {@link ApiKey} instances for the {@link ApiKeyAuthenticationProvider}.
 *
 * <p>Implementations are free to return fresh {@code ApiKey} instances on each lookup.
 * Rate-limit state is held externally in
 * {@link dev.sbs.serverapi.ratelimit.RateLimitFilter} keyed by
 * {@link ApiKey#getKeyValue()}, so instance identity does not affect counter
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
