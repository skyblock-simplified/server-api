package dev.sbs.serverapi.error;

import dev.simplified.client.exception.ApiDecodeException;
import dev.simplified.client.exception.ApiException;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.jetbrains.annotations.NotNull;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.AuthenticationCredentialsNotFoundException;
import org.springframework.security.authentication.InsufficientAuthenticationException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.accept.InvalidApiVersionException;
import org.springframework.web.accept.MissingApiVersionException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.ServletWebRequest;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

/**
 * Global exception handler producing consistent error responses for all errors.
 *
 * <p>Performs content negotiation via the {@code Accept} header through the shared
 * {@link ErrorResponseWriter}: browsers receiving {@code text/html} get a Cloudflare-style
 * HTML error page rendered by {@link ErrorPageRenderer}, while API clients get JSON error
 * responses.</p>
 *
 * <p>Spring Security 401/403 responses raised inside the security filter chain are
 * intercepted by {@link dev.sbs.serverapi.security.ApiKeyAuthenticationEntryPoint} and
 * {@link dev.sbs.serverapi.security.ApiKeyAccessDeniedHandler}, which delegate to the
 * same {@link ErrorResponseWriter}. Method-security exceptions raised by
 * {@code @PreAuthorize} via AOP unwind through the dispatcher servlet before
 * {@code ExceptionTranslationFilter} can route them, so the {@link AccessDeniedException}
 * and {@link AuthenticationException} handlers below mirror the filter's behavior for
 * that path.</p>
 */
@Log4j2
@RequiredArgsConstructor
@RestControllerAdvice
public final class ErrorController extends ResponseEntityExceptionHandler {

    private final @NotNull ErrorResponseWriter responseWriter;

    @Override
    protected @NotNull ResponseEntity<Object> handleExceptionInternal(
            @NotNull Exception ex,
            Object body,
            @NotNull HttpHeaders headers,
            @NotNull HttpStatusCode statusCode,
            @NotNull WebRequest request) {
        HttpServletRequest servletRequest = ((ServletWebRequest) request).getRequest();
        int code = statusCode.value();
        String reason = ex.getMessage() != null ? ex.getMessage() : HttpStatus.valueOf(code).getReasonPhrase();

        Object responseBody;
        HttpHeaders responseHeaders = new HttpHeaders();
        responseHeaders.putAll(headers);

        if (ErrorResponseWriter.acceptsHtml(servletRequest)) {
            responseBody = this.responseWriter.entity(servletRequest, code, reason).getBody();
            responseHeaders.setContentType(MediaType.TEXT_HTML);
        } else {
            responseBody = this.responseWriter.buildBody(code, reason, servletRequest);
            responseHeaders.setContentType(MediaType.APPLICATION_JSON);
        }

        return new ResponseEntity<>(responseBody, responseHeaders, statusCode);
    }

    /**
     * Handles {@link MissingApiVersionException} and {@link InvalidApiVersionException}
     * thrown by Spring Framework's API versioning machinery when a request lacks a
     * required version or specifies one that is not supported.
     */
    @ExceptionHandler({ MissingApiVersionException.class, InvalidApiVersionException.class })
    public @NotNull ResponseEntity<?> handleApiVersion(
            @NotNull Exception ex,
            @NotNull HttpServletRequest request) {
        String message = ex.getMessage() != null ? ex.getMessage() : "API version error";
        return this.responseWriter.entity(request, HttpStatus.BAD_REQUEST.value(), message);
    }

    /**
     * Handles {@link AccessDeniedException} thrown by {@code @PreAuthorize}.
     *
     * <p>The exception is raised from the AOP-driven method security interceptor, so it
     * unwinds into the dispatcher servlet's exception handling phase before Spring
     * Security's {@code ExceptionTranslationFilter} can intercept it. We mirror the
     * filter's "anonymous becomes 401" behavior here so unauthenticated callers receive
     * 401 and authenticated-but-denied callers receive 403.</p>
     */
    @ExceptionHandler(AccessDeniedException.class)
    public @NotNull ResponseEntity<?> handleAccessDenied(
            @NotNull AccessDeniedException ex,
            @NotNull HttpServletRequest request) {
        if (isAnonymous()) {
            String message = ex.getMessage() != null ? ex.getMessage() : "Authentication required";
            return this.responseWriter.entity(request, HttpStatus.UNAUTHORIZED.value(), message);
        }

        String message = ex.getMessage() != null ? ex.getMessage() : "Insufficient permissions";
        return this.responseWriter.entity(request, HttpStatus.FORBIDDEN.value(), message);
    }

    /**
     * Handles {@link AuthenticationException} thrown by Spring Security when no valid
     * credentials are supplied to a protected handler.
     */
    @ExceptionHandler({ AuthenticationException.class, AuthenticationCredentialsNotFoundException.class, InsufficientAuthenticationException.class })
    public @NotNull ResponseEntity<?> handleAuthentication(
            @NotNull AuthenticationException ex,
            @NotNull HttpServletRequest request) {
        String message = ex.getMessage() != null ? ex.getMessage() : "Authentication required";
        return this.responseWriter.entity(request, HttpStatus.UNAUTHORIZED.value(), message);
    }

    private static boolean isAnonymous() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        return authentication == null || !authentication.isAuthenticated()
            || "anonymousUser".equals(authentication.getPrincipal());
    }

    @ExceptionHandler(ApiDecodeException.class)
    public @NotNull ResponseEntity<?> handleApiDecodeException(
            @NotNull ApiDecodeException ex,
            @NotNull HttpServletRequest request) {
        int code = HttpStatus.INTERNAL_SERVER_ERROR.value();
        String reason = ex.getResponse().getReason();
        log.error("Decode exception on {}", ErrorResponseWriter.route(request), ex);
        return this.responseWriter.entity(request, code, reason, ErrorSource.SERVER);
    }

    @ExceptionHandler(ApiException.class)
    public @NotNull ResponseEntity<?> handleApiException(
            @NotNull ApiException ex,
            @NotNull HttpServletRequest request) {
        int code = ex.getStatus().getCode();
        String reason = ex.getResponse().getReason();
        return this.responseWriter.entity(request, code, reason, ErrorSource.API);
    }

    @ExceptionHandler(Exception.class)
    public @NotNull ResponseEntity<?> handleAll(
            @NotNull Exception ex,
            @NotNull HttpServletRequest request) {
        int code = HttpStatus.INTERNAL_SERVER_ERROR.value();
        log.error("Unhandled exception on {}", ErrorResponseWriter.route(request), ex);
        return this.responseWriter.entity(request, code, "An unexpected error occurred");
    }

}
