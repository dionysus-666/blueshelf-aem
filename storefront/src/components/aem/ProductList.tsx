import Link from 'next/link';

export interface Product { sku: string; name: string; brand: string; price: number; salePrice?: number | null; currentPrice: number; onSale: boolean; savingsPercent: number; rating: number; reviewCount: number; inStock: boolean; image: string; shortDescription?: string; highlights?: string[] }

export function ProductList(p: { title?: string; products?: Product[]; total?: number; available?: boolean; stale?: boolean; source?: string; searchText?: string; empty?: boolean }) {
  return (
    <section className="plp">
      <header className="plp__head">
        {p.title && <h2>{p.title}</h2>}
        {p.searchText && <p className="plp__meta">Results for “{p.searchText}” — {p.total} found</p>}
        {p.stale && <p className="plp__stale">⚠ Prices may be out of date (catalog temporarily unavailable).</p>}
        <small className="plp__src">source: {p.source}</small>
      </header>
      {!p.available && <div className="plp__unavailable">Products are temporarily unavailable. Please try again shortly.</div>}
      {p.available && p.empty && <p>No products match.</p>}
      {!p.empty && (
        <ul className="plp__grid">
          {(p.products || []).map((x) => (
            <li className="card" key={x.sku}>
              <Link className="card__link" href={`/product/${x.sku}`}>
                <img className="card__img" src={x.image} alt={x.name} loading="lazy" />
                <span className="card__brand">{x.brand}</span>
                <span className="card__name">{x.name}</span>
              </Link>
              <div className="card__price">
                <span className={`price ${x.onSale ? 'price--sale' : ''}`}>${x.currentPrice.toFixed(2)}</span>
                {x.onSale && <span className="price--was">Was ${x.price}</span>}
                {x.onSale && <span className="badge">Save {x.savingsPercent}%</span>}
              </div>
              <div className="card__rating">★ {x.rating} <small>({x.reviewCount})</small></div>
              <div className={`card__stock ${x.inStock ? 'in' : 'out'}`}>{x.inStock ? 'In stock' : 'Sold out'}</div>
            </li>
          ))}
        </ul>
      )}
    </section>
  );
}
