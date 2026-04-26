package dev.sbs.serverapi.security;

import org.jetbrains.annotations.NotNull;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.web.SecurityFilterChain;

/**
 * Fallback Spring Security configuration that permits all requests when API key
 * authentication is disabled.
 *
 * <p>Active when {@code api.key.authentication.enabled} is missing or set to {@code false}.
 * Without this config, Spring Boot's {@code SecurityAutoConfiguration} would install the
 * default basic-auth chain with a generated password, which is rarely what consumers want
 * (and breaks unauthenticated test/dev workflows). Disabling CSRF and permitting everything
 * keeps Spring Security passive while still letting the auto-configured filter chain run.</p>
 */
@Configuration
@EnableWebSecurity
@ConditionalOnProperty(name = "api.key.authentication.enabled", havingValue = "false", matchIfMissing = true)
public class PermitAllSecurityConfig {

    @Bean
    public @NotNull SecurityFilterChain permitAllSecurityFilterChain(@NotNull HttpSecurity http) throws Exception {
        return http
            .csrf(AbstractHttpConfigurer::disable)
            .authorizeHttpRequests(auth -> auth.anyRequest().permitAll())
            .build();
    }

}
