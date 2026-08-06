# Create a user

Creates a new member of the demo directory. The request body intentionally includes both a scalar value and a collection so the generated JSON example demonstrates nested validation without becoming noisy.

The `name` is limited to 80 characters, `role` must be one of the documented values, and every tag must contain at least one non-whitespace character.

```json
{
  "name": "Grace Hopper",
  "role": "MEMBER",
  "tags": ["platform", "on-call"]
}
```

:::notice warning
A duplicate display name produces a structured `409` response. Clients should show the conflict next to the name field instead of retrying unchanged input.
:::

:::slot request
:::

:::diagram mermaid
sequenceDiagram
    participant Client
    participant Users
    Client->>Users: POST /users
    Users-->>Client: 201 UserResponse
:::

:::slot responses
:::
