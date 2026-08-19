/**
 * Crawl the PUBLISHED site into content-snapshot/ (pages' .model.json, PDPs by SKU, products.json) so the
 * storefront can be built fully static (GitHub Pages) — a "publish to CDN/static" pipeline.
 *   AEM_HOST=http://localhost:4503 CATALOG_HOST=http://localhost:8081 node scripts/snapshot.mjs
 */
import { promises as fs } from 'node:fs';
import path from 'node:path';

const AEM = process.env.AEM_HOST || 'http://localhost:4503';
const CATALOG = process.env.CATALOG_HOST || 'http://localhost:8081';
const ROOT = '/content/blueshelf/us/en';
const OUT = path.join(process.cwd(), 'content-snapshot');
const PUB = path.join(process.cwd(), 'public');

async function getJson(url) { const r = await fetch(url, { headers: { Accept: 'application/json' } }); if (!r.ok) throw new Error(`${url} -> ${r.status}`); return r.json(); }
async function save(file, data) { await fs.mkdir(path.dirname(file), { recursive: true }); await fs.writeFile(file, JSON.stringify(data)); }

/** walk cq:Page nodes (depth-1 JSON per node, like the Sites console does) */
async function pages(p) {
  const j = await getJson(`${AEM}${p}.1.json`);
  const out = [p];
  for (const [name, v] of Object.entries(j)) if (v && typeof v === 'object' && v['jcr:primaryType'] === 'cq:Page') out.push(...await pages(`${p}/${name}`));
  return out;
}

const all = await pages(ROOT);
const skipped = [];
for (const p of all) {
  if (p.endsWith('/search') || p.endsWith('/product')) continue; // query/suffix driven, handled separately
  try { await save(path.join(OUT, `${p}.model.json.json`), await getJson(`${AEM}${p}.model.json`)); console.log('page', p); }
  catch (e) { skipped.push(`${p}: ${e.message}`); }
}
const products = (await getJson(`${CATALOG}/api/products?size=100`)).items;
await save(path.join(PUB, 'products.json'), products);
const skus = [];
for (const pr of products) {
  try { await save(path.join(OUT, `${ROOT}/product.model.json__${pr.sku}.json`), await getJson(`${AEM}${ROOT}/product.model.json/${pr.sku}`)); skus.push(pr.sku); }
  catch (e) { skipped.push(`${pr.sku}: ${e.message}`); }
}
await save(path.join(OUT, 'index.json'), { pages: all.filter((p) => !p.endsWith('/search') && !p.endsWith('/product')), skus, generatedAt: new Date().toISOString() });
console.log(`snapshot: ${all.length} pages, ${skus.length} PDPs, ${products.length} products; skipped: ${skipped.length}`);
if (skipped.length) console.log(skipped.join('\n'));
