package dev.sbs.serverapi.config;

import com.google.gson.Gson;
import dev.sbs.api.SimplifiedApi;
import dev.sbs.serverapi.security.SecurityHeaderInterceptor;
import org.jetbrains.annotations.NotNull;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.converter.json.GsonHttpMessageConverter;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;
import org.springframework.web.servlet.handler.MappedInterceptor;

/**
 * Framework-level web configuration that registers the {@link SecurityHeaderInterceptor}
 * and configures HTTP message converters for the Gson-based ecosystem.
 *
 * <p>The {@link GsonHttpMessageConverter} is exposed as a {@code @Bean} so that Spring
 * Boot's {@code HttpMessageConverters} places it ahead of the default Jackson converter.
 * This ensures Gson - which carries the project's custom {@code TypeAdapter} registrations -
 * always wins content negotiation for {@code application/json}.</p>
 *
 * <p>If a consumer defines a {@link Gson} {@code @Bean}, it is used automatically.
 * Otherwise the {@link SimplifiedApi#getGson()} instance is used as a fallback.</p>
 */
@Configuration
public class ServerWebConfig implements WebMvcConfigurer {

    private final @NotNull Gson gson;

    public ServerWebConfig(@NotNull ObjectProvider<Gson> gsonProvider) {
        this.gson = gsonProvider.getIfAvailable(SimplifiedApi::getGson);
    }

    @Bean
    public @NotNull MappedInterceptor securityHeaderMappedInterceptor() {
        return new MappedInterceptor(new String[]{"/**"}, new SecurityHeaderInterceptor());
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

}
