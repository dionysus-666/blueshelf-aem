'use client';
import { useEffect, useMemo, useState } from 'react';
import { useSearchParams } from 'next/navigation';
import { ProductList } from '@/components/aem/ProductList';
import { type Product, normalize } from '@/lib/products';

/**
 * Search is client-side over products.json, which is crawled straight from catalog-api and therefore lacks the
 * derived price fields the Sling `Product` model adds on AEM pages. `normalize` fills them in at this boundary
 * (same formula as core Product.java) — the missing `currentPrice` used to crash the whole page here.
 */
export function SearchResults() {
  const typed = (useSearchParams().get('q') || '').trim();
  const q = typed.toLowerCase();
  const [all, setAll] = useState<Product[] | null>(null);
  const [error, setError] = useState(false);
  useEffect(() => {
    const base = (process.env.NEXT_PUBLIC_BASE_PATH || '');
    fetch(`${base}/products.json`)
      .then((r) => (r.ok ? r.json() : Promise.reject()))
      .then((list: Product[]) => setAll(list.map(normalize)))
      .catch(() => setError(true));
  }, []);
  const products = useMemo(() => (all || []).filter((p) => !q || `${p.name} ${p.brand} ${p.shortDescription || ''}`.toLowerCase().includes(q)), [all, q]);
  if (error) return <ProductList title="Search results" available={false} empty products={[]} source="UNAVAILABLE" />;
  if (!all) return <p className="muted">Loading…</p>;
  return <ProductList title="Search results" searchText={typed} total={products.length} products={products} available stale={false} source="SNAPSHOT" empty={products.length === 0} />;
}
