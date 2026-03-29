# Server API

Reusable Spring Boot server framework library for the
[SkyBlock Simplified](https://github.com/SkyBlock-Simplified) ecosystem,
providing URL-path-based API versioning, API key authentication with
hierarchical roles, global error handling with Cloudflare-style HTML error
pages, and programmatic server configuration.

## Table of Contents

- [Features](#features)
- [Getting Started](#getting-started)
  - [Prerequisites](#prerequisites)
  - [Installation](#installation)
- [Quick Example](#quick-example)
- [Architecture](#architecture)
  - [Server Configuration](#server-configuration)
  - [API Versioning](#api-versioning)
  - [Security](#security)
  - [Error Handling](#error-handling)
  - [Exception Hierarchy](#exception-hierarchy)
- [Project Structure](#project-structure)
- [Dependencies](#dependencies)
- [Contributing](#contributing)
- [License](#license)

## Features

- **API versioning** - URL-path-based versioning (`/v1/endpoint`,
  `/v2/endpoint`) via `@ApiVersion` annotation, with automatic path prefix
  registration, version condition matching, defense-in-depth validation, and
  helpful error messages when a version is missing or invalid
- **API key authentication** - Header-based (`X-API-Key`) authentication via
  `@ApiKeyProtected` annotation, with hierarchical role permissions
  (`DEVELOPER` > `SUPER_ADMIN` > ... > `LIMITED_ACCESS`), sliding window rate
  limiting, and any-match permission semantics
- **Error handling** - Global `@RestControllerAdvice` with content negotiation:
  browsers (`text/html`) receive Cloudflare-style HTML error pages with
  client/server/API status indicators, while API clients receive JSON error
  responses. Overridable `buildErrorBody()` for custom JSON formats
- **Server configuration** - Immutable `ServerConfig` with builder-pattern
  construction (`ServerConfig.builder()`) or production-tuned preset
  (`ServerConfig.optimized()`), covering Tomcat thread pools, compression,
  HTTP/2, multipart uploads, graceful shutdown, and SpringDoc toggles
- **Security headers** - Automatic `X-Content-Type-Options: nosniff` on every
  response via `SecurityHeaderInterceptor`
- **Conditional activation** - API key security activates only when
  `api.key.authentication.enabled=true`, allowing easy toggling per environment

## Getting Started

### Prerequisites

| Requirement | Version | Notes |
|-------------|---------|-------|
| [JDK](https://adoptium.net/) | **21+** | Required |
| [Gradle](https://gradle.org/) | **9.4+** | Included via wrapper (`./gradlew`) |

### Installation

This module depends on the [api](../api) module, declared as a Maven
coordinate (`dev.sbs:api:0.1.0`). For local development you can clone both
repositories side by side and use a Gradle composite build:

```bash
git clone https://github.com/SkyBlock-Simplified/api.git
git clone https://github.com/SkyBlock-Simplified/server-api.git
cd server-api
```

Build the library:

```bash
./gradlew build
```

Run tests:

```bash
./gradlew test
```

<details>
<summary>Using as a dependency in another Gradle project</summary>

**JitPack** (for snapshot builds):

```kotlin
repositories {
    maven(url = "https://jitpack.io")
}

dependencies {
    implementation("com.github.SkyBlock-Simplified:server-api:master-SNAPSHOT")
}
```

**Composite build** (for local development):

```kotlin
// settings.gradle.kts
includeBuild("../server-api")

// build.gradle.kts
dependencies {
    implementation("dev.sbs:server-api:0.1.0")
}
```

</details>

## Quick Example

```java
@SpringBootApplication(scanBasePackages = { "com.example.myapp", "dev.sbs.serverapi" })
public class MyApplication implements WebMvcConfigurer {

    public static void main(String[] args) {
        ServerConfig config = ServerConfig.builder()
            .withPort(8080)
            .withApplicationName("my-app")
            .withCompressionEnabled(true)
            .withHttp2Enabled(true)
            .build();

        SpringApplication app = new SpringApplication(MyApplication.class);
        app.setDefaultProperties(config.toProperties());
        app.run(args);
    }
}

@ApiVersion(1)
@RestController
@RequestMapping("/items")
public class ItemController {

    // GET /v1/items — public endpoint
    @GetMapping
    public List<Item> listItems() {
        return itemService.findAll();
    }

    // GET /v1/items/{id} — requires API key with ADMIN or higher role
    @ApiKeyProtected(requiredPermissions = ApiKeyRole.ADMIN)
    @GetMapping("/{id}")
    public Item getItem(@PathVariable String id) {
        return itemService.findById(id);
    }
}
```

> [!IMPORTANT]
> Consumers must include `dev.sbs.serverapi` in their `scanBasePackages` for
> Spring to discover the configuration beans, interceptors, and error handler.

## Architecture

### Server Configuration

`ServerConfig` is an immutable configuration class built via its nested
`Builder` (following the `ClassBuilder<T>` pattern with `@BuildFlag`
validation). It converts all fields to a Spring Boot property map via
`toProperties()`:

| Factory | Use Case |
|---------|----------|
| `ServerConfig.builder()` | Full control over every setting |
| `ServerConfig.optimized()` | Production-tuned preset (400 threads, HTTP/2, compression, graceful shutdown, virtual threads) |

Configuration covers: server port and bind address, Tomcat thread pool sizing,
connection limits and timeouts, response compression, HTTP/2, multipart
uploads, graceful shutdown, forward headers strategy, logging level, API key
auth toggle, and SpringDoc toggle.

### API Versioning

URL-path-based versioning via the `@ApiVersion` annotation:

| Class | Role |
|-------|------|
| `@ApiVersion` | TYPE and METHOD annotation specifying supported version numbers |
| `ApiVersionCondition` | Custom `RequestCondition` for version combine/compare |
| `ApiVersionHandlerMapping` | Prepends `/v{N}` path prefixes to annotated handlers |
| `ApiVersionInterceptor` | Defense-in-depth version validation on resolved handlers |
| `VersionRegistryService` | Precomputed index mapping base paths to available version numbers |
| `ApiVersionConfig` | Bean registration for versioning components |

When a request hits an unversioned path that has versioned handlers, the error
controller detects this and throws a `MissingVersionException` with the
available versions. When a request uses an invalid version number, an
`InvalidVersionException` is thrown listing the valid versions.

### Security

Header-based API key authentication via the `@ApiKeyProtected` annotation:

| Class | Role |
|-------|------|
| `@ApiKeyProtected` | TYPE and METHOD annotation requiring API key authentication |
| `ApiKeyRole` | Enum defining hierarchical access roles (`DEVELOPER` through `LIMITED_ACCESS`) |
| `ApiKey` | API key record with roles, rate limit config, and sliding window counter state |
| `ApiKeyAuthenticationInterceptor` | `HandlerInterceptor` enforcing auth, rate limits, and permissions |
| `ApiKeyService` | Key storage, validation, rate limiting, and permission resolution |
| `ApiKeyRoleHierarchy` | Expands assigned roles into the full reachable set via the hierarchy |
| `SecurityHeaderInterceptor` | Sets `X-Content-Type-Options: nosniff` on every response |
| `ApiKeyConfig` | `@ConditionalOnProperty(name = "api.key.authentication.enabled", havingValue = "true")` |

> [!NOTE]
> The role hierarchy is defined by declaration order in `ApiKeyRole` - earlier entries inherit all permissions of later entries:
> ```
> DEVELOPER > SUPER_ADMIN > ADMIN > SUPER_MODERATOR > MODERATOR > SUPER_USER > USER > LIMITED_ACCESS
> ```

### Error Handling

`ErrorController` is a global `@RestControllerAdvice` extending
`ResponseEntityExceptionHandler`. It performs content negotiation via the
`Accept` header:

| Accept Header | Response |
|---------------|----------|
| `text/html` | Cloudflare-style HTML error page rendered by `ErrorPageRenderer` |
| Other (e.g. `application/json`) | JSON error body via overridable `buildErrorBody()` |

> [!TIP]
> Override `ErrorController.buildErrorBody()` in a subclass to return a
> project-specific JSON error response type instead of the default map.

The HTML error pages show three status columns (Client, Server, API) with
error/ok indicators, the error code, a human-readable description, suggested
actions, request route, client IP, and ray ID. All user-controlled values are
XSS-escaped.

Handled exception types:

| Exception | Source |
|-----------|--------|
| `ServerException` | Server-side constraint violations (security, versioning) |
| `ApiException` | Upstream API errors (proxied from Feign clients) |
| `NoResourceFoundException` | 404s with version violation detection |
| `Exception` | Catch-all for unexpected errors |

### Exception Hierarchy

```
ServerException (HttpStatus + message)
├── SecurityException (security/)
│   ├── MissingApiKeyException          — 401 Unauthorized
│   ├── InvalidApiKeyException          — 401 Unauthorized
│   ├── RateLimitExceededException      — 429 Too Many Requests
│   └── InsufficientPermissionException — 403 Forbidden
└── VersionException (version/)
    ├── InvalidVersionException         — 400 Bad Request
    └── MissingVersionException         — 400 Bad Request
```

## Project Structure

```
server-api/
├── src/main/java/dev/sbs/serverapi/
│   ├── config/
│   │   └── ServerConfig.java          # Immutable server config with Builder,
│   │                                  # MemorySize, ShutdownMode, ForwardHeadersStrategy
│   ├── error/
│   │   ├── ErrorController.java       # Global @RestControllerAdvice with content negotiation
│   │   └── ErrorPageRenderer.java     # Cloudflare-style HTML error page renderer
│   ├── exception/
│   │   └── ServerException.java       # Root exception with embedded HttpStatus
│   ├── security/
│   │   ├── ApiKey.java                # API key with roles, rate limit config, sliding window
│   │   ├── ApiKeyAuthenticationInterceptor.java  # HandlerInterceptor for auth enforcement
│   │   ├── ApiKeyConfig.java          # Conditional bean registration
│   │   ├── ApiKeyProtected.java       # @ApiKeyProtected annotation
│   │   ├── ApiKeyRole.java            # Hierarchical role enum
│   │   ├── ApiKeyRoleHierarchy.java   # Role expansion service
│   │   ├── ApiKeyService.java         # Key storage, validation, rate limiting
│   │   ├── SecurityHeaderInterceptor.java  # X-Content-Type-Options header
│   │   └── exception/
│   │       ├── SecurityException.java
│   │       ├── MissingApiKeyException.java
│   │       ├── InvalidApiKeyException.java
│   │       ├── RateLimitExceededException.java
│   │       └── InsufficientPermissionException.java
│   └── version/
│       ├── ApiVersion.java            # @ApiVersion annotation
│       ├── ApiVersionCondition.java   # RequestCondition for version matching
│       ├── ApiVersionConfig.java      # Bean registration
│       ├── ApiVersionHandlerMapping.java  # Custom handler mapping with /v{N} prefixes
│       ├── ApiVersionInterceptor.java # Defense-in-depth version validation
│       ├── VersionRegistryService.java  # Path-to-version index
│       └── exception/
│           ├── VersionException.java
│           ├── InvalidVersionException.java
│           └── MissingVersionException.java
├── src/main/resources/error/
│   ├── error-page.css                 # Minified Cloudflare-style CSS (MIT)
│   └── error-page.html                # HTML template with {{PLACEHOLDER}} tokens
├── src/test/java/dev/sbs/serverapi/
│   ├── TestServer.java                # Runnable test application
│   └── controller/
│       ├── TestApiKeyController.java  # API key auth test endpoints
│       └── TestVersionController.java # API versioning test endpoints
├── build.gradle.kts
└── gradle/libs.versions.toml          # Version catalog
```

## Dependencies

| Library | Version | Purpose |
|---------|---------|---------|
| Spring Boot Starter Web | 3.4.5 | Spring MVC, embedded Tomcat, auto-configuration |
| SimplifiedApi | 0.1.0 | Core utility library (collections, builders, reflection, HTTP client) |
| Lombok | 1.18.36 | Boilerplate reduction |
| simplified-annotations | 1.0.4 | Custom annotation processing |
| JUnit 5 | 5.11.4 | Testing |
| Hamcrest | 2.2 | Test matchers |
| Spring Boot Starter Test | 3.4.5 | Integration testing with `@SpringBootTest` |

> [!NOTE]
> Consumers that depend on `server-api` automatically receive both `api:0.1.0`
> and `spring-boot-starter-web` via `api()` dependencies - no need to declare
> them separately.

## Testing the Framework

The test source set includes a self-contained `TestServer` and example
controllers for exercising the framework without external dependencies.

### Running the TestServer

Run `dev.sbs.serverapi.TestServer.main()` from your IDE to start a lightweight
server on port 8080, then exercise the framework features:

```bash
# API versioning
curl http://localhost:8080/v1/hello
curl http://localhost:8080/v2/hello
curl http://localhost:8080/v3/hello
curl http://localhost:8080/default

# API key authentication (requires X-API-Key header)
curl -H "X-API-Key: dev-key-777" http://localhost:8080/api/basic
curl -H "X-API-Key: dev-key-777" http://localhost:8080/api/admin-panel
curl -H "X-API-Key: dev-key-777" http://localhost:8080/api/restart -X POST

# Error handling
curl http://localhost:8080/v99/hello        # Invalid version
curl http://localhost:8080/nonexistent      # 404
curl http://localhost:8080/hello            # Missing version
```

### Test API Keys

> [!IMPORTANT]
> The `ApiKeyService` currently ships with hardcoded test keys. Replace or
> extend it to load keys from a database or external service for production use.


| Key | Roles | Rate Limit |
|---|---|---|
| `dev-key-777` | `DEVELOPER` (inherits all) | 100 req/60s |
| `mod-key-555` | `MODERATOR` | 50 req/60s |
| `service-key-123` | `USER`, `LIMITED_ACCESS` | 10 req/60s |

## Contributing

See [CONTRIBUTING.md](CONTRIBUTING.md) for development setup, code style
guidelines, and how to submit a pull request.

## License

This project is licensed under the **Apache License 2.0**.
