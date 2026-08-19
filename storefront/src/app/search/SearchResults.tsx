'use client';
import { useEffect, useMemo, useState } from 'react';
import { useSearchParams } from 'next/navigation';
import { ProductList, type Product } from '@/components/aem/ProductList';

export function SearchResults() {
  const q = (useSearchParams().get('q') || '').trim().toLowerCase();
  const [all, setAll] = useState<Product[] | null>(null);
  const [error, setError] = useState(false);
  useEffect(() => {
    const base = (process.env.NEXT_PUBLIC_BASE_PATH || '');
    fetch(`${base}/products.json`).then((r) => (r.ok ? r.json() : Promise.reject())).then(setAll).catch(() => setError(true));
  }, []);
  const products = useMemo(() => (all || []).filter((p) => !q || `${p.name} ${p.brand} ${p.shortDescription || ''}`.toLowerCase().includes(q)), [all, q]);
  if (error) return <ProductList title="Search results" available={false} empty products={[]} source="UNAVAILABLE" />;
  if (!all) return <p className="muted">Loading…</p>;
  return <ProductList title="Search results" searchText={q} total={products.length} products={products} available stale={false} source="SNAPSHOT" empty={products.length === 0} />;
}
