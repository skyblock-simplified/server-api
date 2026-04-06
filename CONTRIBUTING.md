# Contributing to Server API

Thank you for your interest in contributing! This document explains how to get
started, what to expect during the review process, and the conventions this
project follows.

## Table of Contents

- [Getting Started](#getting-started)
  - [Prerequisites](#prerequisites)
  - [Development Setup](#development-setup)
- [Making Changes](#making-changes)
  - [Branching Strategy](#branching-strategy)
  - [Code Style](#code-style)
  - [Commit Messages](#commit-messages)
  - [Testing](#testing)
- [Submitting a Pull Request](#submitting-a-pull-request)
- [Reporting Issues](#reporting-issues)
- [Project Architecture](#project-architecture)
- [Legal](#legal)

## Getting Started

### Prerequisites

| Requirement | Version | Notes |
|-------------|---------|-------|
| [JDK](https://adoptium.net/) | **21+** | Required |
| [Git](https://git-scm.com/) | 2.x+ | For cloning and contributing |
| [IntelliJ IDEA](https://www.jetbrains.com/idea/) | Latest | Recommended IDE (Lombok and Gradle support built-in) |

### Development Setup

1. **Fork and clone the repository**

   [Fork the repository](https://github.com/SkyBlock-Simplified/server-api/fork),
   then clone your fork:

   ```bash
   git clone https://github.com/<your-username>/server-api.git
   cd server-api
   ```

2. **Build the project**

   The Gradle wrapper is included - no separate Gradle installation is needed.

   ```bash
   cd server-api
   ./gradlew build
   ```

4. **Open in IntelliJ IDEA**

   Open the project root as a Gradle project. IntelliJ will automatically
   detect the `build.gradle.kts` and import dependencies. Ensure the Lombok
   plugin is installed and annotation processing is enabled
   (`Settings > Build > Compiler > Annotation Processors`).

5. **Verify the setup**

   ```bash
   ./gradlew test
   ```

## Making Changes

### Branching Strategy

- Create a feature branch from `master` for your work.
- Use a descriptive branch name: `fix/rate-limit-window`,
  `feat/custom-error-body`, `docs/versioning-examples`.

```bash
git checkout -b feat/my-feature master
```

### Code Style

#### General

- **Spring conventions** - Follow standard Spring Boot patterns for
  `@Configuration`, `@RestControllerAdvice`, `HandlerInterceptor`, and
  `RequestMappingHandlerMapping`.
- **Collections** - Always use `Concurrent.newList()`, `Concurrent.newMap()`,
  `Concurrent.newSet()` instead of `new ArrayList`, `new HashMap`, etc.
- **Annotations** - Use `@NotNull` / `@Nullable` from `org.jetbrains.annotations`
  on all public method parameters and return types.
- **Lombok** - Use `@Getter`, `@RequiredArgsConstructor`, `@Log4j2`, etc.
  where appropriate. The logger field is non-static
  (`lombok.log.fieldIsStatic = false`).
- **Builder pattern** - Use `ClassBuilder<T>` with `@BuildFlag` validation.
  Follow the existing pattern in `ServerConfig.Builder`.

#### Braces

- Omit curly braces when the `if` body is a single line.
- Use curly braces when the body wraps across multiple lines.

#### Javadoc

- **Class level** - Noun phrase describing what the type is.
- **Method level** - Active verb, third person singular, describing what the
  method does.
- **Tags** - Always include `@param`, `@return`, `@throws` on public methods.
  Tag descriptions are lowercase sentence fragments with no trailing period.
  Single space after the param name (no column alignment).
- **Punctuation** - Only use single hyphens (` - `) as separators. Never em
  dashes, `&mdash;`, or double hyphens.
- Never use `@author` or `@since`.

#### Exceptions

- All server exceptions extend `ServerException` and carry an `HttpStatus`.
- Leaf exceptions internalize their messages - constructors accept only
  domain-specific parameters and format the message internally.
- Follow the five-constructor pattern documented in the root `CLAUDE.md`.

### Commit Messages

Write clear, concise commit messages that describe *what* changed and *why*.

```
Add rate limit headers to 429 responses

Includes X-RateLimit-Remaining and Retry-After headers when the
sliding window counter rejects a request.
```

- Use the imperative mood ("Add", "Fix", "Update", not "Added", "Fixes").
- Keep the subject line under 72 characters.
- Add a body when the *why* isn't obvious from the subject.

### Testing

Tests use JUnit 5 (Jupiter) with Spring Boot Test for integration tests:

```bash
./gradlew test
```

- Add tests for new functionality where practical.
- Unit tests for interceptors, services, and configuration don't require a
  running Spring context - prefer plain JUnit tests over `@SpringBootTest`
  where possible.
- The test source set includes a `TestServer` and example controllers
  (`TestApiKeyController`, `TestVersionController`) for exercising the
  framework. Run `TestServer.main()` from your IDE to manually verify changes.
- When adding a new framework feature, add example endpoints to the test
  controllers or create new ones under
  `src/test/java/dev/sbs/serverapi/controller/` to demonstrate and verify the
  feature.

## Submitting a Pull Request

1. **Push your branch** to your fork.

   ```bash
   git push origin feat/my-feature
   ```

2. **Open a Pull Request** against the `master` branch of
   [SkyBlock-Simplified/server-api](https://github.com/SkyBlock-Simplified/server-api).

3. **In the PR description**, include:
   - A summary of the changes and the motivation behind them.
   - Steps to test or verify the changes.
   - Whether the change affects the public API surface or downstream consumers.

4. **Respond to review feedback.** PRs may go through one or more rounds of
   review before being merged.

### What gets reviewed

- Correctness of interceptor chains and handler mapping logic.
- Adherence to the builder pattern and `ClassBuilder<T>` conventions.
- Impact on downstream modules (`simplified-server`). Breaking changes to
  the public API should be discussed in the issue tracker before
  implementation.
- Security considerations (XSS in error pages, header injection, rate limit
  bypass).
- Compatibility with Spring Boot auto-configuration and consumer
  `scanBasePackages` setup.

## Reporting Issues

Use [GitHub Issues](https://github.com/SkyBlock-Simplified/server-api/issues)
to report bugs or request features.

When reporting a bug, include:

- **Java version** (`java --version`)
- **Spring Boot version** (check `gradle/libs.versions.toml`)
- **Operating system**
- **Full error stacktrace** (if applicable)
- **Steps to reproduce**
- **Expected vs. actual behavior**

## Project Architecture

A brief overview to help you find your way around the codebase:

```
src/main/java/dev/sbs/serverapi/     # Framework library
├── config/
│   ├── ServerConfig.java              # Immutable config with Builder, MemorySize,
│   │                                  # ShutdownMode, ForwardHeadersStrategy
│   └── ServerWebConfig.java           # Security header interceptor + Gson message converters
├── error/
│   ├── ErrorController.java           # Global @RestControllerAdvice with content negotiation
│   └── ErrorPageRenderer.java         # Cloudflare-style HTML renderer with Placeholder enum
├── exception/
│   └── ServerException.java           # Root exception with embedded HttpStatus
├── security/
│   ├── ApiKey.java                    # Key record with roles and sliding window counter
│   ├── ApiKeyAuthenticationInterceptor.java  # HandlerInterceptor for auth/rate/perms
│   ├── ApiKeyConfig.java              # Conditional bean registration
│   ├── ApiKeyProtected.java           # @ApiKeyProtected annotation
│   ├── ApiKeyRole.java                # Hierarchical role enum
│   ├── ApiKeyRoleHierarchy.java       # Role expansion into reachable set
│   ├── ApiKeyService.java             # Key storage, validation, rate limiting
│   ├── SecurityHeaderInterceptor.java # X-Content-Type-Options: nosniff
│   └── exception/                     # MissingApiKeyException, InvalidApiKeyException,
│                                      # RateLimitExceededException, InsufficientPermissionException
└── version/
    ├── ApiVersion.java                # @ApiVersion annotation
    ├── ApiVersionCondition.java       # RequestCondition for version matching
    ├── ApiVersionConfig.java          # Bean registration
    ├── ApiVersionHandlerMapping.java  # Custom handler mapping with /v{N} prefixes
    ├── ApiVersionInterceptor.java     # Defense-in-depth version validation
    ├── VersionRegistryService.java    # Precomputed path-to-version index
    └── exception/                     # InvalidVersionException, MissingVersionException

src/test/java/dev/sbs/serverapi/      # Test harness
├── TestServer.java                    # Runnable test application (port 8080)
└── controller/
    ├── TestApiKeyController.java      # API key auth test endpoints (/api/*)
    └── TestVersionController.java     # API versioning test endpoints (/v{N}/*)
```

### Key extension points

- **Custom error body** - Override `ErrorController.buildErrorBody()` to return
  a project-specific JSON error response type instead of the default map.
- **New security exception** - Extend `SecurityException` in
  `security/exception/` with the appropriate `HttpStatus`.
- **New version exception** - Extend `VersionException` in
  `version/exception/` with the appropriate `HttpStatus`.
- **Custom server config** - Use `ServerConfig.builder()` with any combination
  of settings, or start from `ServerConfig.optimized()` and adjust.
- **API key storage** - Replace or extend `ApiKeyService` to load keys from a
  database or external service instead of the current hardcoded test keys.

## Legal

By submitting a pull request, you agree that your contributions are licensed
under the [Apache License 2.0](https://www.apache.org/licenses/LICENSE-2.0),
the same license that covers this project.
