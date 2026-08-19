import { notFound } from 'next/navigation';
import type { Metadata } from 'next';
import { fetchPage, pagePath } from '@/lib/aem';
import { AemComponent } from '@/components/aem/mapping';

/**
 * Catch-all route: /tvs -> /content/blueshelf/us/en/tvs.model.json
 * Search results: /search?q=oled -> the page model is fetched with ?q=oled so the ProductList model (search mode)
 * resolves server-side; we mark such requests dynamic (no ISR) because the output depends on the query.
 */
type Props = { params: Promise<{ slug?: string[] }>; searchParams: Promise<Record<string, string | string[] | undefined>> };

export async function generateMetadata({ params }: Props): Promise<Metadata> {
  const { slug } = await params;
  const page = await fetchPage(pagePath(slug)).catch(() => null);
  return { title: page?.title ? `${page.title} | BlueShelf` : 'BlueShelf', description: page?.description || undefined };
}

export default async function Page({ params, searchParams }: Props) {
  const { slug } = await params;
  const sp = await searchParams;
  const q = typeof sp.q === 'string' ? sp.q : undefined;
  const page = await fetchPage(pagePath(slug), q ? { query: { q } } : {});
  if (!page) notFound();
  const order = page[':itemsOrder'] || [];
  return <>{order.map((name) => <AemComponent key={name} item={page[':items'][name]} />)}</>;
}
