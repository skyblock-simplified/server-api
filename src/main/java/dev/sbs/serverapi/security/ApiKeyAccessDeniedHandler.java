package dev.sbs.serverapi.security;

import dev.sbs.serverapi.error.ErrorResponseWriter;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.jetbrains.annotations.NotNull;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.web.access.AccessDeniedHandler;

import java.io.IOException;

/**
 * {@link AccessDeniedHandler} that renders content-negotiated 403 responses for authenticated
 * requests that fail authorization.
 *
 * <p>Activates when {@link org.springframework.security.access.prepost.PreAuthorize @PreAuthorize}
 * denies an authenticated principal. Delegates rendering to the shared
 * {@link ErrorResponseWriter}.</p>
 */
@RequiredArgsConstructor
public class ApiKeyAccessDeniedHandler implements AccessDeniedHandler {

    private final @NotNull ErrorResponseWriter responseWriter;

    @Override
    public void handle(
        @NotNull HttpServletRequest request,
        @NotNull HttpServletResponse response,
        @NotNull AccessDeniedException accessDeniedException) throws IOException {
        String message = accessDeniedException.getMessage() != null
            ? accessDeniedException.getMessage()
            : "Insufficient permissions";
        this.responseWriter.write(request, response, HttpServletResponse.SC_FORBIDDEN, message);
    }

}
