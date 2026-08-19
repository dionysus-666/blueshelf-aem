# mini-dispatcher

A Node model of Adobe's AEM Dispatcher that reads **real `dispatcher.any` syntax** (subset) and implements its
semantics: `/filter`, `/cache /rules`, `/allowAuthorized`, `/ignoreUrlParams`, `/statfileslevel` + `/invalidate`,
`/enableTTL`, `/serveStaleOnError`, `/allowedClients`, and the flush protocol `POST /dispatcher/invalidate.cache`
with `CQ-Action` / `CQ-Handle` headers. Adds `X-Cache: HIT|MISS|PASS(reason)|STALE|DENY` for learning.

Not modelled: `/vanity_urls`, `/sessionmanagement`, sticky connections, `/propagateSyndPost`, gzip, the Apache side
(vhosts, rewrites, mod_security) and Adobe's Dispatcher SDK validator. In AEMaaCS the config lives in
`dispatcher/src/conf.d` + `conf.dispatcher.d` of your project and is validated by Cloud Manager.

```bash
docker compose up -d dispatcher           # :8080 -> publish
curl -D - http://localhost:8080/content/blueshelf/us/en.html | grep X-Cache
curl -X POST -H "CQ-Action: Activate" -H "CQ-Handle: /content/blueshelf/us/en" http://localhost:8080/dispatcher/invalidate.cache
docker exec blueshelf-dispatcher find /var/cache/dispatcher -type f | head     # cached files + .stat + .headers/.ttl
```
