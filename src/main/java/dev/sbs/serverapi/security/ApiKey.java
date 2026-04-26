package dev.sbs.serverapi.security;

import dev.simplified.collection.ConcurrentSet;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.jetbrains.annotations.NotNull;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;

import java.util.Collection;

/**
 * An API key with associated {@link ApiKeyRole} roles and rate limit configuration.
 *
 * <p>Carried as the principal of an authenticated
 * {@link org.springframework.security.core.Authentication}. Rate-limit configuration
 * ({@link #getMaxRequests()}, {@link #getWindowInSeconds()}) is consumed by
 * {@link dev.sbs.serverapi.ratelimit.RateLimitFilter}.</p>
 */
@Getter
@RequiredArgsConstructor
public class ApiKey {

    private final @NotNull String keyValue;
    private final @NotNull ConcurrentSet<ApiKeyRole> permissions;
    private final int maxRequests;
    private final long windowInSeconds;

    /**
     * Granted authorities for this key, derived from {@link #getPermissions()}.
     *
     * @return one {@link SimpleGrantedAuthority} per assigned role, with the
     *         {@code ROLE_} prefix expected by {@code hasRole(...)} expressions
     */
    public @NotNull Collection<GrantedAuthority> getAuthorities() {
        return this.permissions.stream()
            .map(ApiKeyRole::getAuthority)
            .map(SimpleGrantedAuthority::new)
            .map(GrantedAuthority.class::cast)
            .toList();
    }

}
