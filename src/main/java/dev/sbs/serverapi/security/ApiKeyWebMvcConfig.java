package dev.sbs.serverapi.security;

import lombok.RequiredArgsConstructor;
import org.jetbrains.annotations.NotNull;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * Registers the {@link ApiKeyAuthenticationInterceptor} on {@code /**} paths.
 *
 * <p>Separated from {@link ApiKeyConfig} so the interceptor can be constructor-injected
 * as a managed bean, rather than being constructed ad-hoc inside {@code addInterceptors}
 * via {@code @Configuration} self-invocation. Guarded by the same conditional as
 * {@link ApiKeyConfig} so both activate as a unit.
 */
@Configuration
@ConditionalOnProperty(name = "api.key.authentication.enabled", havingValue = "true")
@RequiredArgsConstructor
public class ApiKeyWebMvcConfig implements WebMvcConfigurer {

    private final @NotNull ApiKeyAuthenticationInterceptor interceptor;

    @Override
    public void addInterceptors(@NotNull InterceptorRegistry registry) {
        registry.addInterceptor(interceptor).addPathPatterns("/**");
    }

}
