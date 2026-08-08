import assert from "node:assert/strict";
import test from "node:test";

import {
    authorizationForEndpoint,
    clearAuthorization,
    endpointParametersForRequest,
    loadAuthorization,
    normalizeBearerToken,
    saveAuthorization,
    sendEndpointRequest,
    securityGroupsFor
} from "../../main/resources/META-INF/muonica/js/api.js";
import { curlFor, pathValuesFor, renderMarkdown, renderShell } from "../../main/resources/META-INF/muonica/js/render.js";
import { validateParameters } from "../../main/resources/META-INF/muonica/js/features/request.js";

const project = {
    name: "Muonica demo",
    version: "1.0",
    schemas: {
        UserResponse: {
            type: "object",
            description: "A user returned by the API.",
            requiredProperties: ["id", "role"],
            properties: {
                id: { type: "string", format: "uuid" },
                role: { type: "string", enumValues: ["ADMIN", "MEMBER"] },
                addresses: { type: "array", items: { ref: "Address" } }
            }
        },
        Address: {
            type: "object",
            properties: {
                city: { type: "string", minLength: 2, maxLength: 100, pattern: "[A-Za-z ]+" }
            }
        }
    },
    securitySchemes: [{
        name: "bearerAuth",
        type: "HTTP",
        scheme: "bearer",
        bearerFormat: "JWT",
        parameterName: "Authorization",
        parameterLocation: "HEADER"
    }],
    groups: [{
        name: "Users",
        endpoints: [{
            method: "GET",
            path: "/users/{id}",
            summary: "Get user",
            description: "Returns the full user record.",
            parameters: [{
                name: "id",
                location: "PATH",
                required: true,
                description: "Numeric user identifier.",
                schema: { type: "integer", format: "int64" }
            }, {
                name: "includeInactive",
                location: "QUERY",
                required: false,
                description: "Include inactive users.",
                schema: { type: "boolean" }
            }, {
                name: "X-Request-Id",
                location: "HEADER",
                required: false,
                description: "Request correlation id.",
                schema: { type: "string" }
            }, {
                name: "theme",
                location: "COOKIE",
                required: false,
                description: "Preferred theme.",
                schema: { type: "string" }
            }],
            request: {
                description: "Send a JSON request to this endpoint.",
                content: { "application/json": { type: "object", properties: { include: { type: "boolean" } } } }
            },
            responses: [
                { statusCode: "200", description: "User returned", content: { "application/json": { type: "object", properties: { id: { type: "integer" } } } } },
                { statusCode: "404", description: "User not found", content: {} }
            ],
            securityRequirements: ["bearerAuth"],
            documentationBlocks: [
                { type: "markdown", content: "Returns the full user record.", attributes: {}, origin: "USER" },
                { type: "notice", content: "Deleted users are not returned.", attributes: { level: "warning" }, origin: "USER" },
                { type: "slot", content: "", attributes: { name: "responses" }, origin: "GENERATED" },
                { type: "slot", content: "", attributes: { name: "request" }, origin: "GENERATED" },
                { type: "slot", content: "", attributes: { name: "parameters" }, origin: "GENERATED" },
                { type: "slot", content: "", attributes: { name: "security" }, origin: "GENERATED" },
                { type: "diagram", content: "A -> B: request", attributes: { renderer: "sequence" }, origin: "USER" }
            ]
        }]
    }]
};

test("renders markdown as editorial content", () => {
    const html = renderMarkdown("## Details\n\nUse **this** value.\n\n> Keep it stable.\n\n```json\n{\"ok\": true}\n```");

    assert.match(html, /<h2/);
    assert.match(html, /<strong>this<\/strong>/);
    assert.match(html, /<blockquote>/);
    assert.match(html, /data-language="json"/);
});

test("renders an endpoint as an article flow", () => {
    const selected = { group: project.groups[0], endpoint: project.groups[0].endpoints[0], key: "0:0" };
    const html = renderShell(project, selected, "");

    assert.doesNotMatch(html, /class="brand-logo"/);
    assert.match(html, />muonica<\/span>/);
    assert.match(html, /class="method-badge[^>]*method-get/);
    assert.match(html, /class="notice-block notice-warning"/);
    assert.doesNotMatch(html, /class="parameter-row"/);
    assert.doesNotMatch(html, /<table/);
    assert.match(html, /data-parameter-key="PATH::id"/);
    assert.match(html, /class="request-parameter-row"/);
    assert.match(html, /\* required/);
    assert.match(html, /Additional parameters \(3\)/);
    assert.doesNotMatch(html, /data-parameter-key="QUERY::includeInactive"/);
    assert.doesNotMatch(html, /<h2 class="section-title">Parameters<\/h2>/);
    assert.match(html, /data-copy="\/users\/\{id\}"/);
    assert.match(html, /data-preserve-scroll="sidebar-navigation"/);
    assert.match(html, /id="endpoint-url"[^>]*>\/users\/\{id\}</);
    assert.doesNotMatch(html, /Path parameters/);
    assert.match(html, /id="code-curl"/);
    assert.match(html, /<details class="response-disclosure[^>]*" open>/);

    const securityIndex = html.indexOf("Authentication");
    const requestIndex = html.indexOf("Request body");
    const responsesIndex = html.indexOf("Responses");
    const diagramIndex = html.indexOf("Diagram");
    const requestPanelIndex = html.indexOf('<div class="request-panel');
    const parameterIndex = html.indexOf('data-parameter-key="PATH::id"');
    const codeHeaderIndex = html.indexOf('<div class="panel-header">', requestPanelIndex);
    assert.ok(securityIndex < requestIndex);
    assert.ok(requestIndex < responsesIndex);
    assert.ok(responsesIndex < diagramIndex);
    assert.ok(requestPanelIndex < parameterIndex);
    assert.ok(parameterIndex < codeHeaderIndex);

    const populatedPathHtml = renderShell(project, selected, "", false, { parameterValues: { "PATH::id": "42" } });
    assert.match(populatedPathHtml, /id="endpoint-url"[^>]*>\/users\/\{id\}</);
    assert.match(populatedPathHtml, /data-copy="\/users\/\{id\}"/);
    assert.match(populatedPathHtml, /data-parameter-key="PATH::id"[^>]*value="42"/);

    const expandedHtml = renderShell(project, selected, "", false, { optionalParametersOpen: true });
    assert.match(expandedHtml, /data-parameter-key="QUERY::includeInactive"/);
    assert.match(expandedHtml, /data-parameter-key="HEADER::X-Request-Id"/);
    assert.match(expandedHtml, /data-parameter-key="COOKIE::theme"/);

    const mobileHtml = renderShell(project, selected, "user", true);
    assert.match(mobileHtml, /id="mobile-search"/);
    assert.match(mobileHtml, /data-preserve-scroll="mobile-navigation"/);
    assert.match(mobileHtml, /aria-expanded="true"/);
});

test("renders filterable schemas as navigation links and a dedicated schema reference", () => {
    const selected = { group: project.groups[0], endpoint: project.groups[0].endpoints[0], key: "0:0" };
    const initialHtml = renderShell(project, selected, "");

    assert.match(initialHtml, /<details class="schemas-section" data-schema-section>/);
    assert.match(initialHtml, /class="schema-link" data-schema="UserResponse"/);
    assert.doesNotMatch(initialHtml, /schema-link-selected/);

    const schemaHtml = renderShell(project, undefined, "", false, {
        schemasOpen: true,
        selectedSchemaName: "UserResponse"
    });
    assert.match(schemaHtml, /data-schema-section open/);
    assert.match(schemaHtml, /schema-link schema-link-selected" data-schema="UserResponse"/);
    assert.match(schemaHtml, /<h1 class="endpoint-title[^>]*>UserResponse<\/h1>/);
    assert.match(schemaHtml, /array&lt;Address&gt;/);
    assert.match(schemaHtml, /required/);
    assert.match(schemaHtml, /enum: ADMIN, MEMBER/);
    assert.match(schemaHtml, /&quot;role&quot;.*&quot;ADMIN&quot;/);
    assert.match(schemaHtml, /class="copy-schema-example copy-code[^>]*data-copy="\{[^>]*aria-label="Copy schema example"/);
    assert.doesNotMatch(schemaHtml, /id="endpoint-url"/);

    const filteredHtml = renderShell(project, selected, "city");
    assert.match(filteredHtml, /data-schema="Address"/);
    assert.doesNotMatch(filteredHtml, /data-schema="UserResponse"/);

    const mobileHtml = renderShell(project, selected, "", true);
    assert.match(mobileHtml, /data-preserve-scroll="mobile-navigation"[\s\S]*data-schema="UserResponse"/);
});

test("renders scalar response fields when their schemas include empty properties", () => {
    const selected = {
        group: project.groups[0],
        endpoint: {
            ...project.groups[0].endpoints[0],
            responses: [{
                statusCode: "200",
                description: "Order returned",
                content: {
                    "application/json": {
                        type: "object",
                        properties: {
                            id: { type: "string", format: "uuid", properties: {} },
                            total: { type: "number", properties: {} },
                            paid: { type: "boolean", properties: {} }
                        }
                    }
                }
            }]
        },
        key: "0:0"
    };

    const html = renderShell(project, selected, "");

    assert.match(html, /<span class="text-sky-300">&quot;id&quot;<\/span>: <span class="text-mint">&quot;00000000-0000-0000-0000-000000000000&quot;<\/span>/);
    assert.match(html, /<span class="text-sky-300">&quot;total&quot;<\/span>: <span class="text-brand">0<\/span>/);
    assert.match(html, /<span class="text-sky-300">&quot;paid&quot;<\/span>: <span class="text-brand">true<\/span>/);
    assert.doesNotMatch(html, /<span class="text-sky-300">&quot;total&quot;<\/span>: \{\}/);
});

test("renders global authorization controls without exposing credentials in curl", () => {
    const selected = { group: project.groups[0], endpoint: project.groups[0].endpoints[0], key: "0:0" };
    const html = renderShell(project, selected, "", false, {
        authorization: { bearerAuth: "secret-token" },
        authorizationModalOpen: true
    });

    assert.match(html, /id="authorize-btn"/);
    assert.match(html, /Authorized · 1\/1/);
    assert.match(html, /id="authorization-form"/);
    assert.match(html, /value="secret-token"/);
    assert.doesNotMatch(html, /aria-label="Account"/);
    const curl = curlFor(project.groups[0].endpoints[0], "application/json", "", { "PATH::id": "1" }, project);
    assert.match(curl, /\{\{baseUrl\}\}\/users\/1/);
    assert.doesNotMatch(curl, /api\.muonica\.dev/);
    assert.match(curl, /MUONICA_BEARERAUTH/);
    assert.doesNotMatch(curl, /secret-token/);
});

test("uses a valid UUID placeholder for UUID path parameters", () => {
    const endpoint = {
        path: "/orders/{id}",
        parameters: [{ name: "id", location: "PATH", required: true, schema: { type: "string", format: "uuid" } }]
    };

    assert.deepEqual(pathValuesFor(endpoint), { id: "00000000-0000-0000-0000-000000000000" });
    assert.match(curlFor(endpoint, "", ""), /\/orders\/00000000-0000-0000-0000-000000000000/);
});

test("builds matching authorization headers and query parameters", () => {
    const securedEndpoint = {
        path: "/users",
        securityRequirements: ["bearerAuth", "apiKey"]
    };
    const securedProject = {
        securitySchemes: [
            { name: "bearerAuth", type: "HTTP", scheme: "bearer", parameterName: "Authorization", parameterLocation: "HEADER" },
            { name: "apiKey", type: "API_KEY", parameterName: "key", parameterLocation: "QUERY" }
        ]
    };

    assert.equal(normalizeBearerToken(" Bearer abc123 "), "abc123");
    assert.deepEqual(authorizationForEndpoint(securedEndpoint, securedProject, {
        bearerAuth: "Bearer abc123",
        apiKey: "key-value"
    }, "/users/1"), {
        path: "/users/1?key=key-value",
        headers: { Authorization: "Bearer abc123" },
        cookies: []
    });
});

test("applies one AND security group while preserving alternatives", () => {
    const endpoint = { path: "/admin", securityRequirements: [["bearerAuth", "apiKey"], ["apiKey"]] };
    const project = { securitySchemes: [
        { name: "bearerAuth", type: "HTTP", scheme: "bearer", parameterName: "Authorization", parameterLocation: "HEADER" },
        { name: "apiKey", type: "API_KEY", parameterName: "key", parameterLocation: "QUERY" }
    ] };
    assert.deepEqual(securityGroupsFor(endpoint), [["bearerAuth", "apiKey"], ["apiKey"]]);
    assert.deepEqual(authorizationForEndpoint(endpoint, project, { bearerAuth: "token", apiKey: "key" }, "/admin", 0), {
        path: "/admin?key=key", headers: { Authorization: "Bearer token" }, cookies: []
    });
    assert.match(curlFor(endpoint, "", "", {}, project, 1), /key/);
    assert.doesNotMatch(curlFor(endpoint, "", "", {}, project, 1), /Authorization/);
});

test("renders multipart curl and lets fetch receive FormData without a content type", async () => {
    const endpoint = { method: "POST", path: "/upload", parameters: [], securityRequirements: [], request: {
        content: { "multipart/form-data": { type: "object", properties: { file: { type: "string", format: "binary" } } } }
    } };
    assert.match(curlFor(endpoint, "multipart/form-data", ""), /-F 'file=@<selected-file>'/);
    assert.doesNotMatch(curlFor(endpoint, "multipart/form-data", '{"file":"wrong"}'), /-d/);
    const originalFetch = globalThis.fetch;
    let options;
    try {
        globalThis.fetch = async (_path, request) => { options = request; return { text: async () => "", ok: true, status: 200, statusText: "OK" }; };
        await sendEndpointRequest("POST", "/upload", new FormData(), "multipart/form-data", endpoint, { securitySchemes: [] });
        assert.equal(options.headers["Content-Type"], undefined);
        assert.ok(options.body instanceof FormData);
    } finally { globalThis.fetch = originalFetch; }
});

test("builds endpoint query, header, and cookie parameters from interactive values", () => {
    const endpoint = project.groups[0].endpoints[0];
    const values = {
        "PATH::id": "42",
        "QUERY::includeInactive": "true",
        "HEADER::X-Request-Id": "req-7",
        "COOKIE::theme": "dark"
    };
    assert.deepEqual(endpointParametersForRequest(endpoint, values, "/users/42"), {
        path: "/users/42?includeInactive=true",
        headers: { "X-Request-Id": "req-7" },
        cookies: [{ name: "theme", value: "dark" }]
    });
    const curl = curlFor(endpoint, "", "", values, project);
    assert.match(curl, /\/users\/42\?includeInactive=true/);
    assert.match(curl, /-H \"X-Request-Id: req-7\"/);
    assert.match(curl, /-b \"theme=dark\"/);
});

test("blocks an empty required interactive parameter", () => {
    const input = {
        dataset: { parameterKey: "PATH::id", parameterRequired: "true" },
        value: "",
        classList: { toggle: (name, enabled) => { input.invalid = enabled; } }
    };
    const error = { dataset: { parameterError: "PATH::id" }, hidden: true };
    const root = {
        querySelectorAll: selector => selector === "[data-parameter-key]" ? [input] : [error]
    };

    assert.equal(validateParameters(root), false);
    assert.equal(input.invalid, true);
    assert.equal(error.hidden, false);

    input.value = "42";
    assert.equal(validateParameters(root), true);
    assert.equal(input.invalid, false);
    assert.equal(error.hidden, true);
});

test("clears interactive request cookies after the request completes", async () => {
    const hadDocument = Object.hasOwn(globalThis, "document");
    const previousDocument = globalThis.document;
    const hadFetch = Object.hasOwn(globalThis, "fetch");
    const previousFetch = globalThis.fetch;
    const cookies = [];
    try {
        globalThis.document = { set cookie(value) { cookies.push(value); } };
        globalThis.fetch = async () => ({ text: async () => "", ok: true, status: 200, statusText: "OK" });
        await sendEndpointRequest("GET", "/users/42", "", null, project.groups[0].endpoints[0], project, {}, {
            "COOKIE::theme": "dark"
        });
        assert.deepEqual(cookies, ["theme=dark; path=/", "theme=; Max-Age=0; path=/"]);
    } finally {
        if (hadDocument) globalThis.document = previousDocument;
        else delete globalThis.document;
        if (hadFetch) globalThis.fetch = previousFetch;
        else delete globalThis.fetch;
    }
});

test("persists only non-empty authorization values", () => {
    const values = new Map();
    const storage = {
        getItem: key => values.get(key) || null,
        setItem: (key, value) => values.set(key, value),
        removeItem: key => values.delete(key)
    };

    assert.deepEqual(saveAuthorization({ bearerAuth: " token ", empty: "" }, storage), { bearerAuth: "token" });
    assert.deepEqual(loadAuthorization(storage), { bearerAuth: "token" });
    assert.deepEqual(clearAuthorization(storage), {});
    assert.deepEqual(loadAuthorization(storage), {});
});

test("keeps sparse endpoints calm", () => {
    const sparseProject = {
        ...project,
        groups: [{
            name: "Health",
            endpoints: [{
                method: "GET",
                path: "/health",
                summary: "Health check",
                description: "Returns service health.",
                parameters: [],
                request: null,
                responses: [],
                securityRequirements: [],
                documentationBlocks: [{ type: "slot", content: "", attributes: { name: "request" }, origin: "GENERATED" }]
            }]
        }]
    };
    const html = renderShell(sparseProject, { group: sparseProject.groups[0], endpoint: sparseProject.groups[0].endpoints[0], key: "0:0" }, "");

    assert.match(html, /Try this endpoint/);
    assert.doesNotMatch(html, /Authentication/);
    assert.doesNotMatch(html, /Parameters/);
    assert.doesNotMatch(html, /<table/);
});
