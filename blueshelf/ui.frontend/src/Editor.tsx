import { useCallback, useEffect, useRef, useState } from 'react';
import { addComponent, allowedComponents, deleteNode, getDialog, getJson, listComponents, reorder, replicate, type ComponentDef, type Json } from './api';
import { Dialog } from './Dialog';

/**
 * Page editor ("Touch UI" stand-in).
 * - Center: the real page rendered by Sling in an iframe with ?wcmmode=edit (components get .bs-cmp wrappers)
 * - Overlays: we attach listeners inside the iframe (same-origin) to select / drop components
 * - Left: component browser (filtered by the template's container policy) — drag into the page
 * - Right: the selected component's cq:dialog rendered by <Dialog/>, plus move/delete actions
 * - Toolbar: Edit/Preview, Page Properties, Publish / Unpublish, open published view
 */

interface Selected { path: string; type: string; name: string; container: string; }

export function Editor({ page, onOpenSites }: { page: string; onOpenSites: () => void }) {
  const frame = useRef<HTMLIFrameElement>(null);
  const [mode, setMode] = useState<'edit' | 'preview'>('edit');
  const [pageInfo, setPageInfo] = useState<Json | null>(null);
  const [components, setComponents] = useState<ComponentDef[]>([]);
  const [allowed, setAllowed] = useState<ComponentDef[]>([]);
  const [selected, setSelected] = useState<Selected | null>(null);
  const [dialog, setDialog] = useState<{ def: Json; path: string; title: string } | null>(null);
  const [status, setStatus] = useState<string>('');
  const [busy, setBusy] = useState(false);
  const [reloadKey, setReloadKey] = useState(0);

  const pageContent = `${page}/jcr:content`;
  const reload = useCallback(() => setReloadKey((k) => k + 1), []);

  useEffect(() => {
    getJson(pageContent, 0).then(setPageInfo);
    listComponents().then(setComponents);
  }, [page, pageContent, reloadKey]);

  useEffect(() => {
    if (!pageInfo) return;
    allowedComponents(pageInfo['cq:template'], 'root', components).then(setAllowed);
  }, [pageInfo, components]);

  // ---- wire up the iframe once the page is loaded ----
  function onFrameLoad() {
    const doc = frame.current?.contentDocument;
    if (!doc || mode !== 'edit') return;
    const style = doc.createElement('style');
    style.textContent = `
      .bs-cmp{position:relative;outline:1px dashed rgba(0,70,190,.45);outline-offset:2px;margin:2px 0;cursor:pointer}
      .bs-cmp:hover{outline:2px solid rgba(0,70,190,.8)}
      .bs-cmp.bs-selected{outline:2px solid #0046be;box-shadow:0 0 0 4px rgba(0,70,190,.15)}
      .bs-cmp.bs-over,.bs-drop.bs-over{outline:3px solid #ffe000}
      .bs-drop{border:2px dashed #9aa;color:#667;text-align:center;padding:.75rem;margin:.5rem 0;border-radius:8px;font:14px system-ui}
      .bs-cmp::before{content:attr(data-bs-type);position:absolute;top:-10px;left:6px;background:#0046be;color:#fff;font:11px/1 system-ui;padding:2px 6px;border-radius:4px;opacity:0;transition:opacity .1s;z-index:5}
      .bs-cmp:hover::before,.bs-cmp.bs-selected::before{opacity:1}
    `;
    doc.head.appendChild(style);

    doc.querySelectorAll<HTMLElement>('.bs-cmp').forEach((el) => {
      el.addEventListener('click', (ev) => {
        ev.preventDefault(); ev.stopPropagation();
        doc.querySelectorAll('.bs-selected').forEach((x) => x.classList.remove('bs-selected'));
        el.classList.add('bs-selected');
        const container = (el.parentElement?.closest('[data-bs-container]') as HTMLElement | null)?.dataset.bsContainer || '';
        select({ path: el.dataset.bsPath!, type: el.dataset.bsType!, name: el.dataset.bsName!, container });
      });
      el.addEventListener('dblclick', (ev) => { ev.preventDefault(); openDialogFor(el.dataset.bsPath!, el.dataset.bsType!); });
      wireDrop(el, doc);
    });
    doc.querySelectorAll<HTMLElement>('.bs-drop').forEach((el) => wireDrop(el, doc));
    // links inside the page must not navigate the iframe away in edit mode
    doc.querySelectorAll('a').forEach((a) => a.addEventListener('click', (e) => e.preventDefault()));
  }

  function wireDrop(el: HTMLElement, doc: Document) {
    el.addEventListener('dragover', (e) => { e.preventDefault(); el.classList.add('bs-over'); });
    el.addEventListener('dragleave', () => el.classList.remove('bs-over'));
    el.addEventListener('drop', async (e) => {
      e.preventDefault(); el.classList.remove('bs-over');
      const rt = e.dataTransfer?.getData('text/x-resource-type') || dragging.current;
      if (!rt) return;
      // drop on a component => insert before it; drop on the drop-zone => append
      if (el.classList.contains('bs-cmp')) {
        const container = (el.parentElement?.closest('[data-bs-container]') as HTMLElement | null)?.dataset.bsContainer;
        if (container) await doAdd(container, rt, el.dataset.bsName);
      } else {
        await doAdd(el.dataset.bsContainer!, rt);
      }
      doc.defaultView?.focus();
    });
  }

  const dragging = useRef<string | null>(null);

  async function doAdd(container: string, rt: string, before?: string) {
    setBusy(true);
    try {
      const p = await addComponent(container, rt, before);
      setStatus(`Added ${rt} at ${p}`);
      reload();
      const def = components.find((c) => c.resourceType === rt);
      if (def?.hasDialog) openDialogFor(p, rt);
    } catch (e: any) { setStatus('Error: ' + e.message); } finally { setBusy(false); }
  }

  function select(s: Selected) { setSelected(s); setDialog(null); }

  async function openDialogFor(path: string, type: string) {
    const def = await getDialog(type);
    if (!def) { setStatus(`${type} has no cq:dialog`); return; }
    setDialog({ def, path, title: def['jcr:title'] || type });
  }

  async function act(fn: () => Promise<any>, okMsg?: string) {
    setBusy(true);
    try { const r = await fn(); setStatus(okMsg || String(r || 'Done')); reload(); }
    catch (e: any) { setStatus('Error: ' + e.message); }
    finally { setBusy(false); }
  }

  const frameSrc = `${page}.html?wcmmode=${mode === 'edit' ? 'edit' : 'disabled'}&_=${reloadKey}`;

  return (
    <div className="editor">
      <header className="bar">
        <button onClick={onOpenSites} title="Back to Sites">☰ Sites</button>
        <div className="bar__title">
          <strong>{pageInfo?.['jcr:title'] || page}</strong>
          <code className="muted">{page}</code>
        </div>
        <div className="bar__spacer" />
        <div className="seg">
          <button className={mode === 'edit' ? 'active' : ''} onClick={() => setMode('edit')}>Edit</button>
          <button className={mode === 'preview' ? 'active' : ''} onClick={() => setMode('preview')}>Preview</button>
        </div>
        <button onClick={() => openDialogFor(pageContent, pageInfo?.['sling:resourceType'] || 'blueshelf/components/page')}>Page Properties</button>
        <button className="primary" disabled={busy} onClick={() => act(() => replicate(page, 'activate'))}>Publish</button>
        <button disabled={busy} onClick={() => act(() => replicate(page, 'deactivate'))}>Unpublish</button>
        <a className="btn" href={`http://localhost:4503${page}.html`} target="_blank" rel="noreferrer">View published ↗</a>
      </header>

      <div className="editor__body">
        {mode === 'edit' && (
          <aside className="side side--left">
            <h3>Components</h3>
            <p className="muted small">Drag onto the page, or click + to append. Allowed set comes from the template policy.</p>
            {allowed.map((c) => (
              <div key={c.resourceType} className="cmp" draggable
                   onDragStart={(e) => { dragging.current = c.resourceType; e.dataTransfer.setData('text/x-resource-type', c.resourceType); e.dataTransfer.effectAllowed = 'copy'; }}
                   onDragEnd={() => { dragging.current = null; }}>
                <div><strong>{c.title}</strong><br /><small className="muted">{c.group}</small></div>
                <button title="Append to root container" onClick={() => doAdd(`${pageContent}/root`, c.resourceType)}>+</button>
              </div>
            ))}
            {!allowed.length && <p className="muted">No components allowed here.</p>}
          </aside>
        )}

        <main className="stage">
          <iframe key={frameSrc} ref={frame} title="page" src={frameSrc} onLoad={onFrameLoad} />
        </main>

        {mode === 'edit' && (
          <aside className="side side--right">
            {dialog ? (
              <Dialog dialog={dialog.def} path={dialog.path} onSaved={() => { setDialog(null); setStatus('Saved'); reload(); }} onCancel={() => setDialog(null)} />
            ) : selected ? (
              <div className="sel">
                <h3>{selected.type.split('/').pop()}</h3>
                <code className="muted small">{selected.path}</code>
                <div className="actions">
                  <button className="primary" onClick={() => openDialogFor(selected.path, selected.type)}>Configure</button>
                  <button onClick={() => act(() => reorder(selected.path, 'first'), 'Moved to top')}>⤒ First</button>
                  <button onClick={() => act(() => reorder(selected.path, 'last'), 'Moved to bottom')}>⤓ Last</button>
                  <button className="danger" onClick={() => { if (confirm('Delete component?')) act(() => deleteNode(selected.path), 'Deleted').then(() => setSelected(null)); }}>Delete</button>
                </div>
                <p className="muted small">Tip: double-click a component on the page to open its dialog.</p>
              </div>
            ) : (
              <div className="muted"><h3>Nothing selected</h3><p>Click a component in the page.</p></div>
            )}
          </aside>
        )}
      </div>
      <footer className="statusbar">{busy ? 'Working…' : status}</footer>
    </div>
  );
}
