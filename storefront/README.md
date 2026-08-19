# BlueShelf storefront (headless, Next.js)

Consumes the published page JSON (`<page>.model.json`, Sling Model Exporter — same contract as AEM's SPA Editor)
and maps `:type` → React components (`src/components/aem/mapping.tsx`, cf. AEM SPA SDK `MapTo`).

- `AEM_HOST` — publish (or dispatcher) base URL. Local: `http://localhost:4503` (Phase 5: `http://localhost:8080`).
- Routes: `/` and `/[...slug]` → `/content/blueshelf/us/en/<slug>.model.json`; `/product/[sku]` → `product.model.json/<sku>` (suffix);
  `/search?q=` forwards the query so the search-mode ProductList resolves server-side.
- ISR: 60s + tags per content path; `POST /api/revalidate?secret&path=` for on-demand invalidation after publish.

```bash
npm install && npm run dev        # http://localhost:3000
```
