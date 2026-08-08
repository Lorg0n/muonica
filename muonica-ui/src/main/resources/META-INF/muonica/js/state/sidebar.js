export const SIDEBAR_WIDTH_STORAGE_KEY = "muonica.sidebar-width.v1";
export const SIDEBAR_WIDTH_DEFAULT = 320;
export const SIDEBAR_WIDTH_MIN = 240;
export const SIDEBAR_WIDTH_MAX = 480;

function storageOrDefault(storage) {
    if (storage) return storage;
    try {
        return globalThis.localStorage;
    } catch {
        return undefined;
    }
}

export function clampSidebarWidth(value) {
    if (value === null || value === undefined || value === "") return SIDEBAR_WIDTH_DEFAULT;
    const width = Number(value);
    if (!Number.isFinite(width)) return SIDEBAR_WIDTH_DEFAULT;
    return Math.round(Math.min(SIDEBAR_WIDTH_MAX, Math.max(SIDEBAR_WIDTH_MIN, width)));
}

export function loadSidebarWidth(storage = storageOrDefault()) {
    if (!storage) return SIDEBAR_WIDTH_DEFAULT;
    try {
        return clampSidebarWidth(storage.getItem(SIDEBAR_WIDTH_STORAGE_KEY));
    } catch {
        return SIDEBAR_WIDTH_DEFAULT;
    }
}

export function saveSidebarWidth(width, storage = storageOrDefault()) {
    const normalized = clampSidebarWidth(width);
    try {
        storage?.setItem(SIDEBAR_WIDTH_STORAGE_KEY, String(normalized));
    } catch {
        // Browser storage can be disabled; the current session can still use the width.
    }
    return normalized;
}
