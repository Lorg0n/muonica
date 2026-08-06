import { allEndpoints, loadProject } from "./api.js";
import { renderShell } from "./render.js";

const app = document.querySelector("#app");
let project;
let selectedKey;
let query = "";
let menuOpen = false;

function selectedEndpoint() {
    return allEndpoints(project).find(item => item.key === selectedKey);
}

function render() {
    app.innerHTML = renderShell(project, selectedEndpoint(), query, menuOpen);

    const updateQuery = event => {
        query = event.target.value;
        render();
        app.querySelector(menuOpen ? "#mobile-search" : "#search")?.focus();
    };

    app.querySelector("#search")?.addEventListener("input", updateQuery);
    app.querySelector("#mobile-search")?.addEventListener("input", updateQuery);
    app.querySelector("#menu-toggle")?.addEventListener("click", () => {
        menuOpen = !menuOpen;
        render();
    });

    app.querySelectorAll(".endpoint-link").forEach(button => button.addEventListener("click", (e) => {
        e.preventDefault();
        selectedKey = button.dataset.endpoint;
        menuOpen = false;
        render();
    }));

    app.querySelectorAll(".copy-code").forEach(button => button.addEventListener("click", async () => {
        try {
            await navigator.clipboard.writeText(button.dataset.copy);
            const original = button.innerHTML;
            button.innerHTML = '<svg class="w-4 h-4 text-mint" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><path d="M20 6 9 17l-5-5" stroke-linecap="round" stroke-linejoin="round"/></svg>';
            window.setTimeout(() => { button.innerHTML = original; }, 1200);
        } catch {
            button.innerHTML = "!";
        }
    }));

    app.querySelectorAll(".code-tab").forEach(button => button.addEventListener("click", () => {
        const panel = button.closest(".overflow-hidden");
        const code = panel.querySelector(".code-content");
        const isCurl = button.dataset.tab === "curl";

        // Load pre-rendered HTML snippets from datasets
        code.innerHTML = code.dataset[isCurl ? "curl" : "json"];

        // Update corresponding raw value for the copy button
        const copyBtn = panel.querySelector(".copy-code");
        if (copyBtn) {
            copyBtn.dataset.copy = code.dataset[isCurl ? "rawCurl" : "rawJson"];
        }

        panel.querySelectorAll(".code-tab").forEach(tab => {
            const active = tab === button;
            tab.classList.toggle("bg-ink-800", active);
            tab.classList.toggle("text-white", active);
            tab.classList.toggle("text-ink-400", !active);
        });
    }));
}

window.addEventListener("keydown", event => {
    if (event.key === "Escape" && menuOpen) {
        menuOpen = false;
        render();
    }
    if ((event.metaKey || event.ctrlKey) && event.key === 'k') {
        event.preventDefault();
        app.querySelector("#search")?.focus();
    }
});

async function start() {
    app.innerHTML = `<div class="grid min-h-screen place-items-center text-ink-400">Loading documentation…</div>`;
    try {
        project = await loadProject();
        selectedKey = allEndpoints(project)[0]?.key;
        render();
    } catch (error) {
        app.innerHTML = `<main class="grid min-h-screen place-items-center p-6"><section class="max-w-md rounded-xl border border-ink-800 bg-ink-900 p-6"><h1 class="text-xl font-bold text-white">Documentation unavailable</h1><p class="mt-2 text-ink-400">${error.message}</p><button class="mt-5 rounded-lg bg-brand px-4 py-2 text-sm font-bold text-white hover:bg-brand/90 transition-colors" onclick="location.reload()">Try again</button></section></main>`;
    }
}

start();