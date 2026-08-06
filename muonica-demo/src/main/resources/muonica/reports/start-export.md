# Start a report export

Starts an asynchronous export job. The `202 Accepted` response confirms that the request was queued; it does not mean the file is ready to download.

```json
{
  "from": "2026-01-01",
  "to": "2026-01-31",
  "format": "CSV"
}
```

:::slot request
:::

:::diagram mermaid
sequenceDiagram
    participant Client
    participant Reports
    Client->>Reports: POST /reports/exports
    Reports-->>Client: 202 ExportJob
    Client->>Reports: GET /reports/exports/{id}
    Reports-->>Client: 200 ExportJob
:::

:::slot responses
:::
