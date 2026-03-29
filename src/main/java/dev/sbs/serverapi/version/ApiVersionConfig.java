package dev.sbs.serverapi.version;

import org.jetbrains.annotations.NotNull;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.web.servlet.WebMvcRegistrations;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.handler.MappedInterceptor;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;

/**
 * Replaces the default {@link RequestMappingHandlerMapping} with
 * {@link ApiVersionHandlerMapping} via {@link WebMvcRegistrations}, registers
 * the {@link VersionRegistryService} for precomputed version index lookups,
 * and the {@link ApiVersionInterceptor} for version validation on resolved handlers.
 */
@Configuration
public class ApiVersionConfig {

    @Bean
    public @NotNull WebMvcRegistrations webMvcRegistrations() {
        return new WebMvcRegistrations() {
            @Override
            public @NotNull RequestMappingHandlerMapping getRequestMappingHandlerMapping() {
                return new ApiVersionHandlerMapping();
            }
        };
    }

    @Bean
    public @NotNull VersionRegistryService versionRegistryService(@Qualifier("requestMappingHandlerMapping") @NotNull RequestMappingHandlerMapping handlerMapping) {
        return new VersionRegistryService(handlerMapping);
    }

    @Bean
    public @NotNull ApiVersionInterceptor apiVersionInterceptor(@NotNull VersionRegistryService versionRegistryService) {
        return new ApiVersionInterceptor(versionRegistryService);
    }

    @Bean
    public @NotNull MappedInterceptor apiVersionMappedInterceptor(@NotNull ApiVersionInterceptor apiVersionInterceptor) {
        return new MappedInterceptor(new String[]{"/**"}, apiVersionInterceptor);
    }

}
