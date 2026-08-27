/**
 * Product shape + derived price fields, shared by every product renderer.
 *
 * Two data sources feed the storefront:
 *  - AEM pages (.model.json): the Sling `Product` model (core/.../catalog/Product.java) already exports
 *    currentPrice / onSale / savingsPercent.
 *  - The static search page: products.json crawled straight from catalog-api, which has only price/salePrice.
 * The derived fields are therefore optional, and the helpers below compute them with the SAME formula as
 * Product.java when absent — so a missing field degrades gracefully instead of crashing the page.
 */
export interface Product {
  sku: string; name: string; brand: string; price: number; salePrice?: number | null;
  currentPrice?: number; onSale?: boolean; savingsPercent?: number;
  rating: number; reviewCount: number; inStock: boolean; image: string; shortDescription?: string; highlights?: string[];
}

export const isOnSale = (x: Product): boolean => x.onSale ?? (x.salePrice != null && x.salePrice < x.price);
export const priceOf = (x: Product): number => x.currentPrice ?? (isOnSale(x) ? (x.salePrice as number) : x.price);
export const savingsOf = (x: Product): number => x.savingsPercent ?? (isOnSale(x) ? Math.round((1 - (x.salePrice as number) / x.price) * 100) : 0);

/** Fill in the derived fields once, at the boundary where raw catalog-api data enters. */
export const normalize = (p: Product): Product => ({ ...p, onSale: isOnSale(p), currentPrice: priceOf(p), savingsPercent: savingsOf(p) });
