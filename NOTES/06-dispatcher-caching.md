# Notes 06 — Phase 5: Dispatcher, caching, flush

## Topology now
browser / Next.js storefront → **dispatcher :8080** (filters + file cache) → publish :4503 (Sling) → catalog-api :8081
author :4502 → replication (JSON import) → publish; then **flush** → dispatcher `/dispatcher/invalidate.cache`; then webhook → Next.js `/api/revalidate`.

## dispatcher.any — what each block does (and the incident it prevents)
| Block | Meaning | Classic incident |
|---|---|---|
| `/filter` deny `*` then allow specific `/path` + `/extension` + `/method` | WAF-ish allowlist; last match wins | `/system/console`, `*.infinity.json`, `.2.json`, POST reaching publish; leaking `/bin` servlets |
| `/cache /rules` | what gets written to `/docroot` (last match wins) | caching search results / personalised pages; or caching nothing (publish melts) |
| `/allowAuthorized 0` | never cache requests with `Authorization`/login cookie | logged-in pages served to anonymous users |
| `/ignoreUrlParams` | params that don't change the page (utm_*) are ignored ⇒ cache hit; unknown params ⇒ pass-through | marketing links with `?utm=` busting the cache for every click; or `?foo=` allowed ⇒ cache poisoning/DoS |
| `/statfileslevel N` + `/invalidate` | touching `.stat` at depth ≤N invalidates every matching file below it | `statfileslevel 0` = activating ANY page invalidates the WHOLE site (thundering herd); too high = stale shared nav/footers |
| `/enableTTL` | honours `Cache-Control: max-age` from publish | content that can't be flushed (e.g. servlet JSON) never expires |
| `/serveStaleOnError` | publish down ⇒ serve stale | outage turns into a 502 storm instead of a slightly stale site |
| `/allowedClients` | who may flush | anyone on the internet emptying your cache |
| `/renders` | publish farm + timeouts | |
Verified live with `X-Cache` headers (see the test transcript in this phase): DENY for `/system/*`, `/bin/*`, `.2.json`, editor; PASS for `search?q=`, `?foo=1`; HIT with `?utm_source`; MISS→HIT after flush; sibling page MISS because of `.stat` at level 4; STALE served with publish stopped; uncached page ⇒ 502.

## Flush agent
`ReplicationServiceImpl.flush()` = exactly the AEM flush-agent request (`POST`, `CQ-Action`, `CQ-Handle`). Order: **content on publish first, then flush** — flush-before-activate re-caches the old page for the next visitor. Also a webhook to the storefront (`/api/revalidate`) → Next ISR tag invalidation; in AEM land this would be an Adobe I/O event / custom replication event listener.

## Gotchas hit
- JDK `HttpClient` rejects `Content-Length` as a restricted header; and it tries an h2c upgrade that Node's http server answered by closing the socket ("header parser received no bytes") → force `HTTP_1_1`.
- Sling POST without `_charset_=utf-8` decodes the body as ISO-8859-1 → "â  " mojibake for “—”. Always send `_charset_` (Granite does).
- macOS has no `timeout` — my "deploys" silently did nothing for a while; verify state after every deploy (status of configs/bundles) instead of trusting the command.
- Config change + bundle change must BOTH land: the OSGi config showed the new keys while the old bundle ignored them.

## Exercises
1. Set `/statfileslevel "2"` and show that publishing `/us/en/tvs` invalidates pages under `/content/blueshelf/*` — then argue the right level for a multi-country site (`/content/<site>/<country>/<lang>` ⇒ 4).
2. Add `/vanity` style rewrite: make `/tvs` on the dispatcher map to `/content/blueshelf/us/en/tvs.html` (AEM: Sling mappings `/etc/map` + Apache `mod_rewrite` + `resourceResolver.map()` in link rendering).
3. Deliberately add `/0011 { /glob "/content/blueshelf/*/search.html" /type "allow" }` and watch search results for one user be served to another. Revert.
4. Write a k6/`ab` script: 1000 req/s to `/tvs.html` — compare publish-direct vs dispatcher CPU (`docker stats`).
5. Add `Cache-Control: max-age=300` on `.model.json` responses (Sling filter or servlet header) and observe `.ttl` files + TTL expiry.
