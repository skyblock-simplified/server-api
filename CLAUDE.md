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

`server-api` is a reusable Spring Boot server framework library on **Spring Boot 4.0 / Spring Framework 7.0 / Spring Security 7.0**. It provides Spring 7's native API versioning, Spring Security-backed API key authentication, rate limiting (Bucket4j), error handling, and server configuration as a `java-library` that other Spring Boot applications consume. Follows the same pattern as `discord-api` (framework) vs `simplified-bot` (implementation).

### Package: `dev.sbs.serverapi`

**Exports:** Spring Boot starters (`web`, `actuator`, `security`), `bucket4j-core`, `gson`, and the `simplified-dev` `client` and `gson-extras` libraries via `api()` dependencies. Consumers get all of these transitively, including `@PreAuthorize`, `Authentication`, the `@RequestMapping(version=...)` attribute, and the Bucket4j `Bucket` API.

### Package Structure

**`config/`** - Server-wide configuration:
- `ServerConfig` - Immutable configuration class following the `ClassBuilder` pattern. Inner `Builder` with `@BuildFlag` validation. Static factories: `builder()` for full control, `optimized()` for a production-tuned preset. `toProperties()` converts fields to a `ConcurrentMap<String, Object>` for `SpringApplication.setDefaultProperties()`. Includes `springdocEnabled` toggle controlling SpringDoc/Scalar properties.
- `ServerWebConfig` - Framework-level `WebMvcConfigurer` providing the `GsonHttpMessageConverter` (placed ahead of Jackson), the no-op `ErrorController` bean that displaces Spring Boot's `BasicErrorController`, and the shared `ErrorResponseWriter` bean. Uses a consumer-provided `Gson` `@Bean` if available, otherwise falls back to a default `Gson` created from `GsonSettings.defaults()`. Security response headers are set by Spring Security's `HeadersConfigurer` in `ApiKeySecurityConfig` rather than here.
- `ApiVersionWebConfig` - `WebMvcConfigurer` wiring Spring 7's path-segment API versioning via two predicates: `usePathSegment(0, requestPathPredicate)` gates version extraction to URLs matching `/v<digits>/...` (so non-matching paths bypass extraction); `addPathPrefix("/{version}", classPredicate)` injects the `/{version}` prefix on any controller whose methods declare `version=` (so handlers don't repeat `/v1/` in their `path=`). The default `SemanticApiVersionParser` strips the `v` prefix and parses semver, so `@GetMapping(path = "/hello", version = "1")` is reachable at `/v1/hello`. **Constraint:** `addPathPrefix` is class-level, so don't mix versioned and unversioned methods on the same controller.

**`error/`** - Global error handling and HTML error page rendering:
- `ErrorController` - Global `@RestControllerAdvice` extending `ResponseEntityExceptionHandler`. Delegates content-negotiated rendering to `ErrorResponseWriter`. Includes explicit handlers for `AccessDeniedException` and `AuthenticationException` thrown by `@PreAuthorize` from inside controllers (these unwind to dispatcher servlet exception handling before Spring Security's `ExceptionTranslationFilter` sees them, so we route them to the same writer used by the entry point and access-denied handler). Mirrors the filter's "anonymous becomes 401" behavior. Also handles `MissingApiVersionException` and `InvalidApiVersionException` thrown by Spring 7's versioning machinery, mapping them to 400.
- `ErrorResponseWriter` - Shared utility for content-negotiated error responses (HTML vs JSON). Used by `ErrorController` (returns `ResponseEntity`) and by Spring Security's `AuthenticationEntryPoint` / `AccessDeniedHandler` (writes directly to the response). Holds the `Gson` instance for JSON serialization.
- `ErrorPageRenderer` - Non-instantiable utility class rendering Cloudflare-style HTML error pages. Contains `Placeholder` enum for named `{{TOKEN}}` substitution with XSS escaping, and `ErrorSource` enum (`CLIENT`, `SERVER`, `API`).

**`security/`** - Spring Security-backed API key authentication and authorization:
- `ApiKey` - Authenticated principal carrying the key string, assigned `ApiKeyRole`s, and rate-limit configuration (`maxRequests`, `windowInSeconds`). `getAuthorities()` derives `SimpleGrantedAuthority("ROLE_" + name)` from the role set.
- `ApiKeyRole` - Enum of hierarchical access roles. Declaration order defines the hierarchy (earlier constants inherit later constants' authorities). `getAuthority()` returns `"ROLE_" + name()`.
- `ApiKeyStore` - SPI interface consumers implement to supply `ApiKey` instances. Single method `findByKey(String)`. Implementations are free to return fresh instances on each lookup since rate-limit state is held externally in `RateLimitFilter`.
- `InMemoryApiKeyStore` - Public reference `ApiKeyStore` implementation backed by a concurrent map. Suitable for tests, local development, and stopgap production wiring before a persistent store is available.
- `ApiKeyAuthenticationToken` - `AbstractAuthenticationToken` carrying an `ApiKey` as principal. Two-stage construction: `unauthenticated(String)` + `authenticated(ApiKey, Collection<GrantedAuthority>)`.
- `ApiKeyAuthenticationFilter` - `OncePerRequestFilter` reading the `X-API-Key` header and delegating to the `AuthenticationManager`. On failure invokes the entry point directly so the response is rendered immediately. Added before `UsernamePasswordAuthenticationFilter` in the chain.
- `ApiKeyAuthenticationProvider` - `AuthenticationProvider` resolving an unauthenticated token via `ApiKeyStore.findByKey()`. Throws `BadCredentialsException` for unknown keys.
- `ApiKeyAuthenticationEntryPoint` - `AuthenticationEntryPoint` rendering content-negotiated 401 responses via `ErrorResponseWriter`.
- `ApiKeyAccessDeniedHandler` - `AccessDeniedHandler` rendering content-negotiated 403 responses via `ErrorResponseWriter`.
- `ApiKeyRateLimitFilter` - `OncePerRequestFilter` consulting Bucket4j `Bucket`s keyed by `ApiKey.getKeyValue()`. Capacity and refill come from `ApiKey.getMaxRequests()` and `ApiKey.getWindowInSeconds()`. Anonymous requests pass through. Inserted after `ApiKeyAuthenticationFilter` in the chain. On overflow it writes a 429 response directly via `ErrorResponseWriter` rather than throwing - filter exceptions don't reach `@RestControllerAdvice`, so direct rendering keeps the response content-negotiated.
- `ApiKeySecurityConfig` - `@EnableWebSecurity` + `@EnableMethodSecurity` + `@ConditionalOnProperty(name = "api.key.authentication.enabled", havingValue = "true")`. Declares the `SecurityFilterChain` (stateless, CSRF-disabled, `permitAll` at the chain level so `@PreAuthorize` is the gating mechanism), the `RoleHierarchy` (built from `ApiKeyRole` declaration order), the `MethodSecurityExpressionHandler` wired with the hierarchy, the `AuthenticationManager`, the `ApiKeyRateLimitFilter` bean, and the security headers (`X-Content-Type-Options`, HSTS, `X-Frame-Options: DENY`, `Referrer-Policy: no-referrer`, X-XSS-Protection). **Intentionally declares no default `ApiKeyStore` bean** - consumers must supply one, otherwise startup fails fast with `NoSuchBeanDefinitionException`. A silent empty fallback would 401 every request and be extremely confusing to debug.
- `PermitAllSecurityConfig` - Fallback `@EnableWebSecurity` config active when `api.key.authentication.enabled` is missing or `false`. Permits all requests so Spring Boot's default `SecurityAutoConfiguration` does not install a basic-auth chain with a generated password (which is rarely desired).
- `security/openapi/` - SpringDoc customizers (`ApiKeyOpenApiConfig`, `ApiKeySecurityCustomizer`, `ApiKeyOperationCustomizer`). The operation customizer scans `@PreAuthorize` annotations and infers the qualifying `ApiKeyRole` set from `hasRole`/`hasAnyRole`/`hasAuthority` expressions for documentation purposes.

**API versioning** is provided by Spring Framework 7 directly. There is no `version/` package - declare the version on each handler with `@RequestMapping(version = "1")` (or `@GetMapping(path = ..., version = "1")`) and the framework routes accordingly. Configuration lives in `config/ApiVersionWebConfig`.

**`src/main/resources/error/`** - HTML error page resources:
- `error-page.css` - Minified CSS from [donlon/cloudflare-error-page](https://github.com/donlon/cloudflare-error-page) (MIT license).
- `error-page.html` - HTML template with named `{{PLACEHOLDER}}` tokens.

### Test Source (`src/test/`)

**`TestServer`** - Minimal `@SpringBootApplication` for testing the framework. Boots a lightweight server with API versioning, API key authentication, error handling, and the test controllers. Uses `ServerConfig.builder()` defaults with SpringDoc disabled. Run `main()` from the IDE to start on port 8080.

**`controller/`** - Test controllers exercising framework features:
- `TestApiKeyController` - Endpoints under `/api/` demonstrating `@PreAuthorize` with role requirements (`ADMIN`, `DEVELOPER`, `USER`) resolved through the `ApiKeyRole` hierarchy.
- `TestVersionController` - Endpoints demonstrating Spring 7's `@RequestMapping(version=...)` with multiple versions (`/v1/hello`, `/v2/hello`, `/v3/hello`, `/v1/data`, `/v2/data`). Handlers declare `version="N"` and `path="/hello"` only; the `/v{version}` prefix is injected automatically because `ApiVersionWebConfig`'s class-level predicate detects that the controller has version-bearing methods.
- `TestUnversionedController` - The `/default` endpoint, on its own controller because the path-prefix mechanism is class-level (a single mixed controller would prefix the unversioned method too).

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

When `api.key.authentication.enabled=true` (the default), consumers **must** provide an `ApiKeyStore` bean. For quick bring-up, seed an `InMemoryApiKeyStore`:

```java
@Bean
public ApiKeyStore apiKeyStore() {
    return new InMemoryApiKeyStore()
        .put(new ApiKey("my-key", Concurrent.newSet(ApiKeyRole.USER), 100, 60));
}
```

Protect controllers with `@PreAuthorize`:

```java
@RestController
@PreAuthorize("isAuthenticated()")           // class-level: any valid key
@RequestMapping("/api")
public class MyController {

    @GetMapping("/admin")
    @PreAuthorize("hasRole('ADMIN')")        // method-level: ADMIN-or-higher
    public String adminOnly() { ... }
}
```

### Configuration

- `api.key.authentication.enabled` - Toggles API key security. When `true`, `ApiKeySecurityConfig` activates. When `false` (or missing), `PermitAllSecurityConfig` activates with a permit-all chain. Default `true` in `ServerConfig`.
- `springdocEnabled` - Toggle in `ServerConfig` controlling SpringDoc property output.
- `ServerConfig.builder()` / `ServerConfig.optimized()` for programmatic server tuning.
