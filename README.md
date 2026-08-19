# BlueShelf — an end-to-end AEM-style platform (learning project)

A Best Buy-style retail site built to learn **Adobe Experience Manager** the way it's engineered in real teams — without an AEM
license: the same open-source engine (Apache Sling · Felix OSGi · Jackrabbit Oak · HTL · Sling Models) in the AEM archetype layout,
plus everything around it: authoring UI, backend integration, dispatcher, headless storefront, CI/CD, Edge Delivery Services.

**Live storefront (static publish):** https://dionysus-666.github.io/blueshelf-aem/ · **EDS site:** https://github.com/dionysus-666/blueshelf-eds

```
author :4502  ──replicate──▶  publish :4503  ◀── dispatcher :8080 ◀── Next.js storefront :3000 / GitHub Pages
   │  (Sites console + page editor, dialogs, templates, styles)        │ (filters, cache, flush)
   └── flush agent ───────────────────────────────────────────────────┘          catalog-api :8081 (Spring Boot)
```

| Folder | What |
|---|---|
| `blueshelf/` | AEM project: `core` (Sling Models, OSGi services, servlets, 22 tests/90 % cov), `ui.apps` (components, HTL, Granite dialogs, clientlibs), `ui.content` (pages, editable templates, policies), `ui.config` (run-mode OSGi configs, repoinit), `ui.frontend` (React/TS editor + site JS), `all` |
| `catalog-api/` | Spring Boot product/catalog microservice (+ chaos endpoint for resilience drills) |
| `dispatcher/` | Node model of Adobe's Dispatcher driven by a real `dispatcher.any` |
| `storefront/` | Next.js headless storefront (SPA JSON contract, ISR, static snapshot mode) |
| `infra/` | docker-compose (local + prod), VM bootstrap; `.devcontainer` for Codespaces |
| `.github/workflows/` | `ci` (Cloud Manager-style gates incl. Adobe's `aemanalyser`), `pages` (static publish), `deploy` (any Docker host) |
| `NOTES/` | per-phase learning notes: concepts ↔ AEM, gotchas hit, exercises, interview drills |
| `PLAN.md`, `DECISIONS.md` | roadmap + ADRs |

## Run locally
```bash
./dev.sh up            # author 4502, publish 4503, dispatcher 8080, catalog 8081 (Docker)
./dev.sh deploy-all    # build + install the `all` package on author and publish (JDK 21, Maven)
./dev.sh storefront    # Next.js on :3000 (AEM_HOST=http://localhost:8080)
```
Author UI: http://localhost:4502/sites.html (admin/admin) · editor: http://localhost:4502/editor.html/content/blueshelf/us/en
