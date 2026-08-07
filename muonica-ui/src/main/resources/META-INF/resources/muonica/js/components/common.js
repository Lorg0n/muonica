import {escapeHtml} from "../lib/html.js";

const methodStyles = {
    GET: "method-get", POST: "method-post", PUT: "method-put", PATCH: "method-patch", DELETE: "method-delete"
};

export const copyIcon = '<svg class="w-4 h-4" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><rect x="9" y="9" width="12" height="12" rx="2"/><path d="M5 15V5a2 2 0 0 1 2-2h10"/></svg>';

export function methodBadge(method, compact = false) {
    const value = (method || "API").toUpperCase();
    const style = methodStyles[value] || "method-api";
    return `<span class="method-badge ${compact ? "method-badge-compact" : ""} ${style}">${escapeHtml(value)}</span>`;
}
