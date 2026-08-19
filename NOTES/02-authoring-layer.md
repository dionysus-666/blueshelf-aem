# Notes 02 — The authoring layer (our "mini Touch UI") and how it maps to AEM

## Use it
| URL (author, login admin/admin) | AEM equivalent |
|---|---|
| http://localhost:4502/sites.html | `/sites.html/content` — Sites console |
| http://localhost:4502/editor.html/content/blueshelf/us/en | `/editor.html/content/.../en.html` — page editor |
| http://localhost:4502/content/blueshelf/us/en.html?wcmmode=edit | the page as the editor iframe loads it (`WCMMode.EDIT`) |
| http://localhost:4503/content/blueshelf/us/en.html | published page |

Workflow you should practice until boring (this is the daily loop of an AEM author *and* the thing your
components must support): **Sites → Create page (pick template) → Edit → drag component → double-click →
fill dialog → Done → Preview → Publish → check publish host → Unpublish.**

Dev loop: `./dev.sh fe` (watch React) in one terminal, then `./dev.sh apps` to push HTL/dialog/clientlib,
`./dev.sh bundle` for Java. Full: `./dev.sh deploy-all` (author + publish).

## What each piece is, and what it is in AEM
| Ours | AEM | Notes / gotcha |
|---|---|---|
| `ui.frontend` (Vite+React+TS → `ui.apps/.../clientlibs/author`) | `ui.frontend` (webpack → `clientlib-site`, `clientlib-dependencies`) | AEM serves clientlibs through `/etc.clientlibs/<proj>/clientlibs/<name>.lc-<hash>-lc.min.js` (HTML Library Manager: concat/minify/cache-bust). Sling has no HTML Library Manager, so we serve the files directly and open `/apps/blueshelf/clientlibs` to `everyone` via **repoinit**. In AEM: `allowProxy=true` on the `cq:ClientLibraryFolder`. |
| `_cq_dialog/.content.xml` (Granite UI nodes) | identical | Filevault maps `_cq_dialog` ⇄ `cq:dialog`. Field `name="./prop"` → Sling POST param. Our React `<Dialog>` renders the same resourceTypes Coral UI would. Unknown field type ⇒ falls back to text input. |
| `EditContext.isEdit()` (param/cookie `wcmmode`) | `WCMMode.fromRequest(request)` | Components render wrapper markup only in edit mode; on publish WCMMode is DISABLED. |
| container `.bs-cmp` wrappers + `.bs-drop` | `cq` decoration tags (`<div class="cq-Editable-dom">` / `cq:editConfig`, placeholder `cq-placeholder`) | Editor overlays are computed from these. |
| `/conf/blueshelf/settings/wcm/templates/content-page/{structure,initial,policies}` | identical (Editable Templates) | `structure` = locked layout, `initial` = copied into new pages, `policies` = `cq:policy` mapping → `/conf/.../policies/...` defines **allowed components** (`components=[group:…]`). Our component browser filters on it. |
| `createPage()` = POST `cq:Page` + `:operation=copy` of `initial/jcr:content` + set `jcr:title`/`cq:template` | `PageManager.create(parent, name, template, title)` | Same steps, AEM's Java API wraps them. |
| `addComponent()` = POST `<container>/*` with `sling:resourceType`, `:nameHint`, `:order=before <name>` | editor's `wcmcommand`/ POST to parsys | Sling POST servlet semantics are identical. **Gotcha** (we hit it): when POSTing to `/*`, don't mix `./prop` and plain `prop` names — the new node can lose properties. |
| `saveProperties()` = POST `./prop`, `./prop@Delete`, `./prop@TypeHint` | Granite form submit | `@Delete` removes a prop; `@TypeHint=Boolean/Long/String[]` controls JCR type. |
| `ReplicationServlet` + `ReplicationServiceImpl` (JSON `:operation=import` to publish) | `/bin/replicate.json` → `Replicator` → replication agent → publish receiver | Author and publish are **separate repositories**; only replication moves content. Code (`all` package) must be deployed to **both** (pipeline does it in AEM). Our agent config lives in `ui.config/.../config.author` ⇒ only on author (run mode). |
| `ui.config` run-mode folders `config`, `config.author`, `config.publish` | identical (+ `config.dev/stage/prod`) | OSGi config as JSON (`.cfg.json`), PID = class name; factory configs `PID~name.cfg.json`. JCR installer watches `/apps/*/osgiconfig/config*`. |
| repoinit script in `ui.config` | identical (`RepositoryInitializer~<name>.cfg.json`) | The sanctioned way to create service users, ACLs, paths. Idempotent; runs at startup. |
| form login `/j_security_check` | identical on author | Publish is anonymous; author requires login (SSO/IMS in AEMaaCS). |

## Three more Cloud-Manager-grade validator errors we hit (good — you now recognise them)
1. *APPLICATION package is not supposed to contain content outside /apps or /libs* → `/editor`, `/sites` moved to `ui.content`.
2. *APPLICATION package is not supposed to contain OSGi bundles or configurations* → `ui.config` is `packageType=container`.
3. *Filter … potentially overlapping* → `ui.apps` now owns `/apps/blueshelf/components` + `/clientlibs`, `ui.config` owns `/apps/blueshelf/osgiconfig`. Overlapping filters across packages = the last one installed silently deletes the other's content.
4. *Filter root's ancestor not covered* → `validRoots` (and note: setting it **replaces** the default list).

## What is STILL different from AEM (so you are not surprised on day 1)
- Coral/Granite look & feel, keyboard shortcuts, the "Assets" side panel (DAM), layout mode (responsive grid
  columns), annotations, timewarp, versions, workflows/launches, MSM/live copies, translation, the Content
  Fragment editor. We'll cover the concepts; the UI you'll learn in a sandbox in hours once the model is clear.
- AEM's editor talks to the page through `cq` JS APIs (`Granite.author`), not a simple iframe DOM walk — but
  the data flow (select overlay → load `cq:dialog` → Sling POST → reload component HTML) is the same.
- Publish in AEM goes through **Dispatcher** (cache) and a **flush agent** invalidates it — Phase 5.

## Exercises
1. Add a `badge` textfield to the hero dialog, surface it in HTL, `./dev.sh apps`, edit a hero — no Java needed.
2. Make a new component `promo-card` (title, price, image URL, CTA) with dialog + model + test; add it to the container policy.
3. Create a second template `landing-page` whose `initial` content has two heroes; create a page from it.
4. Break the policy: set `components=[blueshelf/components/text]` — see the component browser shrink.
5. Open `/system/console/configMgr`, find "BlueShelf Replication Agent", set `enabled=false`, try Publish; re-enable via the UI then observe that the JCR config in `/apps/blueshelf/osgiconfig/config.author` is *not* what's active anymore (config precedence gotcha — redeploy restores it).
