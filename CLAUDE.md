# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

See the root [`CLAUDE.md`](../CLAUDE.md) for cross-cutting patterns.

## Build & Test

```bash
# From repo root
./gradlew :server-api:build    # Build
./gradlew :server-api:test     # Run all tests
```

## Module Overview

`server-api` is a reusable Spring Boot server framework library. It provides API versioning, API key authentication, error handling, and server configuration as a `java-library` that other Spring Boot applications consume. Follows the same pattern as `discord-api` (framework) vs `simplified-bot` (implementation).

### Package: `dev.sbs.serverapi`

**Exports:** `api:0.1.0` and `spring-boot-starter-web` via `api()` dependencies. Consumers get Spring Boot and the core API library transitively.

### Package Structure

**`config/`** - Server-wide configuration:
- `ServerConfig` - Immutable configuration class following the `ClassBuilder` pattern. Inner `Builder` with `@BuildFlag` validation. Static factories: `builder()` for full control, `optimized()` for a production-tuned preset. `toProperties()` converts fields to a `ConcurrentMap<String, Object>` for `SpringApplication.setDefaultProperties()`. Includes `springdocEnabled` toggle controlling SpringDoc/Scalar properties.
- `ServerWebConfig` - Framework-level `WebMvcConfigurer` that auto-registers `SecurityHeaderInterceptor` as a `MappedInterceptor` and configures HTTP message converters (`StringHttpMessageConverter` first for HTML error pages, then `GsonHttpMessageConverter` for JSON). Uses a consumer-provided `Gson` `@Bean` if available, otherwise falls back to `SimplifiedApi.getGson()`.

**`error/`** - Global error handling and HTML error page rendering:
- `ErrorController` - Global `@RestControllerAdvice` extending `ResponseEntityExceptionHandler`. Performs content negotiation via the `Accept` header: browsers (`text/html`) receive Cloudflare-style HTML error pages, while API clients receive JSON. Overridable `buildErrorBody()` method allows consumers to customize JSON error response format. Overrides `handleNoResourceFoundException` to detect version violations on 404s.
- `ErrorPageRenderer` - Non-instantiable utility class rendering Cloudflare-style HTML error pages. Contains `Placeholder` enum for named `{{TOKEN}}` substitution with XSS escaping, and `ErrorSource` enum (`CLIENT`, `SERVER`, `API`).

**`exception/`** - Server exception hierarchy:
- `ServerException` - Non-final root exception extending `RuntimeException` with an embedded `HttpStatus` field. Five constructors with `HttpStatus` as first parameter.

**`security/`** - API key authentication, authorization, and rate limiting:
- `SecurityHeaderInterceptor` - Sets `X-Content-Type-Options: nosniff` on every response. Auto-registered by `ServerWebConfig`.
- `ApiKeyRole` - Enum defining hierarchical access roles. Precomputes an immutable hierarchy map.
- `ApiKey` - API key with roles, rate limit config, and sliding window counter state.
- `ApiKeyProtected` - TYPE and METHOD level annotation for API key requirements.
- `ApiKeyAuthenticationInterceptor` - `HandlerInterceptor` for auth/rate/perms enforcement.
- `ApiKeyService` - Key storage, validation, rate limiting, and permission resolution.
- `ApiKeyRoleHierarchy` - Expands assigned roles into the full reachable set.
- `ApiKeyConfig` - `@ConditionalOnProperty(name = "api.key.authentication.enabled", havingValue = "true")`.
- `security/exception/` - `SecurityException` base and four leaf exceptions with internalized messages.

**`version/`** - URL-path-based API versioning (`/v1/endpoint`, `/v2/endpoint`):
- `ApiVersion` - TYPE and METHOD level annotation specifying supported version numbers.
- `ApiVersionCondition` - Custom `RequestCondition` for combine/compare.
- `ApiVersionHandlerMapping` - Custom `RequestMappingHandlerMapping` that prepends `/v{N}` path prefixes.
- `ApiVersionInterceptor` - Defense-in-depth version validation on resolved handlers.
- `VersionRegistryService` - Precomputed index mapping base paths to available version numbers.
- `ApiVersionConfig` - Bean registration for versioning components.
- `version/exception/` - `VersionException` base and two leaf exceptions with internalized format strings.

**`src/main/resources/error/`** - HTML error page resources:
- `error-page.css` - Minified CSS from [donlon/cloudflare-error-page](https://github.com/donlon/cloudflare-error-page) (MIT license).
- `error-page.html` - HTML template with named `{{PLACEHOLDER}}` tokens.

### Test Source (`src/test/`)

**`TestServer`** - Minimal `@SpringBootApplication` for testing the framework. Boots a lightweight server with API versioning, API key authentication, error handling, and the test controllers. Uses `ServerConfig.builder()` defaults with SpringDoc disabled. Run `main()` from the IDE to start on port 8080.

**`controller/`** - Test controllers exercising framework features:
- `TestApiKeyController` - Endpoints under `/api/` demonstrating `@ApiKeyProtected` with `ApiKeyRole` requirements (`ADMIN`, `DEVELOPER`, `USER`).
- `TestVersionController` - Endpoints demonstrating `@ApiVersion` with multiple versions (`/v1/hello`, `/v2/hello`, `/v3/hello`, `/v1/data`, `/v2/data`) and an unversioned `/default` endpoint.

### Consumer Usage

Consumers must scan the `dev.sbs.serverapi` package for Spring to pick up configuration beans:

```java
@SpringBootApplication(scanBasePackages = { "com.example.myapp", "dev.sbs.serverapi" })
public class MyApplication { }
```

To customize the `Gson` instance used by the framework's message converters, define a `@Bean`:

```java
@Bean
public Gson gson() {
    return myCustomGson;
}
```

### Configuration

- `api.key.authentication.enabled` - Toggles API key security (default `true` in `ServerConfig`)
- `springdocEnabled` - Toggle in `ServerConfig` controlling SpringDoc property output
- `ServerConfig.builder()` / `ServerConfig.optimized()` for programmatic server tuning
