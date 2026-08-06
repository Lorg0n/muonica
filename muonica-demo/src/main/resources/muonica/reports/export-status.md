# Get export status

Polls the state of an export job. Clients should use a modest backoff, stop polling after a terminal state, and follow `downloadUrl` only when the status is `READY`.

The possible states are `QUEUED`, `PROCESSING`, `READY`, and `FAILED`.

:::slot parameters
:::

:::slot responses
:::
