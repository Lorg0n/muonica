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

## Modules

- `muonica-core` — framework-independent documentation model.
- `muonica-spring` — discovers Spring MVC handler mappings and exposes the JSON endpoint.
- `muonica-openapi` — OpenAPI 3.1.1 export adapter.
- `muonica-ui` — reserved home for a separate frontend.
- `muonica-demo` — executable Spring Boot example.

## Run

Import the root `build.gradle.kts` into IntelliJ IDEA, configure a JDK 17+ (21 is a good default), and run `MuonicaDemoApplication`. From a terminal:

```bash
./gradlew :muonica-demo:bootRun
```
