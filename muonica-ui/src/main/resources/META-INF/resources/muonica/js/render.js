const methodStyles = {
    GET: "text-mint border-mint/40 bg-mint/10",
    POST: "text-sky-400 border-sky-400/40 bg-sky-400/10",
    PUT: "text-amber-400 border-amber-400/40 bg-amber-400/10",
    PATCH: "text-brand border-brand/40 bg-brand/10",
    DELETE: "text-red-400 border-red-400/40 bg-red-400/10"
};

export const escapeHtml = (value = "") => String(value)
    .replaceAll("&", "&amp;").replaceAll("<", "&lt;")
    .replaceAll(">", "&gt;").replaceAll('"', "&quot;").replaceAll("'", "&#039;");

function methodBadge(method) {
    const value = (method || "API").toUpperCase();
    const style = methodStyles[value] || "text-ink-300 border-ink-700 bg-ink-800";
    return `<span class="mono text-xs font-bold tracking-wide ${style} border rounded-md px-2.5 py-1.5 shrink-0">${escapeHtml(value)}</span>`;
}

function schemaType(schema) {
    if (!schema) return "unknown";
    if (schema.ref) return schema.ref.replace(/^.*\//, "");
    return [schema.type, schema.format].filter(Boolean).join(" · ") || "object";
}

function curlFor(endpoint, contentType, body) {
    return [
        `curl -X ${endpoint.method} https://api.example.com${endpoint.path} \\`,
        "  -H \"Authorization: Bearer $MUONICA_API_KEY\" \\",
        `  -H \"Content-Type: ${contentType}\" \\`,
        `  -d '${body}'`
    ].join("\n");
}

function highlightedJson(code) {
    return escapeHtml(code)
        .replace(/(&quot;[^&]+&quot;)(?=:)/g, '<span class="text-sky-300">$1</span>')
        .replace(/: (&quot;.*?&quot;)/g, ': <span class="text-mint">$1</span>')
        .replace(/: (true|false|null|\d+(?:\.\d+)?)/g, ': <span class="text-brand">$1</span>');
}

function highlightedCurl(code) {
    return escapeHtml(code)
        .replace(/^(curl)/, '<span class="text-sky-300">$1</span>')
        .replace(/(-X|-H|-d)/g, '<span class="text-brand">$1</span>')
        .replace(/(https?:\/\/[^\s\\]+)/g, '<span class="text-mint">$1</span>');
}

export function exampleForSchema(schema, depth = 0) {
    if (!schema || depth > 4) return null;
    if (schema.enumValues?.length) return schema.enumValues[0];
    if (schema.ref) return schema.ref.replace(/^.*\//, "");
    if (schema.type === "array") return [exampleForSchema(schema.items, depth + 1)];
    if (schema.type === "object" || schema.properties) {
        return Object.fromEntries(Object.entries(schema.properties || {}).map(([name, child]) => [name, exampleForSchema(child, depth + 1)]));
    }
    if (["integer", "number"].includes(schema.type)) return 0;
    if (schema.type === "boolean") return true;
    if (schema.format === "date") return "2026-01-01";
    if (schema.format === "date-time") return "2026-01-01T00:00:00Z";
    return "string";
}

function formatLines(str, highlighter) {
    return str.split('\n').map((line, i) =>
        `<tr class="code-line"><td class="select-none text-right text-ink-600 pr-4 pl-4 w-10 align-top">${i + 1}</td><td class="pr-4 whitespace-pre text-ink-200">${highlighter(line)}</td></tr>`
    ).join('');
}

function codePanel(endpoint) {
    const content = Object.entries(endpoint.request?.content || {});
    if (!content.length) return "";
    const [contentType, schema] = content[0];
    const code = JSON.stringify(exampleForSchema(schema), null, 2);
    const curl = curlFor(endpoint, contentType, code);

    const jsonLines = formatLines(code, highlightedJson);
    const curlLines = formatLines(curl, highlightedCurl);

    return `<div>
        <h2 class="text-xl font-bold text-white mb-2">Request body</h2>
        <p class="text-ink-400 mb-4">${escapeHtml(endpoint.request.description || contentType)}</p>
        <div class="bg-ink-900 border border-ink-800 rounded-xl overflow-hidden">
            <div class="flex items-center justify-between px-4 py-2.5 border-b border-ink-800">
                <div class="flex items-center gap-1">
                    <button class="code-tab text-xs font-semibold px-3 py-1.5 rounded-md bg-ink-800 text-white" data-tab="json" type="button">JSON</button>
                    <button class="code-tab text-xs font-semibold px-3 py-1.5 rounded-md text-ink-400 hover:text-white hover:bg-ink-800 transition-colors" data-tab="curl" type="button">cURL</button>
                </div>
                <button class="copy-code w-7 h-7 flex items-center justify-center rounded-md text-ink-400 hover:text-white hover:bg-ink-800 transition-colors" data-copy="${escapeHtml(code)}" aria-label="Copy request example">
                    <svg class="w-4 h-4" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><rect x="9" y="9" width="12" height="12" rx="2"/><path d="M5 15V5a2 2 0 0 1 2-2h10"/></svg>
                </button>
            </div>
            <div class="overflow-x-auto code-scrollbar pb-2 pt-2">
                <table class="w-full mono text-[13px] leading-6">
                    <tbody class="code-content" data-json="${escapeHtml(jsonLines)}" data-curl="${escapeHtml(curlLines)}" data-raw-json="${escapeHtml(code)}" data-raw-curl="${escapeHtml(curl)}">
                        ${jsonLines}
                    </tbody>
                </table>
            </div>
        </div>
    </div>`;
}

function parameterCard(parameters, heading) {
    if (!parameters.length) return "";
    return `<div>
        <h3 class="text-white font-bold mb-3">${escapeHtml(heading)}</h3>
        <div class="bg-ink-900 border border-ink-800 rounded-xl overflow-hidden">
            <table class="w-full text-xs">
                <thead>
                    <tr class="text-ink-500 uppercase tracking-wider text-[10.5px] border-b border-ink-800">
                        <th class="text-left font-semibold px-4 py-3">Parameter</th>
                        <th class="text-left font-semibold px-2 py-3">Type</th>
                        <th class="text-left font-semibold px-2 py-3">Required</th>
                    </tr>
                </thead>
                <tbody class="divide-y divide-ink-800">
                    ${parameters.map(parameter => `<tr>
                        <td class="px-4 py-3 text-white font-semibold mono align-top">${escapeHtml(parameter.name)}${parameter.description ? `<div class="mt-1 font-sans font-normal text-ink-400 whitespace-normal">${escapeHtml(parameter.description)}</div>` : ''}</td>
                        <td class="px-2 py-3 text-ink-400 align-top">${escapeHtml(schemaType(parameter.schema))}</td>
                        <td class="px-2 py-3 font-semibold align-top ${parameter.required ? "text-mint" : "text-ink-500"}">${parameter.required ? "yes" : "no"}</td>
                    </tr>`).join("")}
                </tbody>
            </table>
        </div>
    </div>`;
}

function responseList(responses) {
    if (!responses?.length) return "";
    return `<div class="mt-10"><h2 class="text-xl font-bold text-white mb-4">Responses</h2><div class="space-y-3">${responses.map(response => `<div class="bg-ink-900 border border-ink-800 rounded-xl p-4 flex gap-4 items-center"><span class="mono font-bold text-mint border border-mint/30 bg-mint/5 px-2 py-1 rounded text-sm">${escapeHtml(response.statusCode)}</span><span class="text-sm text-ink-300">${escapeHtml(response.description || "Response")}</span></div>`).join("")}</div></div>`;
}

function documentationBlocks(blocks) {
    return (blocks || []).map(block => `<div class="mt-10 bg-ink-900 border border-ink-800 rounded-xl p-5"><p class="text-[11px] font-semibold tracking-wider text-ink-500 uppercase mb-3">${escapeHtml(block.type)}</p><pre class="whitespace-pre-wrap text-sm text-ink-300 font-sans leading-relaxed">${escapeHtml(block.content)}</pre></div>`).join("");
}

export function renderShell(project, selected, query, menuOpen = false) {
    const groups = project.groups || [];
    const endpoint = selected?.endpoint;
    const title = endpoint ? endpoint.summary || `${endpoint.method} ${endpoint.path}` : project.name || "Muonica";
    const pathParameters = (endpoint?.parameters || []).filter(parameter => parameter.location === "PATH");
    const otherParameters = (endpoint?.parameters || []).filter(parameter => parameter.location !== "PATH");

    return `<header class="sticky top-0 z-30 flex items-center justify-between h-16 px-4 lg:px-6 border-b border-ink-800 bg-ink-950/90 backdrop-blur">
        <div class="flex items-center gap-3">
            <button id="menu-toggle" class="lg:hidden text-ink-400 hover:text-white" aria-label="Toggle navigation" aria-expanded="${menuOpen}"><svg class="w-5 h-5" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><path d="M4 6h16M4 12h16M4 18h16" stroke-linecap="round"/></svg></button>
            <div class="w-8 h-8 rounded-md bg-brand flex items-center justify-center shrink-0">
                <svg viewBox="0 0 24 24" class="w-4 h-4 text-white" fill="none"><path d="M4 12c0-4.4 3.6-8 8-8s8 3.6 8 8-3.6 8-8 8" stroke="currentColor" stroke-width="2.2" stroke-linecap="round"/></svg>
            </div>
            <span class="text-white font-semibold tracking-tight text-[15px]">muonica</span>
            <span class="hidden sm:inline-block ml-1 text-[11px] font-medium text-ink-400 border border-ink-700 rounded px-1.5 py-0.5">API</span>
        </div>

        <div class="hidden md:flex items-center flex-1 max-w-md mx-8">
            <label class="flex items-center gap-2 w-full bg-ink-900 border border-ink-800 rounded-lg px-3 py-2 text-ink-400 hover:border-ink-600 transition-colors cursor-text">
                <svg class="w-4 h-4 shrink-0" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><circle cx="11" cy="11" r="7"/><path d="m21 21-4.3-4.3" stroke-linecap="round"/></svg>
                <input id="search" class="w-full bg-transparent text-sm outline-none placeholder:text-ink-500 text-white" placeholder="Search documentation" value="${escapeHtml(query)}" autocomplete="off">
                <kbd class="ml-auto text-[10px] text-ink-500 border border-ink-700 rounded px-1.5 py-0.5">⌘K</kbd>
            </label>
        </div>

        <div class="flex items-center gap-3 lg:gap-5 text-sm text-ink-300">
            <span class="hidden sm:inline text-ink-400">v${escapeHtml(project.version || "1.0")}</span>
            <span class="hidden sm:inline w-1.5 h-1.5 rounded-full bg-ink-600"></span>
            <span class="hidden sm:inline">API key</span>
            <button disabled class="w-8 h-8 rounded-full bg-ink-800 border border-ink-700 flex items-center justify-center text-ink-300 hover:border-ink-500 transition-colors" aria-label="Account">
                <svg class="w-4 h-4" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><circle cx="12" cy="8" r="4"/><path d="M4 21c0-4 4-6 8-6s8 2 8 6" stroke-linecap="round"/></svg>
            </button>
        </div>
    </header>
    ${menuOpen ? `<div class="fixed inset-x-0 top-16 z-30 max-h-[calc(100vh-64px)] overflow-y-auto border-b border-ink-800 bg-ink-950 p-6 lg:hidden">
        <label class="flex items-center gap-2 w-full bg-ink-900 border border-ink-800 rounded-lg px-3 py-2 text-ink-400 mb-6">
            <svg class="w-4 h-4 shrink-0" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><circle cx="11" cy="11" r="7"/><path d="m21 21-4.3-4.3" stroke-linecap="round"/></svg>
            <input id="mobile-search" class="w-full bg-transparent text-sm outline-none placeholder:text-ink-500 text-white" placeholder="Search documentation" value="${escapeHtml(query)}" autocomplete="off">
        </label>
        <nav aria-label="API navigation">${renderNavigation(groups, selected?.key, query)}</nav>
    </div>` : ""}

    <div class="flex">
        <aside class="hidden lg:block doc-nav w-64 shrink-0 border-r border-ink-800 h-[calc(100vh-64px)] sticky top-16 overflow-y-auto px-5 py-8">
            <nav id="sidebar" aria-label="API navigation">${renderNavigation(groups, selected?.key, query)}</nav>
        </aside>

        <main class="flex-1 min-w-0 px-6 lg:px-10 py-10 max-w-[1600px]">
            ${endpoint ? `<div class="w-full"><p class="text-[11px] font-semibold tracking-wider text-ink-500 uppercase mb-3">${escapeHtml(selected.group.name || "API documentation")}</p>
            <h1 class="text-4xl font-extrabold text-white tracking-tight mb-3">${escapeHtml(title)}</h1>
            ${endpoint.description ? `<p class="text-ink-400 leading-relaxed max-w-2xl mb-10">${escapeHtml(endpoint.description)}</p>` : `<div class="mb-10"></div>`}

            <div class="grid grid-cols-1 xl:grid-cols-[1fr_300px] gap-10">
                <div class="min-w-0">
                    <h2 class="text-xl font-bold text-white mb-4">Endpoint</h2>
                    <div class="flex items-center gap-3 bg-ink-900 border border-ink-800 rounded-xl p-2 mb-10">
                        ${methodBadge(endpoint.method)}
                        <code class="mono text-sm text-mint bg-mint/5 border border-mint/30 rounded-md px-3 py-1.5 flex-1 truncate">${escapeHtml(endpoint.path)}</code>
                        <button class="copy-code shrink-0 w-8 h-8 flex items-center justify-center rounded-md text-ink-400 hover:text-white hover:bg-ink-800 transition-colors" data-copy="${escapeHtml(`${endpoint.method} ${endpoint.path}`)}" aria-label="Copy endpoint">
                            <svg class="w-4 h-4" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><rect x="9" y="9" width="12" height="12" rx="2"/><path d="M5 15V5a2 2 0 0 1 2-2h10"/></svg>
                        </button>
                    </div>
                    ${codePanel(endpoint)}
                    ${responseList(endpoint.responses)}
                    ${documentationBlocks(endpoint.documentationBlocks)}
                </div>
                <div class="space-y-6">
                    ${parameterCard(pathParameters, "Path parameters")}
                    ${parameterCard(otherParameters, "Parameters")}
                    <div class="bg-ink-900 border border-ink-800 rounded-xl p-5">
                        <h3 class="text-white font-bold mb-1.5">Need help?</h3>
                        <p class="text-xs text-ink-400 leading-relaxed">
                            Explore the API reference or
                            <a class="text-brand font-medium hover:underline" href="./openapi.json">contact developer support →</a>
                        </p>
                    </div>
                </div>
            </div></div>` : renderEmpty(project)}
        </main>
    </div>`;
}

function renderNavigation(groups, selectedKey, query) {
    const term = query.trim().toLowerCase();
    return groups.map((group, groupIndex) => {
        const endpoints = (group.endpoints || []).filter(endpoint => !term || [endpoint.path, endpoint.summary, endpoint.description, group.name].filter(Boolean).join(" ").toLowerCase().includes(term));
        if (!endpoints.length) return "";
        return `<div class="mb-7"><p class="text-[11px] font-semibold tracking-wider text-ink-500 uppercase mb-3">${escapeHtml(group.name || "API")}</p>
        <nav class="space-y-0.5 text-sm">${endpoints.map(endpoint => {
            const key = `${groupIndex}:${group.endpoints.indexOf(endpoint)}`;
            const selected = key === selectedKey;
            return `<button class="endpoint-link w-full text-left relative block px-2.5 py-1.5 rounded-md ${selected ? "text-white bg-ink-900 font-medium" : "text-ink-300 hover:text-white hover:bg-ink-900"}" data-endpoint="${key}" ${selected ? 'aria-current="page"' : ""}>
                ${selected ? '<span class="absolute left-0 top-1.5 bottom-1.5 w-[2px] bg-brand rounded-full"></span>' : ""}
                ${escapeHtml(endpoint.summary || endpoint.path)}
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
