import assert from "node:assert/strict";
import test from "node:test";

import {createStore} from "../../main/resources/META-INF/resources/muonica/js/state/store.js";

test("updates shared state without replacing feature collections", () => {
    const bodies = new Map([["0:0", "{}"]]);
    const state = {query: "", requestBodies: bodies};
    let rendered = 0;
    const store = createStore(state, () => { rendered += 1; });

    store.update({query: "users"});

    assert.equal(store.getState(), state);
    assert.equal(state.query, "users");
    assert.equal(state.requestBodies, bodies);
    assert.equal(rendered, 1);
});
