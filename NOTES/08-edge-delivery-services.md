# Notes 08 — Phase 7: Edge Delivery Services (EDS)

Repo: https://github.com/dionysus-666/blueshelf-eds (from `adobe/aem-boilerplate`, custom block `blocks/deal-cards`).
Preview/Live (after the two clicks below): https://main--blueshelf-eds--dionysus-666.aem.page / .aem.live

## Two free one-time steps (you)
1. Install the **AEM Code Sync** GitHub app on the repo: https://github.com/apps/aem-code-sync/installations/new
   (it deploys your code to `*.aem.page` on every push — EDS has no Cloud Manager).
2. Content source: **da.live** (Adobe Document Authoring, free) → create site `blueshelf-eds` for org `dionysus-666`, author `index` and `deals`
   pages (table in `blueshelf-eds/docs/CONTENT.md`), Preview, Publish. Alternative: a Google Drive folder shared with `helix@adobe.com`.
3. Local dev: `npx -y @adobe/aem-cli up` in the repo (local code, previewed content, hot reload).

## EDS vs AEM Sites (what you built in `blueshelf/`) — the comparison interviewers want
| | AEM Sites (Sling/JCR) | Edge Delivery Services |
|---|---|---|
| Content store | JCR nodes, dialogs, templates | documents (Word/Google Docs/da.live) + spreadsheets (JSON) |
| Component | `sling:resourceType` + HTL + Sling Model + `cq:dialog` | **block**: a table whose first cell is the block name → `blocks/<name>/<name>.js/.css` `decorate(block)` |
| Variants / styles | Style System (policies, `cq:styleIds`) | block options in parentheses → CSS classes; section metadata |
| Rendering | server-side HTL on publish, cached by Dispatcher/CDN | backend converts doc → semantic HTML at the edge, browser JS decorates; CDN-first |
| Build | Maven, Cloud Manager, OSGi bundles | **no build step**, vanilla JS/CSS, push to GitHub = deploy (Code Sync) |
| Environments | author / publish / dispatcher, dev/stage/prod | `{branch}--{repo}--{owner}.aem.page` (preview) and `.aem.live` (live); every branch is an environment |
| Performance | your job (clientlibs, caching, Core Web Vitals) | Lighthouse 100 by design: E-L-D loading (eager/lazy/delayed), LCP first, no render-blocking |
| Personalisation / integrations | OSGi services (our CatalogService) | client-side fetch to APIs / spreadsheets → JSON (`/deals.json`), or serverless |
| Authoring UX | in-context editor (Touch UI / Universal Editor) | doc editors + sidekick (Preview/Publish); Universal Editor also supports EDS ("AEM authoring for EDS") |
| When Adobe recommends it | existing enterprise Sites, complex authoring, rich integrations server-side | new marketing/landing sites, speed + performance, content-velocity teams |

Best-Buy-style answer: *campaign/landing/deal pages* are perfect for EDS (content velocity, perfect Lighthouse); *PDP/PLP* stay app-like
(our headless storefront + catalog API); *brand/marketing* content with heavy in-context authoring stays on Sites — and Adobe's
Universal Editor is the bridge across all three.

## Block development rules (from the boilerplate's AGENTS.md — real-world conventions)
- never edit `scripts/aem.js`; `curl localhost:3000/page.plain.html` to see the markup your block gets; authors omit/add cells → decorate defensively;
  scope CSS to `.blockname`; no build step/devDependencies only; PRs must include a preview link.
- Images: `createOptimizedPicture()` → responsive webp variants from the EDS image pipeline (free CDN).
- Data: spreadsheets become JSON endpoints (`/deals.json?sheet=…&limit=`), the EDS equivalent of a Content Fragment list — use for product promos.

## Exercises
1. Author the deals page (table in docs/CONTENT.md), preview, then publish; run Lighthouse on `.aem.live` (expect 100/100/100/100).
2. Add a `price-badge` variant that reads a spreadsheet `/deals.json` and renders live prices; compare with our Sling `product-list`.
3. Create a PR with a branch `feature/hero-video`; observe the branch preview URL; merge → live.
