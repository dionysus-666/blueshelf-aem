import { useEffect, useMemo, useRef, useState } from 'react';
import { getJson, listPages, saveProperties, type Json, type StyleOption } from './api';

/**
 * Renders a Granite UI dialog definition (the cq:dialog node tree) as a form.
 * AEM does this server-side with Coral UI components; we map the same resourceTypes to React inputs.
 * Supported: container, tabs, textfield, textarea, numberfield, select, checkbox, pathfield, richtext.
 * Unknown types render as a plain text input, so the dialog still works (a graceful-degradation habit).
 */

type FieldDef = { name: string; node: Json; type: string };

function children(node: Json): [string, Json][] {
  return Object.entries(node).filter(([, v]) => v && typeof v === 'object' && !Array.isArray(v)) as [string, Json][];
}
function rt(node: Json): string { return String(node['sling:resourceType'] || ''); }
function short(type: string): string { return type.split('/').pop() || type; }

/** Flatten the dialog tree into tabs -> fields (ignoring intermediate containers/columns). */
function collect(node: Json, into: FieldDef[]) {
  for (const [name, child] of children(node)) {
    const t = rt(child);
    if (t.endsWith('/form/textfield') || t.endsWith('/form/textarea') || t.endsWith('/form/numberfield') || t.endsWith('/form/select') ||
        t.endsWith('/form/checkbox') || t.endsWith('/form/pathfield') || t.endsWith('/form/pathbrowser') || t.endsWith('dialog/richtext') || t.endsWith('/form/hidden') ||
        (child.name && !t.includes('container') && !t.includes('tabs'))) {
      into.push({ name, node: child, type: short(t) });
    } else {
      collect(child, into);
    }
  }
}
function tabsOf(dialog: Json): { title: string; fields: FieldDef[] }[] {
  // find first node of type .../tabs ; else single tab
  const stack: Json[] = [dialog];
  while (stack.length) {
    const n = stack.shift()!;
    if (rt(n).endsWith('/tabs')) {
      return children(n.items || {}).map(([name, tab]) => {
        const fields: FieldDef[] = []; collect(tab, fields);
        return { title: tab['jcr:title'] || name, fields };
      });
    }
    stack.push(...children(n).map(([, c]) => c));
  }
  const fields: FieldDef[] = []; collect(dialog, fields);
  return [{ title: dialog['jcr:title'] || 'Properties', fields }];
}

interface Props {
  dialog: Json;           // cq:dialog JSON
  path: string;           // component node path (values are loaded from + saved to here)
  styles?: { available: StyleOption[]; selected: string[] };  // Style System options from the template policy
  onSaved: () => void;
  onCancel: () => void;
}

export function Dialog({ dialog, path, styles, onSaved, onCancel }: Props) {
  const tabs = useMemo(() => tabsOf(dialog), [dialog]);
  const hasStyles = !!styles && styles.available.length > 0;
  const [styleIds, setStyleIds] = useState<string[]>(styles?.selected ?? []);
  const [tab, setTab] = useState(0);
  const [values, setValues] = useState<Record<string, any>>({});
  const [loaded, setLoaded] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [saving, setSaving] = useState(false);

  useEffect(() => {
    setLoaded(false);
    getJson(path, 0).then((j) => { setValues(j || {}); setLoaded(true); });
  }, [path]);

  const propName = (f: FieldDef) => String(f.node.name || '').replace(/^\.\//, '');

  async function save() {
    setSaving(true); setError(null);
    try {
      const out: Record<string, string | string[] | null> = {};
      const hints: Record<string, string> = {};
      for (const t of tabs) for (const f of t.fields) {
        const p = propName(f);
        if (!p) continue;
        const v = values[p];
        if (f.node.required && (v === undefined || v === null || v === '')) throw new Error(`"${f.node.fieldLabel || p}" is required`);
        if (f.type === 'checkbox') {
          const on = String(v) === String(f.node.value ?? 'true');
          out[`./${p}`] = on ? String(f.node.value ?? 'true') : (f.node.uncheckedValue !== undefined ? String(f.node.uncheckedValue) : null);
          if (typeof f.node.value === 'boolean' || f.node.value === undefined) hints[`./${p}`] = 'Boolean';
        } else if (f.type === 'numberfield') {
          out[`./${p}`] = v === undefined || v === '' ? null : String(v); hints[`./${p}`] = 'Long';
        } else {
          out[`./${p}`] = v === undefined || v === null ? null : (Array.isArray(v) ? v.map(String) : String(v));
        }
      }
      // Style System: AEM stores the selected style ids as a String[] named cq:styleIds on the component node
      if (hasStyles) out['./cq:styleIds'] = styleIds.length ? styleIds : null;
      await saveProperties(path, out, hints);
      onSaved();
    } catch (e: any) { setError(e.message); } finally { setSaving(false); }
  }

  if (!loaded) return <div className="dialog"><p className="muted">Loading…</p></div>;

  return (
    <div className="dialog">
      <header className="dialog__head">
        <strong>{dialog['jcr:title'] || 'Edit'}</strong>
        <code className="muted">{path}</code>
      </header>
      {(tabs.length > 1 || hasStyles) && (
        <div className="tabs">
          {tabs.map((t, i) => <button key={i} className={i === tab ? 'tab active' : 'tab'} onClick={() => setTab(i)}>{t.title}</button>)}
          {hasStyles && <button className={tab === tabs.length ? 'tab active' : 'tab'} onClick={() => setTab(tabs.length)}>Styles</button>}
        </div>
      )}
      <div className="dialog__body">
        {tab < tabs.length && tabs[tab].fields.map((f) => (
          <Field key={f.name} f={f} value={values[propName(f)]} onChange={(v) => setValues({ ...values, [propName(f)]: v })} />
        ))}
        {tab === tabs.length && hasStyles && (
          <div className="styles">
            <p className="muted small">Styles come from the template policy (cq:styleGroups). Saved as <code>cq:styleIds</code>; rendered as CSS classes.</p>
            {[...new Set(styles!.available.map((s) => s.group))].map((g) => (
              <fieldset key={g} className="stylegroup"><legend>{g || 'Styles'}</legend>
                {styles!.available.filter((s) => s.group === g).map((s) => (
                  <label key={s.id} className="check">
                    <input type="checkbox" checked={styleIds.includes(s.id)}
                           onChange={(e) => setStyleIds(e.target.checked ? [...styleIds, s.id] : styleIds.filter((x) => x !== s.id))} />
                    {s.label} <code className="muted">.{s.classes}</code>
                  </label>
                ))}
              </fieldset>
            ))}
          </div>
        )}
      </div>
      {error && <div className="error">{error}</div>}
      <footer className="dialog__foot">
        <button onClick={onCancel} disabled={saving}>Cancel</button>
        <button className="primary" onClick={save} disabled={saving}>{saving ? 'Saving…' : 'Done'}</button>
      </footer>
    </div>
  );
}

function Field({ f, value, onChange }: { f: FieldDef; value: any; onChange: (v: any) => void }) {
  const label = f.node.fieldLabel || f.node.text || f.name;
  const desc = f.node.fieldDescription;
  const req = !!f.node.required;
  const id = `fld-${f.name}`;
  const wrap = (input: JSX.Element) => (
    <div className="field">
      {f.type !== 'checkbox' && <label htmlFor={id}>{label}{req && <span className="req">*</span>}</label>}
      {input}
      {desc && <small className="muted">{desc}</small>}
    </div>
  );
  switch (f.type) {
    case 'textarea':
      return wrap(<textarea id={id} value={value ?? ''} onChange={(e) => onChange(e.target.value)} rows={3} />);
    case 'numberfield':
      return wrap(<input id={id} type="number" value={value ?? ''} onChange={(e) => onChange(e.target.value)} />);
    case 'select': {
      const opts = children(f.node.items || {}).map(([k, o]) => ({ value: String(o.value ?? k), text: o.text || k }));
      return wrap(
        <select id={id} value={value ?? ''} onChange={(e) => onChange(e.target.value)}>
          <option value="">—</option>
          {opts.map((o) => <option key={o.value} value={o.value}>{o.text}</option>)}
        </select>,
      );
    }
    case 'checkbox': {
      const onVal = String(f.node.value ?? 'true');
      const checked = String(value) === onVal;
      return wrap(
        <label className="check"><input type="checkbox" checked={checked} onChange={(e) => onChange(e.target.checked ? onVal : (f.node.uncheckedValue ?? ''))} /> {label}</label>,
      );
    }
    case 'pathfield':
    case 'pathbrowser':
      return wrap(<PathField id={id} value={value ?? ''} rootPath={f.node.rootPath || '/content'} onChange={onChange} />);
    case 'richtext':
      return wrap(<RichText id={id} value={value ?? ''} onChange={onChange} />);
    case 'hidden':
      return <input type="hidden" value={value ?? f.node.value ?? ''} />;
    default:
      return wrap(<input id={id} type="text" value={value ?? ''} onChange={(e) => onChange(e.target.value)} />);
  }
}

/** Path picker: type a path/URL or browse pages under rootPath (AEM's pathfield opens a tree picker). */
function PathField({ id, value, rootPath, onChange }: { id: string; value: string; rootPath: string; onChange: (v: string) => void }) {
  const [open, setOpen] = useState(false);
  const [cwd, setCwd] = useState(rootPath);
  const [items, setItems] = useState<{ path: string; title: string; hasChildren: boolean }[]>([]);
  useEffect(() => { if (open) listPages(cwd).then(setItems); }, [open, cwd]);
  const up = cwd.length > rootPath.length ? cwd.substring(0, cwd.lastIndexOf('/')) : null;
  return (
    <div className="pathfield">
      <div className="row">
        <input id={id} type="text" value={value} onChange={(e) => onChange(e.target.value)} placeholder="/content/... or https://" />
        <button type="button" onClick={() => setOpen(!open)} title="Browse">📁</button>
      </div>
      {open && (
        <div className="picker">
          <div className="row"><code>{cwd}</code>{up && <button type="button" onClick={() => setCwd(up)}>↑</button>}</div>
          <ul>
            {items.map((it) => (
              <li key={it.path}>
                <button type="button" onClick={() => { onChange(it.path); setOpen(false); }}>{it.title}</button>
                {it.hasChildren && <button type="button" onClick={() => setCwd(it.path)}>›</button>}
              </li>
            ))}
            {!items.length && <li className="muted">no pages</li>}
          </ul>
        </div>
      )}
    </div>
  );
}

/** Minimal RTE (AEM: Coral RTE). Stores HTML; HTL renders with context='html' which sanitizes. */
function RichText({ id, value, onChange }: { id: string; value: string; onChange: (v: string) => void }) {
  const ref = useRef<HTMLDivElement>(null);
  useEffect(() => { if (ref.current && ref.current.innerHTML !== value) ref.current.innerHTML = value; }, [value]);
  const cmd = (c: string, arg?: string) => { document.execCommand(c, false, arg); onChange(ref.current?.innerHTML || ''); };
  return (
    <div className="rte">
      <div className="rte__bar">
        <button type="button" onClick={() => cmd('bold')}><b>B</b></button>
        <button type="button" onClick={() => cmd('italic')}><i>I</i></button>
        <button type="button" onClick={() => cmd('insertUnorderedList')}>• list</button>
        <button type="button" onClick={() => { const u = prompt('Link URL'); if (u) cmd('createLink', u); }}>link</button>
        <button type="button" onClick={() => cmd('removeFormat')}>clear</button>
      </div>
      <div id={id} ref={ref} className="rte__area" contentEditable suppressContentEditableWarning onInput={() => onChange(ref.current?.innerHTML || '')} />
    </div>
  );
}
