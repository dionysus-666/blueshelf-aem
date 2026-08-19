# Notes 04 — Phase 3: inheritance, Style System, clientlibs

## Component inheritance (Core Components proxy pattern)
- `blueshelf/components/base/title/v1/title` = "library" component (HTL + model + dialog; group `.blueshelf.base` hides it).
- `blueshelf/components/title` = **proxy**: only `.content.xml` with `sling:resourceSuperType`. Scripts AND dialog are inherited
  (editor follows the super-type chain: `api.getDialog`). Upgrade = change `v1`→`v2` in one place.
- `blueshelf/components/teaser/teaser.html` = **override**: same file name as in the super type wins; it `data-sly-include`s the
  base script to avoid copy/paste. (AEM: "proxy + override selectively"; never copy whole Core Components.)
- Policy-over-dialog layering (`TitleModel`): dialog value → policy default (`type=h2` in `/conf/.../policies`) → code default.

## Style System
- Policy node: `cq:styleGroups/item*/cq:styles/item* {cq:styleId, cq:styleLabel, cq:styleClasses}`; mapping in the template:
  `policies/jcr:content/root/blueshelf/components/hero → cq:policy`.
- Author toggles styles (editor **Styles** tab, from `<component>.styles.json`) → saved as `cq:styleIds` (String[]) on the node.
- `StyleModel.cssClasses` resolves ids → classes at render; components add `${style.cssClasses}` to their root element.
- CSS for style classes lives in `clientlib-base/css/styles.css`. Zero Java/dialog changes to add a new look.

## Clientlibs (what HTML Library Manager does, in 120 lines)
- `cq:ClientLibraryFolder` nodes with `categories`, `dependencies`, `embed`, `allowProxy`, and `css.txt`/`js.txt` (`#base=css`).
- `ClientLibraryServlet` (resourceType `cq/ClientLibraryFolder`, ext css|js) concatenates dependencies then own files:
  `/apps/blueshelf/clientlibs/clientlib-site.css` = base + site. AEM adds minification, `/etc.clientlibs` proxy, cache-busting `lc-<hash>-lc`.
- `ui.frontend` (Vite) builds two entries → `clientlib-author` and `clientlib-site`; folder metadata stays in `ui.apps` (not generated).
- `site.ts`: progressive-enhancement type-ahead against the `cq/Page` + `search` selector servlet.

## Gotchas hit (all real)
1. `StyleModel` failed with `MissingElementsException` because `cq:styleIds` was absent → **always** `defaultInjectionStrategy = OPTIONAL`
   (or `@Optional`) for properties that may be missing. HTL just shows nothing — check `error.log`.
2. Anonymous could not read `/conf` → policies/styles resolved only for admin. On AEM `/conf` is world-readable; we fixed it with repoinit.
   Rule: test rendering **anonymously** (publish), not with your admin session.
3. Anonymous can't read `/apps` root → category scan for clientlib dependencies found nothing; we scan relative to the library. (AEM uses a service user.)
4. HTL has no string operators (`startsWith` etc.) — "token recognition error at '^'". Move it to the model (TeaserModel.getLink()).
5. `<sly data-sly-element>` renders nothing — use a real element: `<h2 data-sly-element="${t.type}">`.
6. FileVault validator only knows built-in node types: custom CND must avoid `sling:*` supertypes it can't resolve.

## Exercises
1. Make `hero` a proxy of a `base/hero/v1/hero` (move script/dialog/model binding). Then override only the CTA markup.
2. Add a "Card" style group to `product-list` (e.g. `plp--dark`) — policy + CSS only; verify in the editor.
3. Add `clientlib-dependencies` (e.g. a vendor lib) and `embed` it into `clientlib-site`; inspect the concatenated output order.
4. Create a `v2` of title with an extra `linkURL` field and switch the proxy to v2 — existing content keeps working (backwards compatible dialogs).
