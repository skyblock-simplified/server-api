package dev.sbs.serverapi.config;

import com.google.gson.Gson;
import dev.sbs.serverapi.error.ErrorResponseWriter;
import dev.simplified.gson.GsonSettings;
import org.jetbrains.annotations.NotNull;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.web.servlet.error.ErrorController;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.converter.json.GsonHttpMessageConverter;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * Framework-level web configuration providing HTTP message converters and the shared
 * {@link ErrorResponseWriter} for the Gson-based ecosystem.
 *
 * <p>The {@link GsonHttpMessageConverter} is exposed as a {@code @Bean} so that Spring
 * Boot's {@code HttpMessageConverters} places it ahead of the default Jackson converter.
 * This ensures Gson - which carries the project's custom {@code TypeAdapter} registrations -
 * always wins content negotiation for {@code application/json}.</p>
 *
 * <p>If a consumer defines a {@link Gson} {@code @Bean}, it is used automatically.
 * Otherwise a default Gson instance created from {@link GsonSettings#defaults()} is used
 * as a fallback.</p>
 *
 * <p>Security response headers are no longer set here - Spring Security's
 * {@code HeadersConfigurer} handles {@code X-Content-Type-Options}, HSTS,
 * {@code X-Frame-Options}, and {@code Referrer-Policy} via
 * {@link dev.sbs.serverapi.security.ApiKeySecurityConfig}.</p>
 */
@Configuration
public class ServerWebConfig implements WebMvcConfigurer {

    private final @NotNull Gson gson;

    public ServerWebConfig(@NotNull ObjectProvider<Gson> gsonProvider) {
        this.gson = gsonProvider.getIfAvailable(() -> GsonSettings.defaults().create());
    }

    /**
     * Provides a no-op {@link ErrorController} bean to prevent Spring Boot's
     * {@code BasicErrorController} from registering the default {@code /error} endpoint.
     * The framework's {@link dev.sbs.serverapi.error.ErrorController RestControllerAdvice}
     * handles all error responses.
     *
     * @return a no-op error controller
     */
    @Bean
    public @NotNull ErrorController noOpErrorController() {
        return new ErrorController() {};
    }

    /**
     * Exposes a Gson-backed message converter as a bean so Spring Boot registers it
     * before the default Jackson converter in the converter chain.
     *
     * @return the Gson HTTP message converter
     */
    @Bean
    public @NotNull GsonHttpMessageConverter gsonHttpMessageConverter() {
        GsonHttpMessageConverter converter = new GsonHttpMessageConverter();
        converter.setGson(this.gson);
        return converter;
    }

    /**
     * Shared response writer used by {@link dev.sbs.serverapi.error.ErrorController} and
     * the Spring Security entry-point and access-denied handlers to render content-negotiated
     * error responses.
     *
     * @return the response writer
     */
    @Bean
    public @NotNull ErrorResponseWriter errorResponseWriter() {
        return new ErrorResponseWriter(this.gson);
    }

}
