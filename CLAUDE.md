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
- `ServerWebConfig` - Framework-level `WebMvcConfigurer` that auto-registers `SecurityHeaderInterceptor` as a `MappedInterceptor` and configures HTTP message converters (`StringHttpMessageConverter` first for HTML error pages, then `GsonHttpMessageConverter` for JSON). Uses a consumer-provided `Gson` `@Bean` if available, otherwise falls back to a default `Gson` created from `GsonSettings.defaults()`.

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
- `ApiKeyStore` - SPI interface consumers implement to supply {@link ApiKey} instances to the framework. Single method `findByKey(String)`. Documents an identity contract: the same {@code ApiKey} reference must be returned across lookups so sliding-window rate-limit state on the instance survives.
- `InMemoryApiKeyStore` - Public reference `ApiKeyStore` implementation backed by a concurrent map. Suitable for tests, local development, and stopgap production wiring before a persistent store is available.
- `ApiKeyService` - Validation, rate limiting, and permission resolution; delegates all key lookups to an injected `ApiKeyStore`. Owns no keys itself.
- `ApiKeyRoleHierarchy` - Expands assigned roles into the full reachable set.
- `ApiKeyConfig` - `@ConditionalOnProperty(name = "api.key.authentication.enabled", havingValue = "true")`. Declares the bean definitions (`ApiKeyRoleHierarchy`, `ApiKeyService`, `ApiKeyAuthenticationInterceptor`). **Intentionally declares no default `ApiKeyStore` bean** - consumers must supply one, otherwise startup fails fast with `NoSuchBeanDefinitionException`. A silent empty fallback would 401 every request and be extremely confusing to debug.
- `ApiKeyWebMvcConfig` - `WebMvcConfigurer` that registers `ApiKeyAuthenticationInterceptor` on `/**`. Split out from `ApiKeyConfig` so the interceptor is constructor-injected as a managed bean rather than instantiated via `@Configuration` self-invocation.
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

When `api.key.authentication.enabled=true` (the default), consumers **must** provide an `ApiKeyStore` bean. For quick bring-up, seed an `InMemoryApiKeyStore`:

```java
@Bean
public ApiKeyStore apiKeyStore() {
    return new InMemoryApiKeyStore()
        .put(new ApiKey("my-key", Concurrent.newSet(ApiKeyRole.USER), 100, 60));
}
```

For production, implement `ApiKeyStore` against a database (e.g., wrapping a `JpaRepository`) with an internal identity map so sliding-window rate-limit state on `ApiKey` survives repeated lookups. See the identity contract in `ApiKeyStore`'s Javadoc.

### Configuration

- `api.key.authentication.enabled` - Toggles API key security (default `true` in `ServerConfig`)
- `springdocEnabled` - Toggle in `ServerConfig` controlling SpringDoc property output
- `ServerConfig.builder()` / `ServerConfig.optimized()` for programmatic server tuning

<!-- gitnexus:start -->
# GitNexus — Code Intelligence

This project is indexed by GitNexus as **server-api** (325 symbols, 856 relationships, 26 execution flows). Use the GitNexus MCP tools to understand code, assess impact, and navigate safely.

> If any GitNexus tool warns the index is stale, run `npx gitnexus analyze` in terminal first.

## Always Do

- **MUST run impact analysis before editing any symbol.** Before modifying a function, class, or method, run `gitnexus_impact({target: "symbolName", direction: "upstream"})` and report the blast radius (direct callers, affected processes, risk level) to the user.
- **MUST run `gitnexus_detect_changes()` before committing** to verify your changes only affect expected symbols and execution flows.
- **MUST warn the user** if impact analysis returns HIGH or CRITICAL risk before proceeding with edits.
- When exploring unfamiliar code, use `gitnexus_query({query: "concept"})` to find execution flows instead of grepping. It returns process-grouped results ranked by relevance.
- When you need full context on a specific symbol — callers, callees, which execution flows it participates in — use `gitnexus_context({name: "symbolName"})`.

## When Debugging

1. `gitnexus_query({query: "<error or symptom>"})` — find execution flows related to the issue
2. `gitnexus_context({name: "<suspect function>"})` — see all callers, callees, and process participation
3. `READ gitnexus://repo/server-api/process/{processName}` — trace the full execution flow step by step
4. For regressions: `gitnexus_detect_changes({scope: "compare", base_ref: "main"})` — see what your branch changed

## When Refactoring

- **Renaming**: MUST use `gitnexus_rename({symbol_name: "old", new_name: "new", dry_run: true})` first. Review the preview — graph edits are safe, text_search edits need manual review. Then run with `dry_run: false`.
- **Extracting/Splitting**: MUST run `gitnexus_context({name: "target"})` to see all incoming/outgoing refs, then `gitnexus_impact({target: "target", direction: "upstream"})` to find all external callers before moving code.
- After any refactor: run `gitnexus_detect_changes({scope: "all"})` to verify only expected files changed.

## Never Do

- NEVER edit a function, class, or method without first running `gitnexus_impact` on it.
- NEVER ignore HIGH or CRITICAL risk warnings from impact analysis.
- NEVER rename symbols with find-and-replace — use `gitnexus_rename` which understands the call graph.
- NEVER commit changes without running `gitnexus_detect_changes()` to check affected scope.

## Tools Quick Reference

| Tool | When to use | Command |
|------|-------------|---------|
| `query` | Find code by concept | `gitnexus_query({query: "auth validation"})` |
| `context` | 360-degree view of one symbol | `gitnexus_context({name: "validateUser"})` |
| `impact` | Blast radius before editing | `gitnexus_impact({target: "X", direction: "upstream"})` |
| `detect_changes` | Pre-commit scope check | `gitnexus_detect_changes({scope: "staged"})` |
| `rename` | Safe multi-file rename | `gitnexus_rename({symbol_name: "old", new_name: "new", dry_run: true})` |
| `cypher` | Custom graph queries | `gitnexus_cypher({query: "MATCH ..."})` |

## Impact Risk Levels

| Depth | Meaning | Action |
|-------|---------|--------|
| d=1 | WILL BREAK — direct callers/importers | MUST update these |
| d=2 | LIKELY AFFECTED — indirect deps | Should test |
| d=3 | MAY NEED TESTING — transitive | Test if critical path |

## Resources

| Resource | Use for |
|----------|---------|
| `gitnexus://repo/server-api/context` | Codebase overview, check index freshness |
| `gitnexus://repo/server-api/clusters` | All functional areas |
| `gitnexus://repo/server-api/processes` | All execution flows |
| `gitnexus://repo/server-api/process/{name}` | Step-by-step execution trace |

## Self-Check Before Finishing

Before completing any code modification task, verify:
1. `gitnexus_impact` was run for all modified symbols
2. No HIGH/CRITICAL risk warnings were ignored
3. `gitnexus_detect_changes()` confirms changes match expected scope
4. All d=1 (WILL BREAK) dependents were updated

## Keeping the Index Fresh

After committing code changes, the GitNexus index becomes stale. Re-run analyze to update it:

```bash
npx gitnexus analyze
```

If the index previously included embeddings, preserve them by adding `--embeddings`:

```bash
npx gitnexus analyze --embeddings
```

To check whether embeddings exist, inspect `.gitnexus/meta.json` — the `stats.embeddings` field shows the count (0 means no embeddings). **Running analyze without `--embeddings` will delete any previously generated embeddings.**

> Claude Code users: A PostToolUse hook handles this automatically after `git commit` and `git merge`.

## CLI

| Task | Read this skill file                                |
|------|-----------------------------------------------------|
| Understand architecture / "How does X work?" | `~/.claude/skills/gitnexus/gitnexus-exploring/SKILL.md` |
| Blast radius / "What breaks if I change X?" | `~/.claude/skills/gitnexus/gitnexus-impact-analysis/SKILL.md` |
| Trace bugs / "Why is X failing?" | `~/.claude/skills/gitnexus/gitnexus-debugging/SKILL.md` |
| Rename / extract / split / refactor | `~/.claude/skills/gitnexus/gitnexus-refactoring/SKILL.md` |
| Tools, resources, schema reference | `~/.claude/skills/gitnexus/gitnexus-guide/SKILL.md` |
| Index, status, clean, wiki CLI commands | `~/.claude/skills/gitnexus/gitnexus-cli/SKILL.md` |

<!-- gitnexus:end -->
