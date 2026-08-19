import React, { useEffect, useState } from 'react';
import { createRoot } from 'react-dom/client';
import { currentUser, login, logout } from './api';
import { Editor } from './Editor';
import { Sites } from './Sites';
import './styles.css';

const SITE_ROOT = '/content/blueshelf';

/** Navigation is path-based like AEM: /sites.html/<folder>  and  /editor.html/<page> */
function navigate(mode: 'sites' | 'editor', path: string) {
  const isDev = location.port === '5173';
  if (isDev) { history.pushState({}, '', `/?mode=${mode}&page=${path}`); window.dispatchEvent(new PopStateEvent('popstate')); return; }
  location.href = `/${mode}.html${path}`;
}

function App() {
  const el = document.getElementById('app')!;
  const qs = new URLSearchParams(location.search);
  const [mode, setMode] = useState<'sites' | 'editor'>((qs.get('mode') as any) || (el.dataset.mode as any) || 'sites');
  const [page, setPage] = useState<string>(qs.get('page') || el.dataset.page || '');
  const [user, setUser] = useState<string | null>(null);

  useEffect(() => { currentUser().then(setUser); }, []);
  useEffect(() => {
    const h = () => { const q = new URLSearchParams(location.search); setMode((q.get('mode') as any) || 'sites'); setPage(q.get('page') || ''); };
    window.addEventListener('popstate', h); return () => window.removeEventListener('popstate', h);
  }, []);

  if (user === null) return <div className="center muted">…</div>;
  if (user === 'anonymous') return <Login onDone={() => currentUser().then(setUser)} />;

  return (
    <div className="app">
      <div className="userbar">
        <span>BlueShelf Author · signed in as <b>{user}</b></span>
        <a href="/system/console/bundles" target="_blank" rel="noreferrer">OSGi console</a>
        <a href="/bin/browser.html" target="_blank" rel="noreferrer">JCR browser</a>
        <button className="link" onClick={() => logout().then(() => location.reload())}>Sign out</button>
      </div>
      {mode === 'editor' && page
        ? <Editor page={page} onOpenSites={() => navigate('sites', page.substring(0, page.lastIndexOf('/')))} />
        : <Sites root={SITE_ROOT} onOpenEditor={(p) => navigate('editor', p)} />}
    </div>
  );
}

function Login({ onDone }: { onDone: () => void }) {
  const [u, setU] = useState('admin'); const [p, setP] = useState('admin'); const [err, setErr] = useState('');
  return (
    <form className="login" onSubmit={async (e) => { e.preventDefault(); (await login(u, p)) ? onDone() : setErr('Login failed'); }}>
      <h2>BlueShelf Author</h2>
      <p className="muted small">Sling form authentication (/j_security_check). AEM uses the same login flow on author.</p>
      <div className="field"><label>User</label><input value={u} onChange={(e) => setU(e.target.value)} /></div>
      <div className="field"><label>Password</label><input type="password" value={p} onChange={(e) => setP(e.target.value)} /></div>
      {err && <div className="error">{err}</div>}
      <button className="primary" type="submit">Sign in</button>
    </form>
  );
}

createRoot(document.getElementById('app')!).render(<React.StrictMode><App /></React.StrictMode>);
