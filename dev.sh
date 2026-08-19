#!/usr/bin/env bash
# Dev helper. Usage: ./dev.sh up|down|build|deploy|deploy-publish|deploy-all|bundle|apps|fe|logs|status
set -euo pipefail
ROOT="$(cd "$(dirname "$0")" && pwd)"
export JAVA_HOME=/opt/homebrew/opt/openjdk@21/libexec/openjdk.jdk/Contents/Home
export PATH="$JAVA_HOME/bin:$PATH"
case "${1:-}" in
  up)      (cd "$ROOT/infra" && docker compose up -d --build) ;;
  down)    (cd "$ROOT/infra" && docker compose down) ;;
  build)   (cd "$ROOT/blueshelf" && mvn -B clean install) ;;
  deploy)  (cd "$ROOT/blueshelf" && mvn -B clean install -PautoInstallSinglePackage) ;;
  deploy-publish) (cd "$ROOT/blueshelf" && mvn -B install -PautoInstallSinglePackage -pl all -Dsling.port=4503) ;;
  deploy-all) "$0" deploy && "$0" deploy-publish ;;
  bundle)  (cd "$ROOT/blueshelf" && mvn -B install -PautoInstallBundle -pl core) ;;           # hot-deploy Java only
  apps)    (cd "$ROOT/blueshelf" && mvn -B install -PautoInstallPackage -pl ui.apps) ;;       # HTL/dialogs only (includes FE build)
  fe)      (cd "$ROOT/blueshelf/ui.frontend" && npm run watch) ;;                              # rebuild React on change (then ./dev.sh apps)
  logs)    docker logs -f blueshelf-author ;;
  flush)   curl -s -X POST -H "CQ-Action: Activate" -H "CQ-Handle: ${2:-/content/blueshelf}" http://localhost:8080/dispatcher/invalidate.cache ;;  # flush dispatcher cache
  storefront) (cd "$ROOT/storefront" && npm run dev) ;;
  status)  curl -s -u admin:admin http://localhost:4502/system/console/bundles.json | python3 -c 'import sys,json;d=json.load(sys.stdin);print(d["status"]);[print(b["symbolicName"],b["state"]) for b in d["data"] if "blueshelf" in b["symbolicName"]]' ;;
  *) echo "usage: $0 up|down|build|deploy|deploy-publish|deploy-all|bundle|apps|fe|flush [path]|storefront|logs|status"; exit 1 ;;
esac
