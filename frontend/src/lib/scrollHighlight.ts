export function scrollAndHighlight(elementId: string) {
  const el = document.getElementById(elementId);
  if (!el) return;

  el.scrollIntoView({ behavior: 'smooth', block: 'center' });
  el.classList.add('source-highlight');
  window.setTimeout(() => {
    el.classList.remove('source-highlight');
  }, 1500);
}
