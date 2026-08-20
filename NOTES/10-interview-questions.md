# AEM Full-Stack Interview — Question Bank & Answers
*Prepared for the Best Buy AEM Developer interview · grounded in the BlueShelf project (github.com/dionysus-666/blueshelf-aem) and a real AEMaaCS trial. Answers are written the way you'd SAY them — 15–40 seconds each.*

---

## Plain-words glossary (read first — every answer below uses these)

| Term | In plain words |
|---|---|
| **JCR / repository** | The content database. Everything — pages, text, images, settings — is a tree of nodes with properties, like folders and files. |
| **Sling** | The web framework: a URL points at a node in that tree, and the node says which template renders it. No route table. |
| **OSGi / bundle** | The plugin system for Java code. Your code ships as a "bundle" that can be installed/updated without restarting the server. |
| **OSGi service** | A Java object registered under an interface so other code can ask for it — like Spring beans, but swappable at runtime. |
| **Sling Model** | A small Java class that reads a node's properties (and services) and hands the template clean, ready-to-render values. |
| **HTL** | The template language (like JSX/Thymeleaf) — deliberately dumb: it can display values and loop, but real logic must live in Java. |
| **Component** | One building block on a page (hero banner, product list). = a template + a Java model + a form for authors. |
| **Dialog** | The form an author fills to configure a component. Saving it writes properties onto the page's node. |
| **Template & policy** | Template = the page blueprint (locked parts + starting content). Policy = per-template rules: which components are allowed, which styles exist. |
| **Author / publish** | Two separate servers. Authors work on *author*; clicking Publish copies the content to *publish*, which visitors see. |
| **Replication** | That copying step, author → publish. Content is *replicated*; code must be *deployed* to both. |
| **Dispatcher** | The gatekeeper in front of publish: blocks URLs that shouldn't be public, and caches rendered pages as files so publish isn't hit for every visitor. |
| **Flush** | Telling the dispatcher "this page changed, throw away your cached copy." Happens automatically on publish. |
| **Clientlib** | AEM's way to bundle CSS/JS: named libraries with dependencies, served combined and minified. |
| **Content package** | A zip of part of the content tree — how code and content are shipped between environments. |
| **Content Fragment** | Structured content (like a database record with a schema) with no layout — meant to be consumed as data by any channel. |
| **GraphQL / persisted query** | The API for reading Content Fragments. In production you only expose *saved* queries via GET so they're cacheable and safe. |
| **Headless** | AEM stores/serves the content as JSON; a separate app (e.g. React) renders it. |
| **Universal Editor** | Adobe's current visual editor: click-and-edit directly on the real rendered page, whatever framework renders it. |
| **Edge Delivery Services (EDS)** | Adobe's newest way to build sites: content written in documents, tiny JS/CSS "blocks" in GitHub, extremely fast pages. |
| **AEMaaCS / Cloud Manager** | AEM as Adobe's cloud service, and its mandatory CI/CD pipeline with quality gates. |
| **Run mode** | A label (author/publish, dev/prod) that selects which configuration files apply on that instance. |

---

## 0. The pitch (60–75 seconds)

> I'm moving into AEM, so I built a complete retail site the way an AEM team would build it — BlueShelf, modeled on an electronics store. The content side runs on Apache Sling, the open-source engine AEM is built on. I wrote the components in Java and HTL — Sling Models, author dialogs, editable templates and policies — with the full author-to-publish flow and a dispatcher cache in front. Product data comes from a separate Spring Boot service; my AEM code calls it through an OSGi service with caching, timeouts and a circuit breaker, so pages still render if the product API dies. On top there's a React storefront reading published content as JSON — AEM's headless pattern — and CI on GitHub Actions with the same gates Cloud Manager uses, including Adobe's analyser. I also worked in a real AEM Cloud instance: Universal Editor, Content Fragments, GraphQL. What taught me most were the incidents — a dispatcher cache-poisoning bug, code on author but not publish — each fixed with a prevention step.

**Beats to memorize:** why → engine + components → resilient integration → headless + CI → real AEM cloud → incidents.

**The 3 follow-ups this pitch invites:**

**Q: "Pages still render when the product API goes down — how exactly?"**
Layered degradation. The OSGi service has short timeouts (1.5 s), a TTL cache, stale-if-error (expired cache entries are still served if the backend fails, with a notice), and a circuit breaker that fails fast after 3 consecutive failures so a dead backend can't hold request threads. The model exposes three render states — live, stale, unavailable — and the HTL renders a friendly message for the last one. The key risk isn't the failing component; it's thread-pool exhaustion taking down *every* page — short timeouts and the breaker contain the blast radius.

**Q: "What's a Sling Model? Why not just a servlet?"**
A Sling Model is a POJO that adapts a resource or request into a view-model: `@Model(adaptables=…)`, injectors like `@ValueMapValue` for JCR properties and `@OSGiService` for services, `@PostConstruct` for derived values. HTL binds it with `data-sly-use`. A servlet answers a whole request; a model backs *one component* on a page that contains many, and the same model exports as JSON via `@Exporter` — one class serves both the HTML site and headless clients.

**Q: "Tell me about the cache-poisoning bug."**
Users got other users' search results. Diagnosis: the page returned `X-Cache: HIT` for a query-dependent URL. Two config changes caused it, each harmless alone: a trailing "allow *.html" rule in the dispatcher cache rules overrode the earlier search-page deny (rules are last-match-wins), and an "ignore all query params" line meant `?q=laptop` was treated as identical to the bare URL — the cache key is the URL path only, so the first searcher's page was cached and served to everyone. Fix: revert both, flush the poisoned entry (the cache volume outlives the container). Prevention: I extended our config validator to simulate last-match-wins and fail CI if a query-dependent page's *final* verdict is cacheable.

---

## 1. Sling fundamentals

**Q1. How does Sling turn a URL into a response?**
Resource-first resolution, two steps. First the URL path resolves to a *resource* — a JCR node; the URL decomposes as `path.selectors.extension/suffix`. Second, the resource's `sling:resourceType` selects the *script or servlet*: search `/apps` then `/libs`, pick the script matching selectors + extension + method. No route table — content drives rendering. Example from my project: `/content/blueshelf/us/en/product.html/BS1002` — the page node renders `page.html`, and the suffix carries the SKU so one authored page renders every product.

**Q2. What are selectors, and when do you use them?**
Optional URL tokens between path and extension. Scripts/servlets can claim them: I bound a servlet to `resourceType cq/Page + selector "search" + extension json`, giving `/en.search.json?q=` — a cacheable, ACL-respecting JSON endpoint. Unclaimed selectors fall through to the default script. Numeric selectors on `.json` are the depth of the default JSON renderer.

**Q3. `/apps` vs `/libs`?**
Search-path order: `/apps` (project) overrides `/libs` (product). Overlaying product scripts in `/apps` is possible but discouraged — extend with `sling:resourceSuperType` instead. In AEMaaCS both are immutable at runtime, deployed only by the pipeline.

**Q4. Path-bound vs resourceType-bound servlets?**
ResourceType-bound is the recommended style: the request goes through resource resolution, so ACLs apply, dispatcher caching works per page path, and the endpoint only exists where the content exists. Path-bound (`/bin/...`) bypasses ACLs (you must check permissions yourself) and needs an explicit dispatcher filter hole. I used path-bound only for an author-side admin action (replication trigger).

**Q5. What is the Sling POST servlet?**
The default write API: form-POST to a resource path with `./prop=value` sets properties; `:operation=delete|copy|move`, `:nameHint`, `:order` manage nodes. AEM dialogs are forms that submit exactly this. Two gotchas I hit: always send `_charset_=utf-8` (else mojibake), and it's why dispatchers deny POST on publish — the repo is writable over HTTP for authenticated users.

**Q6. What is the suffix and why use it?**
The path part after the extension: `product.html/BS1001`. It's how one page renders thousands of PDPs without a node per product. Each suffix URL caches separately on the dispatcher; invalidation works via the page's statfile.

**Q7. What is a ResourceResolver / adaptTo?**
The gateway from a request/session to resources, with the user's permissions. `adaptTo()` is Sling's conversion idiom — Resource → Node, Resource → model class, request → model. Rule I follow: never use an admin resolver; use the request's resolver or a service user.

**Q8. Default renderers?**
Without a matching script Sling falls back: `.json` depth renderers (`.2.json`, `.infinity.json`), HTML dump via HtmlRenderer. Useful for debugging, dangerous in prod — my dispatcher denies numeric-selector JSON and `.infinity.json`; I've *seen* HtmlRenderer dump a node publicly when scripts were missing on publish.

## 2. OSGi

**Q9. What is OSGi and why does AEM use it?**
A module system + service registry with dynamic lifecycle. Each bundle declares imported/exported packages with versions; services are registered and injected at runtime and can come and go without restart. AEM is built on Felix — hot deployment of code and config is the practical payoff.

**Q10. Bundle states — what does "Installed" mean and how do you debug it?**
Installed = the bundle's dependencies can't be resolved — usually an `Import-Package` no runtime bundle exports in a compatible version. Everything in it silently stops existing. Debug at `/system/console/bundles`: open the bundle, read the unresolved requirement, then align versions. I keep the MANIFEST rule in mind: every Java import becomes a versioned Import-Package requirement generated by bnd.

**Q11. @Component, @Reference, @Activate — explain.**
Declarative Services: `@Component` registers a class as a service (like Spring `@Service`), `@Reference` injects other services (like `@Autowired`), `@Activate`/`@Modified` receive typed config and re-run on config change without restart. Reference policy static vs dynamic and cardinality control what happens when a dependency disappears.

**Q12. How does OSGi configuration work end-to-end?**
`@ObjectClassDefinition` defines a typed config; `@Designate` binds it; values come from ConfigAdmin. In projects, configs are content: JSON files in `ui.config` under run-mode folders (`config.author`, `config.publish`, `config.prod`, combinable like `config.author.prod`), picked up by the JCR installer. Secrets by interpolation: `$[env:NAME]` / `$[secret:NAME]`.
**Gotcha I demonstrated:** a config edited in the web console *shadows* the deployed one, and redeploying does NOT reclaim it — the installer won't overwrite a config it didn't create. Fix: delete the console config. AEMaaCS removes the console entirely, which is why.

**Q13. Why must a Sling Model's package be exported?**
HTL compiles templates into Java classes living in a different bundle; `data-sly-use` resolves the model class across bundles, which requires `Export-Package`. My first deploy failed exactly there: bundle Active, page 500 "cannot be resolved to a type". Fix: `package-info.java` with `@Version` + bnd `-exportcontents` — the archetype pattern.

**Q14. Sling Scheduler vs Sling Jobs?**
Scheduler: whiteboard pattern — register a `Runnable` with `scheduler.period` or a cron `scheduler.expression`; best-effort, runs on every instance (gate by run mode; `scheduler.concurrent=false` to prevent overlap). Jobs: `JobManager`, persisted, retried, distributed — use when execution must be guaranteed. I run a scheduler that pre-warms hot catalog queries every 5 minutes.

**Q15. What is Sling-Model-Packages?**
The manifest header telling the Sling Models implementation which packages to scan for `@Model` classes. Forget it and models silently never register — HTL gets null, no error. Check `/system/console/status-slingmodels`.

## 3. Sling Models (deep)

**Q16. adaptables Resource vs SlingHttpServletRequest?**
Request-adaptable models can read request state (parameters, suffix, wcmmode) but can't be created from a bare resource — `resource.adaptTo()` returns null silently. Resource-adaptable models work anywhere but see no request. Declare both when you don't need the request. My StoreLocatorModel is request-only because it reads `?zip=`.

**Q17. Injection strategy — why OPTIONAL?**
Default strategy makes every field required; one absent property → `MissingElementsException` → model is null in HTL → blank or broken component with no visible error. `DefaultInjectionStrategy.OPTIONAL` makes missing fields null so you render the empty state. I hit this live with `cq:styleIds`.

**Q18. Which injectors do you reach for?**
Specific over generic `@Inject`: `@ValueMapValue` (JCR property), `@OSGiService`, `@Self` (adaptable itself), `@SlingObject` (resolver/resource/response), `@ChildResource`, `@RequestAttribute`, `@ScriptVariable` (page, currentStyle in AEM). Specific injectors are faster and document intent.

**Q19. Model Exporter?**
`@Exporter(name="jackson", extensions="json")` + resourceType → `<path>.model.json` serialized by Jackson. The backbone of AEM SPA/headless delivery. The JSON is a public API contract: `@JsonIgnore` internals, keep getters stable. My whole React storefront consumes exactly this, with `:type`/`:items`/`:itemsOrder` matching the SPA-editor contract.

**Q20. @PostConstruct vs logic in getters?**
Derived values in `@PostConstruct` — computed once after injection; getters stay cheap because HTL may call them repeatedly. Link normalization (adding `.html` to internal links) is my standard example.

## 4. HTL

**Q21. Why HTL over JSP? What's its philosophy?**
Deliberately logic-less and secure by default: every `${}` output is context-aware XSS-escaped (text, attribute, uri, scriptString…). No string concatenation, no method calls with logic — that lives in models. `@ context='unsafe'` disables escaping and is a code-smell/review-flag. I've hit the guardrails personally: no `+` concatenation, no `startsWith` — both moved to the model.

**Q22. Name the key data-sly attributes.**
`data-sly-use` (bind model), `data-sly-test` (conditional render), `data-sly-list`/`repeat` (iterate), `data-sly-resource` (include another resource through Sling — the composition primitive), `data-sly-include` (include a script file), `data-sly-element`/`attribute`, `data-sly-template`/`call` (reusable markup functions).

**Q23. data-sly-resource vs data-sly-include?**
`resource` includes another *resource* with full Sling resolution (its own resourceType picks its script) — how containers render children. `include` includes another *script* against the current resource — how my teaser override wraps the base script.

**Q24. Contexts — give examples.**
`${link @ context='uri'}` for hrefs (blocks javascript: URLs), `${richText @ context='html'}` sanitizes authored HTML, attribute context for attribute values. Picking the wrong context either breaks output or opens XSS.

## 5. Components, dialogs, templates, policies

**Q25. What makes up an AEM component?**
A `cq:Component` node under `/apps`: HTL script named like the folder, `cq:dialog` (Granite UI form definition), optional `cq:editConfig`, `componentGroup` for the editor's browser, `sling:resourceSuperType` for inheritance. Authored values are just properties on the page's component node.

**Q26. How do dialogs work under the hood?**
The dialog is a content tree of Granite UI resource types (textfield, pathfield, select, checkbox, richtext…) with `name="./prop"`. The editor renders it as a form; submit is a Sling POST writing those properties; the component re-renders. `@Delete` and `@TypeHint` suffixes control removal and types.

**Q27. Proxy components / inheritance?**
Core Components pattern: a versioned base component (`.../base/teaser/v1/teaser`) holds scripts+dialog+model binding; the project component is a *proxy* — just `.content.xml` with `sling:resourceSuperType`. Upgrade = point proxy at v2. Override selectively by shadowing a single file. Dialogs resolve through the supertype chain. Additive dialog changes are fine in-place; breaking changes require a new version.

**Q28. Editable templates — structure/initial/policies?**
Under `/conf/<site>/settings/wcm/templates/<t>`: `structure` = what every page always has (locked), `initial` = starting content copied into new pages, `policies` = mapping of components to policy nodes. Creating a page = new `cq:Page` + copy of `initial/jcr:content` + `cq:template` pointer — what `PageManager.create()` does; I implemented that flow myself.

**Q29. What do policies control?**
Design-time governance per template: which components a container allows, per-component defaults, and the Style System (`cq:styleGroups` → styles with ids/labels/CSS classes). Live demo I can describe: restricting the container policy to one component instantly shrank the editor's component browser — no deployment.

**Q30. Style System — why does it matter?**
Authors toggle predefined looks (stored as `cq:styleIds` on the node, rendered as CSS classes) without new dialog fields or code. Governance stays in the policy; CSS lives in the clientlib. It killed the "one-off dialog checkbox per visual variant" anti-pattern.

**Q31. cq:editConfig?**
Editor behavior for a component: placeholder ("empty text"), drop targets, inplace editing, listeners. My stack renders placeholder states via a WCMMode-style check instead — same concept.

**Q32. What is WCMMode?**
Request-scoped mode: EDIT/PREVIEW/DISABLED. Components render editor affordances only in EDIT; publish is always DISABLED. I implemented the equivalent via a `wcmmode` parameter/cookie and used it for placeholders and editor wrappers.

## 6. Clientlibs

**Q33. What is a client library?**
A `cq:ClientLibraryFolder` with `css.txt`/`js.txt` listing files, addressed by `categories`; `dependencies` (separate includes) vs `embed` (inlined); served concatenated/minified by the HTML Library Manager with cache-busting hashes. `allowProxy=true` exposes `/apps` libs via `/etc.clientlibs` so publish never exposes `/apps` directly.
**Gotcha:** wrong folder type or missing `css.txt` = category silently resolves to nothing. I re-implemented the resolution logic myself (dependencies first, then own files) so I can explain the order.

**Q34. How did your frontend build integrate?**
`ui.frontend` (Vite/TS/React) builds into the clientlib folders inside `ui.apps` (the archetype does this with webpack + aem-clientlib-generator); Maven reactor ordering via a dependency. Author UI and site JS are separate clientlibs/categories.

## 7. Content packages & project structure

**Q35. Walk through the archetype modules.**
`core` (OSGi bundle: models, services, servlets), `ui.apps` (components/HTL/dialogs/clientlibs → /apps, immutable), `ui.content` (sample content + /conf templates/policies, mutable), `ui.config` (OSGi configs per run mode), `ui.frontend` (JS build), `dispatcher` (config), `all` (container package embedding everything — the single deployable Cloud Manager installs).

**Q36. What does filter.xml do? Modes?**
Declares which JCR paths a package *owns*. Default replace mode: on install the subtree becomes exactly the package content — wrong filters delete prod content. `merge` = add only, never touch existing (right for seeding /content authors own); `update` = update matching, don't delete others. I learned merge-vs-update by wondering why my sample-page edits stopped deploying.

**Q37. Package types?**
`application` (only /apps + /libs, no configs/bundles), `content`, `container` (embeds sub-packages and bundles into `/apps/<x>/install`), `mixed`. Cloud Manager validates these strictly — I hit every one of these validator errors and can list them: content outside /apps in an application package, configs in an application package, overlapping filters between packages, uncovered filter ancestors.

**Q38. Mutable vs immutable content in AEMaaCS?**
`/apps`,`/libs` immutable — baked into the image at deploy; `/content`,`/conf`,`/etc`,`/var` mutable. Consequence: no hotfixing scripts in prod, everything through the pipeline; repoinit (idempotent startup scripts) is the sanctioned way to create service users, ACLs, and paths.

## 8. Replication, publish, dispatcher

**Q39. Author→publish: how does content move?**
Replication: activation serializes the content and pushes it over HTTP to publish (agents on author; Sling Content Distribution under the hood in AEMaaCS). Author and publish are separate repositories; code must be *deployed* to both, content is *replicated*. My "works on author, broken on publish" incident is exactly what happens when you forget the second half — publish rendered a raw node dump because the component's scripts didn't exist there.

**Q40. What is the dispatcher? Its three jobs?**
Apache httpd module in front of publish: (1) security filter — deny-by-default allowlist of paths/extensions/methods, (2) file cache of rendered responses, (3) load balancer over the publish farm. I modeled it from a real `dispatcher.any` and can walk every block.

**Q41. Explain /filter vs /cache /rules vs /ignoreUrlParams.**
`/filter` decides what *reaches publish at all* (deny `*`, allow content HTML/JSON, deny `/system`, `/bin`, POST, `.infinity.json`). `/cache /rules` decides what gets *written to the cache* (deny query-dependent pages like search). `/ignoreUrlParams` decides which query params *don't* affect the cached representation — safe default is deny-all: unknown params make the request uncacheable, never poison the cache. All rule sets are **last-match-wins** — the root of my cache-poisoning incident.

**Q42. statfileslevel and invalidation?**
On activation the dispatcher flush agent POSTs `CQ-Handle` to `/dispatcher/invalidate.cache`; the dispatcher deletes the page's files and touches `.stat` files down to `statfileslevel` depth; cached files older than an ancestor `.stat` are re-fetched. Level 0 = any activation invalidates the whole site (thundering herd); too deep = shared headers/footers go stale. Multi-country site: set it around the language-root depth. Order matters: content to publish FIRST, then flush — flush-first re-caches the old page.

**Q43. serveStaleOnError? TTL?**
Stale-on-error: publish down → serve the stale cached copy instead of 502s (I demoed it by stopping publish). `enableTTL` honors Cache-Control max-age for things that can't be flushed by path.

**Q44. Why is caching search results dangerous?**
The cache key is the URL *path*; query strings aren't part of the file name. Cache a `?q=` page and the first user's results are served to all. Correct design: never cache query-dependent HTML; absorb the load in a service-level cache keyed by the actual query. I have the incident, the fix, and a CI validator that simulates last-match-wins to prevent regression.

## 9. Headless: exporters, Content Fragments, GraphQL, UE, EDS

**Q45. Ways to get content out of AEM headlessly?**
(1) Sling Model Exporter `.model.json` — component/page JSON, SPA-editor contract. (2) Content Fragments + GraphQL — structured, page-independent content with a generated schema. (3) Assets HTTP API. (4) Edge Delivery Services with document-based or CF-based content. I've used 1 end-to-end in my project and 2 hands-on in AEM Cloud.

**Q46. Content Fragments vs Experience Fragments?**
CF = structured *data* (model-driven, channel-agnostic, lives in the DAM, queried via GraphQL). XF = reusable *presentation* — an authored fragment of components with variations, droppable on pages or exported to targets.

**Q47. CF models and the GraphQL schema?**
A model defines typed fields (text, number, date, enumeration, references — including fragment references for relations, and content/image references). AEM *generates* the GraphQL schema per configuration/endpoint from the models: `<model>ByPath`, `<model>List` with `filter/sort/limit/offset`, filter syntax `{field: {_expressions: [{value, _operator}]}}`, variations via a `variation` argument, images as typed refs with `_publishUrl`. I've run all of these against a real instance.

**Q48. Persisted queries — why only them in production?**
Saved server-side under `/conf/<config>/settings/graphql/persistentQueries`, executed via GET `/graphql/execute.json/<config>/<name>` → cacheable at dispatcher/CDN, allow-listed API surface, no arbitrary expensive POST queries from clients. I found the trial's POST endpoint open (non-prod) and can articulate why prod blocks it.

**Q49. What is the Universal Editor?**
Adobe's current-generation visual editor: in-context editing over the real rendered app/page (any framework, including EDS and headless apps), writing back through instrumented attributes. I've authored and published with it in AEM Cloud. It's Adobe's bridge between headless flexibility and WYSIWYG authoring.

**Q50. Edge Delivery Services in one minute?**
Content authored as documents (Word/GDocs/da.live) or CFs; GitHub repo of vanilla JS/CSS "blocks" that decorate server-generated semantic HTML; no build step; preview/live per branch (`*.aem.page`/`.aem.live`); Lighthouse-100 by design via eager/lazy/delayed loading. Best for content-velocity marketing pages. I built a custom block (deal-cards, doc-table → cards with price parsing).

**Q51. When would you choose which (Sites vs headless vs EDS)?**
Rule of thumb: rich in-context authoring + server-side integrations → classic Sites (or Sites+UE); app-like, multi-channel, existing React teams → headless (CF/GraphQL or model.json); campaign/landing pages where speed-to-publish and Core Web Vitals dominate → EDS. Retail reality is all three; the storefront PDP/PLP is app-like, campaign pages are EDS-shaped, brand pages are Sites-shaped.

## 10. AEMaaCS & Cloud Manager

**Q52. How is AEMaaCS different from AMS/on-prem?**
Containerized, auto-scaled author/publish; immutable code, mutable content; no web console/CRXDE in prod, no local hotfixes; Cloud Manager is the only deployment path; built-in CDN; Sling Content Distribution replaces classic replication agents; continuous updates by Adobe. Debugging via Developer Console + log forwarding, not SSH.

**Q53. Cloud Manager pipeline stages and gates?**
Build → unit tests (coverage gate ≥50%) → code scanning (SonarQube rules + `aemanalyser` against the SDK API: unresolved imports, deprecated/internal API usage, config problems, oak index rules) → package validation → deploy to dev/stage (with approval) → prod. I replicated this in GitHub Actions and made the analyser fail on a deliberate implementation-package import — same error text a broken pipeline shows: `[api-regions-exportsimports] … importing package(s) … but no bundle is exporting these`.

**Q54. Programs, environments, sandboxes?**
A program holds environments (prod+stage paired, dev, RDE); sandbox programs are for training/POC — auto-provisioned, hibernate after idle, no SLA. Hostnames encode it: `author-p<program>-e<env>.adobeaemcloud.com` — I can read those from my trial instance.

**Q55. Run modes?**
Instance roles/environments selecting config folders: `author`/`publish` × `dev/stage/prod` (+ custom locally). Config resolution picks the most specific matching folder. I run `config.author`, `config.publish`, `config.local`, `config.prod`, `config.author.prod` in the project.

## 11. Testing

**Q56. How do you unit test AEM code?**
JUnit 5 + wcm.io mocks: `AemContext`/`SlingContext` gives an in-memory repo, resource resolver and model registry. Load JSON fixtures as content, `context.registerService(mock)` for OSGi deps, `registerInjectActivateService(new Impl(), configMap)` to activate services with typed config, `context.request()/response()` for servlets. External HTTP: stub server (or WireMock). My project: 27 tests, 90% line coverage — covering models, servlets, cache/stale/breaker logic, and the replication agent's wire format.

**Q57. What do you NOT unit test / what else exists?**
HTL rendering (covered by integration tests against a running instance — Sling Testing Clients in `it.tests`), dispatcher behavior (curl-based smoke tests), UI (Cypress/Playwright). Cloud Manager runs IT and UI test steps too.

## 12. Performance & Oak

**Q58. Where are the caches, front to back?**
CDN → dispatcher file cache → (AEM output caching if any) → service-level data caches (my Caffeine-style TTL cache) → JCR/Oak. Each layer has an invalidation story; HTML caching keys by URL, data caching keys by query. ISR on the React storefront is one more layer with tag-based revalidation on publish.

**Q59. Oak queries — what gets you in trouble?**
Traversal queries (no index) — fine on 100 nodes, meltdown on a million; the log warns. Use indexed queries: property indexes, Lucene indexes under `/oak:index`; in AEMaaCS custom indexes deploy through the package with strict naming rules. QueryBuilder vs JCR-SQL2 is mostly taste; both need indexes. Prefer content structure over queries when possible (tree traversal by path is free).

**Q60. A page is slow — your debugging path?**
Reproduce with `?wcmmode=disabled` on publish-like conditions; check whether it's cacheable (X-Cache headers); `request.log`/RequestProgressTracker for per-component timings; thread dumps if the instance is globally slow (usually a slow downstream holding threads — my chaos drill demonstrates it); then the specific component's model/service. Fix categories: cache it, index it, timeout it, or move it client-side.

## 13. Security

**Q61. Top AEM security practices?**
Dispatcher deny-by-default (block /system, /bin, /crx, raw JSON renderers, POST); no anonymous write anywhere; service users with minimal ACLs via repoinit (never the admin resolver); XSS via HTL contexts (never `context='unsafe'` on user content); CSRF protection for state-changing POSTs; keep `/apps` unreadable on publish (clientlibs via allowProxy); don't leak errors (HtmlRenderer dumps).

**Q62. How do permissions work?**
Principals (users/groups) with ACLs on paths (allow/deny privileges), evaluated on every resource access through the user's session — which is why resourceType-bound servlets inherit security and admin sessions are banned. CUGs for closed content areas. In AEMaaCS, IMS groups map to AEM groups.

## 14. War stories (STAR-ready — 30–45 s each)

1. **Model not exported** — page 500 `HeroModel cannot be resolved` though the bundle was Active → HTL compiles templates in another bundle; models package wasn't in Export-Package → `package-info.java @Version` + bnd exportcontents. *Lesson: OSGi visibility ≠ compilation.*
2. **Anonymous couldn't read /conf** — styles/policies worked for admin, vanished for visitors → repoinit ACL; *always test as anonymous on publish.*
3. **Fragment-action trap** — form submitted to the component path, visitors got a naked HTML fragment → model exposes the containing page URL (AEM: currentPage). *resource.path inside a component is the component node.*
4. **Works-on-author-only** — published page showed a raw property dump (HtmlRenderer) → content replicates, code deploys; publish lacked the new component → deploy `all` to both tiers; why Cloud Manager deploys atomically.
5. **Cache poisoning** (full story in §0). *Last-match-wins + path-only cache keys; validators must test verdicts, not lines; beware config drift.*
6. **Config precedence** — console-edited config shadowed the deployed one; redeploy didn't fix it → delete the ConfigAdmin entry. *Why AEMaaCS has no console.*
Bonus: **chaos test that lied** — resilience test passed because fault injection didn't cover the endpoint → verify your fault actually fires.

## 15. Gap topics (honest one-paragraph answers — "I haven't built this, here's my understanding")

- **Workflows**: JCR-based process definitions (models of steps) run by the workflow engine — approval chains, DAM asset processing (on AEMaaCS asset processing moved to Asset Compute microservices). Custom steps = `WorkflowProcess` OSGi services. Launchers trigger workflows on content events.
- **MSM / Live Copy**: multi-site manager rolls out a blueprint site to live copies (per country/brand) preserving inheritance per component; broken inheritance is tracked. The alternative for translations is language copies + translation projects.
- **Launches**: author future versions of pages alongside current ones, promote on a date.
- **Versioning**: page versions on activation/on demand; restore/diff/Timewarp.
- **DAM/Assets**: `dam:Asset` nodes with renditions; metadata schemas; processing profiles; Dynamic Media for on-the-fly renditions; Connected Assets to share DAM across instances.
- **Translation/i18n**: HTL `${'key' @ i18n}`, dictionaries under /apps (sling:MessageEntry), language copies + translation connectors.
- **Context-Aware Configuration**: config resolved by content context (per-site settings), `@ContextAwareConfiguration` injection — the modern replacement for design dialogs/site configs.
- **Adobe Target/Analytics/Launch**: integrations via IMS; data layer (`adobeDataLayer`) events from components; Target offers/experiences on pages; at Best Buy scale, personalization and A/B run through these — my components' JSON-first design makes them target-able.
- **CIF (Commerce Integration Framework)**: connector + core components binding AEM to a commerce GraphQL backend (Magento-style) — product/category pages driven by live commerce data; my catalog-api integration is the same architecture hand-rolled.
- **Oak indexing detail**: custom Lucene index definitions as content under /oak:index, deployed via package with `-custom-` naming in AEMaaCS; async indexing lanes; `IndexStats` and the `explain` feature for query plans.

## 16. Behavioral + questions to ask them

**Likely behavioral:** why AEM/why this switch (honest: senior SWE fundamentals + deliberate 4-week deep-dive, show the repo); a time you debugged under pressure (pick war story 5); how you learn new tech (this project IS the answer); disagreement story (use the PR review culture you practiced — scope creep, review comments).

**Questions to ask them:**
- "Are you on AEMaaCS or AMS, and where are you in the Edge Delivery / Universal Editor adoption curve?"
- "How does product/commerce data reach page rendering — CIF, custom services, or headless composition?"
- "What does your dispatcher/CDN layer look like, and who owns cache invalidation logic?"
- "How are AEM teams sliced — platform vs feature teams? Where would I start?"
- "What's the current biggest source of incidents?"

---
*Everything in sections 0–14 is backed by code you wrote or incidents you debugged in github.com/dionysus-666/blueshelf-aem — say so in the interview and offer the repo.*
