const methodColors = {
    GET: "bg-green text-[#08110d]", POST: "bg-teal text-[#08110d]",
    PUT: "bg-[#c5a8ff] text-[#140d21]", PATCH: "bg-coral text-[#160809]",
    DELETE: "bg-[#f28b82] text-[#160809]"
};

export const escapeHtml = (value = "") => String(value)
    .replaceAll("&", "&amp;").replaceAll("<", "&lt;")
    .replaceAll(">", "&gt;").replaceAll('"', "&quot;").replaceAll("'", "&#039;");

function methodBadge(method) {
    const value = (method || "API").toUpperCase();
    return `<span class="inline-flex shrink-0 rounded-lg px-3 py-2 text-xs font-bold ${methodColors[value] || "bg-[#303238] text-ink"}">${escapeHtml(value)}</span>`;
}

function schemaType(schema) {
    if (!schema) return "unknown";
    if (schema.ref) return schema.ref.replace(/^.*\//, "");
    return [schema.type, schema.format].filter(Boolean).join(" · ") || "object";
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

function codePanel(endpoint) {
    const content = Object.entries(endpoint.request?.content || {});
    if (!content.length) return "";
    const [contentType, schema] = content[0];
    const code = JSON.stringify(exampleForSchema(schema), null, 2);
    return `<section class="space-y-4">
        <div><h2 class="text-xl font-bold">Request body</h2><p class="mt-2 text-[15px] text-muted">${escapeHtml(endpoint.request.description || contentType)}</p></div>
        <div class="overflow-hidden rounded-xl border border-line bg-[#101011]">
            <div class="flex items-center justify-between border-b border-line px-4 py-2">
                <span class="rounded-md bg-[#303238] px-2 py-1 text-xs font-bold">JSON</span>
                <button class="copy-code inline-flex items-center gap-2 rounded-md px-2 py-1 text-xs font-semibold text-muted hover:bg-[#202125] hover:text-ink" data-copy="${escapeHtml(code)}" aria-label="Copy request example">Copy</button>
            </div>
            <pre class="code-scrollbar overflow-x-auto p-5 text-[13px] leading-6 text-teal"><code>${escapeHtml(code)}</code></pre>
        </div>
    </section>`;
}

function parameterCard(parameters, heading) {
    if (!parameters.length) return "";
    return `<section class="space-y-4"><h2 class="text-xl font-bold">${heading}</h2><div class="rounded-xl border border-line bg-surface p-4">
        <div class="grid grid-cols-[1.2fr_1fr_auto] gap-3 border-b border-line pb-3 text-[11px] font-bold text-dim"><span>PARAMETER</span><span>TYPE</span><span>REQUIRED</span></div>
        ${parameters.map(parameter => `<div class="border-b border-line py-4 last:border-0"><div class="grid grid-cols-[1.2fr_1fr_auto] gap-3 text-sm"><strong>${escapeHtml(parameter.name)}</strong><span class="text-muted">${escapeHtml(schemaType(parameter.schema))}</span><span class="font-semibold ${parameter.required ? "text-green" : "text-muted"}">${parameter.required ? "yes" : "no"}</span></div>${parameter.description ? `<p class="mt-3 text-[13px] text-muted">${escapeHtml(parameter.description)}</p>` : ""}</div>`).join("")}
    </div></section>`;
}

function responseList(responses) {
    if (!responses?.length) return "";
    return `<section class="space-y-4"><h2 class="text-xl font-bold">Responses</h2><div class="space-y-2">${responses.map(response => `<article class="rounded-xl border border-line bg-surface p-4"><div class="flex gap-3"><span class="font-bold text-teal">${escapeHtml(response.statusCode)}</span><span class="text-sm text-muted">${escapeHtml(response.description || "Response")}</span></div></article>`).join("")}</div></section>`;
}

function documentationBlocks(blocks) {
    return (blocks || []).map(block => `<article class="rounded-xl border border-line bg-surface p-4"><p class="mb-2 text-xs font-bold uppercase text-dim">${escapeHtml(block.type)}</p><pre class="whitespace-pre-wrap text-sm text-muted">${escapeHtml(block.content)}</pre></article>`).join("");
}

export function renderShell(project, selected, query, menuOpen = false) {
    const groups = project.groups || [];
    const endpoint = selected?.endpoint;
    const title = endpoint ? endpoint.summary || `${endpoint.method} ${endpoint.path}` : project.name || "Muonica";
    const pathParameters = (endpoint?.parameters || []).filter(parameter => parameter.location === "PATH");
    const otherParameters = (endpoint?.parameters || []).filter(parameter => parameter.location !== "PATH");
    return `<header class="sticky top-0 z-20 flex h-[74px] items-center justify-between gap-5 border-b border-[#111216] bg-canvas px-5 lg:px-6">
        <div class="flex min-w-0 items-center gap-3"><button id="menu-toggle" class="rounded-lg border border-line p-2 text-muted lg:hidden" aria-label="Toggle documentation navigation" aria-expanded="${menuOpen}">☰</button><span class="h-7 w-7 shrink-0 rounded-lg bg-coral"></span><span class="text-xl font-bold">muonica</span><span class="hidden rounded-full border border-line px-2 py-1 text-xs font-semibold text-muted sm:inline">API</span></div>
        <label class="hidden w-full max-w-[350px] items-center gap-2 rounded-lg border border-line bg-surface px-3 py-2 text-muted md:flex"><span aria-hidden="true">⌕</span><input id="search" class="w-full bg-transparent text-sm outline-none placeholder:text-muted" placeholder="Search documentation" value="${escapeHtml(query)}" autocomplete="off"></label>
        <div class="hidden items-center gap-3 text-sm text-muted sm:flex"><span>v${escapeHtml(project.version || "0.0.0")}</span><span class="h-2 w-2 rounded-full bg-[#aaa8b1]"></span><span>API key</span><button disabled class="rounded-lg border border-line p-2 text-dim" aria-label="API key settings">⚿</button></div>
    </header>${menuOpen ? `<div class="fixed inset-x-0 top-[74px] z-30 max-h-[calc(100vh-74px)] overflow-y-auto border-b border-line bg-canvas p-6 lg:hidden"><label class="mb-6 flex items-center gap-2 rounded-lg border border-line bg-surface px-3 py-2 text-muted"><span aria-hidden="true">⌕</span><input id="mobile-search" class="w-full bg-transparent text-sm outline-none placeholder:text-muted" placeholder="Search documentation" value="${escapeHtml(query)}" autocomplete="off"></label><nav aria-label="API navigation">${renderNavigation(groups, selected?.key, query)}</nav></div>` : ""}
    <div class="flex min-h-[calc(100vh-74px)]">
        <aside class="hidden w-[310px] shrink-0 border-r border-[#0e0f11] bg-canvas p-7 lg:block"><nav id="sidebar" aria-label="API navigation">${renderNavigation(groups, selected?.key, query)}</nav></aside>
        <main class="min-w-0 flex-1 bg-panel p-6 md:p-10 lg:p-12">
            ${endpoint ? `<div class="mx-auto max-w-6xl space-y-9"><div class="space-y-3"><p class="text-xs font-semibold uppercase tracking-wide text-dim">${escapeHtml(selected.group.name || "API documentation")}</p><h1 class="text-3xl font-bold md:text-[38px]">${escapeHtml(title)}</h1>${endpoint.description ? `<p class="max-w-3xl text-base text-muted md:text-lg">${escapeHtml(endpoint.description)}</p>` : ""}</div>
            <section class="space-y-4"><h2 class="text-[22px] font-bold">Endpoint</h2><div class="flex items-center justify-between gap-4 rounded-xl border border-line bg-surface p-4">${methodBadge(endpoint.method)}<code class="mr-auto min-w-0 truncate text-base font-medium">${escapeHtml(endpoint.path)}</code><button class="copy-code rounded-md px-2 py-1 text-xs font-semibold text-muted hover:bg-[#202125] hover:text-ink" data-copy="${escapeHtml(`${endpoint.method} ${endpoint.path}`)}">Copy</button></div></section>
            <div class="grid gap-9 xl:grid-cols-[minmax(0,1fr)_292px]"> <div class="space-y-9">${codePanel(endpoint)}${responseList(endpoint.responses)}${documentationBlocks(endpoint.documentationBlocks)}</div><div class="space-y-8">${parameterCard(pathParameters, "Path parameters")}${parameterCard(otherParameters, "Parameters")}<aside class="rounded-xl border border-line bg-surface p-5"><h2 class="font-bold">Need help?</h2><p class="mt-2 text-sm text-muted">Explore the API reference or contact developer support.</p><a class="mt-3 inline-block text-sm font-medium text-teal" href="./openapi.json">OpenAPI document →</a></aside></div></div></div>` : renderEmpty(project)}</main>
    </div>`;
}

function renderNavigation(groups, selectedKey, query) {
    const term = query.trim().toLowerCase();
    return groups.map((group, groupIndex) => {
        const endpoints = (group.endpoints || []).filter(endpoint => !term || [endpoint.path, endpoint.summary, endpoint.description, group.name].filter(Boolean).join(" ").toLowerCase().includes(term));
        if (!endpoints.length) return "";
        return `<section class="mb-7"><h2 class="mb-3 text-xs font-bold uppercase text-ink">${escapeHtml(group.name || "API")}</h2><div class="space-y-1">${endpoints.map(endpoint => {
            const key = `${groupIndex}:${group.endpoints.indexOf(endpoint)}`;
            const selected = key === selectedKey;
            return `<button class="endpoint-link flex w-full items-center gap-2 rounded-md px-3 py-2 text-left text-sm ${selected ? "border-l-2 border-coral bg-[#171719] font-semibold text-ink" : "text-muted hover:bg-[#111214] hover:text-ink"}" data-endpoint="${key}" ${selected ? 'aria-current="page"' : ""}>${methodBadge(endpoint.method)}<span class="truncate">${escapeHtml(endpoint.summary || endpoint.path)}</span></button>`;
        }).join("")}</div></section>`;
    }).join("") || `<p class="text-sm text-muted">No matching endpoints.</p>`;
}

function renderEmpty(project) {
    return `<div class="mx-auto max-w-2xl pt-16"><p class="text-xs font-semibold uppercase text-dim">${escapeHtml(project.name || "Muonica")}</p><h1 class="mt-3 text-3xl font-bold">No documented endpoints yet</h1><p class="mt-3 text-muted">Add Spring MVC handlers to see generated documentation here.</p></div>`;
}
