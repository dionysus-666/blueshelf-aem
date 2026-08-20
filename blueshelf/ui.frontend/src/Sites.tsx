import { useEffect, useState } from 'react';
import { createPage, deleteNode, listPages, listTemplates, replicate, type PageNode } from './api';

/** Sites console: browse the cq:Page tree, create pages from templates, open the editor, publish. */
export function Sites({ root, onOpenEditor }: { root: string; onOpenEditor: (path: string) => void }) {
  const [cwd, setCwd] = useState(root);
  const [pages, setPages] = useState<PageNode[]>([]);
  const [status, setStatus] = useState('');
  const [creating, setCreating] = useState(false);
  const [templates, setTemplates] = useState<{ path: string; title: string }[]>([]);
  const [form, setForm] = useState({ title: '', name: '', template: '' });

  const refresh = () => listPages(cwd).then(setPages);
  useEffect(() => { refresh(); }, [cwd]); // eslint-disable-line react-hooks/exhaustive-deps
  useEffect(() => { listTemplates().then((t) => { setTemplates(t); if (t[0]) setForm((f) => ({ ...f, template: t[0].path })); }); }, []);

  const crumbs = cwd.split('/').filter(Boolean);
  const up = cwd.length > root.length ? cwd.substring(0, cwd.lastIndexOf('/')) : null;

  async function run(fn: () => Promise<any>, ok: string) {
    try { await fn(); setStatus(ok); refresh(); } catch (e: any) { setStatus('Error: ' + e.message); }
  }

  async function submitCreate() {
    const name = form.name || form.title.toLowerCase().replace(/[^a-z0-9]+/g, '-').replace(/(^-|-$)/g, '');
    if (!form.title || !name || !form.template) { setStatus('Title and template are required'); return; }
    await run(() => createPage(cwd, name, form.title, form.template), `Created ${cwd}/${name}`);
    setCreating(false); setForm({ title: '', name: '', template: templates[0]?.path || '' });
  }

  return (
    <div className="sites">
      <header className="bar">
        <strong>BlueShelf Sites</strong>
        <nav className="crumbs">
          {crumbs.map((c, i) => {
            const p = '/' + crumbs.slice(0, i + 1).join('/');
            return <span key={p}>/ <button disabled={p.length < root.length} onClick={() => setCwd(p)}>{c}</button></span>;
          })}
        </nav>
        <div className="bar__spacer" />
        {up && <button onClick={() => setCwd(up)}>↑ Up</button>}
        <button className="primary" onClick={() => setCreating(true)}>+ Create page</button>
      </header>

      {creating && (
        <div className="panel">
          <h3>Create page in <code>{cwd}</code></h3>
          <div className="field"><label>Template</label>
            <select value={form.template} onChange={(e) => setForm({ ...form, template: e.target.value })}>
              {templates.map((t) => <option key={t.path} value={t.path}>{t.title}</option>)}
            </select></div>
          <div className="field"><label>Title *</label><input value={form.title} onChange={(e) => setForm({ ...form, title: e.target.value })} /></div>
          <div className="field"><label>Name (URL segment, optional)</label><input value={form.name} placeholder="auto from title" onChange={(e) => setForm({ ...form, name: e.target.value })} /></div>
          <div className="actions"><button onClick={() => setCreating(false)}>Cancel</button><button className="primary" onClick={submitCreate}>Create</button></div>
        </div>
      )}

      <table className="pages">
        <thead><tr><th>Title</th><th>Name</th><th>Template</th><th></th></tr></thead>
        <tbody>
          {pages.map((p) => (
            <tr key={p.path}>
              <td>{p.isFolder ? <button className="link" onClick={() => setCwd(p.path)}>{p.title}</button> : <button className="link" onClick={() => onOpenEditor(p.path)}>{p.title}</button>}</td>
              <td><code>{p.name}</code>{p.hasChildren && <button className="small" onClick={() => setCwd(p.path)}>children ›</button>}</td>
              <td className="muted small">{p.template?.split('/').pop() || '—'}</td>
              <td className="actions">
                {p.isFolder ? <button onClick={() => setCwd(p.path)}>Open</button> : <>
                <button onClick={() => onOpenEditor(p.path)}>Edit</button>
                <a className="btn" href={`${p.path}.html`} target="_blank" rel="noreferrer">View</a>
                <button onClick={() => run(() => replicate(p.path, 'activate'), `Published ${p.path}`)}>Publish</button>
                <button onClick={() => run(() => replicate(p.path, 'deactivate'), `Unpublished ${p.path}`)}>Unpublish</button>
                <button className="danger" onClick={() => { if (confirm(`Delete ${p.path} and its children?`)) run(() => deleteNode(p.path), 'Deleted'); }}>Delete</button>
                </>}
              </td>
            </tr>
          ))}
          {!pages.length && <tr><td colSpan={4} className="muted">No pages here.</td></tr>}
        </tbody>
      </table>
      <footer className="statusbar">{status}</footer>
    </div>
  );
}
