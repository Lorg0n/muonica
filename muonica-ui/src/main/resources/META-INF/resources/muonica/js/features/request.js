import {sendEndpointRequest} from "../api.js";
import {escapeHtml, highlightedCurl, highlightedJson, curlFor, resolvePath} from "../render.js";

export const editorText = editor => (editor?.innerText || "").replaceAll("\r\n", "\n");

export function pathValuesFrom(root, state) {
    const values = {};
    root.querySelectorAll("[data-path-parameter]").forEach(input => { values[input.dataset.pathParameter] = input.value; });
    state.pathValues.set(state.selectedKey, values);
    return values;
}

export function updateCurlPreview(root, state) {
    const endpoint = state.selectedEntry()?.endpoint;
    const editor = root.querySelector("#code-json");
    const curl = root.querySelector("#code-curl");
    if (!endpoint || !curl) return;
    curl.innerHTML = highlightedCurl(curlFor(endpoint, editor?.dataset.contentType, editor ? editorText(editor) : "", pathValuesFrom(root, state), state.project));
}

export function validateEditor(root) {
    const editor = root.querySelector("#code-json");
    const error = root.querySelector("#json-error");
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

function responseMarkup(text) {
    if (!text.trim()) return '<span class="text-ink-500">No response body</span>';
    try { return highlightedJson(JSON.stringify(JSON.parse(text), null, 2)); }
    catch { return escapeHtml(text); }
}

export async function sendRequest(root, state) {
    const endpoint = state.selectedEntry()?.endpoint;
    const editor = root.querySelector("#code-json");
    const button = root.querySelector("#send-btn");
    const panel = root.querySelector("#response-panel");
    if (!endpoint || !button || !panel) return;
    let requestBody;
    const contentType = editor?.dataset.contentType;
    if (editor) {
        if (!validateEditor(root)) return editor.focus();
        requestBody = editorText(editor);
        state.requestBodies.set(state.selectedKey, JSON.stringify(JSON.parse(requestBody), null, 2));
    }
    const status = root.querySelector("#response-status");
    const responseTime = root.querySelector("#response-time");
    const responseBody = root.querySelector("#response-body");
    const original = button.innerHTML;
    const started = performance.now();
    button.disabled = true;
    button.innerHTML = '<svg class="w-3.5 h-3.5 animate-spin" viewBox="0 0 24 24" fill="none"><circle cx="12" cy="12" r="9" stroke="currentColor" stroke-width="2.4" stroke-opacity=".25"/><path d="M21 12a9 9 0 0 0-9-9" stroke="currentColor" stroke-width="2.4" stroke-linecap="round"/></svg> Sending…';
    panel.classList.remove("hidden");
    try {
        const result = await sendEndpointRequest(endpoint.method, resolvePath(endpoint.path, pathValuesFrom(root, state)), requestBody, contentType, endpoint, state.project, state.authorization);
        if (!button.isConnected) return;
        const successful = result.response.ok;
        status.textContent = `${result.response.status} ${result.response.statusText || (successful ? "OK" : "Response")}`;
        status.className = `mono text-xs font-bold px-2 py-0.5 rounded border ${successful ? "response-status-success" : "response-status-error"}`;
        responseTime.textContent = `${Math.max(1, Math.round(performance.now() - started))}ms`;
        responseBody.innerHTML = responseMarkup(result.text);
    } catch (error) {
        if (!button.isConnected) return;
        status.textContent = "Request failed";
        status.className = "mono text-xs font-bold px-2 py-0.5 rounded border response-status-error";
        responseTime.textContent = `${Math.max(1, Math.round(performance.now() - started))}ms`;
        responseBody.innerHTML = `<span class="text-brand">${escapeHtml(error.message || "The request could not be sent.")}</span>`;
    } finally {
        if (button.isConnected) { button.disabled = false; button.innerHTML = original; }
        panel.scrollIntoView({behavior: "smooth", block: "nearest"});
    }
}
