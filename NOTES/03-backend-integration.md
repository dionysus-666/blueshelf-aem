# Notes 03 — Backend integration (Phase 2): catalog-api ⇄ OSGi service ⇄ components

## What exists
| Piece | Where | AEM correlation |
|---|---|---|
| `catalog-api` Spring Boot service (products, categories, stores, chaos endpoint) | `catalog-api/`, Docker `:8081` | Best Buy's commerce/catalog services — AEM never owns product data |
| `CatalogService` (API) + `CatalogServiceImpl` (`@Component` + `@Designate` config, TTL cache, stale-if-error, circuit breaker, metrics) | `core/.../services` | Your integration layer; in AEM usually HttpClient + Caffeine/Guava, same shape |
| `ProductListModel`, `ProductDetailModel` (request-adaptable, `@OSGiService`, `@Exporter`) | `core/.../models` | "content from JCR, data from services" |
| `ProductSearchServlet` — resourceType-bound (`cq/Page` + `search` selector + `json`) | `core/.../servlets` | `/content/site/en.search.json?q=` — the recommended servlet style |
| Components `product-list`, `product-detail`, `search-box` + dialogs | `ui.apps` | |
| `config.local/...CatalogServiceImpl.cfg.json` (`baseUrl=http://catalog-api:8081`) | `ui.config` | per-env config; AEMaaCS: `config.dev/stage/prod` + `$[env:CATALOG_URL]` |
| Pages: `/us/en/tvs` (list), `/us/en/product.html/<SKU>` (PDP via suffix), `/us/en/search?q=` | `ui.content` | one authored page renders thousands of PDPs |
| Tests: service (JDK HttpServer stub + `OsgiContext` config activation), models (`SlingContext` + Mockito service) | `core/src/test` | wcm.io osgi-mock / sling-mock / aem-mock |

URLs: http://localhost:4502/content/blueshelf/us/en/tvs.html · http://localhost:4502/content/blueshelf/us/en/product.html/BS1002 · http://localhost:4502/content/blueshelf/us/en/search.html?q=oled · http://localhost:4502/content/blueshelf/us/en.search.json?q=oled · `.../root/product_list.model.json` · http://localhost:8081/api/products?category=tvs

## Patterns you should be able to explain
1. **Service boundary**: components/models depend on the `CatalogService` *interface*; HTTP lives in the impl.
   Tests register a Mockito mock as the OSGi service (`context.registerService(CatalogService.class, mock)`), so model tests never touch the network.
2. **OSGi config lifecycle**: `@ObjectClassDefinition` → typed `Config` → `@Activate/@Modified(Config)`; config comes from
   `ui.config` JSON per run mode, or is edited live in `/system/console/configMgr` (gotcha: console edits live in the repo under `/apps/.../config`? No — they go to the OSGi installer's store and *shadow* your deployed config until removed. Redeploy doesn't fix it; delete the console config).
3. **Cache-aside with TTL** per publish instance (+ Dispatcher HTML cache on top). Cache key = immutable `ProductQuery`.
4. **Stale-if-error**: expired entries are still served if the backend fails (page shows a subtle notice) — users see prices a minute old instead of an error.
5. **Circuit breaker**: N consecutive failures → fail fast for T seconds. Why it matters in AEM specifically: every page render holds a Jetty request thread; a 30-second backend timeout under load exhausts the pool and **all** pages (even those not using the backend) start failing. Short timeouts + breaker = blast-radius control.
6. **Three render states** in HTL: live/cache, stale, unavailable — always design the unavailable state; a Best Buy homepage must render without the catalog.
7. **resourceType-bound servlet on `cq/Page`** vs path-bound `/bin`: ACLs, cacheability, publishes only with the page.
8. **URL suffix PDP** (`product.html/BS1001`): one page, many products. Dispatcher caches per full URL; SEO: add canonical + title from the product (exercise).

## Gotchas hit this phase (real, reproducible)
- `ui.content` filter `mode="merge"` → modified sample pages were NOT updated on redeploy (that's the point of merge). We use `update` for the learning sandbox; prod projects seed with `merge` and let authors own `/content`.
- A page node's resource type is `cq:Page`; bind page-level selector servlets to `cq/Page`, not the page component.
- Sling POST to `container/*` — don't mix `./prop` and unprefixed names (properties vanish).
- `validRoots` in the FileVault validator REPLACES the default list; include `/`, `/apps`, `/content`, `/conf`…

## Chaos drill (do it, then describe it in interviews as an incident you handled)
```bash
curl -X POST "http://localhost:8081/api/_chaos?failEvery=1"      # backend dies
curl -s http://localhost:4502/content/blueshelf/us/en/tvs.html | grep -o 'source: [A-Z]*'     # CACHE (still fine)
for q in a b c d; do curl -s "http://localhost:4502/content/blueshelf/us/en/search.html?q=zzz$q" | grep -o 'source: [A-Z]*'; done   # UNAVAILABLE, breaker opens after 3
curl -X POST "http://localhost:8081/api/_chaos?failEvery=0&delayMs=3000"   # slow backend: watch timeouts (1500ms) kick in
docker logs blueshelf-author 2>&1 | grep -E "breaker|Catalog call failed" | tail
```
Then open `/system/console/configMgr` → "BlueShelf Catalog Service" and tune `timeoutMs`/`breakerFailures` live.

## Exercises
1. `Stores near you` component: dialog (zip default), `StoreLocatorModel` calling `/api/stores?zip=`, test with a mock. Add `List<Store> storesNear(zip)` to `CatalogService`.
2. Add a **category select datasource**: instead of static options in the dialog, a servlet `/bin/blueshelf/categories.json` (or better: a resourceType-bound one) that the React dialog reads (`datasource` in Granite). Compare with Granite's `sling:resourceType=…/datasource` pattern.
3. PDP SEO: set `<title>` and `<link rel=canonical>` from the product (page.html needs the model; pass data via `request attribute` or a page-level model).
4. Replace the TTL map with Caffeine embedded in the bundle (bnd `-includeresource`) and compare bundle size / Import-Package.
5. Write a **Sling scheduler** (`@Component` implementing `Runnable` with `scheduler.expression`) that pre-warms the TVs/laptops queries every minute on publish only (`@Reference SlingSettingsService` / run-mode check).
