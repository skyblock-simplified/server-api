package dev.sbs.serverapi.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.jetbrains.annotations.NotNull;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * Pre-authentication filter that extracts the {@code X-API-Key} header and delegates to the
 * {@link AuthenticationManager}.
 *
 * <p>Header absent: the chain proceeds with no authenticated principal. Spring Security's
 * authorization rules will reject access for endpoints requiring authentication via the
 * configured {@link AuthenticationEntryPoint}. Header present: an unauthenticated
 * {@link ApiKeyAuthenticationToken} is built and authenticated; on success, the resulting
 * authentication is placed on {@link SecurityContextHolder}; on failure, the entry point is
 * invoked directly so the response is rendered immediately.</p>
 */
@RequiredArgsConstructor
public class ApiKeyAuthenticationFilter extends OncePerRequestFilter {

    private static final @NotNull String API_KEY_HEADER = "X-API-Key";

    private final @NotNull AuthenticationManager authenticationManager;
    private final @NotNull AuthenticationEntryPoint authenticationEntryPoint;

    @Override
    protected void doFilterInternal(
        @NotNull HttpServletRequest request,
        @NotNull HttpServletResponse response,
        @NotNull FilterChain chain) throws ServletException, IOException {

        String keyValue = request.getHeader(API_KEY_HEADER);

        if (keyValue == null || keyValue.isEmpty()) {
            chain.doFilter(request, response);
            return;
        }

        try {
            Authentication authentication = this.authenticationManager.authenticate(
                ApiKeyAuthenticationToken.unauthenticated(keyValue)
            );
            SecurityContext context = SecurityContextHolder.createEmptyContext();
            context.setAuthentication(authentication);
            SecurityContextHolder.setContext(context);
            request.setAttribute(HttpSessionSecurityContextRepository.SPRING_SECURITY_CONTEXT_KEY, context);
        } catch (AuthenticationException ex) {
            SecurityContextHolder.clearContext();
            this.authenticationEntryPoint.commence(request, response, ex);
            return;
        }

        chain.doFilter(request, response);
    }

}
