export async function copyText(text) {
    if (navigator.clipboard?.writeText) {
        await navigator.clipboard.writeText(text);
        return;
    }
    const helper = document.createElement("textarea");
    helper.value = text;
    helper.style.position = "fixed";
    helper.style.opacity = "0";
    document.body.append(helper);
    helper.select();
    document.execCommand("copy");
    helper.remove();
}

export function showCopyResult(button, successful) {
    const original = button.innerHTML;
    button.innerHTML = successful
        ? '<svg class="w-4 h-4 text-mint" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><path d="M20 6 9 17l-5-5" stroke-linecap="round" stroke-linejoin="round"/></svg>'
        : '<span class="text-brand text-xs font-bold">!</span>';
    window.setTimeout(() => {
        if (button.isConnected) button.innerHTML = original;
    }, 1200);
}
