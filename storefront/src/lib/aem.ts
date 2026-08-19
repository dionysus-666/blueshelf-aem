/**
 * AEM (publish) client. The storefront never talks to the JCR or author — only to the published
 * `.model.json` of pages (Sling Model Exporter). Same contract as AEM's SPA SDK (aem-spa-page-model-manager).
 *
 * Two content modes:
 *  - LIVE     (AEM_HOST set): fetch from publish/dispatcher with ISR (60s + tags, on-demand revalidation)
 *  - SNAPSHOT (CONTENT_SNAPSHOT=1): read JSON files crawled from publish at build time (content-snapshot/),
 *    used for the fully static GitHub Pages build. Same shape, same components — "publish to CDN" pattern.
 */
import { promises as fs } from 'node:fs';
import path from 'node:path';

export const SITE_ROOT = '/content/blueshelf/us/en';
export const SNAPSHOT = process.env.CONTENT_SNAPSHOT === '1';
const HOST = process.env.AEM_HOST || 'http://localhost:8080';
const SNAPSHOT_DIR = path.join(process.cwd(), 'content-snapshot');

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
  return l;
}

/** File name used by the snapshot crawler for a page (+ optional suffix). */
export function snapshotFile(pagePath: string, suffix?: string): string {
  return path.join(SNAPSHOT_DIR, `${pagePath}.model.json${suffix ? '__' + suffix : ''}.json`);
}

export async function fetchPage(pagePathArg: string, opts: { suffix?: string; query?: Record<string, string> } = {}): Promise<AemPage | null> {
  if (SNAPSHOT) {
    try { return JSON.parse(await fs.readFile(snapshotFile(pagePathArg, opts.suffix), 'utf8')); }
    catch { return null; }
  }
  const qs = opts.query ? '?' + new URLSearchParams(opts.query).toString() : '';
  const url = `${HOST}${pagePathArg}.model.json${opts.suffix ? '/' + encodeURIComponent(opts.suffix) : ''}${qs}`;
  const res = await fetch(url, {
    next: { revalidate: 60, tags: [pagePathArg] },
    headers: { Accept: 'application/json' },
  });
  if (res.status === 404) return null;
  if (!res.ok) throw new Error(`AEM ${url} -> ${res.status}`);
  return res.json();
}

/** Snapshot mode only: all page paths + product SKUs (for generateStaticParams). */
export async function snapshotIndex(): Promise<{ pages: string[]; skus: string[] }> {
  try { return JSON.parse(await fs.readFile(path.join(SNAPSHOT_DIR, 'index.json'), 'utf8')); }
  catch { return { pages: [], skus: [] }; }
}
