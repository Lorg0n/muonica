const API_URL = "./api";

export async function loadProject() {
    const response = await fetch(API_URL, { headers: { Accept: "application/json" } });
    if (!response.ok) {
        throw new Error(`Documentation request failed (${response.status})`);
    }
    return response.json();
}

export async function sendEndpointRequest(method, path, body, contentType) {
    const headers = { Accept: "application/json" };
    const hasBody = body !== undefined && body !== null && body !== "";

    // Let the browser add the multipart boundary when a future request editor
    // sends FormData. JSON and other text bodies need an explicit content type.
    if (hasBody && contentType && contentType !== "multipart/form-data") {
        headers["Content-Type"] = contentType;
    }

    const response = await fetch(path, {
        method,
        headers,
        ...(hasBody ? { body } : {})
    });

    return {
        response,
        text: await response.text()
    };
}

export function endpointKey(groupIndex, endpointIndex) {
    return `${groupIndex}:${endpointIndex}`;
}

export function allEndpoints(project) {
    return (project.groups ?? []).flatMap((group, groupIndex) =>
        (group.endpoints ?? []).map((endpoint, endpointIndex) => ({
            group, groupIndex, endpoint, endpointIndex, key: endpointKey(groupIndex, endpointIndex)
        }))
    );
}
