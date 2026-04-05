package dev.sbs.serverapi.error;

import dev.sbs.api.util.StringUtil;
import dev.sbs.api.util.SystemUtil;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.RequiredArgsConstructor;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.springframework.web.util.HtmlUtils;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.EnumMap;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Pure-Java renderer for Cloudflare-style HTML error pages.
 * <p>
 * Loads CSS and HTML template resources once at class load time, then renders
 * error pages by replacing named {@link Placeholder} tokens. All user-controlled
 * values are HTML-escaped via {@link HtmlUtils#htmlEscape(String)} to prevent XSS.
 *
 * @see <a href="https://github.com/donlon/cloudflare-error-page">Cloudflare Error Page Generator</a>
 */
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class ErrorPageRenderer {

    private static final @NotNull String CSS = loadResource("error/error-page.css");
    private static final @NotNull String DARK_CSS = loadResource("error/error-page-dark.css");
    private static final @NotNull String TEMPLATE = loadResource("error/error-page.html");

    /**
     * Named tokens embedded in the HTML template.
     *
     * <p>Each placeholder maps to a {@code {{NAME}}} token in the template.
     * Placeholders with {@code escaped == true} have their values passed through
     * {@link HtmlUtils#htmlEscape(String)} before insertion.</p>
     */
    @Getter
    @RequiredArgsConstructor
    public enum Placeholder {

        PAGE_TITLE(true),
        CSS(false),
        HEADING_TITLE(true),
        ERROR_CODE(false),
        TIMESTAMP(true),
        CLIENT_SOURCE_CLASS(false),
        CLIENT_STATUS_ICON(false),
        CLIENT_STATUS_TEXT(false),
        CLIENT_STATUS_TEXT_CLASS(false),
        SERVER_SOURCE_CLASS(false),
        SERVER_STATUS_ICON(false),
        SERVER_STATUS_TEXT(false),
        SERVER_STATUS_TEXT_CLASS(false),
        API_SOURCE_CLASS(false),
        API_STATUS_ICON(false),
        API_STATUS_TEXT(false),
        API_STATUS_TEXT_CLASS(false),
        WHAT_HAPPENED(true),
        WHAT_CAN_I_DO(false),
        ROUTE(true),
        RAY_ID(false),
        CLIENT_IP(true);

        private final boolean escaped;

        /** The token string as it appears in the HTML template. */
        public @NotNull String token() {
            return "{{" + name() + "}}";
        }

    }

    /**
     * Renders an HTML error page with an explicit error source column.
     *
     * @param statusCode the HTTP status code
     * @param title the HTTP reason phrase
     * @param reason a human-readable error description
     * @param route the HTTP method and request URI
     * @param clientIp the remote client IP address
     * @param rayId the Cf-Ray header value, or null to generate a random ID
     * @param source which status column to mark as the error source
     * @return the rendered HTML page
     */
    public static @NotNull String render(int statusCode, @NotNull String title, @NotNull String reason,
                                         @NotNull String route, @NotNull String clientIp, @Nullable String rayId,
                                         @Nullable ErrorSource source) {
        Map<Placeholder, String> values = new EnumMap<>(Placeholder.class);

        values.put(Placeholder.PAGE_TITLE, statusCode + ": " + title);
        values.put(Placeholder.CSS, CSS + DARK_CSS);
        values.put(Placeholder.HEADING_TITLE, title);
        values.put(Placeholder.ERROR_CODE, String.valueOf(statusCode));
        values.put(Placeholder.TIMESTAMP, java.time.Instant.now().toString());

        values.put(Placeholder.CLIENT_SOURCE_CLASS, source == ErrorSource.CLIENT ? "cf-error-source" : "");
        values.put(Placeholder.CLIENT_STATUS_ICON, source == ErrorSource.CLIENT ? "error" : "ok");
        values.put(Placeholder.CLIENT_STATUS_TEXT, source == ErrorSource.CLIENT ? "Error" : "Working");
        values.put(Placeholder.CLIENT_STATUS_TEXT_CLASS, source == ErrorSource.CLIENT ? "text-red-error" : "text-green-success");

        values.put(Placeholder.SERVER_SOURCE_CLASS, source == ErrorSource.SERVER ? "cf-error-source" : "");
        values.put(Placeholder.SERVER_STATUS_ICON, source == ErrorSource.SERVER ? "error" : "ok");
        values.put(Placeholder.SERVER_STATUS_TEXT, source == ErrorSource.SERVER ? "Error" : "Working");
        values.put(Placeholder.SERVER_STATUS_TEXT_CLASS, source == ErrorSource.SERVER ? "text-red-error" : "text-green-success");

        values.put(Placeholder.API_SOURCE_CLASS, source == ErrorSource.API ? "cf-error-source" : "");
        values.put(Placeholder.API_STATUS_ICON, source == ErrorSource.API ? "error" : "ok");
        values.put(Placeholder.API_STATUS_TEXT, source == ErrorSource.API ? "Error" : "Working");
        values.put(Placeholder.API_STATUS_TEXT_CLASS, source == ErrorSource.API ? "text-red-error" : "text-green-success");

        values.put(Placeholder.WHAT_HAPPENED, reason);
        values.put(Placeholder.WHAT_CAN_I_DO, whatCanIDo(statusCode));
        values.put(Placeholder.ROUTE, route);
        values.put(Placeholder.RAY_ID, resolveRayId(rayId));
        values.put(Placeholder.CLIENT_IP, clientIp);

        String html = TEMPLATE;
        for (Map.Entry<Placeholder, String> entry : values.entrySet()) {
            String value = entry.getKey().isEscaped() ? HtmlUtils.htmlEscape(entry.getValue()) : entry.getValue();
            html = html.replace(entry.getKey().token(), value);
        }

        return html;
    }

    private static @NotNull String resolveRayId(@Nullable String rayId) {
        if (StringUtil.isNotEmpty(rayId))
            return rayId;

        byte[] bytes = new byte[8];
        ThreadLocalRandom.current().nextBytes(bytes);
        StringBuilder sb = new StringBuilder(16);

        for (byte b : bytes)
            sb.append(String.format("%02x", b));

        return sb.toString();
    }

    private static @NotNull String whatCanIDo(int statusCode) {
        return switch (statusCode) {
            case 400 -> "<p>Please check the request URL and parameters for errors.</p>";
            case 401 -> "<p>Verify that a valid API key is included in the <code>X-API-Key</code> header.</p>";
            case 403 -> "<p>Your API key does not have the required permissions for this resource.</p>";
            case 404 -> "<p>Check the URL for typos and verify the correct version prefix is included (e.g. <code>/v1/</code>).</p>";
            case 405 -> "<p>The HTTP method used is not supported for this endpoint. Check the API documentation for allowed methods.</p>";
            case 429 -> "<p>You have exceeded the rate limit. Please wait before retrying your request.</p>";
            default -> {
                if (statusCode >= 500)
                    yield "<p>This is a problem on our end. Please try again later.</p>";
                yield "<p>Please check your request and try again.</p>";
            }
        };
    }

    private static @NotNull String loadResource(@NotNull String path) {
        try (InputStream is = SystemUtil.getResource(path)) {
            if (is == null)
                throw new IllegalStateException("Classpath resource not found: " + path);
            return new String(is.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

}
