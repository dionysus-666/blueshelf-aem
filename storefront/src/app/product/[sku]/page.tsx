import { notFound } from 'next/navigation';
import type { Metadata } from 'next';
import { fetchPage, SITE_ROOT, SNAPSHOT, snapshotIndex, type AemContainer } from '@/lib/aem';
import { AemComponent } from '@/components/aem/mapping';

/** /product/BS1001 -> /content/blueshelf/us/en/product.model.json/BS1001 (URL suffix carries the SKU, like the HTL site) */
type Props = { params: Promise<{ sku: string }> };

export async function generateStaticParams() {
  if (!SNAPSHOT) return [];
  const { skus } = await snapshotIndex();
  return skus.map((sku) => ({ sku }));
}

function productOf(page: AemContainer | null) {
  const root = page?.[':items']?.root as AemContainer | undefined;
  const pd = root && Object.values(root[':items'] || {}).find((i) => i[':type'] === 'blueshelf/components/product-detail') as any;
  return pd?.product as { name?: string; shortDescription?: string } | undefined;
}

export async function generateMetadata({ params }: Props): Promise<Metadata> {
  const { sku } = await params;
  const page = await fetchPage(`${SITE_ROOT}/product`, { suffix: sku }).catch(() => null);
  const p = productOf(page);
  return { title: p?.name ? `${p.name} | BlueShelf` : 'Product | BlueShelf', description: p?.shortDescription };
}

export default async function ProductPage({ params }: Props) {
  const { sku } = await params;
  const page = await fetchPage(`${SITE_ROOT}/product`, { suffix: sku });
  if (!page) notFound();
  return <>{(page[':itemsOrder'] || []).map((name) => <AemComponent key={name} item={page[':items'][name]} />)}</>;
}
