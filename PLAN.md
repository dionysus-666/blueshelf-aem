# AEM Full-Stack Bootcamp — "BlueShelf" (a Best Buy–style retail site)

Goal: in ~4–6 weeks, build, test, CI/CD and deploy an end-to-end AEM-style project that
mirrors what a Best Buy Full-Stack AEM Engineer does day to day, and be able to *talk about it*
in an interview with real war stories.

You: 7 yrs SWE. So this plan is written as **"AEM concept → what you already know → gotcha"**.

---

## 0. Reality check from research (read this first)

| Finding | Consequence for us |
|---|---|
| AEM SDK (`aem-sdk-quickstart-*.jar`) is only downloadable from Adobe Software Distribution **with a licensed Adobe ID**. No free trial. | We build on **Apache Sling Starter** (Docker `apache/sling:14`) — the literal open-source core of AEM (Sling + Felix OSGi + Oak JCR + HTL + Sling Models). Java/HTL/OSGi code is 1:1 portable. If you later get SDK access (friend/partner), the same Maven project deploys to it. |
| Adobe's current stated direction: **AEM as a Cloud Service** (AEMaaCS) + **Edge Delivery Services** (EDS) for new sites; classic "headful" Sites still dominates enterprise codebases; **headless** (Content Fragments + GraphQL → React/Next) is now first-class. | We do all three layers: (1) classic component dev, (2) headless JSON/GraphQL-style API consumed by a React/Next.js storefront, (3) a small EDS site (free: GitHub + aem-boilerplate). |
| Typical senior JD: Java, OSGi, Sling Models, HTL, Servlets, Core Components, Dispatcher, Maven, Cloud Manager CI/CD, React/TS, REST/GraphQL integrations, unit tests (JUnit5 + AEM Mocks), performance/caching. | Each maps to a phase below. |
| Best Buy specifics: large retail site, heavy personalization/A-B (Adobe Target), analytics (Adobe Analytics / AEP), product data from separate commerce/catalog services, hybrid Richfield MN. Public Best Buy job posts were not indexable, but the pattern above matches every Fortune-500 retail AEM team. | Our domain = product catalog + promos + store locator + content pages, with a separate "Catalog API" microservice (what Best Buy calls its commerce/product services). |

---

## 1. Mental model (AEM → what you know)

| AEM thing | Think of it as | Gotcha |
|---|---|---|
| **JCR / Oak** (`/content`, `/apps`, `/conf`, `/libs`) | A hierarchical NoSQL DB *that is also your filesystem/router*. Everything is a node with properties. | Paths are identity. URL `/content/blueshelf/us/en/tvs.html` **is** the node path + selector + extension. |
| **Sling** | A REST framework where the URL resolves to a **resource (node)**, and the node's `sling:resourceType` picks the **script** (HTL) or **servlet**. No controllers/routes table. | Biggest mindset shift: *resource-first resolution*. Learn the URL decomposition: `path . selectors . extension / suffix`. |
| **OSGi / Felix** | A modular DI container with hot-deploy. `@Component` = Spring `@Service`; `@Reference` = `@Autowired`; `@Designate` config = `@ConfigurationProperties`. | Bundles can be *installed but not active* (unresolved imports). You WILL hit `Unresolved requirement: Import-Package`. Learn to read `/system/console/bundles`. |
| **HTL (Sightly)** | A deliberately dumb, XSS-safe template language. `${model.title}` is auto-context-escaped. | No logic in templates — push logic to Sling Models. `data-sly-use` binds a model. `@ context='unsafe'` is a code smell. |
| **Sling Models** | POJOs with `@Model(adaptables=...)` that adapt a Resource/Request into a view-model. `@ValueMapValue` = read a JCR prop. `@OSGiService` = inject service. `@ChildResource`, `@Self`, `@PostConstruct`. | Adaptables matter: `Resource` vs `SlingHttpServletRequest`. Use `@Via`, `DefaultInjectionStrategy.OPTIONAL` or you get null models with no error. |
| **Components / dialogs** | React components + a JSON-schema form (Granite/Coral UI `cq:dialog`) that authors fill in; values saved as node props. | Dialog XML is verbose; copy from Core Components. `sling:resourceSuperType` = inheritance (proxy components). |
| **Templates / Policies** (editable templates) | Page layouts + which components are allowed where + per-component config. | `cq:template`, `cq:policy`, `structure` vs `initial` content. |
| **Core Components (WCM)** | Adobe's open-source React-like component library (Teaser, Carousel, Image, Text, List…). You *proxy* them, not copy them. | Version them, don't fork. `sling:resourceSuperType=core/wcm/components/text/v2/text`. |
| **Author / Publish / Dispatcher** | CMS UI (author) → activated/replicated content → stateless publish farm → Apache httpd + dispatcher module = **CDN/edge cache + WAF**. | Dispatcher `filter` rules deny-by-default; `cache` rules & `statfileslevel`; `/invalidate` flushes; `?query` params bust cache unless ignored. #1 prod incident source. |
| **Content packages (FileVault)** | `.zip` of JCR XML (`.content.xml`) — your "migration scripts" for content/config. `filter.xml` decides what paths the package owns. | **Mutable vs immutable** content: `/apps` and `/libs` are immutable in Cloud Service (deployed only via pipeline); `/content` `/conf` are mutable. Wrong filter = you delete prod content on deploy. |
| **Maven archetype modules** | `core` (Java OSGi bundle) · `ui.apps` (components/dialogs/HTL) · `ui.content` (sample content) · `ui.config` (OSGi configs) · `ui.frontend` (webpack → clientlibs) · `dispatcher` · `it.tests` · `all` (fat package). | `all` is the deployable; `ui.frontend` builds client libraries (`cq:ClientLibraryFolder` with `categories`). |
| **Clientlibs** | Bundled CSS/JS with categories & dependencies; AEM serves/minifies/caches them. | `allowProxy=true` + `/etc.clientlibs` path or dispatcher blocks them. |
| **Cloud Manager** | Adobe-hosted CI/CD: git push → build → code quality gates (Sonar, `aemanalyser`) → deploy dev/stage/prod. | Quality gate fails on `Import-Package` to non-exported APIs, banned Oak indexes, etc. We emulate with GitHub Actions + `aemanalyser-maven-plugin`. |
| **Content Fragments + GraphQL / Sling Model Exporter (.model.json)** | Structured content → headless JSON. `.model.json` = Jackson export of Sling Models. | `@Exporter(name="jackson")` + `@JsonProperty`/`@JsonIgnore`. Persisted queries only in prod (dispatcher blocks POST GraphQL). |
| **Edge Delivery Services** | Git repo of vanilla JS/CSS "blocks" + docs (Google Drive / SharePoint / da.live) as content source; Adobe CDN renders. Lighthouse 100 by design. | No bundler; "blocks" decorate DOM; `fstab.yaml` points at content source. Different paradigm from Sling. |

---

## 2. Project: **BlueShelf**

A consumer-electronics storefront:
- **Content pages** (home, category landing, deals, store locator, articles) — authored in the CMS.
- **Product Detail / Listing** components that call a **Catalog API** (separate Spring Boot service, mimicking Best Buy's commerce services) with caching + circuit breaker.
- **Promo / Hero / Teaser / Carousel** via proxied Core Components + custom components.
- **Headless layer**: `.model.json` / JSON API consumed by a **Next.js storefront** (React + TS) deployed to Vercel.
- **Dispatcher-like edge cache** (Apache httpd/nginx in Docker) with cache/flush rules.
- **CI/CD**: GitHub Actions = Cloud Manager stand-in: build, unit tests (JUnit5 + `io.wcm` AEM Mocks), `aemanalyser`, content-package validation, deploy to a cloud VM/Fly.io, then Next.js → Vercel.
- **EDS mini-site** for the "deals" blog using `adobe/aem-boilerplate` (free).

### Repo layout (archetype-identical)
```
aem/
├── PLAN.md
├── blueshelf/                      # AEM project (Maven, archetype layout)
│   ├── pom.xml                     # parent
│   ├── core/                       # OSGi bundle: Sling Models, Services, Servlets, Schedulers
│   ├── ui.apps/                    # /apps/blueshelf: components, HTL, dialogs, templates
│   ├── ui.content/                 # /content/blueshelf sample pages + /conf
│   ├── ui.config/                  # OSGi configs per runmode
│   ├── ui.frontend/                # TS/SCSS → clientlibs
│   ├── dispatcher/                 # httpd + dispatcher-style rules (Docker)
│   ├── it.tests/                   # integration tests against running instance
│   └── all/                        # container package
├── catalog-api/                    # Spring Boot product/catalog microservice
├── storefront/                     # Next.js headless consumer (Vercel)
├── eds-deals/                      # Edge Delivery Services site (aem-boilerplate)
└── infra/                          # docker-compose: sling author/publish, dispatcher, catalog-api
```

---

## 3. Step-by-step phases

### Phase 0 — Environment (Day 1)  ✅ started
- Java 21 (Sling 14 / AEMaaCS SDK both OK; **Java 23 is NOT supported by AEM SDK — gotcha**), Maven 3.9, Docker, Node 20.
- `docker compose up` → Sling "author" on :4502 and "publish" on :4503 (we map ports to match AEM muscle memory), admin/admin.
- Learn to navigate: `/system/console` (Felix web console: bundles, components, configs, logs), `/bin/browser.html` (JCR browser — CRXDE Lite equivalent).

### Phase 1 — Sling fundamentals via the Maven project (Days 2–4)
1. Generate archetype-layout project (hand-rolled so it works on Sling; `core`, `ui.apps`, `ui.content`, `all`).
2. First component: `hero` — HTL + Sling Model + dialog-ish props. Deploy with `mvn clean install -PautoInstallPackage`.
3. Exercises: URL decomposition (`/content/blueshelf/en.hero.json`), resource resolution, `.json` default renderers, selectors.
4. Unit-test the model with **wcm.io AEM Mocks** (`@ExtendWith(AemContextExtension.class)`) — what interviewers ask.

### Phase 2 — Backend integration (Days 5–9)
1. `catalog-api` (Spring Boot): `/products?category=tvs`, `/products/{sku}`, `/stores?zip=`. Seed JSON. Dockerized.
2. OSGi service `CatalogService` + `@Designate` OSGi config (base URL, timeout) → `ProductListModel`, `ProductDetailModel`.
3. Servlet `/bin/blueshelf/search` (resourceType-bound, not path-bound — gotcha: path-bound servlets are discouraged & dispatcher-blocked).
4. Resilience: Caffeine cache, timeouts, fallback content; Sling Jobs / Scheduler to pre-warm.
5. Tests: Mockito + AemContext + WireMock for the HTTP service.

### Phase 3 — Authoring experience (Days 10–13)
1. Page component + editable template + policies; proxy Core Components (text/image/teaser/carousel).
2. Dialogs (`cq:dialog` Granite XML), `cq:editConfig`, design/style system.
3. `ui.frontend` with webpack/TS/SCSS → clientlib (`categories`, `dependencies`, `allowProxy`).
4. Sling Model Exporter: `@Exporter(name="jackson")` → `.model.json` on every page.
> Done early (see NOTES/02): we built our own Sites console + page editor on top of the real mechanics (cq:dialog, Sling POST, templates/policies, replication). Remaining in this phase: proxying Core Components patterns, clientlib categories, style system.

### Phase 4 — Headless + React (Days 14–18)
1. Next.js `storefront`: App Router, fetch `.model.json` from publish through the "dispatcher". Render `ResponsiveGrid`-like component mapping (same idea as AEM SPA Editor's `MapTo`).
2. Product pages: SSR/ISR from catalog-api; content from Sling.
3. Deploy storefront to Vercel; env vars for AEM host.

### Phase 5 — Publish, Dispatcher, Caching (Days 19–22)
1. Author→publish replication (we emulate with content-distribution / package push).
2. httpd/nginx "dispatcher" in Docker: deny-by-default filters, cache `.html/.json`, ignore query params, flush endpoint, TTL.
3. Load test with k6; prove cache hit ratio; demonstrate "stale content after publish" incident + fix (statfile invalidation).

### Phase 6 — CI/CD = Cloud Manager stand-in (Days 23–26)
1. GitHub Actions: build, unit tests + Jacoco ≥ 50%, `aemanalyser-maven-plugin` (real Cloud Manager analyzer, free), FileVault validation, Docker images.
2. Deploy Sling publish + dispatcher + catalog-api to a cheap VM (Fly.io/Hetzner/EC2) with docker compose; Vercel for storefront.
3. Runmodes & env-specific OSGi config (`config.author`, `config.publish`, `config.prod`) — env secrets via `$[env:...]` (Cloud Service pattern).

### Phase 7 — Edge Delivery Services (Days 27–29)
1. Create repo from `adobe/aem-boilerplate`, install AEM Code Sync app, content in Google Drive / da.live.
2. Build a `deals-cards` block; understand `decorate()`, section metadata, Lighthouse.
3. Talk track: when EDS vs. classic Sites vs. headless.

### Phase 8 — Interview hardening (Days 30+)
- Write `DECISIONS.md` (why proxy core components, why exporter vs GraphQL, dispatcher rules).
- Drill: OSGi lifecycle, Sling resolution, `@Reference` policies, Oak indexes/queries (`/oak:index`, QueryBuilder vs JCR-SQL2), workflows, MSM/Live Copy, replication agents, permissions (ACLs/CUGs), Cloud Manager pipelines, Dispatcher flush, CDN, performance.
- Mock "on-call" incidents: bundle not active, cache not invalidating, slow query (traversal warning), dialog not saving.

---

## 4. Daily rhythm
Each phase: (1) I explain the concept + correlation + gotcha, (2) we implement, (3) you do an exercise, (4) we write a short note in `NOTES/` for interview recall.

Progress is tracked at the bottom of this file.

## Progress
- [x] Phase 0: toolchain + plan
- [x] Phase 1: hero component + model + tests rendering on author (NOTES/01)
- [x] Phase 1b (pulled forward from Phase 3): authoring layer — Sites console, page editor, Granite dialogs, editable template + policy, ui.frontend (React/TS), ui.config (run modes, repoinit), replication to publish (NOTES/02)
- [x] Phase 2: catalog-api (Spring Boot, Docker) + CatalogService (OSGi config, TTL cache, stale-if-error, circuit breaker) + Product List/Detail/Search components, resourceType servlet, tests (NOTES/03)
- [x] Phase 3: proxy/inherited components, Style System, clientlibs (cq:ClientLibraryFolder + servlet), ui.frontend two-entry build (NOTES/04)
- [x] Phase 4: page/container exporters (SPA JSON contract) + Next.js storefront with ISR & revalidation (NOTES/05). Vercel deploy happens in Phase 6 once publish is reachable from the internet.
- [x] Phase 5: mini-dispatcher (real dispatcher.any semantics: filters, cache rules, statfiles, ignoreUrlParams, TTL, stale-on-error, flush protocol) + flush agent + storefront revalidation (NOTES/06)
- [x] Phase 6: GitHub Actions CI green (tests 90% cov, aemanalyser gate, dispatcher validator, GHCR images) + deploy workflow ready (NOTES/07). Repo: https://github.com/dionysus-666/blueshelf-aem. Pending from you: a VM (secrets) + Vercel import.
- [ ] Phase 7: Edge Delivery Services
- [ ] Phase 8: interview hardening
