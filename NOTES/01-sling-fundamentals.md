# Notes 01 — Sling/AEM fundamentals (Phase 0–1, done)

## What is running
| URL | What | AEM equivalent |
|---|---|---|
| http://localhost:4502/system/console/bundles | Felix web console (bundles, components, configs, logs) | same URL in AEM |
| http://localhost:4502/bin/browser.html | Composum JCR browser | CRXDE Lite `/crx/de` |
| http://localhost:4502/bin/packages.html | Composum package manager | `/crx/packmgr` |
| http://localhost:4502/content/blueshelf/us/en.html | our home page | same |
| http://localhost:4502/content/blueshelf/us/en/jcr:content/root/hero.model.json | Sling Model Exporter JSON | same (headless/SPA backbone) |
| http://localhost:4502/content/blueshelf/us/en/jcr:content.2.json | default JSON renderer, depth 2 | same (`.infinity.json`) |
| http://localhost:4503/... | publish (separate repo, empty until we replicate) | publish |

Login admin/admin.

## Commands
```bash
source .envrc                                   # JAVA_HOME -> JDK 21 (AEM SDK refuses 23/26)
cd infra && docker compose up -d                # author :4502, publish :4503
cd blueshelf
mvn clean install                               # build + unit tests (JaCoCo report in core/target/site/jacoco)
mvn clean install -PautoInstallSinglePackage    # build + deploy `all` package to author
mvn clean install -PautoInstallSinglePackage -Dsling.port=4503   # deploy to publish
mvn install -PautoInstallBundle -pl core        # hot-deploy only the Java bundle (seconds)
```

## How a request becomes HTML (memorize this — it's asked in every AEM interview)
`GET /content/blueshelf/us/en.html`
1. **Resource resolution**: path `/content/blueshelf/us/en` → JCR node (type `cq:Page`). Decompose URL as
   `resourcePath . selectors . extension / suffix`. Sling tries longest path first; the rest become selectors/extension.
2. **Servlet/script resolution**: resource's `sling:resourceType` (or primary node type when absent →
   `cq:Page` → `/apps/cq/Page/Page.html`). Search paths: `/apps` then `/libs` (override by shadowing!).
   Script file name chosen by selectors + extension + method (`hero.html`, `hero.teaser.html`, `POST.jsp`...).
3. **Script execution**: `Page.html` does `data-sly-resource="jcr:content"` → resolves `blueshelf/components/page`
   → `page.html` → includes `root` (container) → container iterates children → each child's own
   `sling:resourceType` picks `hero.html` / `text.html`.
4. **HTL + Sling Model**: `data-sly-use.hero="com.blueshelf.core.models.HeroModel"` adapts the current
   resource/request to the model; getters render with context-aware XSS escaping.

Same URL with `.model.json` → no script at all: Sling Models Jackson **Exporter** servlet serializes the model.

## The three real errors we hit today (and will hit again in AEM)
1. **Maven build: package validation failures** (FileVault validator = what Cloud Manager runs):
   - *"Filter root's ancestor '/apps/cq' is not covered"* → every filter root's ancestors must exist
     in the package or be "valid roots". Fix: filter the parent, or add `.content.xml` ancestors.
   - *"Package of type APPLICATION is not supposed to contain OSGi bundles"* → archetype rule:
     `ui.apps` = scripts/dialogs only; bundles + sub-packages are embedded by the **`all` container** package.
   - *Invalid CND* → the validator only knows standard + declared nodetypes. Keep CNDs minimal.
2. **HTL: `com.blueshelf.core.models.HeroModel cannot be resolved to a type`** although the bundle was *Active*.
   Cause: the models package was **Private-Package**, not exported. HTL compiles templates to Java classes in
   a different bundle, so your model package must be `Export-Package`d. Fix: `package-info.java` with
   `@Version("1.0.0")` + bnd `-exportcontents: ${packages;VERSIONED}` (that's literally what the AEM archetype does).
3. **Node types**: Sling has no `cq:Page`. We register a minimal CQ CND via `Sling-Nodetypes` header in core and
   via `META-INF/vault/nodetypes.cnd` in packages so our content is AEM-shaped. On AEM those types pre-exist.

## Mini-exercises (do these yourself, 30 min)
1. Add a `badge` property to the hero (dialog-less for now): set it with the Sling POST servlet
   `curl -u admin:admin -F badge="Deal of the day" http://localhost:4502/content/blueshelf/us/en/jcr:content/root/hero`,
   then surface it in `HeroModel` + `hero.html`, hot-deploy with `-PautoInstallBundle` + `-PautoInstallPackage -pl ui.apps`.
   Note the POST servlet: **the JCR is writable over HTTP by default** — this is why Dispatcher filters deny POST in prod.
2. Request `/content/blueshelf/us/en.foo.bar.html` — why does it still render? (selectors are optional unless a script claims them)
3. Request `/content/blueshelf/us/en/jcr:content/root/hero.html` directly — component renders standalone. That's what AEM's editor does for "refresh component".
4. Break it on purpose: remove `Sling-Model-Packages` from the bnd config, redeploy, observe HTL returns `null` model with *no* error. Put it back.
5. In `/system/console/bundles`, stop bundle `blueshelf.core` and reload the page. Understand "Installed vs Active".

## Interview one-liners you can now say truthfully
- "Sling is resource-first: the URL resolves to a JCR resource, and the resource's resourceType selects the script. There's no route table."
- "HTL is logic-less and context-aware-escaped; logic lives in Sling Models adapted from Resource or Request."
- "We kept `/apps` (immutable, code) and `/content` (mutable) in separate packages with `merge` filters on content, and deploy a single `all` container package — mirroring Cloud Manager."
- "Model Exporter gives us `.model.json` for headless consumers without writing servlets."
