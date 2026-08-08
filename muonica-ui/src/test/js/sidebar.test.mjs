import assert from "node:assert/strict";
import test from "node:test";

import {
    SIDEBAR_WIDTH_STORAGE_KEY,
    clampSidebarWidth,
    loadSidebarWidth,
    saveSidebarWidth
} from "../../main/resources/META-INF/muonica/js/state/sidebar.js";

function storage() {
    const values = new Map();
    return {
        getItem: key => values.get(key) || null,
        setItem: (key, value) => values.set(key, value)
    };
}

test("clamps sidebar widths to the supported range", () => {
    assert.equal(clampSidebarWidth(100), 240);
    assert.equal(clampSidebarWidth(360.4), 360);
    assert.equal(clampSidebarWidth(900), 480);
    assert.equal(clampSidebarWidth("not a width"), 320);
});

test("persists normalized sidebar width", () => {
    const browserStorage = storage();

    assert.equal(loadSidebarWidth(browserStorage), 320);
    assert.equal(saveSidebarWidth(999, browserStorage), 480);
    assert.equal(browserStorage.getItem(SIDEBAR_WIDTH_STORAGE_KEY), "480");
    assert.equal(loadSidebarWidth(browserStorage), 480);
});
