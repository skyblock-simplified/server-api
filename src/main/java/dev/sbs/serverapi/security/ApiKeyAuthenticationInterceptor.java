package dev.sbs.serverapi.security;

import dev.sbs.serverapi.security.exception.InsufficientPermissionException;
import dev.sbs.serverapi.security.exception.InvalidApiKeyException;
import dev.sbs.serverapi.security.exception.MissingApiKeyException;
import dev.sbs.serverapi.security.exception.RateLimitExceededException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.jetbrains.annotations.NotNull;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerInterceptor;

/**
 * Interceptor that enforces API key authentication, rate limiting, and permission checks.
 *
 * <p>Extracts the API key from the {@code X-API-Key} request header. Resolves the
 * {@link ApiKeyProtected} annotation from the handler method first, falling back to
 * the controller class if not present at the method level.</p>
 */
@RequiredArgsConstructor
public class ApiKeyAuthenticationInterceptor implements HandlerInterceptor {

    private static final @NotNull String API_KEY_HEADER = "X-API-Key";

    private final @NotNull ApiKeyService apiKeyService;

    @Override
    public boolean preHandle(
            @NotNull HttpServletRequest request,
            @NotNull HttpServletResponse response,
            @NotNull Object handler) throws Exception {

        if (handler instanceof HandlerMethod handlerMethod) {
            ApiKeyProtected apiKeyProtected = handlerMethod.getMethodAnnotation(ApiKeyProtected.class);
            if (apiKeyProtected == null)
                apiKeyProtected = handlerMethod.getBeanType().getAnnotation(ApiKeyProtected.class);

            if (apiKeyProtected == null)
                return true;

            String apiKey = request.getHeader(API_KEY_HEADER);

            if (apiKey == null || apiKey.isEmpty())
                throw new MissingApiKeyException();

            ApiKey key = this.apiKeyService.resolve(apiKey).orElseThrow(InvalidApiKeyException::new);

            if (!this.apiKeyService.allowRequest(key))
                throw new RateLimitExceededException();

            ApiKeyRole[] required = apiKeyProtected.requiredPermissions();
            if (required.length > 0 && !this.apiKeyService.hasPermission(key, required))
                throw new InsufficientPermissionException();

            return true;
        }

        return true;
    }

}
