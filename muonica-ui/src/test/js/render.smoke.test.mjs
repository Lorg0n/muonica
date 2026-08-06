import assert from "node:assert/strict";
import test from "node:test";

import {
    authorizationForEndpoint,
    clearAuthorization,
    loadAuthorization,
    normalizeBearerToken,
    saveAuthorization
} from "../../main/resources/META-INF/resources/muonica/js/api.js";
import { curlFor, renderMarkdown, renderShell } from "../../main/resources/META-INF/resources/muonica/js/render.js";

const project = {
    name: "Muonica demo",
    version: "1.0",
    schemas: {},
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

    assert.match(html, /class="method-badge[^>]*method-get/);
    assert.match(html, /class="notice-block notice-warning"/);
    assert.match(html, /class="parameter-row"/);
    assert.doesNotMatch(html, /<table/);
    assert.match(html, /data-path-parameter="id"/);
    assert.match(html, /id="code-curl"/);
    assert.match(html, /<details class="response-disclosure[^>]*" open>/);

    const securityIndex = html.indexOf("Authentication");
    const parametersIndex = html.indexOf("Parameters");
    const requestIndex = html.indexOf("Request body");
    const responsesIndex = html.indexOf("Responses");
    const diagramIndex = html.indexOf("Diagram");
    assert.ok(securityIndex < parametersIndex);
    assert.ok(parametersIndex < requestIndex);
    assert.ok(requestIndex < responsesIndex);
    assert.ok(responsesIndex < diagramIndex);

    const mobileHtml = renderShell(project, selected, "user", true);
    assert.match(mobileHtml, /id="mobile-search"/);
    assert.match(mobileHtml, /aria-expanded="true"/);
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
    const curl = curlFor(project.groups[0].endpoints[0], "application/json", "", { id: "1" }, project);
    assert.match(curl, /MUONICA_BEARERAUTH/);
    assert.doesNotMatch(curl, /secret-token/);
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
