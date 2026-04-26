package dev.sbs.serverapi.security;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.core.GrantedAuthority;

import java.util.Collection;
import java.util.Collections;

/**
 * {@link org.springframework.security.core.Authentication} implementation backed by an {@link ApiKey}.
 *
 * <p>Constructed in two stages. The {@linkplain #unauthenticated(String) unauthenticated form}
 * carries only the raw key string from the {@code X-API-Key} header and is passed to the
 * {@link org.springframework.security.authentication.AuthenticationManager}. The
 * {@linkplain #authenticated(ApiKey, Collection) authenticated form} carries the resolved
 * {@link ApiKey} as its principal and the granted authorities derived from
 * {@link ApiKey#getPermissions()}.</p>
 *
 * @see ApiKeyAuthenticationProvider
 * @see ApiKeyAuthenticationFilter
 */
public final class ApiKeyAuthenticationToken extends AbstractAuthenticationToken {

    private final @NotNull String keyValue;
    private final @Nullable ApiKey apiKey;

    private ApiKeyAuthenticationToken(@NotNull String keyValue, @Nullable ApiKey apiKey, @NotNull Collection<? extends GrantedAuthority> authorities) {
        super(authorities);
        this.keyValue = keyValue;
        this.apiKey = apiKey;
        super.setAuthenticated(apiKey != null);
    }

    /**
     * Creates an unauthenticated token carrying only the raw key string.
     *
     * @param keyValue the key string extracted from the {@code X-API-Key} header
     * @return an unauthenticated token suitable for {@link org.springframework.security.authentication.AuthenticationManager#authenticate}
     */
    public static @NotNull ApiKeyAuthenticationToken unauthenticated(@NotNull String keyValue) {
        return new ApiKeyAuthenticationToken(keyValue, null, Collections.emptyList());
    }

    /**
     * Creates an authenticated token carrying the resolved {@link ApiKey} and its authorities.
     *
     * @param apiKey the resolved key
     * @param authorities the authorities granted to the key
     * @return an authenticated token
     */
    public static @NotNull ApiKeyAuthenticationToken authenticated(@NotNull ApiKey apiKey, @NotNull Collection<? extends GrantedAuthority> authorities) {
        return new ApiKeyAuthenticationToken(apiKey.getKeyValue(), apiKey, authorities);
    }

    /**
     * Returns the raw key string, used by the {@link ApiKeyAuthenticationProvider} for lookup.
     *
     * @return the {@code X-API-Key} header value
     */
    @Override
    public @NotNull String getCredentials() {
        return this.keyValue;
    }

    /**
     * Returns the authenticated principal, or the raw key string before authentication completes.
     *
     * @return the {@link ApiKey} when authenticated, otherwise the raw key string
     */
    @Override
    public @NotNull Object getPrincipal() {
        return this.apiKey != null ? this.apiKey : this.keyValue;
    }

    /**
     * Returns the resolved key, or {@code null} if this token is unauthenticated.
     *
     * @return the authenticated {@link ApiKey}, or {@code null}
     */
    public @Nullable ApiKey getApiKey() {
        return this.apiKey;
    }

    @Override
    public void setAuthenticated(boolean authenticated) {
        if (authenticated)
            throw new IllegalArgumentException("Cannot set this token to authenticated - use the authenticated factory");

        super.setAuthenticated(false);
    }

}
