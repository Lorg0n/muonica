const API_URL = "/muonica/api";

export async function loadProject() {
    const response = await fetch(API_URL, { headers: { Accept: "application/json" } });
    if (!response.ok) {
        throw new Error(`Documentation request failed (${response.status})`);
    }
    return response.json();
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
