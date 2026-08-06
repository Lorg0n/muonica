# Update a user

Applies a partial profile update. Omitting a property leaves the existing value unchanged, which makes this operation suitable for focused settings screens and small administrative edits.

```json
{
  "name": "Grace Hopper",
  "role": "ADMIN"
}
```

:::notice info
The user identifier is immutable. Send only the fields that should change; an empty object is a valid no-op in this simplified demo.
:::

:::slot parameters
:::

:::slot request
:::

:::slot responses
:::
