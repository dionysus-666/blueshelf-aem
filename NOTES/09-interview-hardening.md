# Notes 09 — Phase 8: interview hardening (Full-Stack AEM Developer, Best Buy-style)

## 1. The 2-minute project pitch (memorise, then personalise)
> "I built an end-to-end AEM-style platform to learn the stack the way Best Buy runs it. On the content side it's the same engine
> AEM is built on — Apache Sling, Felix OSGi, Jackrabbit Oak, HTL and Sling Models — in the archetype layout (`core`, `ui.apps`,
> `ui.content`, `ui.config`, `ui.frontend`, `all`) with FileVault packages, run-mode configs and repoinit. I wrote the components
> (hero, teaser, title as proxy components with `sling:resourceSuperType`, product list/detail/search) with Granite dialogs, editable
> templates and policies including the Style System, plus a React/TS authoring UI to exercise the real Sling POST/replication mechanics.
> The backend integrates a Spring Boot catalog API through an OSGi service with typed config, a TTL cache with stale-if-error and a
> circuit breaker, unit-tested with Sling/OSGi mocks at 90 % coverage. Content flows author → publish → a dispatcher I modelled from
> `dispatcher.any` (filters, cache rules, statfiles, flush agent) → a Next.js headless storefront consuming the SPA-editor JSON contract
> with ISR and on-demand revalidation. CI on GitHub Actions mirrors Cloud Manager: package validation, Adobe's aemanalyser against the
> AEMaaCS SDK, coverage gate, images to GHCR, deploy workflow, and a static publish to GitHub Pages. I also built an Edge Delivery
> Services site with a custom block. I'd be glad to walk through any layer."

Links: code https://github.com/dionysus-666/blueshelf-aem · live storefront https://dionysus-666.github.io/blueshelf-aem/ · EDS https://github.com/dionysus-666/blueshelf-eds

## 2. Core questions with answers grounded in THIS project
**Sling resolution** — URL `path.selectors.ext/suffix` → resource → `sling:resourceType` (or node type, e.g. `cq:Page` → `/apps/cq/Page/Page.html`)
→ script/servlet by selectors+ext+method; `/apps` overrides `/libs`. Evidence: `PageModel` registered for `cq/Page`; `ProductSearchServlet` on `cq/Page` + `search` selector; PDP via suffix.

**OSGi** — bundle lifecycle (Installed vs Active: unresolved `Import-Package`), DS `@Component/@Reference/@Activate/@Modified`, metatype
`@Designate/@ObjectClassDefinition`, configs via `ui.config` per run mode, `$[env:…]` interpolation; `@Reference` policies (static/dynamic,
cardinality). Evidence: `CatalogServiceImpl`, `ReplicationServiceImpl`; the bundle export gotcha (`package-info.java` + `-exportcontents`).

**Sling Models** — adaptables (Resource vs Request), injectors (`@ValueMapValue`, `@OSGiService`, `@Self`, `@SlingObject`, `@RequestAttribute`,
`@ChildResource`), `DefaultInjectionStrategy.OPTIONAL`, `@PostConstruct`, `@Exporter(jackson)` → `.model.json`, `ModelFactory` with wrapped
requests, models resolved through `resourceSuperType`. Evidence: every model; `ComponentExporter`; `StyleModel` failure story.

**HTL** — context-aware escaping (`@ context='html'|'uri'|'attribute'`), `data-sly-use/resource/include/list/repeat/test/element/template+call`,
no logic in templates (the `startsWith` story), `properties`, `resource`, `request` bindings, `wcmmode`. Evidence: all `.html` scripts.

**Components / authoring** — `cq:dialog` Granite XML, `cq:editConfig`, proxy components, editable templates (`structure/initial/policies`),
policies → allowed components + Style System, clientlibs (`categories/dependencies/embed/allowProxy`, `css.txt`), Core Components usage.
Evidence: `ui.apps`, `ui.content/conf`, the editor.

**Content packages** — FileVault, `filter.xml` modes (replace/merge/update), package types (application/content/container), `all`, embedded
bundles in `/apps/*/install`, validation errors we hit (ancestor coverage, overlapping filters, content outside /apps, configs only in container).

**Replication / publish / dispatcher** — author→publish agents, flush agents (`CQ-Action/CQ-Handle`), `dispatcher.any` filters + cache rules,
`/statfileslevel`, `/ignoreUrlParams`, `/allowAuthorized`, TTL, stale-on-error, CDN in front (AEMaaCS: Fastly). Evidence: `dispatcher/`, flush test transcript.

**Headless** — Model Exporter vs Content Fragments + GraphQL (persisted queries), SPA Editor/`MapTo`, Universal Editor, ISR/revalidation,
`:type/:items/:itemsOrder`. Evidence: `storefront/`.

**Performance/resilience** — short timeouts, caches at every layer (service cache → dispatcher → CDN → ISR), circuit breaker, render states;
Oak: avoid traversals, property/Lucene indexes (`/oak:index`), QueryBuilder vs JCR-SQL2; thread-pool exhaustion story.

**Cloud Manager / AEMaaCS** — pipeline stages, quality gates (coverage ≥50 %, aemanalyser, Sonar), immutable `/apps` vs mutable `/content`,
repoinit, run modes `config.dev/stage/prod`, env vars/secrets, Dispatcher SDK validation, log forwarding, no direct instance access.

**Testing** — JUnit 5 + `SlingContext`/`AemContext` (wcm.io), `registerService(mock)`, `registerInjectActivateService(service, config)`,
JSON fixtures, servlet tests with mock request/response, HttpServer stubs, JaCoCo. Evidence: 22 tests.

## 3. Incident runbooks (say these as stories; each is reproducible here)
| Symptom | Likely cause | How I proved/fixed it |
|---|---|---|
| Page 500: `HeroModel cannot be resolved to a type` | model package not exported | `package-info.java` `@Version` + bnd `-exportcontents` → MANIFEST `Export-Package` |
| `data-sly-use` returns null, no error | missing `Sling-Model-Packages` header, or required injection failed | check `/system/console/status-slingmodels`, logs at DEBUG, make props OPTIONAL |
| `MissingElementsException … styleIds` | absent property on a model without OPTIONAL | `DefaultInjectionStrategy.OPTIONAL` |
| Styles work for admin, not for visitors | anonymous can't read `/conf` | repoinit ACL; **test anonymously** |
| New component doesn't appear on an existing page after deploy | `ui.content` filter `merge` | intended; update via authoring (or `update` mode in sandboxes) |
| Bundle "Installed" not "Active" | unresolved `Import-Package` version range | `/system/console/bundles` → find the missing package/version; align versions |
| Config change has no effect | console-edited config shadows deployed one / old bundle | check Status → Configurations; delete console config; verify bundle version |
| Dispatcher serves old page after publish | flush before activate, wrong `/invalidate` glob, wrong `statfileslevel`, flush client not allowed | `X-Cache`, `.stat` mtimes, dispatcher log `INVALIDATE`; order content-then-flush |
| Search results cached for everyone | cache rule too broad / query param not in ignore list | `/cache /rules deny` for search paths; `/ignoreUrlParams` deny default |
| Whole site slow, even static pages | slow downstream API exhausting request threads | short timeouts + circuit breaker + stale-if-error; thread dumps |
| Mojibake “â€”” on save | Sling POST without `_charset_` | always send `_charset_=utf-8` |
| Cloud Manager build fails: filter/package violations | see FileVault validator errors in NOTES/02, 03 | fix filters / package types |
| `/bin/*` servlet 404 on publish | dispatcher filter denies it (by design) | use resourceType-bound servlet under `/content/...` |

## 4. Gaps to study (not built here — be honest and show you know them)
Workflows & Workflow Launchers · MSM/Live Copy/Blueprints · Launches · Assets/DAM (renditions, Asset Compute, Dynamic Media) ·
Content Fragments + CF Models + GraphQL persisted queries · Universal Editor instrumentation · Translation/i18n (`/libs/wcm/core/i18n`, dictionaries) ·
Oak indexing in depth (Lucene index definitions, `oak:index` deployment rules in AEMaaCS) · Sling Context-Aware Configuration ·
Permissions (groups, CUGs, closed user groups) · Adobe Target/Analytics/Launch integration (ContextHub, data layer `adobeDataLayer`) ·
Commerce integration (CIF connector) · Service users via repoinit + `ServiceUserMapped` · Sling Jobs/Schedulers · Cloud Manager IP allow lists, CDN rules, WAF.
For each: read the Adobe doc page + write one paragraph in your own words + a 1-line "how it maps to what I built".

## 5. 2-week drill schedule
- Days 1–3: redo Phase 1–3 exercises from memory (component + model + dialog + test + policy) — aim < 30 min each.
- Days 4–5: break/fix drills from section 3 (pick 5, cause them, fix them, narrate).
- Days 6–7: dispatcher: rewrite `dispatcher.any` for a multi-country site; explain each rule out loud.
- Days 8–9: headless: add a component end-to-end (model → exporter → React) + GraphQL reading (CF models, persisted queries).
- Days 10–11: Cloud Manager/AEMaaCS reading (architecture, pipelines, Dispatcher SDK, env vars, repoinit) + Q&A self-test.
- Days 12–13: gaps list (section 4) — one paragraph each.
- Day 14: mock interview: pitch + 15 random questions from section 2 + 2 incident stories.
