import {escapeHtml} from "../lib/html.js";

const methodStyles = {
    GET: "method-get", POST: "method-post", PUT: "method-put", PATCH: "method-patch", DELETE: "method-delete"
};

const badgeStyles = {
    ADMIN: "docs-badge-admin",
    BETA: "docs-badge-beta",
    DEPRECATED: "docs-badge-deprecated",
    INTERNAL: "docs-badge-internal"
};

export const copyIcon = '<svg class="w-4 h-4" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><rect x="9" y="9" width="12" height="12" rx="2"/><path d="M5 15V5a2 2 0 0 1 2-2h10"/></svg>';

export function methodBadge(method, compact = false) {
    const value = (method || "API").toUpperCase();
    const style = methodStyles[value] || "method-api";
    return `<span class="method-badge ${compact ? "method-badge-compact" : ""} ${style}">${escapeHtml(value)}</span>`;
}

function badgeKey(value) {
    return value.toUpperCase();
}

export function documentationBadges(badges = [], compact = false) {
    const values = [];
    for (const badge of Array.isArray(badges) ? badges : []) {
        const value = String(badge ?? "").trim();
        if (value && !values.some(existing => existing.toLowerCase() === value.toLowerCase())) values.push(value);
    }
    if (!values.length) return "";
    const classes = ["docs-badges", compact ? "docs-badges-compact" : ""].filter(Boolean).join(" ");
    return `<span class="${classes}" role="list" aria-label="Endpoint badges">${values.map(value => {
        const style = badgeStyles[badgeKey(value)] || "docs-badge-neutral";
        return `<span class="docs-badge ${style}" role="listitem">${escapeHtml(value)}</span>`;
    }).join("")}</span>`;
}
