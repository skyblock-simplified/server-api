package dev.sbs.serverapi.security;

import dev.sbs.serverapi.error.ErrorResponseWriter;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.jetbrains.annotations.NotNull;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;

import java.io.IOException;

/**
 * {@link AuthenticationEntryPoint} that renders content-negotiated 401 responses for
 * unauthenticated requests to protected endpoints.
 *
 * <p>Spring Security routes authentication failures and "no credentials supplied" denials
 * here, before the request reaches the {@link org.springframework.web.bind.annotation.ControllerAdvice}.
 * Delegates rendering to the shared {@link ErrorResponseWriter}, so browsers receive an
 * HTML error page and API clients receive JSON.</p>
 */
@RequiredArgsConstructor
public class ApiKeyAuthenticationEntryPoint implements AuthenticationEntryPoint {

    private final @NotNull ErrorResponseWriter responseWriter;

    @Override
    public void commence(
        @NotNull HttpServletRequest request,
        @NotNull HttpServletResponse response,
        @NotNull AuthenticationException authException) throws IOException {
        String message = authException.getMessage() != null ? authException.getMessage() : "Authentication required";
        this.responseWriter.write(request, response, HttpServletResponse.SC_UNAUTHORIZED, message);
    }

}
