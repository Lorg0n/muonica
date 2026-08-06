# Upload an avatar

Stores a profile image using `multipart/form-data`. The generated request panel exposes the file part as a binary field while keeping the endpoint path editable.

The server should normalize the image into a square thumbnail and reject files that exceed the service's upload policy. This demo focuses on the documentation shape rather than image processing.

:::notice warning
The uploaded file is not persisted between application restarts. Use a stable object-storage key in a production implementation.
:::

:::slot parameters
:::

:::slot request
:::

:::slot responses
:::
