# Create an order

Creates an order from a customer identifier, one or more line items, and a payment method. The server validates the complete request before reserving inventory.

```json
{
  "customerId": "0f4b9d4a-3cf5-4ea2-9dc1-4f6c7e0c4a21",
  "lines": [
    {
      "sku": "muonica-notebook",
      "quantity": 2,
      "unitPrice": 64.50
    }
  ],
  "paymentMethod": "CARD"
}
```

:::notice warning
A `409` means inventory changed while the order was being prepared. Refresh the product availability before asking the user to try again.
:::

:::slot request
:::

:::slot responses
:::
