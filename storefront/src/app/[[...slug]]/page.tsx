import { notFound } from 'next/navigation';
import type { Metadata } from 'next';
import { fetchPage, pagePath, SITE_ROOT, SNAPSHOT, snapshotIndex } from '@/lib/aem';
import { AemComponent } from '@/components/aem/mapping';

/**
 * Catch-all route: /tvs -> /content/blueshelf/us/en/tvs.model.json
 * (search lives in its own route: /search is query-dependent and rendered client-side)
 */
type Props = { params: Promise<{ slug?: string[] }> };

// Static export (GitHub Pages): pre-render every page found in the snapshot
export async function generateStaticParams() {
  if (!SNAPSHOT) return [];
  const { pages } = await snapshotIndex();
  return pages.map((p) => ({ slug: p === SITE_ROOT ? [] : p.substring(SITE_ROOT.length + 1).split('/') }));
}

export async function generateMetadata({ params }: Props): Promise<Metadata> {
  const { slug } = await params;
  const page = await fetchPage(pagePath(slug)).catch(() => null);
  return { title: page?.title ? `${page.title} | BlueShelf` : 'BlueShelf', description: page?.description || undefined };
}

export default async function Page({ params }: Props) {
  const { slug } = await params;
  const page = await fetchPage(pagePath(slug));
  if (!page) notFound();
  const order = page[':itemsOrder'] || [];
  return <>{order.map((name) => <AemComponent key={name} item={page[':items'][name]} />)}</>;
}
