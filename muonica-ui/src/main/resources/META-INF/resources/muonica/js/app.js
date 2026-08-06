import { allEndpoints, endpointKey, loadProject } from "./api.js";
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
    app.querySelectorAll(".endpoint-link").forEach(button => button.addEventListener("click", () => {
        selectedKey = button.dataset.endpoint;
        menuOpen = false;
        render();
    }));
    app.querySelectorAll(".copy-code").forEach(button => button.addEventListener("click", async () => {
        try {
            await navigator.clipboard.writeText(button.dataset.copy);
            const original = button.textContent;
            button.textContent = "Copied";
            window.setTimeout(() => { button.textContent = original; }, 1400);
        } catch {
            button.textContent = "Copy failed";
        }
    }));
}

window.addEventListener("keydown", event => {
    if (event.key === "Escape" && menuOpen) {
        menuOpen = false;
        render();
    }
});

async function start() {
    app.innerHTML = `<div class="grid min-h-screen place-items-center text-muted">Loading documentation…</div>`;
    try {
        project = await loadProject();
        selectedKey = allEndpoints(project)[0]?.key;
        render();
    } catch (error) {
        app.innerHTML = `<main class="grid min-h-screen place-items-center p-6"><section class="max-w-md rounded-xl border border-line bg-surface p-6"><h1 class="text-xl font-bold">Documentation unavailable</h1><p class="mt-2 text-muted">${error.message}</p><button class="mt-5 rounded-lg bg-coral px-4 py-2 text-sm font-bold text-[#160809]" onclick="location.reload()">Try again</button></section></main>`;
    }
}

start();
