#!/usr/bin/env bash
# Rebuilds out/ from fixtures/, the documents and a catalog, without an API key or network.
# Usage: ./replay.sh [path/to/field_catalog.json]   (default: the committed catalog)
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
CATALOG="${1:-$ROOT/case-intake-exercise/catalog/field_catalog.json}"
if [ ! -f "$CATALOG" ]; then
  echo "Catalog not found: $CATALOG" >&2
  exit 1
fi
CATALOG="$(cd "$(dirname "$CATALOG")" && pwd)/$(basename "$CATALOG")"

cd "$ROOT/backend"
./mvnw -q -B -DskipTests package
# Replay never needs a key; unset any so a run cannot reach a provider by accident.
env -u GEMINI_API_KEY -u OPENAI_API_KEY \
  java -jar target/application-0.0.1-SNAPSHOT.jar \
    --app.root="$ROOT" \
    --app.catalog="$CATALOG" \
    --app.pipeline.command=replay
