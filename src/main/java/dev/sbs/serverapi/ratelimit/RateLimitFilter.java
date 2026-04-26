package dev.sbs.serverapi.ratelimit;

import dev.sbs.serverapi.ratelimit.exception.RateLimitExceededException;
import dev.sbs.serverapi.security.ApiKey;
import io.github.bucket4j.Bucket;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.jetbrains.annotations.NotNull;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.time.Duration;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * Per-{@link ApiKey} rate-limit filter backed by Bucket4j token buckets.
 *
 * <p>Inspects the {@link SecurityContextHolder} for an authenticated {@link ApiKey} principal.
 * Anonymous requests are passed through untouched - authorization rules elsewhere decide
 * whether they are allowed. For authenticated requests, a {@link Bucket} keyed by
 * {@link ApiKey#getKeyValue()} is consulted; one token is consumed per request. On overflow,
 * {@link RateLimitExceededException} is thrown and routed to
 * {@link dev.sbs.serverapi.error.ErrorController} for a 429 response.</p>
 *
 * <p>Buckets are created lazily on first use, using {@link ApiKey#getMaxRequests()} as
 * capacity and {@link ApiKey#getWindowInSeconds()} as the greedy-refill interval.</p>
 */
public class RateLimitFilter extends OncePerRequestFilter {

    private final @NotNull ConcurrentMap<String, Bucket> buckets = new ConcurrentHashMap<>();

    @Override
    protected void doFilterInternal(
        @NotNull HttpServletRequest request,
        @NotNull HttpServletResponse response,
        @NotNull FilterChain chain) throws ServletException, IOException {

        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();

        if (authentication != null && authentication.getPrincipal() instanceof ApiKey apiKey) {
            Bucket bucket = this.buckets.computeIfAbsent(apiKey.getKeyValue(), k -> bucketFor(apiKey));

            if (!bucket.tryConsume(1))
                throw new RateLimitExceededException();
        }

        chain.doFilter(request, response);
    }

    /**
     * Clears the cached bucket for the given key, releasing its state for garbage collection.
     *
     * <p>Intended for consumers that revoke or rotate keys.</p>
     *
     * @param keyValue the key string whose bucket should be cleared
     */
    public void clearBucket(@NotNull String keyValue) {
        this.buckets.remove(keyValue);
    }

    private static @NotNull Bucket bucketFor(@NotNull ApiKey apiKey) {
        return Bucket.builder()
            .addLimit(limit -> limit
                .capacity(apiKey.getMaxRequests())
                .refillGreedy(apiKey.getMaxRequests(), Duration.ofSeconds(apiKey.getWindowInSeconds())))
            .build();
    }

}
