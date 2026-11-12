# Medical report pipeline — case intake

Reads reports about suspected side effects of medicines, asks a language model for the fields in the catalog, and
writes one JSON file per document with every value tied to the exact quote it came from. A Spring Boot API serves the
results, and a React screen lets a reviewer check each value against the document.

The exercise brief, documents, catalog, schemas, answers and checker are in [case-intake-exercise/](case-intake-exercise/).

| Part | Where | Stack |
|---|---|---|
| Extraction pipeline (`extract` / `replay` commands) | [backend/](backend/) | Java 21, Spring Boot 3.5 |
| REST API | [backend/](backend/) | Spring Boot 3.5 |
| Review UI | [frontend/](frontend/) | React 19, TypeScript, Vite |
| Outputs / recordings | `out/`, `fixtures/` | JSON |

**Provider and model:** Google Gemini, `gemini-3.5-flash-lite` (free tier). This is the model that produced the committed
`out/`. `gemini-2.5-flash` is no longer available to new keys.
Switching provider is configuration only; see [Configuration](#configuration).

## Prerequisites

- Java 21 (Maven comes with the wrapper, `./mvnw`)
- Node.js 20+ and npm
- Python 3.9+ (only for the checker)

## Start the backend and UI from fixtures (no key)

```bash
./start.sh
```

Open http://localhost:8080. The script builds the UI into the backend jar, then serves the API and the UI from the
committed `out/`. Use `PORT=9090 ./start.sh` for another port.

### Development mode (hot reload)

```bash
# terminal 1: API on :8080
cd backend && ./mvnw spring-boot:run

# terminal 2: UI on :5173, proxies /api to :8080
cd frontend && npm install && npm run dev
```

## Run the pipeline with a key

```bash
export GEMINI_API_KEY=...        # or put GEMINI_API_KEY=... in backend/.env (git-ignored)
./extract.sh
```

- Sends one request per document. S03 is an image-only PDF and is flagged `unsupported_format` without a request.
- Records every request and response, including failed attempts and with the key removed, to `fixtures/<ID>.json`,
  then writes `out/<ID>.json` and `out/duplicates.json`.
- Logs each attempt with its HTTP status and duration.
- Documents that already have a successful recording are not sent again. If the daily quota runs out, run it again
  later and it resumes. Delete `fixtures/<ID>.json` to re-ask the model for one document.
- The run stops at the first document that fails and writes nothing to `out/`, so `out/` never mixes runs.

Useful overrides (any Spring property can be passed through):

```bash
./extract.sh --app.llm.providers.gemini.model=gemini-3.8-flash      # another Gemini model
./extract.sh --app.llm.providers.gemini.thinking-level=low      # faster answers
./extract.sh --app.llm.requests-per-minute=5                    # stricter rate limit
./extract.sh --app.llm.timeout=900s                             # slower model
OPENAI_API_KEY=... ./extract.sh --app.llm.provider=openai       # any OpenAI-compatible endpoint
GROQ_API_KEY=... ./extract.sh --app.llm.provider=openai \
  --app.llm.providers.openai.base-url=https://api.groq.com/openai/v1 \
  --app.llm.providers.openai.model=llama-3.3-70b-versatile \
  --app.llm.providers.openai.api-key-env=GROQ_API_KEY
```

## Replay (no key, no network)

```bash
./replay.sh                               # committed catalog
./replay.sh path/to/changed_catalog.json  # same recordings, new catalog
```

`replay.sh` rebuilds `out/` from `fixtures/`, the documents and the catalog. With a changed catalog, every recorded
value is checked against the new rules. A value that no longer passes, or a field the recordings do not cover, comes
out `unknown` with a note saying why.

## Score the outputs

```bash
python3 case-intake-exercise/tools/check.py \
  --catalog case-intake-exercise/catalog/field_catalog.json \
  --answers case-intake-exercise/answers \
  --out out \
  --docs case-intake-exercise/documents
```

## Tests

```bash
cd backend && ./mvnw test                       # offline; includes "replay reproduces out/" and "no key in fixtures/"
cd frontend && npm test && npx tsc --noEmit     # offline
cd frontend && npm run lint
```

`CommittedOutputsTest` replays the committed `fixtures/` and compares the result with the committed `out/`; it also
scans `fixtures/` for API keys.

## API

| Method and path | Purpose |
|---|---|
| `GET /api/catalog` | The catalog the UI renders from |
| `GET /api/documents` | id, title, case count, flags, format, review progress |
| `GET /api/documents/{id}` | Document text, extraction output, review state |
| `PATCH /api/documents/{id}/cases/{caseIndex}/fields/{fieldId}` | Body `{"decision": "accepted" \| "rejected" \| "edited" \| "pending", "value": ...}`; an edited value must pass the catalog |
| `GET /api/duplicates` | Duplicate groups with a field-by-field comparison |

Errors are RFC 9457 problem details. Review decisions are stored in `reviews/` (git-ignored); the API never changes
`out/`.

## UI keyboard shortcuts

| Key | Action |
|---|---|
| `J` / `↓`, `K` / `↑` | Next / previous field |
| `A`, `R`, `E`, `U` | Accept, reject, edit, undo |
| `F` | Only fields that need attention |
| `[` / `]` | Previous / next case |
| `?` | Help |

Clicking a highlighted quote in the document selects its field.

## Configuration

All settings are in [backend/src/main/resources/application.yml](backend/src/main/resources/application.yml) under
`app.*`: paths (relative to `app.root`), provider and model, rate limit, retries, timeout and duplicate thresholds. API
keys are never in configuration: each provider names the environment variable it reads (`api-key-env`).

## Repository layout

```
case-intake-exercise/   brief, documents, catalog, schemas, answers, checker (inputs, unchanged)
backend/                Spring Boot: pipeline (cli, extraction, llm, catalog, document, duplicates) and API (api, review)
frontend/               React review UI
fixtures/               recorded model exchanges, key removed
out/                    one output per document + duplicates.json
extract.sh replay.sh start.sh
NOTES.md                evaluation, decisions, AI use
```
