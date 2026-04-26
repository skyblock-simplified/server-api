package dev.sbs.serverapi.exception;

import dev.sbs.serverapi.ratelimit.exception.RateLimitExceededException;
import dev.sbs.serverapi.version.exception.VersionException;
import lombok.Getter;
import org.intellij.lang.annotations.PrintFormat;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.springframework.http.HttpStatus;

/**
 * Thrown when a server-side constraint produces an HTTP error response.
 *
 * @see RateLimitExceededException
 * @see VersionException
 */
@Getter
public class ServerException extends RuntimeException {

    private final @NotNull HttpStatus status;

    /**
     * Constructs a new {@code ServerException} with the specified status and cause.
     *
     * @param status the HTTP status code for the error response
     * @param cause the underlying throwable that caused this exception
     */
    public ServerException(@NotNull HttpStatus status, @NotNull Throwable cause) {
        super(cause);
        this.status = status;
    }

    /**
     * Constructs a new {@code ServerException} with the specified status and detail message.
     *
     * @param status the HTTP status code for the error response
     * @param message the detail message
     */
    public ServerException(@NotNull HttpStatus status, @NotNull String message) {
        super(message);
        this.status = status;
    }

    /**
     * Constructs a new {@code ServerException} with the specified status, cause, and detail message.
     *
     * @param status the HTTP status code for the error response
     * @param cause the underlying throwable that caused this exception
     * @param message the detail message
     */
    public ServerException(@NotNull HttpStatus status, @NotNull Throwable cause, @NotNull String message) {
        super(message, cause);
        this.status = status;
    }

    /**
     * Constructs a new {@code ServerException} with the specified status and a formatted detail message.
     *
     * @param status the HTTP status code for the error response
     * @param message the format string
     * @param args the format arguments
     */
    public ServerException(@NotNull HttpStatus status, @NotNull @PrintFormat String message, @Nullable Object... args) {
        super(String.format(message, args));
        this.status = status;
    }

    /**
     * Constructs a new {@code ServerException} with the specified status, cause, and a formatted detail message.
     *
     * @param status the HTTP status code for the error response
     * @param cause the underlying throwable that caused this exception
     * @param message the format string
     * @param args the format arguments
     */
    public ServerException(@NotNull HttpStatus status, @NotNull Throwable cause, @NotNull @PrintFormat String message, @Nullable Object... args) {
        super(String.format(message, args), cause);
        this.status = status;
    }

}
