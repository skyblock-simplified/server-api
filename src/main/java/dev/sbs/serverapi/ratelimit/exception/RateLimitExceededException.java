package dev.sbs.serverapi.ratelimit.exception;

import dev.sbs.serverapi.exception.ServerException;
import org.springframework.http.HttpStatus;

/**
 * Thrown when an API key has exceeded its allowed request rate.
 */
public final class RateLimitExceededException extends ServerException {

    /**
     * Constructs a new {@code RateLimitExceededException}.
     */
    public RateLimitExceededException() {
        super(HttpStatus.TOO_MANY_REQUESTS, "Rate limit exceeded");
    }

}
