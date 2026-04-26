package dev.sbs.serverapi.security;

import dev.sbs.serverapi.error.ErrorResponseWriter;
import dev.sbs.serverapi.ratelimit.RateLimitFilter;
import org.jetbrains.annotations.NotNull;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.access.expression.method.DefaultMethodSecurityExpressionHandler;
import org.springframework.security.access.hierarchicalroles.RoleHierarchy;
import org.springframework.security.access.hierarchicalroles.RoleHierarchyImpl;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.ProviderManager;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.annotation.web.configurers.HeadersConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.security.web.header.writers.ReferrerPolicyHeaderWriter;
import org.springframework.security.web.header.writers.XXssProtectionHeaderWriter;

/**
 * Spring Security configuration for API key authentication, authorization, and rate limiting.
 *
 * <p>Conditional on {@code api.key.authentication.enabled=true} (the default). Disabling
 * the property keeps the application running without any configured {@link ApiKeyStore},
 * useful during local development.</p>
 *
 * <p>Wires:</p>
 * <ul>
 *   <li><b>{@link SecurityFilterChain}</b> - stateless, CSRF disabled (REST). Inserts
 *       {@link ApiKeyAuthenticationFilter} before
 *       {@link UsernamePasswordAuthenticationFilter} and
 *       {@link RateLimitFilter} immediately after. Authorization is delegated to
 *       {@code @PreAuthorize}; the chain itself permits all requests.</li>
 *   <li><b>{@link RoleHierarchy}</b> - built from {@link ApiKeyRole} declaration order so
 *       earlier constants inherit later constants' authorities.</li>
 *   <li><b>Method-security expression handler</b> - wired with the role hierarchy so
 *       {@code @PreAuthorize("hasRole('USER')")} succeeds for higher-tier roles.</li>
 *   <li><b>{@link AuthenticationManager}</b> - composes
 *       {@link ApiKeyAuthenticationProvider} which delegates to the consumer-supplied
 *       {@link ApiKeyStore}.</li>
 *   <li><b>Security headers</b> - {@code X-Content-Type-Options: nosniff}, HSTS,
 *       {@code X-Frame-Options: DENY}, {@code Referrer-Policy: no-referrer}, applied to
 *       every response.</li>
 * </ul>
 *
 * <p>This configuration declares <strong>no default {@link ApiKeyStore} bean</strong> -
 * consumers must supply one, otherwise startup fails fast with
 * {@code NoSuchBeanDefinitionException}. A silent empty fallback would 401 every request
 * and be extremely confusing to debug.</p>
 *
 * @see ApiKeyStore
 * @see InMemoryApiKeyStore
 */
@Configuration
@EnableWebSecurity
@EnableMethodSecurity
@ConditionalOnProperty(name = "api.key.authentication.enabled", havingValue = "true")
public class ApiKeySecurityConfig {

    @Bean
    public @NotNull RoleHierarchy roleHierarchy() {
        ApiKeyRole[] roles = ApiKeyRole.values();
        StringBuilder sb = new StringBuilder();

        for (int i = 0; i < roles.length - 1; i++) {
            if (i > 0)
                sb.append('\n');

            sb.append(roles[i].getAuthority())
                .append(" > ")
                .append(roles[i + 1].getAuthority());
        }

        return RoleHierarchyImpl.fromHierarchy(sb.toString());
    }

    @Bean
    public @NotNull DefaultMethodSecurityExpressionHandler methodSecurityExpressionHandler(@NotNull RoleHierarchy roleHierarchy) {
        DefaultMethodSecurityExpressionHandler handler = new DefaultMethodSecurityExpressionHandler();
        handler.setRoleHierarchy(roleHierarchy);
        return handler;
    }

    @Bean
    public @NotNull ApiKeyAuthenticationProvider apiKeyAuthenticationProvider(@NotNull ApiKeyStore apiKeyStore) {
        return new ApiKeyAuthenticationProvider(apiKeyStore);
    }

    @Bean
    public @NotNull AuthenticationManager authenticationManager(@NotNull ApiKeyAuthenticationProvider provider) {
        return new ProviderManager(provider);
    }

    @Bean
    public @NotNull ApiKeyAuthenticationEntryPoint apiKeyAuthenticationEntryPoint(@NotNull ErrorResponseWriter responseWriter) {
        return new ApiKeyAuthenticationEntryPoint(responseWriter);
    }

    @Bean
    public @NotNull ApiKeyAccessDeniedHandler apiKeyAccessDeniedHandler(@NotNull ErrorResponseWriter responseWriter) {
        return new ApiKeyAccessDeniedHandler(responseWriter);
    }

    @Bean
    public @NotNull RateLimitFilter rateLimitFilter() {
        return new RateLimitFilter();
    }

    @Bean
    public @NotNull SecurityFilterChain securityFilterChain(
        @NotNull HttpSecurity http,
        @NotNull AuthenticationManager authenticationManager,
        @NotNull ApiKeyAuthenticationEntryPoint entryPoint,
        @NotNull ApiKeyAccessDeniedHandler accessDeniedHandler,
        @NotNull RateLimitFilter rateLimitFilter) throws Exception {

        ApiKeyAuthenticationFilter authFilter = new ApiKeyAuthenticationFilter(authenticationManager, entryPoint);

        return http
            .csrf(AbstractHttpConfigurer::disable)
            .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .authorizeHttpRequests(auth -> auth.anyRequest().permitAll())
            .addFilterBefore(authFilter, UsernamePasswordAuthenticationFilter.class)
            .addFilterAfter(rateLimitFilter, ApiKeyAuthenticationFilter.class)
            .exceptionHandling(ex -> ex
                .authenticationEntryPoint(entryPoint)
                .accessDeniedHandler(accessDeniedHandler)
            )
            .headers(headers -> headers
                .contentTypeOptions(opts -> {})
                .frameOptions(HeadersConfigurer.FrameOptionsConfig::deny)
                .httpStrictTransportSecurity(hsts -> hsts.includeSubDomains(true).maxAgeInSeconds(31536000))
                .referrerPolicy(rp -> rp.policy(ReferrerPolicyHeaderWriter.ReferrerPolicy.NO_REFERRER))
                .xssProtection(xss -> xss.headerValue(XXssProtectionHeaderWriter.HeaderValue.ENABLED_MODE_BLOCK))
            )
            .build();
    }

}
