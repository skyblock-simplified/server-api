package dev.sbs.serverapi.error;

import dev.sbs.serverapi.exception.ServerException;
import dev.sbs.serverapi.version.VersionRegistryService;
import dev.sbs.serverapi.version.exception.InvalidVersionException;
import dev.sbs.serverapi.version.exception.MissingVersionException;
import dev.simplified.client.exception.ApiDecodeException;
import dev.simplified.client.exception.ApiException;
import dev.simplified.collection.ConcurrentSet;
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
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.ServletWebRequest;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Global exception handler producing consistent error responses for all errors.
 *
 * <p>Performs content negotiation via the {@code Accept} header through the shared
 * {@link ErrorResponseWriter}: browsers receiving {@code text/html} get a Cloudflare-style
 * HTML error page rendered by {@link ErrorPageRenderer}, while API clients get JSON error
 * responses.</p>
 *
 * <p>Spring Security 401/403 responses are not handled here - they are intercepted in
 * the security filter chain by {@link dev.sbs.serverapi.security.ApiKeyAuthenticationEntryPoint}
 * and {@link dev.sbs.serverapi.security.ApiKeyAccessDeniedHandler}, which delegate to the
 * same {@link ErrorResponseWriter} for consistent rendering.</p>
 */
@Log4j2
@RequiredArgsConstructor
@RestControllerAdvice
public final class ErrorController extends ResponseEntityExceptionHandler {

    private static final @NotNull Pattern VERSION_PREFIX = Pattern.compile("^/v(\\d+)(/.*)$");

    private final @NotNull VersionRegistryService versionRegistryService;
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

    @Override
    protected @NotNull ResponseEntity<Object> handleNoResourceFoundException(
            @NotNull NoResourceFoundException ex,
            @NotNull HttpHeaders headers,
            @NotNull HttpStatusCode status,
            @NotNull WebRequest request) {
        String requestUri = ((ServletWebRequest) request).getRequest().getRequestURI();
        Matcher versionMatcher = VERSION_PREFIX.matcher(requestUri);

        if (versionMatcher.matches()) {
            int requestedVersion = Integer.parseInt(versionMatcher.group(1));
            String basePath = versionMatcher.group(2);
            ConcurrentSet<Integer> available = versionRegistryService.getVersionsForPath(basePath);

            if (available != null) {
                InvalidVersionException versionEx = new InvalidVersionException(requestedVersion, basePath, available);
                return handleExceptionInternal(versionEx, null, headers, versionEx.getStatus(), request);
            }
        }

        ConcurrentSet<Integer> available = versionRegistryService.getVersionsForPath(requestUri);
        if (available != null && !available.isEmpty()) {
            MissingVersionException versionEx = new MissingVersionException(requestUri, available);
            return handleExceptionInternal(versionEx, null, headers, versionEx.getStatus(), request);
        }

        return handleExceptionInternal(ex, null, headers, status, request);
    }

    @ExceptionHandler(ServerException.class)
    public @NotNull ResponseEntity<?> handleServerException(
            @NotNull ServerException ex,
            @NotNull HttpServletRequest request) {
        HttpStatus status = ex.getStatus();
        return this.responseWriter.entity(request, status.value(), ex.getMessage());
    }

    /**
     * Handles {@link AccessDeniedException} thrown by {@link org.springframework.security.access.prepost.PreAuthorize}.
     *
     * <p>The exception is thrown from the AOP-driven method security interceptor, so it
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
