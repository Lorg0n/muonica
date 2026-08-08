import {allEndpoints, clearAuthorization, loadAuthorization, loadProject, saveAuthorization} from "./api.js";
import {escapeHtml, highlightedJson, renderShell} from "./render.js";
import {copyText, showCopyResult} from "./lib/clipboard.js";
import {renderDiagram} from "./features/diagrams.js";
import {editorText, sendRequest, updateCurlPreview, validateEditor} from "./features/request.js";
import {clampSidebarWidth, loadSidebarWidth, saveSidebarWidth} from "./state/sidebar.js";
import {createStore} from "./state/store.js";

const root = document.querySelector("#app");
const state = {
    project: null,
    selectedKey: undefined,
    query: "",
    menuOpen: false,
    authorizationModalOpen: false,
    authorization: loadAuthorization(),
    requestBodies: new Map(),
    parameterValues: new Map(),
    optionalParametersOpen: new Map(),
    activeTabs: new Map(),
    securityGroupIndexes: new Map(),
    schemasOpen: false,
    selectedSchemaName: undefined,
    sidebarWidth: loadSidebarWidth(),
    selectedEntry() { return allEndpoints(this.project).find(item => item.key === this.selectedKey); },
    selectedState() {
        return {
            requestBody: this.requestBodies.get(this.selectedKey),
            parameterValues: this.parameterValues.get(this.selectedKey),
            optionalParametersOpen: this.optionalParametersOpen.get(this.selectedKey) || false,
            activeTab: this.activeTabs.get(this.selectedKey) || "json",
            securityGroupIndex: this.securityGroupIndexes.get(this.selectedKey) || 0,
            schemasOpen: this.schemasOpen,
            selectedSchemaName: this.selectedSchemaName,
            sidebarWidth: this.sidebarWidth,
            authorization: this.authorization,
            authorizationModalOpen: this.authorizationModalOpen
        };
    }
};

const store = createStore(state, render);
let sidebarResize;

function updateSidebarWidth(width) {
    const sidebarWidth = clampSidebarWidth(width);
    state.sidebarWidth = sidebarWidth;
    const sidebarShell = root.querySelector("[data-sidebar-shell]");
    sidebarShell?.style.setProperty("--muonica-sidebar-width", `${sidebarWidth}px`);
    root.querySelector("[data-sidebar-resizer]")?.setAttribute("aria-valuenow", String(sidebarWidth));
}

function startSidebarResize(event) {
    const resizer = event.target.closest("[data-sidebar-resizer]");
    if (!resizer || (event.pointerType === "mouse" && event.button !== 0)) return;

    const sidebarShell = resizer.closest("[data-sidebar-shell]");
    if (!sidebarShell) return;
    event.preventDefault();
    sidebarResize = {
        pointerId: event.pointerId,
        resizer,
        startX: event.clientX,
        startWidth: state.sidebarWidth
    };
    resizer.setPointerCapture?.(event.pointerId);
    document.body.classList.add("sidebar-resizing");
}

function moveSidebarResize(event) {
    if (!sidebarResize || event.pointerId !== sidebarResize.pointerId) return;
    updateSidebarWidth(sidebarResize.startWidth + event.clientX - sidebarResize.startX);
}

function finishSidebarResize(event) {
    if (!sidebarResize || (event && event.pointerId !== sidebarResize.pointerId)) return;
    const {resizer, pointerId} = sidebarResize;
    try { resizer.releasePointerCapture?.(pointerId); } catch { /* Pointer capture may already be released. */ }
    saveSidebarWidth(state.sidebarWidth);
    sidebarResize = undefined;
    document.body.classList.remove("sidebar-resizing");
}

function handleSidebarResizeKeydown(event) {
    const resizer = event.target.closest("[data-sidebar-resizer]");
    if (!resizer) return;
    const step = event.shiftKey ? 40 : 16;
    if (event.key === "ArrowRight") updateSidebarWidth(state.sidebarWidth + step);
    else if (event.key === "ArrowLeft") updateSidebarWidth(state.sidebarWidth - step);
    else if (event.key === "Home") updateSidebarWidth(Number(resizer.getAttribute("aria-valuemin")));
    else if (event.key === "End") updateSidebarWidth(Number(resizer.getAttribute("aria-valuemax")));
    else return;
    event.preventDefault();
    saveSidebarWidth(state.sidebarWidth);
}

root.addEventListener("pointerdown", startSidebarResize);
root.addEventListener("keydown", handleSidebarResizeKeydown);
window.addEventListener("pointermove", moveSidebarResize);
window.addEventListener("pointerup", finishSidebarResize);
window.addEventListener("pointercancel", finishSidebarResize);

function render() {
    const scrollPositions = new Map([...root.querySelectorAll("[data-preserve-scroll]")]
        .map(element => [element.dataset.preserveScroll, element.scrollTop]));
    root.innerHTML = renderShell(state.project, state.selectedEntry(), state.query, state.menuOpen, state.selectedState());
    root.querySelectorAll("[data-preserve-scroll]").forEach(element => {
        element.scrollTop = scrollPositions.get(element.dataset.preserveScroll) || 0;
    });
}

function updateSearch(input) {
    const selectionStart = input.selectionStart;
    const selectionEnd = input.selectionEnd;
    store.update({query: input.value});
    const replacement = root.querySelector(state.menuOpen ? "#mobile-search" : "#search");
    replacement?.focus();
    if (replacement && selectionStart !== null && selectionEnd !== null) replacement.setSelectionRange(selectionStart, selectionEnd);
}

function setActiveTab(button) {
    const tab = button.dataset.tab;
    state.activeTabs.set(state.selectedKey, tab);
    root.querySelector("#code-json")?.classList.toggle("hidden", tab !== "json");
    root.querySelector("#code-curl")?.classList.toggle("hidden", tab !== "curl");
    root.querySelectorAll(".code-tab").forEach(tabButton => {
        const active = tabButton === button;
        tabButton.classList.toggle("code-tab-active", active);
        tabButton.classList.toggle("bg-ink-800", active);
        tabButton.classList.toggle("text-white", active);
        tabButton.classList.toggle("text-ink-400", !active);
    });
    if (tab === "curl") updateCurlPreview(root, state);
}

async function copyFrom(button) {
    const panel = button.closest("[data-code-panel]");
    const activeTab = state.activeTabs.get(state.selectedKey) || (panel?.querySelector("#code-json") ? "json" : "curl");
    const text = panel
        ? (activeTab === "curl" ? panel.querySelector("#code-curl")?.innerText : panel.querySelector("#code-json")?.innerText) || ""
        : button.dataset.copy || "";
    try { await copyText(text); showCopyResult(button, true); }
    catch { showCopyResult(button, false); }
}

root.addEventListener("input", event => {
    const target = event.target;
    if (target.matches("#search, #mobile-search")) return updateSearch(target);
    if (target.matches("#code-json")) {
        state.requestBodies.set(state.selectedKey, editorText(target));
        validateEditor(root);
        updateCurlPreview(root, state);
    }
    if (target.matches("[data-parameter-key]")) updateCurlPreview(root, state);
});

root.addEventListener("change", event => {
    if (event.target.matches("[data-multipart-part]")) updateCurlPreview(root, state);
    if (event.target.matches("[data-security-group]")) {
        state.securityGroupIndexes.set(state.selectedKey, Number(event.target.value));
        render();
    }
});

root.addEventListener("blur", event => {
    const editor = event.target;
    if (!editor.matches("#code-json")) return;
    try {
        const formatted = JSON.stringify(JSON.parse(editorText(editor)), null, 2);
        state.requestBodies.set(state.selectedKey, formatted);
        editor.innerHTML = highlightedJson(formatted);
        root.querySelector("#json-error")?.classList.add("hidden");
        updateCurlPreview(root, state);
    } catch {
        // Preserve invalid in-progress JSON.
    }
}, true);

root.addEventListener("toggle", event => {
    const disclosure = event.target;
    if (disclosure.matches("[data-schema-section]")) state.schemasOpen = disclosure.open;
}, true);

root.addEventListener("click", async event => {
    const button = event.target.closest("button");
    if (!button) return;
    if (button.matches("#menu-toggle")) return store.update({menuOpen: !state.menuOpen});
    if (button.matches(".endpoint-link")) return store.update({selectedKey: button.dataset.endpoint, selectedSchemaName: undefined, menuOpen: false});
    if (button.matches(".schema-link")) return store.update({selectedKey: undefined, selectedSchemaName: button.dataset.schema, menuOpen: false});
    if (button.matches("[data-optional-parameters-toggle]")) {
        state.optionalParametersOpen.set(state.selectedKey, !state.optionalParametersOpen.get(state.selectedKey));
        return render();
    }
    if (button.matches(".code-tab")) return setActiveTab(button);
    if (button.matches("#send-btn")) return sendRequest(root, state);
    if (button.matches("[data-diagram-render]")) return renderDiagram(button);
    if (button.matches(".copy-code")) return copyFrom(button);
    if (button.matches(".copy-response")) {
        try { await copyText(root.querySelector("#response-body")?.innerText || ""); showCopyResult(button, true); }
        catch { showCopyResult(button, false); }
        return;
    }
    if (button.matches("#authorize-btn")) {
        store.update({authorizationModalOpen: true});
        root.querySelector("[data-auth-scheme]")?.focus();
        return;
    }
    if (button.matches("#auth-close, #auth-cancel")) return store.update({authorizationModalOpen: false});
    if (button.matches("#clear-authorization")) {
        state.authorization = clearAuthorization();
        render();
        root.querySelector("[data-auth-scheme]")?.focus();
        return;
    }
    if (button.matches("[data-auth-toggle]")) {
        const input = root.querySelector(`#${button.dataset.authToggle}`);
        if (input) { const visible = input.type === "text"; input.type = visible ? "password" : "text"; button.textContent = visible ? "Show" : "Hide"; }
    }
});

root.addEventListener("click", event => {
    if (event.target.matches("[data-auth-backdrop]")) store.update({authorizationModalOpen: false});
});

root.addEventListener("submit", event => {
    if (!event.target.matches("#authorization-form")) return;
    event.preventDefault();
    const values = {};
    root.querySelectorAll("[data-auth-scheme]").forEach(input => { values[input.dataset.authScheme] = input.value; });
    state.authorization = saveAuthorization(values);
    state.authorizationModalOpen = false;
    render();
});

window.addEventListener("keydown", event => {
    if (event.key === "Escape" && (state.authorizationModalOpen || state.menuOpen)) {
        return store.update(state.authorizationModalOpen ? {authorizationModalOpen: false} : {menuOpen: false});
    }
    if ((event.metaKey || event.ctrlKey) && event.key.toLowerCase() === "k") {
        event.preventDefault();
        root.querySelector("#search")?.focus();
    }
});

async function start() {
    root.innerHTML = '<div class="grid min-h-screen place-items-center text-ink-400">Loading documentation…</div>';
    try {
        state.project = await loadProject();
        state.selectedKey = allEndpoints(state.project)[0]?.key;
        render();
    } catch (error) {
        root.innerHTML = `<main class="grid min-h-screen place-items-center p-6"><section class="max-w-md rounded-xl border border-ink-800 bg-ink-900 p-6"><h1 class="text-xl font-bold text-white">Documentation unavailable</h1><p class="mt-2 text-ink-400">${escapeHtml(error.message)}</p><button class="mt-5 rounded-lg bg-brand px-4 py-2 text-sm font-bold text-white hover:bg-brand/90 transition-colors" onclick="location.reload()" type="button">Try again</button></section></main>`;
    }
}

start();
