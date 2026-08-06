# Get a user

Returns the full user record for a numeric identifier.

Use this operation when a client needs the canonical profile representation. The identifier is part of the URL, so it is also reflected in the request sandbox and generated cURL command.

:::notice warning
Deleted users are not returned by this operation.
:::

:::slot request
:::

The response includes the user's role.

Clients should treat the role as an enum and avoid assuming that the list of roles is permanently closed. A missing user is represented by a structured `404` response with a stable error code.

:::slot responses
:::
