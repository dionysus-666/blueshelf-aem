#!/usr/bin/env bash
# Dev helper. Usage: ./dev.sh up|down|build|deploy|deploy-publish|bundle|logs|status
set -euo pipefail
ROOT="$(cd "$(dirname "$0")" && pwd)"
export JAVA_HOME=/opt/homebrew/opt/openjdk@21/libexec/openjdk.jdk/Contents/Home
export PATH="$JAVA_HOME/bin:$PATH"
case "${1:-}" in
  up)      (cd "$ROOT/infra" && docker compose up -d) ;;
  down)    (cd "$ROOT/infra" && docker compose down) ;;
  build)   (cd "$ROOT/blueshelf" && mvn -B clean install) ;;
  deploy)  (cd "$ROOT/blueshelf" && mvn -B clean install -PautoInstallSinglePackage) ;;
  deploy-publish) (cd "$ROOT/blueshelf" && mvn -B clean install -PautoInstallSinglePackage -Dsling.port=4503) ;;
  bundle)  (cd "$ROOT/blueshelf" && mvn -B install -PautoInstallBundle -pl core) ;;
  logs)    docker logs -f blueshelf-author ;;
  status)  curl -s -u admin:admin http://localhost:4502/system/console/bundles.json | python3 -c 'import sys,json;d=json.load(sys.stdin);print(d["status"]);[print(b["symbolicName"],b["state"]) for b in d["data"] if "blueshelf" in b["symbolicName"]]' ;;
  *) echo "usage: $0 up|down|build|deploy|deploy-publish|bundle|logs|status"; exit 1 ;;
esac
