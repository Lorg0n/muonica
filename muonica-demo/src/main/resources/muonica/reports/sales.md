# Read sales report

Aggregates revenue and order count for an inclusive date range. Choose `DAY`, `WEEK`, or `MONTH` when the client needs a chart at a specific resolution.

The response includes a top-level total and a series of buckets. This keeps summary cards fast while giving charting clients enough detail for a first render.

:::notice warning
The `from` date must not be after `to`. Invalid ranges return a structured `422` response instead of an empty report.
:::

:::slot parameters
:::

:::slot responses
:::
