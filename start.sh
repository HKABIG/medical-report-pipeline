#!/usr/bin/env bash
# Builds the review UI into the backend jar and serves API and UI from the committed out/ on http://localhost:8080.
# No API key needed.
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PORT="${PORT:-8080}"

(cd "$ROOT/frontend" && npm ci --no-audit --no-fund && npm run build)
(cd "$ROOT/backend" && ./mvnw -q -B -DskipTests package)

echo "Review UI: http://localhost:$PORT"
exec java -jar "$ROOT/backend/target/application-0.0.1-SNAPSHOT.jar" --app.root="$ROOT" --server.port="$PORT"
