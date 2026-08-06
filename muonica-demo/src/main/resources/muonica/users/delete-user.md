# Delete a user

Permanently removes a user from the in-memory directory. This operation returns no body on success, so clients can treat `204 No Content` as the completion signal.

:::notice danger
Deletion is irreversible in the demo. Real applications should consider a retention window, soft deletion, or an explicit confirmation step before calling this endpoint.
:::

:::slot parameters
:::

:::slot responses
:::
