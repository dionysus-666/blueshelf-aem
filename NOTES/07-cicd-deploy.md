# Notes 07 — Phase 6: CI/CD (Cloud Manager stand-in) and deployment

## Pipeline (GitHub Actions) ↔ Cloud Manager
| GitHub job | Cloud Manager step | What it does |
|---|---|---|
| `aem` | Build + Unit Test + Code Scanning | `mvn clean install -Daem.analyser.failOnErrors=true`: JUnit (22 tests), JaCoCo **≥50 % gate** (CM's metric), FileVault package validation, **`aemanalyser-maven-plugin`** (the very analysers CM runs, against the latest `aem-sdk-api`) |
| `dispatcher` | Dispatcher config validation | `dispatcher/validate.mjs` (stand-in for Adobe's Dispatcher SDK validator) |
| `catalog-api`, `storefront` | (non-AEM services — Best Buy has many) | Spring Boot tests, Next.js type-check + build |
| `images` | Build images | pushes `blueshelf-dispatcher`, `blueshelf-catalog-api` to GHCR (main only) |
| `deploy` (after `ci` on main, or manual) | Deploy to Dev/Stage/Prod | ssh to the VM → `docker compose pull/up` (publish, dispatcher, catalog-api, author on 127.0.0.1) → upload+install the `all` package on **author and publish** → smoke test through the dispatcher → revalidate storefront |

Gates you will recognise from Cloud Manager: coverage ≥ 50 %, no analyser errors (unresolved Import-Package, deprecated API regions, bad OSGi config/env-var usage, content in wrong package type), no overlapping filters, dispatcher config valid.

## Run-mode configs in the wild
`config` (all) · `config.author` · `config.publish` · `config.local` · `config.prod` · `config.author.prod` (combined!). Secrets via Felix
interpolation `$[env:AEM_ADMIN_PASSWORD;default=admin]` — identical syntax to AEMaaCS (`$[env:…]`, `$[secret:…]`). The interpolation plugin
ships in Sling Starter and in AEM.

## Hosting choice
- **GitHub**: CI + container registry — yes, best option (free, and the pipeline maps 1:1 to Cloud Manager).
- **VM for the runtime** (one Docker host is enough): Hetzner CX22 (~€4/mo) is the cheapest reliable; Oracle Cloud "Always Free" ARM VM works
  if `apache/sling` pulls for arm64 (it's multi-arch); AWS `t3.small` free-tier-ish. `infra/vm-bootstrap.sh` prepares it. Secrets to set in the repo:
  `DEPLOY_HOST`, `DEPLOY_USER=deploy`, `DEPLOY_SSH_KEY`, `AEM_ADMIN_PASSWORD` (+ optional `STOREFRONT_REVALIDATE_URL`).
- **Storefront on Vercel**: import the GitHub repo in Vercel, *Root Directory* = `storefront`, env `AEM_HOST=http://<DEPLOY_HOST>` and
  `REVALIDATE_SECRET`. Vercel's Git integration gives preview deployments per PR — the headless analogue of CM's dev environments.
- Author stays private (127.0.0.1 on the VM): `ssh -L 4502:localhost:4502 deploy@<host>` then http://localhost:4502/sites.html — like AEM author behind VPN/IMS.

## Gotchas
- `aemanalyser` needs the `all` package built first; run it in the `all` module, gate with `failOnAnalyserErrors`.
- `timeout` doesn't exist on macOS → scripts that "worked" did nothing. Verify deployments by checking state, not exit codes.
- Coverage was 26 % before adding servlet/replication/exporter tests; the tests themselves show how to test servlets with `SlingContext`
  (`registerInjectActivateService(new Servlet())`, `context.request()/response()`), `@Reference`s via `registerService(mock)`, and services with `OsgiContext` + config map.
- Deploying code to author AND publish in the same pipeline run is mandatory; content moves only via replication.

## Exercises
1. Add a `stage` environment: `infra/docker-compose.stage.yml` + GitHub Environment with required reviewers (= CM's "approve production deploy").
2. Break the build on purpose: import `com.day.cq.wcm.api.Page` in a model → the analyser fails (package not exported by our runtime; on AEM it would pass). Revert.
3. Add SonarCloud (free for public repos) to mirror CM's Sonar rules; fix the top 5 findings.
4. Add `mvn -Pit.tests` integration tests (Sling Testing Clients) that hit a running instance in CI via docker-compose services.
