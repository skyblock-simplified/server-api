package dev.sbs.serverapi.config;

import dev.sbs.api.collection.concurrent.Concurrent;
import dev.sbs.api.collection.concurrent.ConcurrentList;
import dev.sbs.api.collection.concurrent.ConcurrentMap;
import dev.sbs.api.reflection.Reflection;
import dev.sbs.api.util.builder.BuildFlag;
import dev.sbs.api.util.builder.ClassBuilder;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.jetbrains.annotations.NotNull;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.logging.LogLevel;

/**
 * Immutable configuration class for the Spring Boot server, constructed via its nested {@link Builder}.
 *
 * <p>Holds server tuning parameters, compression settings, thread pool sizing, and
 * application metadata. Each field maps to a Spring Boot property key and is emitted
 * by {@link #toProperties()} as a {@link ConcurrentMap} suitable for
 * {@link SpringApplication#setDefaultProperties}.</p>
 *
 * <p>Use {@link #builder()} for full control over every setting, or {@link #optimized()}
 * for a production-tuned preset targeting a high-core-count server behind a reverse proxy.</p>
 *
 * @see Builder
 */
@Getter
@RequiredArgsConstructor(access = AccessLevel.PRIVATE)
public final class ServerConfig {

    private final int port;
    private final @NotNull String address;
    private final @NotNull String contextPath;
    private final int maxThreads;
    private final int minSpareThreads;
    private final boolean virtualThreadsEnabled;
    private final int acceptCount;
    private final int maxConnections;
    private final int connectionTimeout;
    private final int keepAliveTimeout;
    private final int maxKeepAliveRequests;
    private final boolean compressionEnabled;
    private final @NotNull MemorySize compressionMinSize;
    private final @NotNull ConcurrentList<String> compressionMimeTypes;
    private final boolean http2Enabled;
    private final @NotNull MemorySize maxRequestHeaderSize;
    private final @NotNull MemorySize maxFormPostSize;
    private final boolean multipartEnabled;
    private final @NotNull MemorySize multipartMaxFileSize;
    private final @NotNull MemorySize multipartMaxRequestSize;
    private final @NotNull ShutdownMode shutdownMode;
    private final int shutdownTimeout;
    private final @NotNull ForwardHeadersStrategy forwardHeadersStrategy;
    private final @NotNull String applicationName;
    private final @NotNull LogLevel rootLogLevel;
    private final boolean apiKeyAuthEnabled;
    private final boolean springdocEnabled;
    private final boolean actuatorEnabled;
    private final @NotNull ConcurrentList<String> actuatorExposedEndpoints;
    private final @NotNull String actuatorBasePath;
    private final int managementPort;
    private final @NotNull HealthDetailsVisibility healthShowDetails;

    /**
     * Returns a new {@link Builder} for constructing a {@link ServerConfig} instance.
     *
     * @return a new builder with default values
     */
    public static @NotNull Builder builder() {
        return new Builder();
    }

    /**
     * Builds a production-tuned {@link ServerConfig} targeting a high-core-count server
     * (16 threads, 96 GB RAM, 10 Gbps NIC) behind a reverse proxy serving JSON and images.
     *
     * @return a fully constructed, optimized server configuration
     */
    public static @NotNull ServerConfig optimized() {
        return builder()
            .withMaxThreads(400)
            .withMinSpareThreads(50)
            .withVirtualThreadsEnabled(true)
            .withAcceptCount(200)
            .withMaxConnections(10000)
            .withConnectionTimeout(10)
            .withCompressionEnabled(true)
            .withCompressionMinSize(MemorySize.bytes(512))
            .withCompressionMimeTypes(Concurrent.newList(
                "application/json",
                "application/javascript",
                "image/svg+xml",
                "text/html",
                "text/json",
                "text/javascript",
                "text/plain"
            ))
            .withHttp2Enabled(true)
            .withMaxRequestHeaderSize(MemorySize.kilobytes(16))
            .withMaxFormPostSize(MemorySize.megabytes(1))
            .withMultipartMaxFileSize(MemorySize.megabytes(50))
            .withMultipartMaxRequestSize(MemorySize.megabytes(75))
            .withShutdownMode(ShutdownMode.GRACEFUL)
            .withShutdownTimeout(20)
            .withForwardHeadersStrategy(ForwardHeadersStrategy.NATIVE)
            .build();
    }

    /**
     * Converts this configuration to a Spring Boot property map suitable for
     * {@link org.springframework.boot.SpringApplication#setDefaultProperties}.
     *
     * @return an unmodifiable map of Spring Boot property keys to their string values
     */
    public @NotNull ConcurrentMap<String, Object> toProperties() {
        ConcurrentMap<String, Object> props = Concurrent.newMap();

        props.put("server.port", this.port);
        props.put("server.address", this.address);
        props.put("server.servlet.context-path", this.contextPath);
        props.put("server.tomcat.threads.max", this.maxThreads);
        props.put("server.tomcat.threads.min-spare", this.minSpareThreads);
        props.put("spring.threads.virtual.enabled", this.virtualThreadsEnabled);
        props.put("server.tomcat.accept-count", this.acceptCount);
        props.put("server.tomcat.max-connections", this.maxConnections);
        props.put("server.connection-timeout", this.connectionTimeout + "s");
        props.put("server.tomcat.keep-alive-timeout", this.keepAliveTimeout + "s");
        props.put("server.tomcat.max-keep-alive-requests", this.maxKeepAliveRequests);
        props.put("server.compression.enabled", this.compressionEnabled);
        props.put("server.compression.min-response-size", this.compressionMinSize.toPropertyValue());
        props.put("server.compression.mime-types", String.join(",", this.compressionMimeTypes));
        props.put("server.http2.enabled", this.http2Enabled);
        props.put("server.max-http-request-header-size", this.maxRequestHeaderSize.toPropertyValue());
        props.put("server.tomcat.max-http-form-post-size", this.maxFormPostSize.toPropertyValue());
        props.put("spring.servlet.multipart.enabled", this.multipartEnabled);
        props.put("spring.servlet.multipart.max-file-size", this.multipartMaxFileSize.toPropertyValue());
        props.put("spring.servlet.multipart.max-request-size", this.multipartMaxRequestSize.toPropertyValue());
        props.put("server.shutdown", this.shutdownMode.name().toLowerCase());
        props.put("spring.lifecycle.timeout-per-shutdown-phase", this.shutdownTimeout + "s");
        props.put("server.forward-headers-strategy", this.forwardHeadersStrategy.name());
        props.put("spring.application.name", this.applicationName);
        props.put("logging.level.root", this.rootLogLevel.name());
        props.put("spring.main.allow-bean-definition-overriding", true);
        props.put("api.key.authentication.enabled", this.apiKeyAuthEnabled);
        props.put("springdoc.api-docs.enabled", this.springdocEnabled);
        props.put("springdoc.scalar.enabled", this.springdocEnabled);
        props.put("springdoc.use-root-path", this.springdocEnabled);
        props.put("springdoc.api-docs.path", "/v3/api-docs");

        if (this.actuatorEnabled) {
            props.put("management.endpoints.web.exposure.include", String.join(",", this.actuatorExposedEndpoints));
            props.put("management.endpoints.web.base-path", this.actuatorBasePath);
            props.put("management.endpoint.health.show-details", this.healthShowDetails.getValue());
            if (this.managementPort > 0)
                props.put("management.server.port", this.managementPort);
        } else {
            props.put("management.endpoints.enabled-by-default", false);
            props.put("management.endpoints.web.exposure.include", "");
        }

        return props.toUnmodifiableMap();
    }

    /**
     * Fluent builder for constructing {@link ServerConfig} instances.
     *
     * <p>All fields carry sensible defaults matching Spring Boot conventions.
     * Call {@link #build()} to validate and produce an immutable configuration.</p>
     *
     * @see ServerConfig#builder()
     * @see ServerConfig#optimized()
     */
    public static class Builder implements ClassBuilder<ServerConfig> {

        private int port = 8080;
        @BuildFlag(nonNull = true)
        private String address = "0.0.0.0";
        @BuildFlag(nonNull = true)
        private String contextPath = "/";
        private int maxThreads = 200;
        private int minSpareThreads = 10;
        private boolean virtualThreadsEnabled = false;
        private int acceptCount = 100;
        private int maxConnections = 8192;
        private int connectionTimeout = 20;
        private int keepAliveTimeout = 60;
        private int maxKeepAliveRequests = 100;
        private boolean compressionEnabled = false;
        @BuildFlag(nonNull = true)
        private MemorySize compressionMinSize = MemorySize.bytes(2048);
        @BuildFlag(nonNull = true)
        private ConcurrentList<String> compressionMimeTypes = Concurrent.newList(
            "text/html",
            "text/xml",
            "text/plain",
            "text/css",
            "text/javascript",
            "application/javascript",
            "application/json",
            "application/xml"
        );
        private boolean http2Enabled = false;
        @BuildFlag(nonNull = true)
        private MemorySize maxRequestHeaderSize = MemorySize.kilobytes(8);
        @BuildFlag(nonNull = true)
        private MemorySize maxFormPostSize = MemorySize.megabytes(2);
        private boolean multipartEnabled = true;
        @BuildFlag(nonNull = true)
        private MemorySize multipartMaxFileSize = MemorySize.megabytes(1);
        @BuildFlag(nonNull = true)
        private MemorySize multipartMaxRequestSize = MemorySize.megabytes(10);
        @BuildFlag(nonNull = true)
        private ShutdownMode shutdownMode = ShutdownMode.IMMEDIATE;
        private int shutdownTimeout = 30;
        @BuildFlag(nonNull = true)
        private ForwardHeadersStrategy forwardHeadersStrategy = ForwardHeadersStrategy.NONE;
        @BuildFlag(nonNull = true)
        private String applicationName = "simplified-server";
        @BuildFlag(nonNull = true)
        private LogLevel rootLogLevel = LogLevel.INFO;
        private boolean apiKeyAuthEnabled = true;
        private boolean springdocEnabled = true;
        private boolean actuatorEnabled = false;
        @BuildFlag(nonNull = true)
        private ConcurrentList<String> actuatorExposedEndpoints = Concurrent.newList("health", "info");
        @BuildFlag(nonNull = true)
        private String actuatorBasePath = "/actuator";
        private int managementPort = -1;
        @BuildFlag(nonNull = true)
        private HealthDetailsVisibility healthShowDetails = HealthDetailsVisibility.NEVER;

        /** Sets the server port. */
        public @NotNull Builder withPort(int port) {
            this.port = port;
            return this;
        }

        /** Sets the server bind address. */
        public @NotNull Builder withAddress(@NotNull String address) {
            this.address = address;
            return this;
        }

        /** Sets the servlet context path. */
        public @NotNull Builder withContextPath(@NotNull String contextPath) {
            this.contextPath = contextPath;
            return this;
        }

        /** Sets the maximum number of Tomcat worker threads. */
        public @NotNull Builder withMaxThreads(int maxThreads) {
            this.maxThreads = maxThreads;
            return this;
        }

        /** Sets the minimum number of spare Tomcat worker threads. */
        public @NotNull Builder withMinSpareThreads(int minSpareThreads) {
            this.minSpareThreads = minSpareThreads;
            return this;
        }

        /** Sets whether virtual threads are enabled (Java 21+). */
        public @NotNull Builder withVirtualThreadsEnabled(boolean virtualThreadsEnabled) {
            this.virtualThreadsEnabled = virtualThreadsEnabled;
            return this;
        }

        /** Sets the Tomcat accept queue length. */
        public @NotNull Builder withAcceptCount(int acceptCount) {
            this.acceptCount = acceptCount;
            return this;
        }

        /** Sets the maximum number of concurrent Tomcat connections. */
        public @NotNull Builder withMaxConnections(int maxConnections) {
            this.maxConnections = maxConnections;
            return this;
        }

        /** Sets the connection timeout in seconds. */
        public @NotNull Builder withConnectionTimeout(int seconds) {
            this.connectionTimeout = seconds;
            return this;
        }

        /** Sets the keep-alive timeout in seconds. */
        public @NotNull Builder withKeepAliveTimeout(int seconds) {
            this.keepAliveTimeout = seconds;
            return this;
        }

        /** Sets the maximum number of keep-alive requests per connection. */
        public @NotNull Builder withMaxKeepAliveRequests(int maxKeepAliveRequests) {
            this.maxKeepAliveRequests = maxKeepAliveRequests;
            return this;
        }

        /** Sets whether response compression is enabled. */
        public @NotNull Builder withCompressionEnabled(boolean compressionEnabled) {
            this.compressionEnabled = compressionEnabled;
            return this;
        }

        /** Sets the minimum response size to trigger compression. */
        public @NotNull Builder withCompressionMinSize(@NotNull MemorySize compressionMinSize) {
            this.compressionMinSize = compressionMinSize;
            return this;
        }

        /** Sets the MIME types eligible for compression. */
        public @NotNull Builder withCompressionMimeTypes(@NotNull ConcurrentList<String> compressionMimeTypes) {
            this.compressionMimeTypes = compressionMimeTypes;
            return this;
        }

        /** Sets whether HTTP/2 is enabled. */
        public @NotNull Builder withHttp2Enabled(boolean http2Enabled) {
            this.http2Enabled = http2Enabled;
            return this;
        }

        /** Sets the maximum HTTP request header size. */
        public @NotNull Builder withMaxRequestHeaderSize(@NotNull MemorySize maxRequestHeaderSize) {
            this.maxRequestHeaderSize = maxRequestHeaderSize;
            return this;
        }

        /** Sets the maximum HTTP form post size. */
        public @NotNull Builder withMaxFormPostSize(@NotNull MemorySize maxFormPostSize) {
            this.maxFormPostSize = maxFormPostSize;
            return this;
        }

        /** Sets whether multipart file uploads are enabled. */
        public @NotNull Builder withMultipartEnabled(boolean multipartEnabled) {
            this.multipartEnabled = multipartEnabled;
            return this;
        }

        /** Sets the maximum file size for multipart uploads. */
        public @NotNull Builder withMultipartMaxFileSize(@NotNull MemorySize multipartMaxFileSize) {
            this.multipartMaxFileSize = multipartMaxFileSize;
            return this;
        }

        /** Sets the maximum total request size for multipart uploads. */
        public @NotNull Builder withMultipartMaxRequestSize(@NotNull MemorySize multipartMaxRequestSize) {
            this.multipartMaxRequestSize = multipartMaxRequestSize;
            return this;
        }

        /** Sets the server shutdown mode. */
        public @NotNull Builder withShutdownMode(@NotNull ShutdownMode shutdownMode) {
            this.shutdownMode = shutdownMode;
            return this;
        }

        /** Sets the graceful shutdown timeout in seconds. */
        public @NotNull Builder withShutdownTimeout(int seconds) {
            this.shutdownTimeout = seconds;
            return this;
        }

        /** Sets the forward headers strategy for reverse proxy support. */
        public @NotNull Builder withForwardHeadersStrategy(@NotNull ForwardHeadersStrategy forwardHeadersStrategy) {
            this.forwardHeadersStrategy = forwardHeadersStrategy;
            return this;
        }

        /** Sets the Spring application name. */
        public @NotNull Builder withApplicationName(@NotNull String applicationName) {
            this.applicationName = applicationName;
            return this;
        }

        /** Sets the root logging level. */
        public @NotNull Builder withRootLogLevel(@NotNull LogLevel rootLogLevel) {
            this.rootLogLevel = rootLogLevel;
            return this;
        }

        /** Sets whether API key authentication is enabled. */
        public @NotNull Builder withApiKeyAuthEnabled(boolean apiKeyAuthEnabled) {
            this.apiKeyAuthEnabled = apiKeyAuthEnabled;
            return this;
        }

        /** Sets whether SpringDoc OpenAPI documentation is enabled. */
        public @NotNull Builder withSpringdocEnabled(boolean springdocEnabled) {
            this.springdocEnabled = springdocEnabled;
            return this;
        }

        /** Sets whether Spring Boot Actuator endpoints are enabled. */
        public @NotNull Builder withActuatorEnabled(boolean actuatorEnabled) {
            this.actuatorEnabled = actuatorEnabled;
            return this;
        }

        /** Sets the actuator endpoints exposed over HTTP. */
        public @NotNull Builder withActuatorExposedEndpoints(@NotNull ConcurrentList<String> actuatorExposedEndpoints) {
            this.actuatorExposedEndpoints = actuatorExposedEndpoints;
            return this;
        }

        /** Sets the base path for actuator endpoints. */
        public @NotNull Builder withActuatorBasePath(@NotNull String actuatorBasePath) {
            this.actuatorBasePath = actuatorBasePath;
            return this;
        }

        /** Sets a separate port for management endpoints, or {@code -1} to use the server port. */
        public @NotNull Builder withManagementPort(int managementPort) {
            this.managementPort = managementPort;
            return this;
        }

        /** Sets the visibility level for health endpoint details. */
        public @NotNull Builder withHealthShowDetails(@NotNull HealthDetailsVisibility healthShowDetails) {
            this.healthShowDetails = healthShowDetails;
            return this;
        }

        /**
         * Validates builder flags and constructs an immutable {@link ServerConfig}.
         *
         * @return a fully constructed server configuration
         */
        @Override
        public @NotNull ServerConfig build() {
            Reflection.validateFlags(this);

            return new ServerConfig(
                this.port,
                this.address,
                this.contextPath,
                this.maxThreads,
                this.minSpareThreads,
                this.virtualThreadsEnabled,
                this.acceptCount,
                this.maxConnections,
                this.connectionTimeout,
                this.keepAliveTimeout,
                this.maxKeepAliveRequests,
                this.compressionEnabled,
                this.compressionMinSize,
                this.compressionMimeTypes,
                this.http2Enabled,
                this.maxRequestHeaderSize,
                this.maxFormPostSize,
                this.multipartEnabled,
                this.multipartMaxFileSize,
                this.multipartMaxRequestSize,
                this.shutdownMode,
                this.shutdownTimeout,
                this.forwardHeadersStrategy,
                this.applicationName,
                this.rootLogLevel,
                this.apiKeyAuthEnabled,
                this.springdocEnabled,
                this.actuatorEnabled,
                this.actuatorExposedEndpoints,
                this.actuatorBasePath,
                this.managementPort,
                this.healthShowDetails
            );
        }

    }

    /**
     * Maps to the {@code server.shutdown} Spring Boot property.
     */
    public enum ShutdownMode {

        IMMEDIATE,
        GRACEFUL

    }

    /**
     * Maps to the {@code server.forward-headers-strategy} Spring Boot property.
     */
    public enum ForwardHeadersStrategy {

        NONE,
        NATIVE,
        FRAMEWORK

    }

    /**
     * Maps to the {@code management.endpoint.health.show-details} Spring Boot property.
     */
    @Getter
    @RequiredArgsConstructor
    public enum HealthDetailsVisibility {

        NEVER("never"),
        WHEN_AUTHORIZED("when-authorized"),
        ALWAYS("always");

        private final @NotNull String value;

    }

    /**
     * Typed size representation for Spring Boot memory-based properties.
     *
     * <p>Provides factory methods for common units and converts to the string
     * format expected by Spring Boot (e.g., {@code "8KB"}, {@code "2MB"}).</p>
     */
    @Getter
    @RequiredArgsConstructor(access = AccessLevel.PRIVATE)
    public static final class MemorySize {

        private final long value;
        private final @NotNull MemoryUnit unit;

        /**
         * Creates a size in bytes.
         *
         * @param value the number of bytes
         * @return a new memory size
         */
        public static @NotNull MemorySize bytes(long value) {
            return new MemorySize(value, MemoryUnit.BYTES);
        }

        /**
         * Creates a size in kilobytes.
         *
         * @param value the number of kilobytes
         * @return a new memory size
         */
        public static @NotNull MemorySize kilobytes(long value) {
            return new MemorySize(value, MemoryUnit.KB);
        }

        /**
         * Creates a size in megabytes.
         *
         * @param value the number of megabytes
         * @return a new memory size
         */
        public static @NotNull MemorySize megabytes(long value) {
            return new MemorySize(value, MemoryUnit.MB);
        }

        /**
         * Creates a size in gigabytes.
         *
         * @param value the number of gigabytes
         * @return a new memory size
         */
        public static @NotNull MemorySize gigabytes(long value) {
            return new MemorySize(value, MemoryUnit.GB);
        }

        /**
         * Converts this size to its Spring Boot property string representation.
         *
         * @return a string such as {@code "512"}, {@code "8KB"}, or {@code "2MB"}
         */
        public @NotNull String toPropertyValue() {
            return this.value + this.unit.getSuffix();
        }

        @Override
        public @NotNull String toString() {
            return toPropertyValue();
        }

    }

    /**
     * Unit of measurement for memory sizes.
     */
    @Getter
    @RequiredArgsConstructor
    public enum MemoryUnit {

        BYTES(""),
        KB("KB"),
        MB("MB"),
        GB("GB");

        private final @NotNull String suffix;

    }

}
