const API_URL = "./api";
export const AUTH_STORAGE_KEY = "muonica.authorization.v1";

function storageOrDefault(storage) {
    if (storage) return storage;
    try {
        return globalThis.localStorage;
    } catch {
        return undefined;
    }
}

export function normalizeBearerToken(value = "") {
    return String(value).trim().replace(/^Bearer\s+/i, "");
}

export function loadAuthorization(storage = storageOrDefault()) {
    if (!storage) return {};
    try {
        const parsed = JSON.parse(storage.getItem(AUTH_STORAGE_KEY) || "{}");
        return Object.fromEntries(Object.entries(parsed).filter(([, value]) => typeof value === "string"));
    } catch {
        return {};
    }
}

export function saveAuthorization(values, storage = storageOrDefault()) {
    const sanitized = Object.fromEntries(Object.entries(values || {})
        .map(([name, value]) => [name, String(value || "").trim()])
        .filter(([, value]) => value));
    try {
        storage?.setItem(AUTH_STORAGE_KEY, JSON.stringify(sanitized));
    } catch {
        // Browser storage can be disabled; requests can still use this returned value.
    }
    return sanitized;
}

export function clearAuthorization(storage = storageOrDefault()) {
    try {
        storage?.removeItem(AUTH_STORAGE_KEY);
    } catch {
        // Treat unavailable storage as already clear.
    }
    return {};
}

function schemeType(scheme) {
    return String(scheme?.type || "").toUpperCase();
}

function schemeLocation(scheme) {
    return String(scheme?.parameterLocation || "HEADER").toUpperCase();
}

function schemeValue(scheme, value) {
    if (!value) return "";
    if (schemeType(scheme) === "HTTP" && String(scheme.scheme || "").toLowerCase() === "bearer") {
        const token = normalizeBearerToken(value);
        return token ? `Bearer ${token}` : "";
    }
    if (schemeType(scheme) === "HTTP" && scheme.scheme) {
        return `${scheme.scheme} ${String(value).trim()}`;
    }
    return String(value).trim();
}

function appendQuery(path, name, value) {
    const separator = path.includes("?") ? "&" : "?";
    return `${path}${separator}${encodeURIComponent(name)}=${encodeURIComponent(value)}`;
}

function setCookie(name, value) {
    if (typeof document !== "undefined") {
        try {
            document.cookie = `${encodeURIComponent(name)}=${encodeURIComponent(value)}; path=/`;
        } catch {
            // Cookie auth is best effort because browser policies may reject it.
        }
    }
}

export function authorizationForEndpoint(endpoint, project, values = {}, basePath = endpoint?.path || "") {
    const schemes = Object.fromEntries((project?.securitySchemes || []).map(scheme => [scheme.name, scheme]));
    let path = basePath;
    const headers = {};
    const cookies = [];
    for (const name of endpoint?.securityRequirements || []) {
        const scheme = schemes[name];
        const value = values[name];
        if (!scheme || !value) continue;
        const resolved = schemeValue(scheme, value);
        if (!resolved) continue;
        const parameterName = scheme.parameterName || "Authorization";
        const location = schemeLocation(scheme);
        if (location === "QUERY") path = appendQuery(path, parameterName, resolved);
        else if (location === "COOKIE") {
            cookies.push({ name: parameterName, value: resolved });
            setCookie(parameterName, resolved);
        } else headers[parameterName] = resolved;
    }
    return { path, headers, cookies };
}

export async function loadProject() {
    const response = await fetch(API_URL, { headers: { Accept: "application/json" } });
    if (!response.ok) {
        throw new Error(`Documentation request failed (${response.status})`);
    }
    return response.json();
}

export async function sendEndpointRequest(method, path, body, contentType, endpoint, project, authorization = {}) {
    const headers = { Accept: "application/json" };
    const hasBody = body !== undefined && body !== null && body !== "";
    const requestAuthorization = authorizationForEndpoint(endpoint, project, authorization, path);
    path = requestAuthorization.path || path;
    Object.assign(headers, requestAuthorization.headers);

    // Let the browser add the multipart boundary when a future request editor
    // sends FormData. JSON and other text bodies need an explicit content type.
    if (hasBody && contentType && contentType !== "multipart/form-data") {
        headers["Content-Type"] = contentType;
    }

    const response = await fetch(path, {
        method,
        headers,
        credentials: "same-origin",
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
