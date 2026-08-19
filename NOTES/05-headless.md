# Notes 05 — Phase 4: headless (Next.js storefront)

## What exists
- `PageModel` + `ContainerModel` + `ComponentExporter` (core): `/content/blueshelf/us/en.model.json` returns the
  SPA-Editor JSON contract: `:type`, `:path`, `:items`, `:itemsOrder`, page title/description/navigation.
  Registered for `blueshelf/components/page` AND `cq/Page`, so the page URL itself answers (like AEM).
  Child models are resolved through `ModelFactory` with a request wrapper (what `data-sly-resource` does) so
  request-scoped models (ProductList reading `?q=`, ProductDetail reading the suffix) export correctly.
- `storefront/` (Next.js 16, App Router, TS): `src/lib/aem.ts` client, `src/components/aem/*` mapped by `:type`
  (= AEM SPA SDK `MapTo`), routes `/[[...slug]]`, `/product/[sku]`, `/search?q=`, `/api/revalidate`.
  ISR 60s + cache tags per content path; on-demand revalidation endpoint for publish events.

## Headful vs headless vs hybrid — how to talk about it
| | Headful (HTL on publish) | Headless (this storefront) | Hybrid / SPA Editor / Universal Editor |
|---|---|---|---|
| Rendering | AEM renders HTML | Next.js renders from JSON | React app, but authored in-context in AEM |
| Authoring | full WYSIWYG | content in AEM, preview = the storefront (or a preview env) | WYSIWYG on top of the React app |
| Caching | Dispatcher/CDN per URL | ISR / CDN per route + tag invalidation | both |
| When | marketing pages, SEO-first | app-like experiences, multi-channel, existing React team | teams wanting both — Adobe's direction is the **Universal Editor** |
Best-Buy-like reality: product pages are app-like and data-heavy (headless-friendly); campaign/landing content wants in-context authoring.

## Gotchas
- The exporter only serializes public getters; `@JsonIgnore` what the frontend must not see (paths, internal flags). Review the JSON
  like an API contract: it IS your API.
- Request-scoped models need the wrapped request (resource swap). Using `getModelFromResource` on a request-only model returns null silently.
- The storefront reads PUBLISH. Author changes are invisible until activated — that's correct, and a frequent "bug report".
- Links in content are AEM paths (`/content/.../tvs.html`) → map to routes (`toRoute`). In AEM you'd use the Link rewriter / `LinkHandler` + Sling mappings (`/etc/map`) to shorten URLs on publish.
- Search pages are query-dependent: they must be dynamic / `Cache-Control: private` — never let ISR/Dispatcher cache per-user results under the plain URL.

## Exercises
1. Add a `NavigationModel` (children pages, depth, hideInNav) exported on the page and render a proper nav + breadcrumb.
2. Use `next/image` with `remotePatterns` for product images; compare LCP in Lighthouse with plain `<img>`.
3. Implement `generateStaticParams` for `/product/[sku]` from `GET /api/products` (build-time PDPs) — think about what happens when a SKU is discontinued.
4. Add a hidden component to the page and confirm the storefront renders an "Unmapped component" placeholder (never breaks).
