import './site.css';

/**
 * Site JS (ships in clientlib-site). Progressive enhancement: the search box works as a plain GET form;
 * with JS we add type-ahead against the resourceType-bound servlet  <page>.search.json?q=
 */
function typeahead(form: HTMLFormElement) {
  const input = form.querySelector<HTMLInputElement>('.search__input');
  const endpoint = form.dataset.searchEndpoint;
  if (!input || !endpoint) return;
  const box = document.createElement('ul');
  box.className = 'search__suggest';
  form.appendChild(box);
  let timer: number | undefined;
  let ctrl: AbortController | undefined;
  input.addEventListener('input', () => {
    window.clearTimeout(timer);
    const q = input.value.trim();
    if (q.length < 2) { box.innerHTML = ''; return; }
    timer = window.setTimeout(async () => {
      ctrl?.abort(); ctrl = new AbortController();
      try {
        const res = await fetch(`${endpoint}?q=${encodeURIComponent(q)}&size=5`, { signal: ctrl.signal });
        if (!res.ok) { box.innerHTML = ''; return; }
        const data = await res.json();
        box.innerHTML = (data.items || []).map((p: any) =>
          `<li><a href="${productUrl(form, p.sku)}"><b>${esc(p.brand)}</b> ${esc(p.name)} <span>$${p.currentPrice ?? p.price}</span></a></li>`).join('');
      } catch { /* aborted */ }
    }, 200);
  });
  document.addEventListener('click', (e) => { if (!form.contains(e.target as Node)) box.innerHTML = ''; });
}
function productUrl(form: HTMLFormElement, sku: string) {
  return (form.dataset.productPage || '/content/blueshelf/us/en/product') + '.html/' + encodeURIComponent(sku);
}
function esc(s: string) { return String(s ?? '').replace(/[&<>"]/g, (c) => ({ '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;' }[c]!)); }

document.querySelectorAll<HTMLFormElement>('form.search').forEach(typeahead);
