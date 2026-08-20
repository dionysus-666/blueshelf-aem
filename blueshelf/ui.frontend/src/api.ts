/**
 * Thin client over the Sling HTTP API. Every function here is a call AEM's own UI makes too:
 *  - GET  <path>.<depth>.json            default JSON renderer (read the repository)
 *  - POST <path>  (form fields)          Sling POST servlet (create/update/delete/copy/move/order)
 *  - POST /j_security_check              form login
 *  - POST /bin/blueshelf/replicate       our replication servlet (AEM: /bin/replicate.json)
 */

export type Json = Record<string, any>;

const jsonHeaders = { Accept: 'application/json' };

export async function getJson(path: string, depth: number | 'infinity' = 0): Promise<Json | null> {
  const res = await fetch(`${path}.${depth}.json`, { headers: jsonHeaders, credentials: 'same-origin' });
  if (res.status === 404) return null;
  if (!res.ok) throw new Error(`GET ${path}.${depth}.json -> ${res.status}`);
  return res.json();
}

/** Sling POST servlet. Returns the JSON status (path, location, changes). */
export async function post(path: string, fields: Record<string, string | string[]> | FormData): Promise<Json> {
  const body = fields instanceof FormData ? fields : toForm(fields);
  if (!body.has('_charset_')) body.append('_charset_', 'utf-8');
  const res = await fetch(path, { method: 'POST', body, headers: jsonHeaders, credentials: 'same-origin' });
  let data: Json = {};
  try { data = await res.json(); } catch { /* non-json error page */ }
  if (!res.ok) throw new Error(data['status.message'] || data.message || `POST ${path} -> ${res.status}`);
  return data;
}

function toForm(fields: Record<string, string | string[]>): FormData {
  const fd = new FormData();
  for (const [k, v] of Object.entries(fields)) {
    if (Array.isArray(v)) v.forEach((x) => fd.append(k, x)); else fd.append(k, v);
  }
  return fd;
}

// ---------- auth ----------
export async function currentUser(): Promise<string> {
  const res = await fetch('/system/sling/info.sessionInfo.json', { credentials: 'same-origin' });
  if (!res.ok) return 'anonymous';
  const j = await res.json();
  return j.userID ?? 'anonymous';
}
export async function login(user: string, password: string): Promise<boolean> {
  const fd = new FormData();
  fd.append('j_username', user); fd.append('j_password', password); fd.append('j_validate', 'true');
  const res = await fetch('/j_security_check', { method: 'POST', body: fd, credentials: 'same-origin' });
  return res.ok;
}
export async function logout(): Promise<void> {
  await fetch('/system/sling/logout', { credentials: 'same-origin' });
}

// ---------- pages ----------
export interface PageNode { path: string; name: string; title: string; template?: string; hasChildren: boolean; resourceType?: string; isFolder?: boolean; }

export async function listPages(parent: string): Promise<PageNode[]> {
  const j = await getJson(parent, 2); // depth 2 so we can see each child's jcr:content + grandchildren existence
  if (!j) return [];
  const FOLDERS = ['sling:Folder', 'sling:OrderedFolder', 'nt:folder'];
  const isNode = (v: any, types: string[]) => v && typeof v === 'object' && types.includes(v['jcr:primaryType']);
  // AEM's Sites console lists pages AND folders (e.g. /content/site/us is a folder, /us/en the language root page)
  return Object.entries(j)
    .filter(([, v]) => isNode(v, ['cq:Page', ...FOLDERS]))
    .map(([name, v]) => {
      const isFolder = FOLDERS.includes(v['jcr:primaryType']);
      const jc = v['jcr:content'] || {};
      const hasChildren = Object.values(v).some((c: any) => isNode(c, ['cq:Page', ...FOLDERS]));
      return { path: `${parent}/${name}`, name, title: isFolder ? `📁 ${jc['jcr:title'] || name}` : (jc['jcr:title'] || name), template: jc['cq:template'], resourceType: jc['sling:resourceType'], hasChildren, isFolder };
    });
}

export async function listTemplates(): Promise<{ path: string; title: string }[]> {
  const j = await getJson('/conf/blueshelf/settings/wcm/templates', 2);
  if (!j) return [];
  return Object.entries(j)
    .filter(([, v]) => v && v['jcr:primaryType'] === 'cq:Template')
    .map(([name, v]) => ({ path: `/conf/blueshelf/settings/wcm/templates/${name}`, title: v['jcr:content']?.['jcr:title'] || name }));
}

/**
 * Create a page from a template — what AEM's PageManager.create() does:
 * 1. create the cq:Page node, 2. copy <template>/initial/jcr:content under it, 3. set title + cq:template.
 */
export async function createPage(parent: string, name: string, title: string, template: string): Promise<string> {
  const path = `${parent}/${name}`;
  await post(path, { 'jcr:primaryType': 'cq:Page' });
  await post(`${template}/initial/jcr:content`, { ':operation': 'copy', ':dest': `${path}/jcr:content`, ':replace': 'true' });
  await post(`${path}/jcr:content`, { './jcr:title': title, './cq:template': template });
  return path;
}
export async function deleteNode(path: string): Promise<void> { await post(path, { ':operation': 'delete' }); }

// ---------- components ----------
export interface ComponentDef { resourceType: string; title: string; group: string; description?: string; hasDialog: boolean; }

export async function listComponents(): Promise<ComponentDef[]> {
  const j = await getJson('/apps/blueshelf/components', 2);
  if (!j) return [];
  return Object.entries(j)
    .filter(([, v]) => v && v['jcr:primaryType'] === 'cq:Component')
    .map(([name, v]) => ({
      resourceType: `blueshelf/components/${name}`,
      title: v['jcr:title'] || name,
      group: v['componentGroup'] || '',
      description: v['jcr:description'],
      hasDialog: !!v['cq:dialog'],
    }));
}

/** Allowed components for a container = the template's policy for that container (AEM: design/policy). */
export async function allowedComponents(templatePath: string | undefined, containerName: string, all: ComponentDef[]): Promise<ComponentDef[]> {
  const visible = all.filter((c) => c.group && !c.group.startsWith('.')); // '.hidden', '.blueshelf.base' are not authorable
  if (!templatePath) return visible;
  const mapping = await getJson(`${templatePath}/policies/jcr:content/${containerName}`, 0);
  const policyRel = mapping?.['cq:policy'];
  if (!policyRel) return visible;
  const policy = await getJson(`/conf/blueshelf/settings/wcm/policies/${policyRel}`, 0);
  const rules: string[] = ([] as string[]).concat(policy?.components || []);
  if (!rules.length) return visible;
  return visible.filter((c) => rules.some((r) => (r.startsWith('group:') ? c.group === r.slice(6) : c.resourceType === r)));
}

/**
 * Dialog lookup WITH inheritance: if the component has no cq:dialog, follow sling:resourceSuperType
 * (proxy components inherit the base component's dialog). AEM's Granite does the same resolution.
 */
export async function getDialog(resourceType: string): Promise<Json | null> {
  let rt: string | undefined = resourceType;
  for (let hops = 0; rt && hops < 10; hops++) {
    const dlg = await getJson(`/apps/${rt}/cq:dialog`, 10);
    if (dlg) return dlg;
    const def = await getJson(`/apps/${rt}`, 0);
    rt = def?.['sling:resourceSuperType'];
  }
  return null;
}

export interface StyleOption { id: string; label: string; classes: string; group: string; }
/** Style System: styles offered by the template policy for this component + currently selected ids. */
export async function getStyles(path: string): Promise<{ available: StyleOption[]; selected: string[] }> {
  const res = await fetch(`${path}.styles.json`, { headers: jsonHeaders, credentials: 'same-origin' });
  if (!res.ok) return { available: [], selected: [] };
  return res.json();
}

/** Add a component to a container; `before` = sibling name to insert before (Sling `:order`). */
export async function addComponent(containerPath: string, resourceType: string, before?: string): Promise<string> {
  const hint = resourceType.split('/').pop() || 'component';
  const fields: Record<string, string> = {
    'jcr:primaryType': 'nt:unstructured',
    'sling:resourceType': resourceType,
    ':nameHint': hint,
  };
  if (before) fields[':order'] = `before ${before}`;
  const r = await post(`${containerPath}/*`, fields); // "/*" => Sling generates a unique node name from :nameHint
  return r.path;
}
export async function reorder(path: string, order: string): Promise<void> { await post(path, { ':order': order }); }

/**
 * Save dialog values onto a component node. Mirrors what Granite's form submit sends:
 *   ./prop=value   | ./prop@Delete (to remove)   | ./prop@TypeHint=Boolean
 */
export async function saveProperties(path: string, values: Record<string, string | string[] | null>, typeHints: Record<string, string> = {}): Promise<void> {
  const fd = new FormData();
  for (const [name, v] of Object.entries(values)) {
    if (v === null || v === '' || (Array.isArray(v) && v.length === 0)) {
      fd.append(`${name}@Delete`, 'true');
    } else if (Array.isArray(v)) {
      v.forEach((x) => fd.append(name, x));
      fd.append(`${name}@TypeHint`, 'String[]');
    } else {
      fd.append(name, v);
      if (typeHints[name]) fd.append(`${name}@TypeHint`, typeHints[name]);
    }
  }
  await post(path, fd);
}

// ---------- replication ----------
export async function replicate(path: string, action: 'activate' | 'deactivate'): Promise<string> {
  const fd = new FormData(); fd.append('path', path); fd.append('action', action);
  const res = await fetch('/bin/blueshelf/replicate', { method: 'POST', body: fd, credentials: 'same-origin' });
  const j = await res.json().catch(() => ({ ok: false, message: `HTTP ${res.status}` }));
  if (!j.ok) throw new Error(j.message);
  return j.message;
}
