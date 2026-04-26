package dev.sbs.serverapi.error;

import com.google.gson.Gson;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.util.HtmlUtils;

import java.io.IOException;
import java.io.PrintWriter;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.Map;

/**
 * Shared response writer for HTTP error rendering.
 *
 * <p>Performs content negotiation via the {@code Accept} header: browsers receive
 * Cloudflare-style HTML error pages from {@link ErrorPageRenderer}, API clients receive
 * JSON. Used by {@link ErrorController} for handler-level exceptions and by the Spring
 * Security {@link org.springframework.security.web.AuthenticationEntryPoint} and
 * {@link org.springframework.security.web.access.AccessDeniedHandler} which intercept
 * 401/403 responses before they reach the controller advice.</p>
 */
@RequiredArgsConstructor
public class ErrorResponseWriter {

    private static final @NotNull String CLOUDFLARE_RAY_HEADER = "Cf-Ray";

    private final @NotNull Gson gson;

    /**
     * Writes an error response directly to the servlet response, performing content
     * negotiation. Used by Spring Security entry points and access-denied handlers
     * which run outside the {@link org.springframework.web.bind.annotation.ControllerAdvice}
     * flow.
     *
     * @param request the current request
     * @param response the response to write to
     * @param statusCode the HTTP status code
     * @param message a human-readable error description
     * @throws IOException if writing to the response fails
     */
    public void write(
        @NotNull HttpServletRequest request,
        @NotNull HttpServletResponse response,
        int statusCode,
        @NotNull String message) throws IOException {

        response.setStatus(statusCode);

        if (acceptsHtml(request)) {
            response.setContentType(MediaType.TEXT_HTML_VALUE);
            response.setCharacterEncoding(StandardCharsets.UTF_8.name());
            try (PrintWriter writer = response.getWriter()) {
                writer.write(html(statusCode, message, request, sourceFor(statusCode)));
            }
        } else {
            response.setContentType(MediaType.APPLICATION_JSON_VALUE);
            response.setCharacterEncoding(StandardCharsets.UTF_8.name());
            try (PrintWriter writer = response.getWriter()) {
                writer.write(this.gson.toJson(buildBody(statusCode, message, request)));
            }
        }
    }

    /**
     * Builds a {@link ResponseEntity} for the given status, message, and request,
     * performing the same content negotiation as {@link #write}.
     *
     * @param request the current request
     * @param statusCode the HTTP status code
     * @param message a human-readable error description
     * @return an HTML or JSON response entity
     */
    public @NotNull ResponseEntity<?> entity(
        @NotNull HttpServletRequest request,
        int statusCode,
        @NotNull String message) {
        return entity(request, statusCode, message, sourceFor(statusCode));
    }

    /**
     * Builds a {@link ResponseEntity} with an explicit error source column.
     *
     * @param request the current request
     * @param statusCode the HTTP status code
     * @param message a human-readable error description
     * @param source which status column to mark as the error source
     * @return an HTML or JSON response entity
     */
    public @NotNull ResponseEntity<?> entity(
        @NotNull HttpServletRequest request,
        int statusCode,
        @NotNull String message,
        @NotNull ErrorSource source) {

        if (acceptsHtml(request)) {
            return ResponseEntity.status(statusCode)
                .contentType(MediaType.TEXT_HTML)
                .body(html(statusCode, message, request, source));
        }

        return ResponseEntity.status(statusCode)
            .contentType(MediaType.APPLICATION_JSON)
            .body(buildBody(statusCode, message, request));
    }

    /**
     * Returns whether the request's {@code Accept} header indicates an HTML preference.
     *
     * @param request the current request
     * @return {@code true} if the {@code Accept} header contains {@code text/html}
     */
    public static boolean acceptsHtml(@NotNull HttpServletRequest request) {
        String accept = request.getHeader(HttpHeaders.ACCEPT);
        return accept != null && accept.contains("text/html");
    }

    /**
     * Returns the canonical {@code METHOD path} string for an error response.
     *
     * @param request the current request
     * @return the HTTP method and HTML-escaped, URL-decoded request URI
     */
    public static @NotNull String route(@NotNull HttpServletRequest request) {
        return request.getMethod() + " " + HtmlUtils.htmlEscape(URLDecoder.decode(request.getRequestURI(), StandardCharsets.UTF_8));
    }

    /**
     * Builds the JSON error response body.
     *
     * @param statusCode the HTTP status code
     * @param message a human-readable error description
     * @param request the current request
     * @return a map of {@code status}, {@code error}, {@code message}, {@code path}
     */
    public @NotNull Map<String, Object> buildBody(int statusCode, @Nullable String message, @NotNull HttpServletRequest request) {
        return Map.of(
            "status", statusCode,
            "error", HttpStatus.valueOf(statusCode).getReasonPhrase(),
            "message", message != null ? message : HttpStatus.valueOf(statusCode).getReasonPhrase(),
            "path", route(request)
        );
    }

    private static @NotNull String html(int statusCode, @NotNull String message, @NotNull HttpServletRequest request, @NotNull ErrorSource source) {
        return ErrorPageRenderer.render(
            statusCode,
            HttpStatus.valueOf(statusCode).getReasonPhrase(),
            message,
            route(request),
            request.getRemoteAddr(),
            request.getHeader(CLOUDFLARE_RAY_HEADER),
            source
        );
    }

    private static @NotNull ErrorSource sourceFor(int statusCode) {
        return statusCode < 500 ? ErrorSource.CLIENT : ErrorSource.SERVER;
    }

}
