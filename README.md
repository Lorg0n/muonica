# Muonica

Muonica is an extensible documentation library for Spring Boot. Its own Java model is the source of truth; Spring integration and OpenAPI are adapters around that model.

## Current vertical slice

`muonica-demo` starts a Spring Boot application and exposes:

```text
GET /muonica              # minimal landing page
GET /muonica/api          # Muonica JSON model
GET /muonica/openapi.json # OpenAPI 3.1.1 JSON
```

The scanner detects Spring MVC mappings, request/response DTOs, validation constraints,
multipart parts, standard parameter locations, Muonica documentation blocks, and Muonica
security annotations. The OpenAPI module exports the neutral model; it is not the source of truth.

## Muonica Pages

Muonica Pages composes technical API metadata with Markdown resources packaged in the application JAR:

```java
@MuonicaDocumentation(file = "classpath:/muonica/users/get-user.md")
```

Supported file directives include `:::notice warning`, `:::diagram mermaid`, and technical slots such as
`:::slot request`, `:::slot responses`, `:::slot parameters`, and `:::slot security`. Documentation is
inherited from project to group to endpoint. Regular blocks are additive; the nearest declaration owns a
slot. Missing technical slots are generated and appended as a fallback.

Documentation resources are loaded and cached during startup. Cache invalidation is not supported in v1,
so Markdown changes require an application restart. Resource errors are strict by default:

```yaml
muonica:
  documentation:
    strict: true
```

With `strict: false`, invalid sources are skipped and diagnostics are exposed as `documentationWarnings` in
the Muonica JSON model.

## Modules

- `muonica-core` — framework-independent documentation model.
- `muonica-spring` — discovers Spring MVC handler mappings and exposes the JSON endpoint.
- `muonica-openapi` — OpenAPI 3.1.1 export adapter.
- `muonica-ui` — the autonomous Muonica Pages frontend and static resources.
- `muonica-demo` — executable Spring Boot example.

## Run

Import the root `build.gradle.kts` into IntelliJ IDEA, configure a JDK 17+ (21 is a good default), and run `MuonicaDemoApplication`. From a terminal:

```bash
./gradlew :muonica-demo:bootRun
```
