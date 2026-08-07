export function createStore(initialState, onChange) {
    const state = initialState;

    return {
        getState: () => state,
        update(change) {
            Object.assign(state, change);
            onChange(state);
        },
        mutate(mutator) {
            mutator(state);
            onChange(state);
        }
    };
}
