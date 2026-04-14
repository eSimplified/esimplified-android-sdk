# CLAUDE.md -- eSIMplified Android SDK

## Project Overview

eSIMplified Android SDK -- AAR library providing eSIM API access for Android applications. Provides typed repository interfaces for authentication, eSIM management, package browsing, orders, payments, and more. All networking, authentication, and token management are handled internally.

- **Package:** `io.esimplified.sdk`
- **Maven:** `io.esimplified:android-sdk:{version}`
- **Language:** Kotlin 2.2.20
- **Architecture:** Single module Android library

## Architecture

```
Models (public) --> Repository interfaces (public) --> Repository implementations (internal) --> Network layer (internal)
```

- **Models** (`io.esimplified.sdk.model.*`) -- `@Serializable` data classes exposed as public API
- **Repository interfaces** (`io.esimplified.sdk.repository.*`) -- public API surface that consuming apps depend on
- **Repository implementations** (`io.esimplified.sdk.repository.impl.*`) -- `internal` classes, not accessible to consumers
- **Network layer** (`io.esimplified.sdk.network.*`) -- `internal` Retrofit/OkHttp setup
- **Auth** (`io.esimplified.sdk.auth.*`) -- session management, token storage, interceptors
- **Entry point:** `EsimSdk.initialize(context, config)` then `EsimSdk.koinModule()`

## Git Workflow

### Branching Model

```
main (production)
  ├── feature/FeatureNameTicketNumber  (e.g., feature/KredsEndpoint1245)
  └── bugfix/BugNameTicketNumber       (e.g., bugfix/QuoteResponseParsing1301)
```

### Branch Naming

- **Feature branches:** `feature/FeatureNameTicketNumber` (e.g., `feature/KredsEndpoint1245`)
- **Bugfix branches:** `bugfix/BugNameTicketNumber` (e.g., `bugfix/QuoteResponseParsing1301`)

### Flow

1. Create feature/ or bugfix/ branch from `main`
2. Work on branch, commit changes
3. PR into `main`
4. After merge, bump version in `sdk/build.gradle.kts` if publishing
5. Tag the release on GitHub (e.g., `v1.1.0`)
6. Publish: `./gradlew publishToMavenLocal` (dev) or `./gradlew publish` (GitHub Packages)

### Commit Messages

Use conventional commits:
- `feat:` -- new feature
- `fix:` -- bug fix
- `chore:` -- maintenance tasks
- `refactor:` -- code restructuring without behavior change
- `test:` -- adding or updating tests
- `docs:` -- documentation changes
- `build:` -- build system or dependency changes

## Code Style

- All implementation classes marked `internal`
- All model classes are `@Serializable` data classes
- Repository interfaces are the public API surface
- No Android Context in models or interfaces (keep domain pure where possible)
- Use Timber for logging, never println
- No comments except `// region` markers for code folding
- No TODO/FIXME/HACK -- fix it or file an issue

## Build

```bash
# Verify the build
./gradlew assembleRelease

# Run tests
./gradlew test

# Publish to Maven Local (for local development)
./gradlew publishToMavenLocal

# Full publish command for Maven Local
./gradlew publishReleasePublicationToMavenLocal

# Clean build
./gradlew clean assembleRelease
```

## Testing

- Unit tests in `sdk/src/test/`
- Use FakeSecureStorage for testing (no Android dependencies in tests)
- Use MockWebServer for interceptor tests
- All tests must pass before publishing
- Run tests: `./gradlew test`

## Publishing

### Dev (Maven Local)

```bash
./gradlew publishToMavenLocal
```

Published to `~/.m2/repository/io/esimplified/android-sdk/{version}/`. The consuming app resolves it via `mavenLocal()` in `settings.gradle.kts`.

### Production (GitHub Packages)

1. Bump version in `sdk/build.gradle.kts`
2. Run `./gradlew publish` (requires `GITHUB_ACTOR` and `GITHUB_TOKEN` or `gpr.user`/`gpr.token` in `~/.gradle/gradle.properties`)

### Versioning

Semantic versioning (major.minor.patch):
- **MAJOR** -- Breaking API changes (removed/renamed repository methods, model field changes)
- **MINOR** -- New features (new repository methods, new models, new optional parameters)
- **PATCH** -- Bug fixes, internal improvements, documentation updates

Version is defined in `sdk/build.gradle.kts`. Only bump for GitHub Packages releases, not for local development.

## GitHub

- **Repo:** `eSimplified/esimplified-android-sdk`
