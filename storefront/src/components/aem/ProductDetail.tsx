import { type Product, isOnSale, priceOf, savingsOf } from '@/lib/products';

export function ProductDetail(p: { sku?: string; product?: Product | null; found?: boolean; notFound?: boolean; noSku?: boolean }) {
  if (p.noSku) return <section className="pdp"><p>No product selected.</p></section>;
  if (!p.found || !p.product) return <section className="pdp"><div className="pdp__missing">Sorry — product <b>{p.sku}</b> is not available right now.</div></section>;
  const x = p.product;
  return (
    <section className="pdp">
      <div className="pdp__grid">
        <img className="pdp__img" src={x.image} alt={x.name} />
        <div className="pdp__info">
          <div className="card__brand">{x.brand}</div>
          <h1 className="pdp__name">{x.name}</h1>
          <div className="card__rating">★ {x.rating} <small>({x.reviewCount} reviews)</small> · SKU {x.sku}</div>
          <div className="pdp__price">
            <span className={`price ${isOnSale(x) ? 'price--sale' : ''}`}>${priceOf(x).toFixed(2)}</span>
            {isOnSale(x) && <span className="price--was">Was ${x.price}</span>}
            {isOnSale(x) && <span className="badge">Save {savingsOf(x)}%</span>}
          </div>
          <p>{x.shortDescription}</p>
          <ul className="pdp__highlights">{(x.highlights || []).map((h) => <li key={h}>{h}</li>)}</ul>
          <button className="pdp__cta" disabled={!x.inStock}>{x.inStock ? 'Add to Cart' : 'Sold Out'}</button>
        </div>
      </div>
    </section>
  );
}
