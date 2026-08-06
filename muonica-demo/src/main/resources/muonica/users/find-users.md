# Find users

Searches the demo directory with optional filters. The operation is useful for directory screens, admin pickers, and support tools that need a small result set without loading every profile.

The `query` value is matched against the display name. `createdAfter` narrows the result to recently created accounts, while `X-Request-Id` is echoed by production-style clients into their logs for support investigations.

:::notice info
This demo returns a compact collection example. A production API would normally add pagination metadata and a continuation cursor.
:::

:::slot parameters
:::

:::slot request
:::

:::slot responses
:::
