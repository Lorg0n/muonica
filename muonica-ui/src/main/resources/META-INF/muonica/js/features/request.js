import {sendEndpointRequest} from "../api.js";
import {escapeHtml, highlightedCurl, highlightedJson, curlFor, pathValuesFor, resolvePath} from "../render.js";

export const editorText = editor => (editor?.innerText || "").replaceAll("\r\n", "\n");

export function parameterValuesFrom(root, state) {
    const values = {...(state.parameterValues.get(state.selectedKey) || {})};
    root.querySelectorAll("[data-parameter-key]").forEach(input => { values[input.dataset.parameterKey] = input.value; });
    state.parameterValues.set(state.selectedKey, values);
    return values;
}

export function validateParameters(root) {
    let valid = true;
    root.querySelectorAll("[data-parameter-key]").forEach(input => {
        const required = input.dataset.parameterRequired === "true";
        const missing = required && !input.value.trim();
        input.classList.toggle("request-parameter-input-error", missing);
        const error = [...root.querySelectorAll("[data-parameter-error]")]
            .find(element => element.dataset.parameterError === input.dataset.parameterKey);
        if (error) error.hidden = !missing;
        if (missing) valid = false;
    });
    return valid;
}

function multipartBody(root) {
    const fields = [...root.querySelectorAll("[data-multipart-part]")];
    if (!fields.length) return null;
    const body = new FormData();
    for (const input of fields) {
        const files = [...(input.files || [])];
        const file = files[0];
        if (input.dataset.multipartRequired === "true" && !file && !input.value) {
            input.classList.add("request-parameter-input-error");
            input.focus();
            return undefined;
        }
        input.classList.remove("request-parameter-input-error");
        if (files.length) files.forEach(value => body.append(input.dataset.multipartPart, value));
        else if (input.value) body.append(input.dataset.multipartPart, input.value);
    }
    return body;
}

export function updateCurlPreview(root, state) {
    const endpoint = state.selectedEntry()?.endpoint;
    const editor = root.querySelector("#code-json");
    const curl = root.querySelector("#code-curl");
    if (!endpoint || !curl) return;
    const contentType = editor?.dataset.contentType || Object.keys(endpoint.request?.content || {})[0];
    const securityGroupIndex = state.securityGroupIndexes?.get(state.selectedKey) || state.securityGroupIndex || 0;
    curl.innerHTML = highlightedCurl(curlFor(endpoint, contentType, contentType === "multipart/form-data" ? "" : editor ? editorText(editor) : "",
        parameterValuesFrom(root, state), state.project, securityGroupIndex));
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
    const parameterValues = parameterValuesFrom(root, state);
    if (!validateParameters(root)) {
        root.querySelector(".request-parameter-input-error")?.focus();
        return;
    }
    let requestBody;
    const contentType = editor?.dataset.contentType;
    const multipart = root.querySelectorAll("[data-multipart-part]").length > 0;
    if (multipart) {
        requestBody = multipartBody(root);
        if (requestBody === undefined) return;
    } else if (editor) {
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
        const result = await sendEndpointRequest(endpoint.method, resolvePath(endpoint.path, pathValuesFor(endpoint, parameterValues)), requestBody,
            multipart ? "multipart/form-data" : contentType, endpoint, state.project, state.authorization, parameterValues,
            state.securityGroupIndexes?.get(state.selectedKey) || state.securityGroupIndex || 0);
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
