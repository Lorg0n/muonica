export function renderDiagram(button) {
    const container = button.closest("[data-diagram]");
    const renderer = button.dataset.diagramRender;
    const source = container?.querySelector(".diagram-source")?.innerText || "";
    const output = container?.querySelector(".diagram-output");
    const render = globalThis.muonicaDiagramRenderers?.[renderer];
    if (typeof render !== "function" || !output) {
        button.textContent = "Source shown below";
        return Promise.resolve();
    }
    output.classList.remove("hidden");
    output.innerHTML = "";
    return Promise.resolve(render(source, output))
        .then(() => { button.textContent = "Rendered"; })
        .catch(() => {
            output.classList.add("hidden");
            button.textContent = "Source shown below";
        });
}
