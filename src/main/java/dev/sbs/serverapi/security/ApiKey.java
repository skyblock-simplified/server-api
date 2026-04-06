package dev.sbs.serverapi.security;

import dev.simplified.collection.ConcurrentSet;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.jetbrains.annotations.NotNull;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * An API key with associated {@link ApiKeyRole} roles and sliding window rate limit state.
 *
 * <p>Rate limiting uses a synchronized sliding window counter that estimates the current
 * request rate across two adjacent time windows.
 *
 * <p>TODO: rate-limit state (the {@code windowCounts} map and {@link #allowRequest()} logic)
 * should be extracted out of this class and owned by {@link ApiKeyService}, keyed by
 * {@link #getKeyValue()}. That extraction decouples identity from counter state, lets
 * {@link ApiKeyStore} implementations return fresh instances without resetting counters,
 * and opens the door to distributing rate-limit state via a Hazelcast {@code IMap}.
 */
@Getter
@RequiredArgsConstructor
public class ApiKey {

    private final @NotNull String keyValue;
    private final @NotNull ConcurrentSet<ApiKeyRole> permissions;
    private final int maxRequests;
    private final long windowInSeconds;

    // ConcurrentHashMap is required here for its atomic computeIfAbsent with AtomicLong
    private final @NotNull ConcurrentHashMap<Long, AtomicLong> windowCounts = new ConcurrentHashMap<>();

    /**
     * Checks whether a request is allowed under the sliding window rate limit.
     *
     * <p>Increments the current window counter, estimates the weighted request count
     * across the current and previous windows, and rolls back the increment if the
     * limit is exceeded.</p>
     *
     * @return {@code true} if the request is allowed
     */
    public synchronized boolean allowRequest() {
        long now = System.currentTimeMillis() / 1000;
        long currentWindowStart = (now / windowInSeconds) * windowInSeconds;
        long previousWindowStart = currentWindowStart - windowInSeconds;

        windowCounts.keySet().removeIf(t -> t < previousWindowStart);

        long currentCount = windowCounts.computeIfAbsent(currentWindowStart, k -> new AtomicLong(0)).incrementAndGet();
        AtomicLong prevCounter = windowCounts.get(previousWindowStart);
        long previousCount = (prevCounter != null) ? prevCounter.get() : 0;

        double elapsedInCurrentWindow = (double) (now % windowInSeconds) / windowInSeconds;
        double estimatedCount = (previousCount * (1.0 - elapsedInCurrentWindow)) + currentCount;

        if (estimatedCount > maxRequests) {
            windowCounts.get(currentWindowStart).decrementAndGet();
            return false;
        }

        return true;
    }

}
