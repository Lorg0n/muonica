# Muonica

Muonica is an extensible documentation library for Spring Boot. Its own Java model is the source of truth; Spring integration and OpenAPI are adapters around that model.

## Current vertical slice

`muonica-demo` starts a Spring Boot application and exposes a live endpoint catalogue at:

```text
GET /muonica/api
```

For example, it reports the demo `GET /users/{id}` handler with its controller and method names.

## Modules

- `muonica-core` — framework-independent documentation model.
- `muonica-spring` — discovers Spring MVC handler mappings and exposes the JSON endpoint.
- `muonica-openapi` — reserved adapter module for OpenAPI 3.1 export.
- `muonica-ui` — reserved home for a separate frontend.
- `muonica-demo` — executable Spring Boot example.

## Run

Import the root `build.gradle.kts` into IntelliJ IDEA, configure a JDK 17+ (21 is a good default), and run `MuonicaDemoApplication`. From a terminal:

```bash
./gradlew :muonica-demo:bootRun
```
