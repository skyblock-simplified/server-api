package dev.sbs.serverapi.security;

import dev.simplified.collection.ConcurrentSet;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.jetbrains.annotations.NotNull;

/**
 * An API key with associated {@link ApiKeyRole} roles and rate limit configuration.
 *
 * <p>Sliding-window rate-limit state lives on {@link ApiKeyService}, keyed by
 * {@link #getKeyValue()}.</p>
 */
@Getter
@RequiredArgsConstructor
public class ApiKey {

    private final @NotNull String keyValue;
    private final @NotNull ConcurrentSet<ApiKeyRole> permissions;
    private final int maxRequests;
    private final long windowInSeconds;

}
