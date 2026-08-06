# Muonica demo

This project is a small but deliberately realistic Spring MVC API. It gives the Muonica UI enough variety to demonstrate the difference between generated reference data and documentation written for humans.

Explore the documented endpoints below. Each group includes a short product context, while each operation explains the intent, inputs, authentication, and the shape of a useful response.

## A practical reference

The demo uses users, orders, and report exports because they expose different documentation needs: simple reads, validated JSON bodies, multipart uploads, lifecycle transitions, asynchronous jobs, and binary downloads.

The examples are intentionally deterministic. You can copy the generated request examples, edit path values in the sandbox, and use the response examples as fixtures when trying the API from a local client.

## Conventions

- Dates and timestamps use UTC.
- Identifiers in the order and report APIs are UUIDs.
- Collection endpoints accept optional filters and return a compact example collection.
- Error responses use a small `code` and `message` payload so clients can display actionable feedback.
