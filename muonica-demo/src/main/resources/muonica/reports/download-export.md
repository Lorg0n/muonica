# Download an export

Downloads the completed report as a binary file. The response content type is `application/octet-stream`; clients should use the requested export format when choosing a filename extension.

:::notice warning
A `409` means the job exists but is not ready. Keep polling the status endpoint instead of retrying the download in a tight loop.
:::

:::slot parameters
:::

:::slot responses
:::
