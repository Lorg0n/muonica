import {
    allEndpoints,
    clearAuthorization,
    loadAuthorization,
    loadProject,
    saveAuthorization,
    sendEndpointRequest
} from "./api.js";
import {
    escapeHtml,
    highlightedCurl,
    highlightedJson,
    curlFor,
    renderShell,
    resolvePath
} from "./render.js";

const app = document.querySelector("#app");
let project;
let selectedKey;
let query = "";
let menuOpen = false;
let authorizationModalOpen = false;
let authorization = loadAuthorization();
const requestBodies = new Map();
const pathValues = new Map();
const activeTabs = new Map();

function selectedEntry() {
    return allEndpoints(project).find(item => item.key === selectedKey);
}

function selectedEndpoint() {
    return selectedEntry()?.endpoint;
}

function selectedState() {
    return {
        requestBody: requestBodies.get(selectedKey),
        pathValues: pathValues.get(selectedKey),
        activeTab: activeTabs.get(selectedKey) || "json",
        authorization,
        authorizationModalOpen
    };
}

function editorText(editor) {
    return (editor?.innerText || "").replaceAll("\r\n", "\n");
}

function setCopySuccess(button) {
    const original = button.innerHTML;
    button.innerHTML = '<svg class="w-4 h-4 text-mint" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><path d="M20 6 9 17l-5-5" stroke-linecap="round" stroke-linejoin="round"/></svg>';
    window.setTimeout(() => {
        if (button.isConnected) button.innerHTML = original;
    }, 1200);
}

async function copyText(text, button) {
    try {
        if (navigator.clipboard?.writeText) {
            await navigator.clipboard.writeText(text);
        } else {
            const helper = document.createElement("textarea");
            helper.value = text;
            helper.style.position = "fixed";
            helper.style.opacity = "0";
            document.body.append(helper);
            helper.select();
            document.execCommand("copy");
            helper.remove();
        }
        setCopySuccess(button);
    } catch {
        const original = button.innerHTML;
        button.innerHTML = '<span class="text-brand text-xs font-bold">!</span>';
        window.setTimeout(() => {
            if (button.isConnected) button.innerHTML = original;
        }, 1200);
    }
}

function currentPathValues() {
    const values = {};
    app.querySelectorAll("[data-path-parameter]").forEach(input => {
        values[input.dataset.pathParameter] = input.value;
    });
    pathValues.set(selectedKey, values);
    return values;
}

function updateCurlPreview() {
    const endpoint = selectedEndpoint();
    const editor = app.querySelector("#code-json");
    const curl = app.querySelector("#code-curl");
    if (!endpoint || !curl) return;
    curl.innerHTML = highlightedCurl(curlFor(
        endpoint,
        editor?.dataset.contentType,
        editor ? editorText(editor) : "",
        currentPathValues(),
        project
    ));
}

function validateEditor() {
    const editor = app.querySelector("#code-json");
    const error = app.querySelector("#json-error");
    if (!editor || !error) return true;
    try {
        JSON.parse(editorText(editor));
        error.classList.add("hidden");
        return true;
    } catch {
        error.textContent = "Invalid JSON";
        error.classList.remove("hidden");
        return false;
    }
}

function updateEndpointPreview() {
    if (!selectedEndpoint()) return;
    updateCurlPreview();
}

function responseMarkup(text) {
    if (!text.trim()) return '<span class="text-ink-500">No response body</span>';
    try {
        return highlightedJson(JSON.stringify(JSON.parse(text), null, 2));
    } catch {
        return escapeHtml(text);
    }
}

async function sendRequest() {
    const endpoint = selectedEndpoint();
    const editor = app.querySelector("#code-json");
    const button = app.querySelector("#send-btn");
    const panel = app.querySelector("#response-panel");
    if (!endpoint || !button || !panel) return;

    let requestBody;
    const contentType = editor?.dataset.contentType;
    if (editor) {
        if (!validateEditor()) {
            editor.focus();
            return;
        }
        requestBody = editorText(editor);
        requestBodies.set(selectedKey, JSON.stringify(JSON.parse(requestBody), null, 2));
    }

    const status = app.querySelector("#response-status");
    const responseTime = app.querySelector("#response-time");
    const responseBody = app.querySelector("#response-body");
    const original = button.innerHTML;
    const started = performance.now();
    const path = resolvePath(endpoint.path, currentPathValues());

    button.disabled = true;
    button.innerHTML = '<svg class="w-3.5 h-3.5 animate-spin" viewBox="0 0 24 24" fill="none"><circle cx="12" cy="12" r="9" stroke="currentColor" stroke-width="2.4" stroke-opacity=".25"/><path d="M21 12a9 9 0 0 0-9-9" stroke="currentColor" stroke-width="2.4" stroke-linecap="round"/></svg> Sending…';
    panel.classList.remove("hidden");

    try {
        const result = await sendEndpointRequest(endpoint.method, path, requestBody, contentType, endpoint, project, authorization);
        if (!button.isConnected) return;
        const successful = result.response.ok;
        const statusCode = result.response.status;
        status.textContent = `${statusCode} ${result.response.statusText || (successful ? "OK" : "Response")}`;
        status.className = `mono text-xs font-bold px-2 py-0.5 rounded border ${successful ? "response-status-success" : "response-status-error"}`;
        responseTime.textContent = `${Math.max(1, Math.round(performance.now() - started))}ms`;
        responseBody.innerHTML = responseMarkup(result.text);
        button.disabled = false;
        button.innerHTML = original;
        panel.classList.remove("hidden");
        panel.scrollIntoView({ behavior: "smooth", block: "nearest" });
    } catch (error) {
        if (!button.isConnected) return;
        status.textContent = "Request failed";
        status.className = "mono text-xs font-bold px-2 py-0.5 rounded border response-status-error";
        responseTime.textContent = `${Math.max(1, Math.round(performance.now() - started))}ms`;
        responseBody.innerHTML = `<span class="text-brand">${escapeHtml(error.message || "The request could not be sent.")}</span>`;
        panel.classList.remove("hidden");
        panel.scrollIntoView({ behavior: "smooth", block: "nearest" });
    } finally {
        if (button.isConnected) {
            button.disabled = false;
            button.innerHTML = original;
        }
    }
}

function attachRequestInteractions() {
    const editor = app.querySelector("#code-json");
    editor?.addEventListener("input", () => {
        requestBodies.set(selectedKey, editorText(editor));
        validateEditor();
        updateCurlPreview();
    });
    editor?.addEventListener("blur", () => {
        try {
            const parsed = JSON.parse(editorText(editor));
            const formatted = JSON.stringify(parsed, null, 2);
            requestBodies.set(selectedKey, formatted);
            editor.innerHTML = highlightedJson(formatted);
            const error = app.querySelector("#json-error");
            error?.classList.add("hidden");
            updateCurlPreview();
        } catch {
            // Keep invalid text in place so an in-progress edit is never discarded.
        }
    });

    app.querySelectorAll("[data-path-parameter]").forEach(input => {
        input.addEventListener("input", updateEndpointPreview);
    });
    app.querySelectorAll(".code-tab").forEach(button => {
        button.addEventListener("click", () => {
            const tab = button.dataset.tab;
            activeTabs.set(selectedKey, tab);
            app.querySelector("#code-json")?.classList.toggle("hidden", tab !== "json");
            app.querySelector("#code-curl")?.classList.toggle("hidden", tab !== "curl");
            app.querySelectorAll(".code-tab").forEach(tabButton => {
                const active = tabButton === button;
                tabButton.classList.toggle("code-tab-active", active);
                tabButton.classList.toggle("bg-ink-800", active);
                tabButton.classList.toggle("text-white", active);
                tabButton.classList.toggle("text-ink-400", !active);
            });
            if (tab === "curl") updateCurlPreview();
        });
    });
    app.querySelector("#send-btn")?.addEventListener("click", sendRequest);
}

function attachDiagramInteractions() {
    app.querySelectorAll("[data-diagram-render]").forEach(button => button.addEventListener("click", async () => {
        const container = button.closest("[data-diagram]");
        const renderer = button.dataset.diagramRender;
        const source = container?.querySelector(".diagram-source")?.innerText || "";
        const output = container?.querySelector(".diagram-output");
        const renderers = globalThis.muonicaDiagramRenderers || {};
        const render = renderers[renderer];
        if (typeof render !== "function" || !output) {
            button.textContent = "Source shown below";
            return;
        }
        try {
            output.classList.remove("hidden");
            output.innerHTML = "";
            await render(source, output);
            button.textContent = "Rendered";
        } catch {
            output.classList.add("hidden");
            button.textContent = "Source shown below";
        }
    }));
}

function attachAuthorizationInteractions() {
    app.querySelector("#authorize-btn")?.addEventListener("click", () => {
        authorizationModalOpen = true;
        render();
        app.querySelector("[data-auth-scheme]")?.focus();
    });
    app.querySelector("#auth-close")?.addEventListener("click", () => {
        authorizationModalOpen = false;
        render();
    });
    app.querySelector("#auth-cancel")?.addEventListener("click", () => {
        authorizationModalOpen = false;
        render();
    });
    app.querySelector("[data-auth-backdrop]")?.addEventListener("click", event => {
        if (event.target !== event.currentTarget) return;
        authorizationModalOpen = false;
        render();
    });
    app.querySelector("#authorization-form")?.addEventListener("submit", event => {
        event.preventDefault();
        const values = {};
        app.querySelectorAll("[data-auth-scheme]").forEach(input => {
            values[input.dataset.authScheme] = input.value;
        });
        authorization = saveAuthorization(values);
        authorizationModalOpen = false;
        render();
    });
    app.querySelector("#clear-authorization")?.addEventListener("click", () => {
        authorization = clearAuthorization();
        render();
        app.querySelector("[data-auth-scheme]")?.focus();
    });
    app.querySelectorAll("[data-auth-toggle]").forEach(button => button.addEventListener("click", () => {
        const input = app.querySelector(`#${button.dataset.authToggle}`);
        if (!input) return;
        const visible = input.type === "text";
        input.type = visible ? "password" : "text";
        button.textContent = visible ? "Show" : "Hide";
    }));
}

function render() {
    app.innerHTML = renderShell(project, selectedEntry(), query, menuOpen, selectedState());
    attachRequestInteractions();
    attachDiagramInteractions();
    attachAuthorizationInteractions();

    const updateQuery = event => {
        const input = event.target;
        const selectionStart = input.selectionStart;
        const selectionEnd = input.selectionEnd;
        query = event.target.value;
        render();
        const replacement = app.querySelector(menuOpen ? "#mobile-search" : "#search");
        replacement?.focus();
        if (replacement && selectionStart !== null && selectionEnd !== null) {
            replacement.setSelectionRange(selectionStart, selectionEnd);
        }
    };

    app.querySelector("#search")?.addEventListener("input", updateQuery);
    app.querySelector("#mobile-search")?.addEventListener("input", updateQuery);
    app.querySelector("#menu-toggle")?.addEventListener("click", () => {
        menuOpen = !menuOpen;
        render();
    });

    app.querySelectorAll(".endpoint-link").forEach(button => button.addEventListener("click", event => {
        event.preventDefault();
        selectedKey = button.dataset.endpoint;
        menuOpen = false;
        render();
    }));

    app.querySelectorAll(".copy-code").forEach(button => button.addEventListener("click", () => {
        const panel = button.closest("[data-code-panel]");
        const activeTab = activeTabs.get(selectedKey) || (panel?.querySelector("#code-json") ? "json" : "curl");
        const text = panel
            ? (activeTab === "curl" ? panel.querySelector("#code-curl")?.innerText : panel.querySelector("#code-json")?.innerText) || ""
            : button.dataset.copy || "";
        copyText(text, button);
    }));
    app.querySelectorAll(".copy-response").forEach(button => button.addEventListener("click", () => {
        copyText(app.querySelector("#response-body")?.innerText || "", button);
    }));
}

window.addEventListener("keydown", event => {
    if (event.key === "Escape" && authorizationModalOpen) {
        authorizationModalOpen = false;
        render();
        return;
    }
    if (event.key === "Escape" && menuOpen) {
        menuOpen = false;
        render();
    }
    if ((event.metaKey || event.ctrlKey) && event.key.toLowerCase() === "k") {
        event.preventDefault();
        app.querySelector("#search")?.focus();
    }
});

async function start() {
    app.innerHTML = '<div class="grid min-h-screen place-items-center text-ink-400">Loading documentation…</div>';
    try {
        project = await loadProject();
        selectedKey = allEndpoints(project)[0]?.key;
        render();
    } catch (error) {
        app.innerHTML = `<main class="grid min-h-screen place-items-center p-6"><section class="max-w-md rounded-xl border border-ink-800 bg-ink-900 p-6"><h1 class="text-xl font-bold text-white">Documentation unavailable</h1><p class="mt-2 text-ink-400">${escapeHtml(error.message)}</p><button class="mt-5 rounded-lg bg-brand px-4 py-2 text-sm font-bold text-white hover:bg-brand/90 transition-colors" onclick="location.reload()" type="button">Try again</button></section></main>`;
    }
}

start();
