import { onBeforeUnmount, onMounted } from "vue";

const keyboardControlSelector = [
  "input", "textarea", "select", "audio", "video",
  '[contenteditable]:not([contenteditable="false"])',
  '[role="textbox"]', '[role="combobox"]', '[role="listbox"]', '[role="option"]',
  '[aria-haspopup="listbox"]', '[role="slider"]', '[role="spinbutton"]',
  '[role="menu"]', '[role="menubar"]', '[role="tablist"]',
].join(",");

export function useSlidePaging(canPage: () => boolean, page: (direction: -1 | 1) => void) {
  function onKeydown(event: KeyboardEvent) {
    if (event.key !== "ArrowLeft" && event.key !== "ArrowRight") return;
    if (event.defaultPrevented || event.isComposing || event.altKey || event.ctrlKey || event.metaKey || event.shiftKey) return;
    // Dropdown options can be teleported outside the player; inspect the event path and focus.
    if (event.composedPath().some((target) => target instanceof Element && target.closest(keyboardControlSelector))
      || document.activeElement?.closest(keyboardControlSelector)) return;
    if (!canPage()) return;

    event.preventDefault();
    page(event.key === "ArrowLeft" ? -1 : 1);
  }

  onMounted(() => window.addEventListener("keydown", onKeydown));
  onBeforeUnmount(() => window.removeEventListener("keydown", onKeydown));
}
