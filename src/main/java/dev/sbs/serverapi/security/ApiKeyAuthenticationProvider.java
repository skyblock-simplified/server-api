package dev.sbs.serverapi.security;

import lombok.RequiredArgsConstructor;
import org.jetbrains.annotations.NotNull;
import org.springframework.security.authentication.AuthenticationProvider;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;

/**
 * {@link AuthenticationProvider} that resolves an {@link ApiKeyAuthenticationToken} against
 * the consumer-supplied {@link ApiKeyStore}.
 *
 * <p>On a successful lookup, returns an authenticated token carrying the {@link ApiKey} as
 * its principal and the authorities derived from {@link ApiKey#getAuthorities()}. On an
 * unknown key, throws {@link BadCredentialsException} which Spring Security routes through
 * the configured {@link org.springframework.security.web.AuthenticationEntryPoint}.</p>
 */
@RequiredArgsConstructor
public class ApiKeyAuthenticationProvider implements AuthenticationProvider {

    private final @NotNull ApiKeyStore apiKeyStore;

    @Override
    public @NotNull Authentication authenticate(@NotNull Authentication authentication) throws AuthenticationException {
        String keyValue = (String) authentication.getCredentials();
        ApiKey apiKey = this.apiKeyStore.findByKey(keyValue)
            .orElseThrow(() -> new BadCredentialsException("Invalid API key"));

        return ApiKeyAuthenticationToken.authenticated(apiKey, apiKey.getAuthorities());
    }

    @Override
    public boolean supports(@NotNull Class<?> authentication) {
        return ApiKeyAuthenticationToken.class.isAssignableFrom(authentication);
    }

}
