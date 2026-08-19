# Architecture decisions (ADR-style, short)

1. **Apache Sling Starter instead of the AEM SDK** — the SDK is license-gated; Sling + Felix + Oak + HTL + Sling Models is the same engine.
   Trade-off: no Touch UI/DAM/Core Components; we built a thin authoring UI on the real mechanics and register the `cq:` node types ourselves.
2. **Archetype-identical Maven layout** (`core/ui.apps/ui.content/ui.config/ui.frontend/all`) — so the project and your muscle memory port to AEM unchanged; Cloud Manager validators and `aemanalyser` run as-is.
3. **Proxy components + versioned base library** — Core Components pattern: upgrade by changing `v1`→`v2`, override selectively.
4. **Policy-driven Style System over dialog fields** — authors vary looks without dev changes; governed per template.
5. **Content from JCR, data from services** — products never live in the repository; an OSGi service with typed config, TTL cache, stale-if-error and a circuit breaker isolates the site from the catalog's availability.
6. **resourceType-bound servlets over `/bin` paths** — ACLs, cacheability, dispatcher-friendly; `/bin` only for author-side admin actions.
7. **Model Exporter (`.model.json`) as the headless contract** — same shape AEM's SPA SDK uses; Content Fragments/GraphQL is the other option for structured, page-independent content.
8. **Dispatcher semantics modelled from `dispatcher.any`** — learn the real rules; `X-Cache` header added for observability. Flush after activate; never cache query-dependent pages.
9. **Next.js storefront reads publish through the dispatcher** (never author, never JCR) with ISR + tag revalidation; static snapshot build for free hosting (GitHub Pages).
10. **GitHub Actions as Cloud Manager** — same gates (tests + ≥50 % coverage, package validation, aemanalyser, dispatcher validation), images to GHCR, deploy workflow for any Docker host; Codespaces for a free live stack.
11. **Edge Delivery Services for content-velocity pages** — separate repo, no build, blocks; AEM Sites/headless for app-like pages.
