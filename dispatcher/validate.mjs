/** Minimal stand-in for Adobe's Dispatcher SDK validator: parse + enforce the rules Cloud Manager enforces in spirit. */
import fs from 'node:fs';
import { parseAny } from './any-parser.mjs';
const farm = Object.values(parseAny(fs.readFileSync(new URL('./dispatcher.any', import.meta.url), 'utf8')).farms)[0];
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
if (problems.length) { console.error('dispatcher.any INVALID:\n - ' + problems.join('\n - ')); process.exit(1); }
console.log(`dispatcher.any OK: ${filters.length} filter rules, ${rules(cache.rules).length} cache rules, statfileslevel ${cache.statfileslevel}`);
