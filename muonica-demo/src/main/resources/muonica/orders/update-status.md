# Change order status

Moves an order through the fulfilment lifecycle. Status changes are deliberately explicit because a client should be able to show who or what initiated a transition.

The valid states are `PENDING`, `PAID`, `FULFILLING`, `SHIPPED`, and `CANCELLED`. A production service would also return transition metadata such as the actor and timestamp.

```json
{
  "status": "FULFILLING"
}
```

:::notice danger
Do not retry a rejected transition blindly. A `409` indicates that the order has moved to a different state and should be fetched again first.
:::

:::slot parameters
:::

:::slot request
:::

:::slot responses
:::
