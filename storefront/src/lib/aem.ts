/**
 * AEM (publish) client. The storefront never talks to the JCR or author — only to the published
 * `.model.json` of pages (Sling Model Exporter). Same contract as AEM's SPA SDK (aem-spa-page-model-manager).
 *
 * Caching strategy (ISR): every page JSON is cached 60s and tagged by its content path; on-demand
 * revalidation (POST /api/revalidate) is triggered when content is published (see dispatcher flush, Phase 5).
 */
const HOST = process.env.AEM_HOST || 'http://localhost:4503';
export const SITE_ROOT = '/content/blueshelf/us/en';

export interface AemItem { ':type': string; ':path'?: string; [k: string]: unknown }
export interface AemContainer extends AemItem { ':items': Record<string, AemItem>; ':itemsOrder': string[] }
export interface AemPage extends AemContainer {
  title?: string; description?: string; navigation?: { title: string; path: string }[];
}

/** Map a storefront route (e.g. "/tvs") to the AEM page path. */
export function pagePath(slug: string[] = []): string {
  return slug.length ? `${SITE_ROOT}/${slug.join('/')}` : SITE_ROOT;
}
/** Map an AEM content path or link (…/tvs.html) back to a storefront route. */
export function toRoute(link: string | undefined | null): string {
  if (!link) return '#';
  if (/^https?:\/\//.test(link)) return link;
  let l = link.replace(/\.html$/, '');
  if (l.startsWith(SITE_ROOT)) l = l.substring(SITE_ROOT.length) || '/';
  // /product.html/BS1001 => /product/BS1001  (suffix pattern)
  return l.replace(/^\/product\//, '/product/');
}

export async function fetchPage(path: string, opts: { suffix?: string; query?: Record<string, string> } = {}): Promise<AemPage | null> {
  const qs = opts.query ? '?' + new URLSearchParams(opts.query).toString() : '';
  const url = `${HOST}${path}.model.json${opts.suffix ? '/' + encodeURIComponent(opts.suffix) : ''}${qs}`;
  const res = await fetch(url, {
    // ISR: cache 60s, tag by content path so publishing can invalidate exactly this page
    next: { revalidate: 60, tags: [path] },
    headers: { Accept: 'application/json' },
  });
  if (res.status === 404) return null;
  if (!res.ok) throw new Error(`AEM ${url} -> ${res.status}`);
  return res.json();
}
