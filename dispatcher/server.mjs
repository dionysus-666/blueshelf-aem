/**
 * mini-dispatcher — a faithful-enough model of Adobe's AEM Dispatcher (Apache module) in ~300 lines of Node.
 * Reads dispatcher.any (real syntax subset) and implements:
 *   /filter           allow/deny rules on method, url, path, selectors, extension, suffix, query (last match wins)
 *   /cache /rules     what is cacheable (last match wins), /docroot file cache mirroring the URL path
 *   /allowAuthorized  requests with Authorization / login cookie are never cached when "0"
 *   /ignoreUrlParams  query params that don't affect the cached representation; other params => no cache
 *   /statfileslevel   .stat files; a cached file is stale when an ancestor .stat (<= level) is newer
 *   /invalidate       which cached files are auto-invalidated by .stat touch (others live until explicit delete)
 *   /enableTTL        honours Cache-Control: max-age from publish (.ttl files)
 *   /serveStaleOnError serve stale when publish is down
 *   POST /dispatcher/invalidate.cache  (CQ-Action, CQ-Handle headers) from /allowedClients — the flush protocol
 * Debug header: X-Cache: HIT|MISS|PASS|STALE  (real dispatcher uses dispatcher.log; we add a header for learning).
 */
import http from 'node:http';
import fs from 'node:fs';
import path from 'node:path';
import { parseAny } from './any-parser.mjs';

const CONF = process.env.DISPATCHER_ANY || new URL('./dispatcher.any', import.meta.url).pathname;
const PORT = Number(process.env.PORT || 8080);
const farm = Object.values(parseAny(fs.readFileSync(CONF, 'utf8')).farms)[0];
const cacheCfg = farm.cache;
const DOCROOT = process.env.DOCROOT || cacheCfg.docroot;
const render = Object.values(farm.renders)[0];
const STATLEVEL = Number(cacheCfg.statfileslevel || 0);
const log = (...a) => console.log(new Date().toISOString(), ...a);
fs.mkdirSync(DOCROOT, { recursive: true });

// ---------- glob helpers (dispatcher globs: * ? [..], plus quoted '(a|b)' regex-ish alternatives) ----------
function globToRegex(g) {
  if (g.startsWith('(') && g.endsWith(')')) return new RegExp(`^${g}$`);
  const re = g.replace(/[.+^${}()|\\]/g, '\\$&').replace(/\*/g, '.*').replace(/\?/g, '.');
  return new RegExp(`^${re}$`);
}
const match = (g, v) => g === undefined || (v !== undefined && globToRegex(String(g)).test(String(v)));
const rulesOf = (obj) => Object.keys(obj || {}).sort().map((k) => obj[k]);
/** dispatcher semantics: evaluate all rules, last matching rule decides */
function lastMatch(rules, test) {
  let verdict;
  for (const r of rules) if (test(r)) verdict = r.type;
  return verdict;
}

// ---------- Sling URL decomposition: path.selectors.extension/suffix ----------
function decompose(urlPath) {
  const firstDot = urlPath.indexOf('.', urlPath.lastIndexOf('/') > 0 ? 0 : 0);
  // find the first dot that belongs to the last segment chain: Sling takes the first dot in the path
  const dot = urlPath.indexOf('.');
  if (dot < 0) return { path: urlPath, selectors: '', extension: '', suffix: '' };
  const before = urlPath.substring(0, dot);
  const rest = urlPath.substring(dot + 1);          // selectors.ext/suffix
  const slash = rest.indexOf('/');
  const selExt = slash < 0 ? rest : rest.substring(0, slash);
  const suffix = slash < 0 ? '' : rest.substring(slash);
  const parts = selExt.split('.');
  const extension = parts.pop();
  return { path: before, selectors: parts.join('.'), extension, suffix };
}

// ---------- filter ----------
function filterAllows(req, u) {
  const d = decompose(u.pathname);
  const rules = rulesOf(farm.filter);
  const verdict = lastMatch(rules, (r) =>
    match(r.method, req.method) && match(r.url, u.pathname + (u.search || '')) && match(r.path, d.path) &&
    match(r.selectors, d.selectors) && match(r.extension, d.extension) && match(r.suffix, d.suffix) &&
    match(r.query, u.search ? u.search.substring(1) : undefined) && match(r.glob, `${req.method} ${u.pathname}`));
  return verdict === 'allow';
}

// ---------- cache decisions ----------
function cacheable(req, u) {
  if (req.method !== 'GET') return { ok: false, why: 'method' };
  if (cacheCfg.allowAuthorized === '0' && (req.headers.authorization || /(^|;\s*)(login-token|sling\.formauth)=/.test(req.headers.cookie || ''))) return { ok: false, why: 'authorized' };
  if (lastMatch(rulesOf(cacheCfg.rules), (r) => match(r.glob, u.pathname)) !== 'allow') return { ok: false, why: 'rules' };
  // query params: all must be "ignored" (type allow in /ignoreUrlParams), otherwise pass-through
  for (const k of u.searchParams.keys()) {
    if (lastMatch(rulesOf(cacheCfg.ignoreUrlParams), (r) => match(r.glob, k)) !== 'allow') return { ok: false, why: `param ${k}` };
  }
  return { ok: true };
}
const cacheFile = (u) => path.join(DOCROOT, u.pathname === '/' ? '/index.html' : u.pathname);
function isStale(file, u) {
  const st = fs.statSync(file);
  // TTL
  const ttlFile = file + '.ttl';
  if (cacheCfg.enableTTL === '1' && fs.existsSync(ttlFile) && Number(fs.readFileSync(ttlFile, 'utf8')) < Date.now()) return 'ttl';
  // .stat files: only if the file matches /invalidate rules
  if (lastMatch(rulesOf(cacheCfg.invalidate), (r) => match(r.glob, u.pathname)) !== 'allow') return false;
  let dir = path.dirname(file);
  for (let depth = depthOf(dir); dir.startsWith(DOCROOT); dir = path.dirname(dir), depth--) {
    const stat = path.join(dir, '.stat');
    if (depth <= STATLEVEL && fs.existsSync(stat) && fs.statSync(stat).mtimeMs > st.mtimeMs) return 'stat';
    if (dir === DOCROOT) break;
  }
  return false;
}
const depthOf = (dir) => path.relative(DOCROOT, dir).split(path.sep).filter(Boolean).length;

// ---------- invalidation (the flush agent protocol) ----------
function invalidate(handle, action) {
  // 1. delete the cached files for the handle (page.html, page.model.json, page.*.json ...) and its subtree
  const base = path.join(DOCROOT, handle);
  const dir = path.dirname(base), name = path.basename(base);
  let removed = 0;
  if (fs.existsSync(dir)) for (const f of fs.readdirSync(dir)) {
    if (f === name || f.startsWith(name + '.')) { fs.rmSync(path.join(dir, f), { recursive: true, force: true }); removed++; }
  }
  // 2. touch .stat files from docroot down to statfileslevel along the handle path
  let cur = DOCROOT;
  const segs = handle.split('/').filter(Boolean);
  for (let i = 0; i <= Math.min(STATLEVEL, segs.length); i++) {
    fs.mkdirSync(cur, { recursive: true });
    const stat = path.join(cur, '.stat');
    fs.writeFileSync(stat, '');
    const now = new Date(); fs.utimesSync(stat, now, now);
    if (i < segs.length) cur = path.join(cur, segs[i]);
  }
  log(`INVALIDATE ${action} ${handle} removed=${removed} stat-touched`);
  return removed;
}

// ---------- proxy ----------
function proxy(req, u, onResponse) {
  const opts = { hostname: render.hostname, port: Number(render.port), path: u.pathname + (u.search || ''), method: req.method,
    headers: { ...req.headers, host: `${render.hostname}:${render.port}`, 'x-forwarded-host': req.headers.host || '' }, timeout: Number(render.timeout || 10000) };
  const p = http.request(opts, onResponse);
  p.on('timeout', () => p.destroy(new Error('render timeout')));
  req.pipe(p);
  return p;
}

const server = http.createServer((req, res) => {
  const u = new URL(req.url, 'http://x');
  const clientIp = (req.socket.remoteAddress || '').replace('::ffff:', '');

  // flush endpoint
  if (u.pathname === '/dispatcher/invalidate.cache' && req.method === 'POST') {
    if (lastMatch(rulesOf(cacheCfg.allowedClients), (r) => match(r.glob, clientIp)) !== 'allow') { res.writeHead(403); return res.end('flush not allowed from ' + clientIp); }
    const handle = req.headers['cq-handle'] || '/'; const action = req.headers['cq-action'] || 'Activate';
    const n = invalidate(String(handle), String(action));
    res.writeHead(200, { 'content-type': 'text/plain' }); return res.end(`<H1>OK</H1> invalidated ${handle} (${n} files)\n`);
  }

  if (!filterAllows(req, u)) { log(`DENY ${req.method} ${req.url} from ${clientIp}`); res.writeHead(404, { 'x-cache': 'DENY' }); return res.end('404 Not Found (dispatcher filter)'); }

  const c = cacheable(req, u);
  const file = cacheFile(u);
  if (c.ok && fs.existsSync(file) && fs.statSync(file).isFile()) {
    const stale = isStale(file, u);
    if (!stale) {
      const headers = fs.existsSync(file + '.headers') ? JSON.parse(fs.readFileSync(file + '.headers', 'utf8')) : {};
      res.writeHead(200, { ...headers, 'x-cache': 'HIT' });
      return fs.createReadStream(file).pipe(res);
    }
    log(`STALE(${stale}) ${u.pathname}`);
  }

  const upstream = proxy(req, u, (pr) => {
    const store = c.ok && pr.statusCode === 200;
    const keep = {};
    for (const h of (cacheCfg.headers ? Object.values(cacheCfg.headers) : [])) if (pr.headers[h.toLowerCase()]) keep[h.toLowerCase()] = pr.headers[h.toLowerCase()];
    res.writeHead(pr.statusCode, { ...pr.headers, 'x-cache': store ? 'MISS' : `PASS(${c.why || 'status'})` });
    if (!store) return pr.pipe(res);
    fs.mkdirSync(path.dirname(file), { recursive: true });
    const tmp = file + '.tmp' + process.pid;
    const ws = fs.createWriteStream(tmp);
    pr.pipe(ws); pr.pipe(res);
    ws.on('finish', () => {
      fs.renameSync(tmp, file);
      fs.writeFileSync(file + '.headers', JSON.stringify(keep));
      const m = /max-age=(\d+)/.exec(pr.headers['cache-control'] || '');
      if (cacheCfg.enableTTL === '1' && m) fs.writeFileSync(file + '.ttl', String(Date.now() + Number(m[1]) * 1000));
      log(`CACHED ${u.pathname}`);
    });
  });
  upstream.on('error', (e) => {
    // serveStaleOnError: publish down but we still have a (stale) copy
    if (cacheCfg.serveStaleOnError === '1' && fs.existsSync(file)) {
      const headers = fs.existsSync(file + '.headers') ? JSON.parse(fs.readFileSync(file + '.headers', 'utf8')) : {};
      res.writeHead(200, { ...headers, 'x-cache': 'STALE' }); return fs.createReadStream(file).pipe(res);
    }
    log(`RENDER ERROR ${u.pathname}: ${e.message}`);
    res.writeHead(502, { 'x-cache': 'ERROR' }); res.end('502 Bad Gateway (render unavailable)');
  });
});

server.listen(PORT, () => log(`mini-dispatcher on :${PORT} -> ${render.hostname}:${render.port}, docroot ${DOCROOT}, statfileslevel ${STATLEVEL}`));
