#!/usr/bin/env bash
# Runs the extraction pipeline against the configured model and records every exchange under fixtures/.
# The API key comes from the environment (GEMINI_API_KEY by default). If backend/.env exists, it is loaded first.
# Documents that already have a successful recording are not sent again; delete fixtures/<ID>.json to redo one.
# Usage: ./extract.sh [extra Spring arguments, e.g. --app.llm.provider=openai]
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
if [ -f "$ROOT/backend/.env" ]; then
  set -a
  # shellcheck disable=SC1091
  . "$ROOT/backend/.env"
  set +a
fi

cd "$ROOT/backend"
./mvnw -q -B -DskipTests package
java -jar target/application-0.0.1-SNAPSHOT.jar \
  --app.root="$ROOT" \
  --app.pipeline.command=extract \
  "$@"
