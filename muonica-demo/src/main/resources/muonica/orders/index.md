# Orders

Orders show the lifecycle side of the demo API. A client can list work, inspect line items, create a new order, move it through fulfilment, and cancel it before processing begins.

Order identifiers and customer identifiers are UUIDs. Status transitions are explicit so a client can render a reliable timeline instead of inferring state from timestamps.

:::notice info
The examples use a single deterministic order so request and response examples remain stable across restarts.
:::

:::slot security
:::
