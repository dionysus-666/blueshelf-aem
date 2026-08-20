/** Minimal stand-in for Adobe's Dispatcher SDK validator: parse + enforce the rules Cloud Manager enforces in spirit. */
import fs from 'node:fs';
import { parseAny } from './any-parser.mjs';
const CONF = process.env.DISPATCHER_ANY || new URL('./dispatcher.any', import.meta.url);
const farm = Object.values(parseAny(fs.readFileSync(CONF, 'utf8')).farms)[0];
const rules = (o) => Object.keys(o || {}).sort().map((k) => o[k]);
const problems = [];
const filters = rules(farm.filter);
if (!(filters[0]?.type === 'deny' && filters[0]?.url === '*')) problems.push('first /filter rule must deny everything (/type "deny" /url "*")');
for (const p of ['/system/*', '/bin/*', '/crx/*']) if (!filters.some((f) => f.type === 'deny' && f.url === p)) problems.push(`missing deny for ${p}`);
if (!filters.some((f) => f.type === 'deny' && f.method === 'POST')) problems.push('POST must be denied by default');
if (!filters.some((f) => f.type === 'deny' && /infinity/.test(f.url || ''))) problems.push('deny *.infinity.json');
const cache = farm.cache || {};
if (!cache.docroot) problems.push('/cache /docroot missing');
if (rules(cache.rules)[0]?.type !== 'deny') problems.push('first /cache /rules entry should deny * (cache allow-list)');
if (Number(cache.statfileslevel ?? 0) < 1) problems.push('/statfileslevel should be >= 1 (0 invalidates the whole site on every activation)');
if (rules(cache.ignoreUrlParams)[0]?.type !== 'deny') problems.push('/ignoreUrlParams must start with deny * (unknown params bypass cache, never poison it)');
if (!cache.allowedClients) problems.push('/allowedClients missing: anyone could flush the cache');
// --- semantic checks: simulate last-match-wins the way the dispatcher actually evaluates ---
// A deny rule that a LATER allow overrides is invisible in review; assert the final VERDICT instead.
const globToRegex = (g) => (g.startsWith('(') && g.endsWith(')'))
  ? new RegExp(`^${g}$`)
  : new RegExp('^' + g.replace(/[.+^${}()|\\]/g, '\\$&').replace(/\*/g, '.*').replace(/\?/g, '.') + '$');
const verdict = (ruleSet, value) => {
  let v;
  for (const r of rules(ruleSet)) if (r.glob !== undefined && globToRegex(String(r.glob)).test(value)) v = r.type;
  return v;
};
// query-dependent pages must NEVER be cacheable (final verdict), regardless of rule ordering
for (const path of ['/content/blueshelf/us/en/search.html', '/content/blueshelf/us/en.search.json', '/content/blueshelf/us/en/search.model.json']) {
  if (verdict(cache.rules, path) === 'allow') problems.push(`query-dependent page is cacheable after last-match evaluation: ${path}`);
}
// unknown query params must stay "not ignored" (= uncacheable), or one user's page is served to another
for (const param of ['q', 'zip', 'anythingunknown']) {
  if (verdict(cache.ignoreUrlParams, param) === 'allow') problems.push(`unknown query param '${param}' would be IGNORED for caching -> cache poisoning risk`);
}
if (problems.length) { console.error('dispatcher.any INVALID:\n - ' + problems.join('\n - ')); process.exit(1); }
console.log(`dispatcher.any OK: ${filters.length} filter rules, ${rules(cache.rules).length} cache rules, statfileslevel ${cache.statfileslevel}`);
