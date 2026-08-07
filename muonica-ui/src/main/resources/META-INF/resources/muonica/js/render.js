const methodStyles = {
    GET: "method-get",
    POST: "method-post",
    PUT: "method-put",
    PATCH: "method-patch",
    DELETE: "method-delete"
};

export const escapeHtml = (value = "") => String(value)
    .replaceAll("&", "&amp;").replaceAll("<", "&lt;")
    .replaceAll(">", "&gt;").replaceAll('"', "&quot;").replaceAll("'", "&#039;");

const copyIcon = '<svg class="w-4 h-4" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><rect x="9" y="9" width="12" height="12" rx="2"/><path d="M5 15V5a2 2 0 0 1 2-2h10"/></svg>';

function methodBadge(method, compact = false) {
    const value = (method || "API").toUpperCase();
    const style = methodStyles[value] || "method-api";
    return `<span class="method-badge ${compact ? "method-badge-compact" : ""} ${style}">${escapeHtml(value)}</span>`;
}

function schemaType(schema) {
    if (!schema) return "unknown";
    if (schema.ref) return schema.ref.replace(/^.*\//, "");
    return [schema.type, schema.format].filter(Boolean).join(" · ") || "object";
}

function defaultPathValue(parameter) {
    const name = (parameter.name || "value").toLowerCase();
    if (name === "batchid") return "batch_8f2ac1";
    if (name === "id" || name.endsWith("id")) return "1";
    return `${parameter.name || "value"}_value`;
}

function pathValue(pathValues, parameter) {
    return Object.prototype.hasOwnProperty.call(pathValues || {}, parameter.name)
        ? pathValues[parameter.name]
        : defaultPathValue(parameter);
}

export function resolvePath(path, pathValues = {}) {
    return (path || "").replaceAll(/\{([^}]+)\}/g, (_, name) => {
        const value = pathValues[name];
        return value === undefined || value === "" ? `{${name}}` : encodeURIComponent(value);
    });
}

function authPlaceholder(scheme) {
    const value = `${scheme?.name || "AUTH"}`.toUpperCase().replace(/[^A-Z0-9]+/g, "_").replace(/^_|_$/g, "");
    return `$MUONICA_${value || "AUTH_VALUE"}`;
}

function authCurlValue(scheme) {
    const placeholder = authPlaceholder(scheme);
    return String(scheme?.type || "").toUpperCase() === "HTTP" && String(scheme?.scheme || "").toLowerCase() === "bearer"
        ? `Bearer ${placeholder}`
        : String(scheme?.type || "").toUpperCase() === "HTTP" && scheme?.scheme
            ? `${scheme.scheme} ${placeholder}`
            : placeholder;
}

export function curlFor(endpoint, contentType, body, pathValues = {}, project = {}) {
    let path = resolvePath(endpoint.path, pathValues);
    const schemes = Object.fromEntries((project.securitySchemes || []).map(scheme => [scheme.name, scheme]));
    const browserOrigin = globalThis.location?.origin;
    const baseUrl = browserOrigin && browserOrigin !== "null" ? browserOrigin : "{{baseUrl}}";
    const url = () => `${baseUrl}${path}`;
    const lines = [`curl -X ${endpoint.method} ${url()}`];
    (endpoint.securityRequirements || []).forEach(name => {
        const scheme = schemes[name];
        if (!scheme) return;
        const parameterName = scheme.parameterName || "Authorization";
        const location = String(scheme.parameterLocation || "HEADER").toUpperCase();
        const value = authCurlValue(scheme);
        if (location === "QUERY") {
            path += `${path.includes("?") ? "&" : "?"}${encodeURIComponent(parameterName)}=${value}`;
            lines[0] = `curl -X ${endpoint.method} ${url()}`;
        } else if (location !== "COOKIE") {
            lines.push(`  -H \"${parameterName}: ${value}\"`);
        } else {
            lines.push(`  -b \"${parameterName}=${value}\"`);
        }
    });
    if (contentType) lines.push(`  -H \"Content-Type: ${contentType}\"`);
    if (body !== undefined && body !== "") {
        const indented = body.split("\n").map(line => `  ${line}`).join("\n");
        lines.push(`  -d '${indented}'`);
    }
    return lines.map((line, index) => index === lines.length - 1 ? line : `${line} \\`).join("\n");
}

export function highlightedJson(code) {
    return escapeHtml(code)
        .replace(/(&quot;[^&]+&quot;)(?=:)/g, '<span class="text-sky-300">$1</span>')
        .replace(/: (&quot;.*?&quot;)/g, ': <span class="text-mint">$1</span>')
        .replace(/: (-?\d+(?:\.\d+)?|true|false|null)([,}]?)/g, ': <span class="text-brand">$1</span>$2');
}

export function highlightedCurl(code) {
    return escapeHtml(code)
        .replace(/^(curl)/, '<span class="text-sky-300">$1</span>')
        .replace(/(-X|-H|-d)(?=\s)/g, '<span class="text-brand">$1</span>')
        .replace(/(https?:\/\/[^\s\\]+)/g, '<span class="text-mint">$1</span>');
}

export function exampleForSchema(schema, schemas = {}, depth = 0) {
    if (!schema || depth > 7) return null;
    if (schema.enumValues?.length) return schema.enumValues[0];
    if (schema.ref) {
        const name = schema.ref.replace(/^.*\//, "");
        return schemas[name] ? exampleForSchema(schemas[name], schemas, depth + 1) : name;
    }
    if (schema.type === "array") return [exampleForSchema(schema.items, schemas, depth + 1)];
    if (schema.type === "object" || schema.properties) {
        return Object.fromEntries(Object.entries(schema.properties || {})
            .map(([name, child]) => [name, exampleForSchema(child, schemas, depth + 1)]));
    }
    if (["integer", "number"].includes(schema.type)) return 0;
    if (schema.type === "boolean") return true;
    if (schema.format === "uuid") return "00000000-0000-0000-0000-000000000000";
    if (schema.format === "date") return "2026-01-01";
    if (schema.format === "date-time") return "2026-01-01T00:00:00Z";
    return schema.format === "binary" ? "file.bin" : "string";
}

function responseExampleForSchema(schema, schemas = {}, depth = 0) {
    if (!schema || depth > 7) return null;
    if (schema.type === "array") return [responseExampleForSchema(schema.items, schemas, depth + 1)];
    if (schema.ref) {
        const name = schema.ref.replace(/^.*\//, "");
        return schemas[name] ? responseExampleForSchema(schemas[name], schemas, depth + 1) : null;
    }
    if (schema.type === "object" || schema.properties) {
        return Object.fromEntries(Object.entries(schema.properties || {})
            .map(([name, child]) => [name, responseExampleForSchema(child, schemas, depth + 1)]));
    }
    if (["integer", "number"].includes(schema.type)) return 0;
    if (schema.type === "boolean") return true;
    return "hello";
}

function pathParameterInputs(endpoint, pathValues = {}) {
    const parameters = (endpoint.parameters || []).filter(parameter => parameter.location === "PATH");
    if (!parameters.length) return "";
    return `<div class="request-paths">
        <div class="request-path-heading" aria-hidden="true"><span>Name</span><span>Description</span></div>
        <div class="request-path-list">
            ${parameters.map(parameter => `<div class="request-path-row">
                <div class="request-path-details">
                    <p class="request-path-name">${escapeHtml(parameter.name)}${parameter.required ? ' <span class="request-path-required">* required</span>' : ""}</p>
                    <p class="request-path-type">${escapeHtml(schemaType(parameter.schema))}</p>
                    <p class="request-path-location">(path)</p>
                </div>
                <div class="request-path-value">
                    ${parameter.description ? `<p class="request-path-description">${escapeHtml(parameter.description)}</p>` : ""}
                    <label>
                        <span class="sr-only">Value for ${escapeHtml(parameter.name)}</span>
                        <input data-path-parameter="${escapeHtml(parameter.name)}" value="${escapeHtml(pathValue(pathValues, parameter))}" placeholder="${escapeHtml(parameter.name)}" class="request-path-input" autocomplete="off" aria-label="Value for ${escapeHtml(parameter.name)}">
                    </label>
                </div>
            </div>`).join("")}
        </div>
    </div>`;
}

function codePanel(endpoint, project, state = {}) {
    const content = Object.entries(endpoint.request?.content || {});
    const [contentType, schema] = content[0] || [null, null];
    const defaultCode = schema ? JSON.stringify(exampleForSchema(schema, project.schemas), null, 2) : "";
    const code = state.requestBody ?? defaultCode;
    const curl = curlFor(endpoint, contentType, code, state.pathValues || {}, project);
    const hasBody = content.length > 0;
    const protectedEndpoint = (endpoint.securityRequirements || []).length > 0;
    const activeTab = state.activeTab === "curl" || !hasBody ? "curl" : "json";
    const description = endpoint.request?.description || (hasBody
        ? `Send ${contentType} data to this endpoint.`
        : "This endpoint does not define a request body.");

    return `<div data-code-panel="true">
        <div class="section-heading">
            <div><h2 class="section-title">${hasBody ? "Request body" : "Try this endpoint"}</h2><p class="mt-1.5 text-sm text-ink-400">${escapeHtml(description)}${hasBody ? " Click into the code below to edit it." : " The request is sent without a body."}</p></div>
            <span class="section-meta">interactive</span>
        </div>

        <div class="request-panel content-surface">
            ${pathParameterInputs(endpoint, state.pathValues || {})}
            <div class="panel-header">
                <div class="flex items-center gap-1">
                    ${hasBody ? `<button class="code-tab text-xs font-semibold px-3 py-1.5 rounded-md ${activeTab === "json" ? "code-tab-active" : "text-ink-400 hover:text-white hover:bg-ink-800"}" data-tab="json" type="button">JSON</button>` : ""}
                    <button class="code-tab text-xs font-semibold px-3 py-1.5 rounded-md ${activeTab === "curl" ? "code-tab-active" : "text-ink-400 hover:text-white hover:bg-ink-800"}" data-tab="curl" type="button">cURL</button>
                </div>
                <div class="flex items-center gap-2">
                    <span id="json-error" class="hidden text-[11px] font-medium text-brand" role="alert"></span>
                    <button class="copy-code w-7 h-7 flex items-center justify-center rounded-md text-ink-400 hover:text-white hover:bg-ink-800 transition-colors" data-copy-kind="code" aria-label="Copy code" type="button">${copyIcon}</button>
                </div>
            </div>
            <div class="overflow-x-auto code-scrollbar">
                ${hasBody ? `<pre id="code-json" contenteditable="true" spellcheck="false" data-content-type="${escapeHtml(contentType)}" class="${activeTab === "json" ? "" : "hidden "}code-editor mono text-[13px] leading-6 text-ink-200 px-4 py-4 outline-none whitespace-pre focus:bg-ink-850/40">${highlightedJson(code)}</pre>` : ""}
                <pre id="code-curl" class="${activeTab === "curl" ? "" : "hidden "}code-curl mono text-[13px] leading-6 text-ink-200 px-4 py-4 whitespace-pre-wrap">${highlightedCurl(curl)}</pre>
            </div>
            <div class="panel-footer">
                <span class="text-[11px] text-ink-500">${protectedEndpoint ? "Saved authorization is applied automatically." : "Requests are sent to this API."}</span>
                <button id="send-btn" class="send-button flex items-center gap-2 disabled:opacity-60 disabled:cursor-wait text-xs font-semibold px-4 py-2 rounded-lg transition-colors shrink-0" type="button">
                    <svg class="w-3.5 h-3.5" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.4"><path d="m5 12 14 0M13 6l6 6-6 6" stroke-linecap="round" stroke-linejoin="round"/></svg>
                    Send request
                </button>
            </div>
        </div>

        <div id="response-panel" class="response-panel content-surface mt-5" aria-live="polite">
            <div class="panel-header">
                <div class="flex items-center gap-3">
                    <span class="text-xs font-semibold text-ink-400">Response</span>
                    <span id="response-status" class="mono text-xs font-bold px-2 py-0.5 rounded border text-ink-400 border-ink-700 bg-ink-800">Waiting</span>
                    <span id="response-time" class="text-[11px] text-ink-500">Not sent yet</span>
                </div>
                <button class="copy-response w-7 h-7 flex items-center justify-center rounded-md text-ink-400 hover:text-white hover:bg-ink-800 transition-colors" aria-label="Copy response" type="button">${copyIcon}</button>
            </div>
            <div class="overflow-x-auto code-scrollbar">
                <pre id="response-body" class="mono text-[13px] leading-6 text-ink-200 px-4 py-4 whitespace-pre"><span class="text-ink-500">{
  "message": "Send a request to see the response"
}</span></pre>
            </div>
        </div>
        <p class="text-xs text-ink-500 mt-3">The response below shows the result returned by your API.</p>
    </div>`;
}

function parameterCard(parameters, heading) {
    if (!parameters.length) return "";
    return `<div class="parameter-group">
        <h3 class="parameter-group-title">${escapeHtml(heading)}</h3>
        <div class="parameter-list content-surface">
            ${parameters.map(parameter => `<div class="parameter-row">
                <div>
                    <p class="parameter-name">${escapeHtml(parameter.name)}</p>
                    ${parameter.description ? `<p class="parameter-description">${escapeHtml(parameter.description)}</p>` : ""}
                </div>
                <div>
                    <p class="parameter-location">${escapeHtml(String(parameter.location || "parameter").toLowerCase())}</p>
                    <p class="parameter-type">${escapeHtml(schemaType(parameter.schema))}</p>
                    <span class="parameter-required ${parameter.required ? "" : "parameter-optional"}">${parameter.required ? "required" : "optional"}</span>
                </div>
            </div>`).join("")}
        </div>
    </div>`;
}

function responseList(responses, schemas = {}) {
    if (!responses?.length) return "";
    const firstSuccess = responses.findIndex(response => /^2/.test(response.statusCode || ""));
    return `<section class="section-block"><div class="section-heading"><h2 class="section-title">Responses</h2><span class="section-meta">${responses.length} ${responses.length === 1 ? "outcome" : "outcomes"}</span></div><div class="space-y-3">${responses.map((response, index) => {
        const success = /^2/.test(response.statusCode || "");
        const schema = Object.values(response.content || {})[0];
        const example = schema ? JSON.stringify(responseExampleForSchema(schema, schemas), null, 2) : null;
        const responseHeader = `<div class="flex flex-1 min-w-0 gap-4 items-center p-4">
                <span class="mono font-bold ${success ? "text-mint bg-mint/5" : "text-brand bg-brand/5"} px-2 py-1 rounded text-sm shrink-0">${escapeHtml(response.statusCode || "—")}</span>
                <span class="text-sm text-ink-300">${escapeHtml(response.description || "Response")}</span>
            </div>`;
        return example ? `<details class="response-disclosure content-surface overflow-hidden" ${index === firstSuccess ? "open" : ""}>
            <summary class="flex items-center cursor-pointer hover:bg-ink-850/50 transition-colors">
                ${responseHeader}
                <svg class="response-chevron w-4 h-4 ml-auto mr-4 shrink-0 text-ink-500 transition-transform" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><path d="m6 9 6 6 6-6" stroke-linecap="round" stroke-linejoin="round"/></svg>
            </summary>
            <pre class="response-example mono text-[13px] leading-6 text-ink-200 px-4 py-4 whitespace-pre overflow-x-auto">${highlightedJson(example)}</pre>
        </details>` : `<div class="content-surface overflow-hidden">${responseHeader}</div>`;
    }).join("")}</div></section>`;
}

function markdownInline(value) {
    let result = escapeHtml(value);
    result = result.replace(/`([^`]+)`/g, '<code class="mono text-brand bg-ink-850 rounded px-1.5 py-0.5 text-[.9em]">$1</code>');
    result = result.replace(/\*\*([^*]+)\*\*/g, "<strong>$1</strong>");
    result = result.replace(/\*([^*]+)\*/g, "<em>$1</em>");
    result = result.replace(/\[([^\]]+)\]\(((?:https?:\/\/|\/|\.|#)[^\s)]+)\)/g,
        '<a class="text-brand hover:underline" href="$2" target="_blank" rel="noreferrer">$1</a>');
    return result;
}

export function renderMarkdown(markdown = "") {
    const lines = String(markdown).replaceAll("\r\n", "\n").split("\n");
    const output = [];
    let listType = null;
    let inCode = false;
    let codeLanguage = "";
    let codeLines = [];
    let codeFence = null;

    const closeList = () => {
        if (listType) {
            output.push(`</${listType}>`);
            listType = null;
        }
    };
    const closeCode = () => {
        if (inCode) {
            output.push(`<pre class="mt-3 overflow-x-auto rounded-lg bg-ink-950 border border-ink-800 p-4 mono text-[13px] leading-6 text-ink-200"><code data-language="${escapeHtml(codeLanguage)}">${escapeHtml(codeLines.join("\n"))}</code></pre>`);
            inCode = false;
            codeLanguage = "";
            codeLines = [];
            codeFence = null;
        }
    };

    for (const line of lines) {
        const fence = line.match(/^\s*(`{3,}|~{3,})\s*([\w-]*)\s*$/);
        if (fence) {
            closeList();
            if (inCode && fence[1][0] === codeFence[0] && fence[1].length >= codeFence.length) closeCode();
            else if (inCode) codeLines.push(line);
            else {
                inCode = true;
                codeFence = fence[1];
                codeLanguage = fence[2] || "";
            }
            continue;
        }
        if (inCode) {
            codeLines.push(line);
            continue;
        }
        const heading = line.match(/^\s*(#{1,6})\s+(.+?)\s*#*\s*$/);
        if (heading) {
            closeList();
            const level = heading[1].length;
            const styles = {1: "text-3xl", 2: "text-2xl", 3: "text-xl", 4: "text-lg", 5: "text-base", 6: "text-sm"};
            output.push(`<h${level} class="${styles[level]} font-bold text-white ${level === 1 ? "mb-4" : "mt-6 mb-3"}">${markdownInline(heading[2])}</h${level}>`);
            continue;
        }
        const unordered = line.match(/^\s*[-*]\s+(.+)$/);
        const ordered = line.match(/^\s*\d+\.\s+(.+)$/);
        if (unordered || ordered) {
            const nextList = ordered ? "ol" : "ul";
            if (listType !== nextList) {
                closeList();
                listType = nextList;
                output.push(`<${listType} class="${listType === "ol" ? "list-decimal" : "list-disc"} pl-6 space-y-1 text-ink-300">`);
            }
            output.push(`<li>${markdownInline((unordered || ordered)[1])}</li>`);
            continue;
        }
        const quote = line.match(/^\s*>\s?(.*)$/);
        if (quote) {
            closeList();
            output.push(`<blockquote>${markdownInline(quote[1])}</blockquote>`);
            continue;
        }
        if (/^\s*([-*_])(?:\s*\1){2,}\s*$/.test(line)) {
            closeList();
            output.push("<hr>");
            continue;
        }
        if (!line.trim()) {
            closeList();
            continue;
        }
        closeList();
        output.push(`<p>${markdownInline(line)}</p>`);
    }
    closeList();
    closeCode();
    return output.join("");
}

function originLabel(block) {
    if (block.origin === "GENERATED") return "auto-generated";
    if (block.origin === "INHERITED") return "inherited";
    return "documented";
}

function blockSource(block) {
    const source = block.attributes?.source;
    const line = block.attributes?.line;
    return source ? `<span class="text-[10px] text-ink-500">${escapeHtml(source.split("/").pop())}${line ? `:${escapeHtml(line)}` : ""}</span>` : "";
}

function noticeBlock(block) {
    const level = block.attributes?.level || "info";
    const normalizedLevel = ["info", "warning", "danger"].includes(level) ? level : "info";
    const icons = { info: "i", warning: "!", danger: "!" };
    return `<aside class="notice-block notice-${normalizedLevel}" role="note">
        <div class="notice-heading"><span class="notice-icon" aria-hidden="true">${icons[normalizedLevel]}</span>${escapeHtml(normalizedLevel)}</div>
        <div class="markdown">${renderMarkdown(block.content)}</div>
    </aside>`;
}

function diagramBlock(block) {
    const renderer = block.attributes?.renderer || "unknown";
    return `<section class="section-block diagram-panel" data-diagram="${escapeHtml(renderer)}">
        <div class="panel-header"><div><h2 class="section-title">Diagram</h2><p class="section-meta mt-1">${escapeHtml(renderer)}</p></div><button data-diagram-render="${escapeHtml(renderer)}" class="diagram-action text-xs font-semibold rounded-md px-3 py-1.5" type="button">Render</button></div>
        <pre class="diagram-source mono text-[13px] leading-6 px-5 py-4 overflow-x-auto whitespace-pre-wrap">${escapeHtml(block.content)}</pre>
        <div class="diagram-output hidden border-t border-ink-800 p-5"></div>
    </section>`;
}

function securitySection(endpoint, project, block, authorization = {}) {
    const requirements = endpoint.securityRequirements || [];
    if (!requirements.length && block.origin === "GENERATED") return "";
    const schemes = Object.fromEntries((project.securitySchemes || []).map(scheme => [scheme.name, scheme]));
    const configured = requirements.filter(name => authorization[name]).length;
    return `<section class="section-block"><div class="section-heading"><h2 class="section-title">Authentication</h2><span class="section-meta">${requirements.length ? `${configured}/${requirements.length} configured` : escapeHtml(originLabel(block))}</span></div>
        <div class="auth-surface content-surface">${requirements.length ? `<div>${requirements.map(name => {
            const scheme = schemes[name];
            const details = scheme ? [scheme.type, scheme.scheme, scheme.bearerFormat].filter(Boolean).join(" · ") : "Security requirement";
            const placement = scheme?.parameterName ? `${scheme.parameterName}${scheme.parameterLocation ? ` · ${String(scheme.parameterLocation).toLowerCase()}` : ""}` : "Operation security requirement";
            const status = authorization[name] ? "configured" : "not configured";
            return `<div class="auth-row"><div><p class="auth-name">${escapeHtml(name)}</p><p class="auth-details">${escapeHtml(details)}</p></div><div class="text-right"><p class="auth-label">${escapeHtml(placement)}</p><p class="auth-status ${authorization[name] ? "auth-status-ready" : "auth-status-missing"}">${status}</p></div></div>`;
        }).join("")}</div>` : `<p class="text-sm text-ink-400">No authentication is required for this operation.</p>`}</div>
    </section>`;
}

function parametersSection(endpoint, block) {
    const otherParameters = (endpoint.parameters || []).filter(parameter => parameter.location !== "PATH");
    if (!otherParameters.length) return "";
    return `<section class="section-block"><div class="section-heading"><h2 class="section-title">Parameters</h2><span class="section-meta">${escapeHtml(originLabel(block))}</span></div><div>${parameterCard(otherParameters, "Query and header parameters")}</div></section>`;
}

function genericDocumentationBlock(block) {
    return `<section class="section-block content-surface p-5"><div class="flex items-center justify-between gap-3 mb-3"><p class="section-meta">${escapeHtml(block.type)}</p>${blockSource(block)}</div><pre class="whitespace-pre-wrap text-sm text-ink-300 font-sans leading-relaxed">${escapeHtml(block.content)}</pre></section>`;
}

export function documentationBlocks(blocks, endpoint, project, state = {}) {
    const rendered = block => {
        if (block.type === "markdown") return `<section class="section-block prose-documentation markdown">${renderMarkdown(block.content)}${blockSource(block)}</section>`;
        if (block.type === "notice") return noticeBlock(block);
        if (block.type === "diagram") return diagramBlock(block);
        if (block.type === "slot") {
            const name = block.attributes?.name;
            if (name === "request") return `<section class="section-block">${codePanel(endpoint, project, {...state, pathValues: state.pathValues})}</section>`;
            if (name === "responses") return responseList(endpoint.responses, project.schemas);
            if (name === "parameters") return parametersSection(endpoint, block);
            if (name === "security") return securitySection(endpoint, project, block, state.authorization);
        }
        return genericDocumentationBlock(block);
    };
    const authored = (blocks || []).filter(block => block.type === "markdown" || block.type === "notice");
    const knownSlots = new Set(["security", "parameters", "request", "responses"]);
    const diagrams = (blocks || []).filter(block => block.type === "diagram" || (block.type !== "markdown" && block.type !== "notice" && (block.type !== "slot" || !knownSlots.has(block.attributes?.name))));
    const slotBlocks = (blocks || []).filter(block => block.type === "slot" && block.attributes?.name);
    const orderedSlots = ["security", "parameters", "request", "responses"]
        .flatMap(name => slotBlocks.filter(block => block.attributes.name === name));
    return [...authored, ...orderedSlots, ...diagrams].map(rendered).join("");
}

function authorizationFieldLabel(scheme) {
    if (String(scheme.type || "").toUpperCase() === "HTTP" && String(scheme.scheme || "").toLowerCase() === "bearer") {
        return "Bearer token";
    }
    if (String(scheme.type || "").toUpperCase() === "API_KEY") return "API key";
    return `${scheme.name} value`;
}

function authorizationSchemeDetails(scheme) {
    const type = String(scheme.type || "").toUpperCase() === "API_KEY" ? "API key" : String(scheme.scheme || scheme.type || "HTTP");
    const location = String(scheme.parameterLocation || "HEADER").toLowerCase();
    return `${type} · ${scheme.parameterName || "Authorization"} in ${location}`;
}

function authorizationModal(project, state = {}) {
    const schemes = project.securitySchemes || [];
    if (!state.authorizationModalOpen || !schemes.length) return "";
    const values = state.authorization || {};
    const configured = schemes.filter(scheme => values[scheme.name]).length;
    return `<div id="auth-modal" class="auth-modal-backdrop" role="presentation" data-auth-backdrop>
        <section class="auth-modal" role="dialog" aria-modal="true" aria-labelledby="auth-modal-title">
            <div class="auth-modal-header">
                <div><p class="eyebrow mb-2">Request authorization</p><h2 id="auth-modal-title" class="text-xl font-bold text-white">Authorize API requests</h2></div>
                <button id="auth-close" class="auth-close" type="button" aria-label="Close authorization dialog">×</button>
            </div>
            <p class="auth-modal-intro">Credentials stay in this browser and are added only to requests for the matching protected schemes.</p>
            <form id="authorization-form" class="auth-form">
                ${schemes.map((scheme, index) => `<div class="auth-field">
                    <div class="auth-field-heading"><label for="auth-value-${index}">${escapeHtml(authorizationFieldLabel(scheme))}</label><span class="section-meta">${escapeHtml(scheme.name)}</span></div>
                    <div class="auth-input-wrap"><input id="auth-value-${index}" data-auth-scheme="${escapeHtml(scheme.name)}" class="auth-input" type="password" value="${escapeHtml(values[scheme.name] || "")}" placeholder="Enter ${escapeHtml(authorizationFieldLabel(scheme).toLowerCase())}" autocomplete="off" spellcheck="false"><button class="auth-toggle" data-auth-toggle="auth-value-${index}" type="button">Show</button></div>
                    <p class="auth-field-help">${escapeHtml(authorizationSchemeDetails(scheme))}</p>
                </div>`).join("")}
                <p class="auth-security-note">${configured ? `${configured} of ${schemes.length} scheme${schemes.length === 1 ? "" : "s"} configured.` : "No credentials configured yet."} Values are stored with browser local storage.</p>
                <div class="auth-modal-actions"><button id="clear-authorization" class="auth-clear" type="button" ${configured ? "" : "disabled"}>Clear all</button><div class="flex items-center gap-2"><button id="auth-cancel" class="auth-cancel" type="button">Cancel</button><button class="send-button auth-save" type="submit">Save authorization</button></div></div>
            </form>
        </section>
    </div>`;
}

export function renderShell(project, selected, query, menuOpen = false, state = {}) {
    const groups = project.groups || [];
    const endpoint = selected?.endpoint;
    const title = endpoint ? endpoint.summary || `${endpoint.method} ${endpoint.path}` : project.name || "Muonica";
    const pathParameters = (endpoint?.parameters || []).filter(parameter => parameter.location === "PATH");
    const description = endpoint?.description || selected?.group.description || project.description;
    const resolvedPathValues = state.pathValues || Object.fromEntries(pathParameters.map(parameter => [parameter.name, defaultPathValue(parameter)]));
    const endpointPath = endpoint?.path || "";
    const responses = endpoint?.responses || [];

    const schemes = project.securitySchemes || [];
    const configuredSchemes = schemes.filter(scheme => state.authorization?.[scheme.name]).length;
    const authorizationLabel = configuredSchemes ? `Authorized · ${configuredSchemes}/${schemes.length}` : "Authorize";

    return `<header class="doc-header sticky top-0 z-30 flex items-center justify-between h-16 px-4 lg:px-6 border-b backdrop-blur">
        <div class="flex items-center gap-3">
            <button id="menu-toggle" class="lg:hidden text-ink-400 hover:text-white" aria-label="Toggle navigation" aria-expanded="${menuOpen}"><svg class="w-5 h-5" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><path d="M4 6h16M4 12h16M4 18h16" stroke-linecap="round"/></svg></button>
            <span class="text-white font-semibold tracking-tight text-[15px]">muonica</span>
            <span class="hidden sm:inline-block ml-1 text-[11px] font-medium text-ink-400 border border-ink-700 rounded px-1.5 py-0.5">API</span>
        </div>

        <div class="hidden md:flex items-center flex-1 max-w-md mx-8">
            <label class="flex items-center gap-2 w-full bg-ink-900 border border-ink-800 rounded-lg px-3 py-2 text-ink-400 hover:border-ink-600 transition-colors cursor-text">
                <svg class="w-4 h-4 shrink-0" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><circle cx="11" cy="11" r="7"/><path d="m21 21-4.3-4.3" stroke-linecap="round"/></svg>
                <input id="search" class="docs-search-input w-full bg-transparent text-sm outline-none placeholder:text-ink-500 text-white" dir="ltr" placeholder="Search documentation" value="${escapeHtml(query)}" autocomplete="off">
                <kbd class="ml-auto text-[10px] text-ink-500 border border-ink-700 rounded px-1.5 py-0.5">⌘K</kbd>
            </label>
        </div>

        <div class="flex items-center gap-3 lg:gap-5 text-sm text-ink-300">
            <span class="hidden sm:inline text-ink-400">v${escapeHtml(project.version || "1.0")}</span>
            <span class="hidden sm:inline w-1.5 h-1.5 rounded-full bg-ink-600"></span>
            ${schemes.length ? `<button id="authorize-btn" class="authorize-button ${configuredSchemes ? "authorize-button-ready" : ""}" type="button" aria-haspopup="dialog" aria-expanded="${Boolean(state.authorizationModalOpen)}">${escapeHtml(authorizationLabel)}</button>` : ""}
        </div>
    </header>
    ${menuOpen ? `<div class="fixed inset-x-0 top-16 z-30 max-h-[calc(100vh-64px)] overflow-y-auto border-b border-ink-800 bg-ink-950 p-6 lg:hidden">
        <label class="flex items-center gap-2 w-full bg-ink-900 border border-ink-800 rounded-lg px-3 py-2 text-ink-400 mb-6">
            <svg class="w-4 h-4 shrink-0" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><circle cx="11" cy="11" r="7"/><path d="m21 21-4.3-4.3" stroke-linecap="round"/></svg>
            <input id="mobile-search" class="docs-search-input w-full bg-transparent text-sm outline-none placeholder:text-ink-500 text-white" dir="ltr" placeholder="Search documentation" value="${escapeHtml(query)}" autocomplete="off">
        </label>
        <nav aria-label="API navigation">${renderNavigation(groups, selected?.key, query)}</nav>
    </div>` : ""}

    ${authorizationModal(project, state)}

    <div class="flex">
        <aside class="hidden lg:block doc-nav w-64 shrink-0 border-r border-ink-800 h-[calc(100vh-64px)] sticky top-16 overflow-y-auto px-5 py-8">
            <p class="eyebrow mb-3">Documentation</p>
            <nav id="sidebar" aria-label="API navigation">${renderNavigation(groups, selected?.key, query)}</nav>
        </aside>

        <main class="docs-main flex-1 min-w-0 px-6 lg:px-12 py-10 lg:py-14">
            ${endpoint ? `<div class="mx-auto max-w-[1180px]">
                <div class="endpoint-hero">
                    <p class="eyebrow mb-3">${escapeHtml(selected.group.name || "API documentation")}</p>
                    <h1 class="endpoint-title text-4xl lg:text-5xl font-extrabold text-white tracking-tight mb-4">${escapeHtml(title)}</h1>
                    ${description ? `<p class="endpoint-description text-[15px] text-ink-300 leading-7 mb-7">${escapeHtml(description)}</p>` : ""}
                    <div class="endpoint-line" aria-label="API endpoint">
                        ${methodBadge(endpoint.method)}
                        <code id="endpoint-url" class="endpoint-path mono text-[13px] text-ink-200 px-2 py-1.5 flex-1">${escapeHtml(endpointPath)}</code>
                        <button class="copy-code shrink-0 w-8 h-8 flex items-center justify-center rounded-md text-ink-400 hover:text-white hover:bg-ink-800 transition-colors" data-copy="${escapeHtml(endpointPath)}" aria-label="Copy endpoint path" type="button">${copyIcon}</button>
                    </div>
                </div>

                <div class="docs-layout grid grid-cols-1 xl:grid-cols-[minmax(0,850px)_240px] gap-10 lg:gap-14">
                    <article class="docs-article">
                        ${documentationBlocks(endpoint.documentationBlocks, endpoint, project, {...state, pathValues: resolvedPathValues})}
                    </article>
                    <aside class="docs-aside hidden xl:block" aria-label="Endpoint details">
                        <div class="aside-card">
                            <p class="eyebrow mb-4">At a glance</p>
                            <dl class="space-y-4">
                                <div><dt class="section-meta">Method</dt><dd class="mono mt-1 text-sm text-white">${escapeHtml(endpoint.method || "API")}</dd></div>
                                <div><dt class="section-meta">Responses</dt><dd class="mt-1 text-sm text-white">${responses.length || "—"}</dd></div>
                                <div><dt class="section-meta">Reference</dt><dd class="mt-1 text-xs leading-5"><a class="text-brand hover:text-white hover:underline" href="./openapi.json">OpenAPI schema →</a></dd></div>
                            </dl>
                        </div>
                    </aside>
                </div>
            </div>` : renderEmpty(project)}
        </main>
    </div>`;
}

function renderNavigation(groups, selectedKey, query) {
    const term = query.trim().toLowerCase();
    return groups.map((group, groupIndex) => {
        const endpoints = (group.endpoints || []).filter(endpoint => !term || [endpoint.path, endpoint.summary, endpoint.description, group.name].filter(Boolean).join(" ").toLowerCase().includes(term));
        if (!endpoints.length) return "";
        return `<div class="mb-7"><p class="eyebrow mb-2">${escapeHtml(group.name || "API")}</p>
        <nav class="space-y-0.5 text-[14px]">${endpoints.map(endpoint => {
            const key = `${groupIndex}:${group.endpoints.indexOf(endpoint)}`;
            const selected = key === selectedKey;
            const method = (endpoint.method || "API").toUpperCase();
            let shortMethod = method;
            if (method === "DELETE") shortMethod = "DEL";
            if (method === "PATCH") shortMethod = "PAT";
            if (method === "OPTIONS") shortMethod = "OPT";

            return `<button class="endpoint-link w-full text-left flex items-center gap-3 -mx-3 px-3 py-2 rounded-lg transition-colors ${selected ? "text-white bg-ink-800 font-medium" : "text-ink-400 hover:text-ink-200 hover:bg-ink-900"}" data-endpoint="${key}" ${selected ? 'aria-current="page"' : ""} type="button">
                <span class="sidebar-method text-method-${method.toLowerCase()}">${escapeHtml(shortMethod)}</span>
                <span class="min-w-0 truncate">${escapeHtml(endpoint.summary || endpoint.path)}</span>
            </button>`;
        }).join("")}</nav></div>`;
    }).join("") || `<p class="text-sm text-ink-400">No matching endpoints.</p>`;
}

function renderEmpty(project) {
    return `<div class="mx-auto max-w-2xl pt-16 text-center">
        <p class="text-[11px] font-semibold tracking-wider text-ink-500 uppercase mb-3">${escapeHtml(project.name || "Muonica")}</p>
        <h1 class="text-4xl font-extrabold text-white tracking-tight mb-3">No documented endpoints yet</h1>
        <p class="text-ink-400 leading-relaxed max-w-2xl mx-auto">Add Spring MVC handlers to see generated documentation here.</p>
    </div>`;
}
