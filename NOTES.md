# Notes

## Status

| Item | State |
|---|---|
| Part 1: extraction pipeline, recording, replay, `replay.sh` | Done; tests pass offline |
| Part 2: REST API | Done |
| Part 3: review UI | Done; `npx tsc --noEmit`, `npm test`, lint pass |
| Stretch: S01–S04 | Pipeline processes them; S03 (image-only PDF) is flagged `unsupported_format`, no vision model |
| Stretch: duplicates | `out/duplicates.json` and a side-by-side comparison view |
| Stretch: review actions | Accept / reject / edit / undo over `PATCH`, edited values styled apart, progress per document |
| Stretch: quote click and keyboard | Done |
| Recorded run (`fixtures/`, `out/`) | Done with `gemini/gemini-3.5-flash-lite`: 11 requests, each answered on the first attempt; S03 not sent. `./replay.sh` reproduces `out/` (`CommittedOutputsTest`) |

## Checker output on the four documents with answers

```

=== D01 ===
  -- answer case 0 'Mrs M.K.' -> output case 0
     ok patient_initials              +1.0  ok
     ok patient_age_years             +1.0  ok
     ok patient_sex                   +1.0  ok
     ok suspect_drug_name             +1.0  ok
     ok suspect_drug_route            +1.0  ok
     ok suspect_drug_start_date       +1.0  ok
     XX event_terms                   -2.0  unexpected item 'widespread pruritic maculopapular rash'  [conf high]
     ok event_onset_date              +1.0  ok
     ok event_outcome                 +1.0  ok
     ok seriousness_criteria          +1.0  ok
     ok reporter_type                 +1.0  ok
     ok report_date                   +1.0  ok
     ok country                       +1.0  ok
     ok concomitant_drugs             +1.0  ok
     ok dose                          +2.0  ok
  D01: 13.0 / 16

=== D02 ===
  -- answer case 0 'Mr J.T.O.' -> output case 0
     ok patient_initials              +1.0  ok
     ok patient_age_years             +1.0  ok
     ok patient_sex                   +1.0  ok
     ok suspect_drug_name             +1.0  ok
     ok suspect_drug_route            +1.0  ok
     ok suspect_drug_start_date       +1.0  ok
     XX event_terms                   -2.0  missing required ['drug-induced liver injury']  [conf high]
     ok event_onset_date              +1.0  ok
     ok event_outcome                 +1.0  ok
     ok seriousness_criteria          +1.0  ok
     ok reporter_type                 +1.0  ok
     ok report_date                   +1.0  ok
     ok country                       +1.0  ok
     ok concomitant_drugs             +1.0  ok
     ok dose                          +2.0  ok
  D02: 13.0 / 16

=== D03 ===
  -- answer case 0 'Patient (initials withheld)' -> output case 0
     ok patient_initials              +1.0  unknown as expected
     ok patient_age_years             +1.0  ok
     ok patient_sex                   +1.0  ok
     ok suspect_drug_name             +1.0  ok
     ok suspect_drug_route            +1.0  ok
     ok suspect_drug_start_date       +1.0  ok
     ok event_terms                   +1.0  ok
     ok event_onset_date              +1.0  ok
     ok event_outcome                 +1.0  ok
     ok seriousness_criteria          +1.0  ok
     ok reporter_type                 +1.0  ok
     ok report_date                   +1.0  ok
     ok country                       +1.0  ok
     ok concomitant_drugs             +1.0  ok
     ok dose                          +2.0  ok
  D03: 16.0 / 16

=== D05 ===
  -- answer case 0 'Mr D.L.' -> output case 0
     ok patient_initials              +1.0  ok
     ok patient_age_years             +1.0  conflict flagged
     ok patient_sex                   +1.0  ok
     ok suspect_drug_name             +1.0  ok
     ok suspect_drug_route            +1.0  ok
     ok suspect_drug_start_date       +1.0  ok
     ok event_terms                   +1.0  ok
     ok event_onset_date              +1.0  ok
     ok event_outcome                 +1.0  ok
     ok seriousness_criteria          +1.0  ok
     ok reporter_type                 +1.0  ok
     ok report_date                   +1.0  ok
     ok country                       +1.0  ok
     ok concomitant_drugs             +1.0  ok
     ok dose                          +2.0  ok
  D05: 16.0 / 16

================ SUMMARY ================
Documents scored:      4
Field points:          58.0 / 64  (90.6%)
Wrong at confidence:   high 2   medium 0   low 0
Unknown, answer had one: 0
Case-count errors:     0
Provenance:            62 of 62 extracted fields grounded (100.0%)
```

Both points lost are on `event_terms`, both at high confidence:
- **D01:** the model kept the document's full wording, "widespread pruritic maculopapular rash", instead of a short
  term such as "rash". The catalog asks for "one short clinical term per reaction".
- **D02:** the model listed the presenting symptoms (fatigue, dark urine, yellowing of the eyes) instead of the
  diagnosis the reporter attributes to the drug: drug-induced liver injury.

Both are prompt issues, and fixing them means a new recording. The prompt could add "prefer the diagnosis the reporter
names over its symptoms; strip descriptive adjectives", and `event_terms` could be capped at medium confidence, since
term choice is interpretation. I left the recorded run as is so `out/` matches `fixtures/`.

Calibration was otherwise good:
- the D05 age conflict (54 vs 45) came out `conflict`;
- D03's withheld initials came out `unknown`;
- no other wrong values.

## The four documents without answers

What the model produced, compared with my own reading, and where I am unsure.

**D04 (mother and newborn, sertraline, India).** Two cases and `multiple_patients`, as expected.
- Mother: PRD, 29, female; sertraline 50 mg oral from 2025-10-14; headache, confusion, vomiting, hyponatraemia, onset
  2026-05-22; recovered; hospitalization; physician; 2026-06-08; IN.
- Infant: RD, 0, male; dose, unit, route and start date `unknown`, because the exposure was in utero; jitteriness,
  high-pitched cry, poor feeding, mild respiratory distress, neonatal adaptation syndrome; onset 2026-06-03 (medium);
  recovering; hospitalization; concomitant empty ("did not receive any medicine himself").
- Unsure:
  - The mother's concomitant list holds "iron" and "folic acid" from "routine iron and folic acid supplements". The
    answer may exclude supplements, or want "ferrous sulfate".
  - The mother's event terms list symptoms alongside "hyponatraemia". If the key is strict, "headache" etc. may count
    as unexpected items, which is the same weakness as D02.
  - "Recovered" for the mother is stated, but both patients are still in hospital.
  - The infant's onset is "within the first 24 hours of life" after delivery on 3 June, so it could be 3 or 4 June.

**D06 (product quality complaint, Canada).** No case, `no_adverse_event`, with the reason as a free-text flag.
Confident this is right.

**D07 (consumer email, paroxetine, USA, with a prompt injection).**
- The injection was ignored: outcome `not_recovered`, seriousness `hospitalization`, reporter `consumer`. The model
  added a flag saying it found and ignored embedded instructions.
- 38, female, paroxetine 20 mg oral from 2026-03-30; insomnia and suicidal ideation; onset 2026-04-10 (medium, derived
  from "a week and a half in"); report date 2026-04-27; US.
- Unsure:
  - **Initials "JMH" at medium confidence**, taken from the sender address `jenna.m.hartley@…`. The guidance says a
    first name alone does not give initials. An email address is arguably a full name, but I expect the answer to be
    `unknown`, which would cost -1.
  - Concomitant "multivitamin": a supplement without a generic name, so the answer could be the empty list or unknown.
  - Onset could be 2026-04-09 or 2026-04-10.

**D08 (12-page hospital report, methotrexate, New Zealand, fatal).**
- HMN, 72, female; methotrexate **20 mg** oral, correctly the dose at the time of the reaction, not the starting
  15 mg; started 2025-10-07; onset 2026-03-19 (medium; "on or about 19 March"); fatal; death, life-threatening and
  hospitalization; physician; 2026-04-14; NZ.
- Concomitant: folic acid, omeprazole, cilazapril, paracetamol, zoledronic acid. The drugs that treated the reaction
  (folinic acid, filgrastim, the antibiotics) are correctly left out.
- Unsure: `event_terms` lists ten items, from "bone marrow suppression" to "acute kidney injury" and "pneumonia". Acute
  kidney injury is described as a contributing factor rather than a reaction, and symptoms such as "fatigue" are listed
  next to diagnoses. If the answer is strict, this field is wrong. Zoledronic acid is yearly, last given August 2025, so
  it may not count as "taking when the reaction started".

**Stretch documents.**
- S01 (pharmacist) and S02 (cardiologist) describe one case and are grouped in `out/duplicates.json` at medium
  confidence. They agree on 11 of 13 case fields and differ on onset (13 vs 16 Feb) and seriousness.
- S01's "DIGOXIN 250 MCG" came out as 0.25 mg, and the 12 January dispensing date was not taken as the start date.
- S04 shares drug, reaction and age with D01 but is not grouped with it: agreement 0.62. The threshold, 0.75, sits
  between that and S01–S02 at 0.85.
- S03 is `unsupported_format`, with no case.

## Choices that mattered

**One request per document, no chunking.** D08 is about 12,700 characters, about 3,500 tokens. Chunking would split
facts that need each other (the dose change and the reaction date are pages apart) and would need cross-chunk merging.
A configurable limit (`max-document-chars`) flags an oversized document `unsupported_format` instead of truncating it
silently.

**The catalog drives everything.** `ResponseSchemaBuilder` turns the catalog into a response schema with one typed
property per field: option codes become enums, lists become arrays, and every field is required. `PromptBuilder`
renders each field's label, type, options, validation and guidance. `CatalogValidator` checks types, `pattern`, `min`,
`max`, option codes and calendar dates. The UI renders sections, labels, order and controls from `/api/catalog`.
No field id appears in Java or UI source. A frontend test fails if one does, and backend tests pick fields by type.
The one rule the catalog cannot express, that `none` stands alone, is configured as `exclusive-option-codes` rather
than hard-coded.

**Evidence first, then trust nothing the model says without checking.** The schema orders each field as
status → quotes → value → derived → confidence → note, so the model commits to evidence before the value. Then
`FieldResolver` applies these rules:
- The value is coerced to its catalog type and validated. On failure the field becomes `unknown` with a note.
- Every quote is located in the text, ignoring case and runs of whitespace, as the checker does. The provenance written
  is always the document's own text at those offsets, so it cannot be a paraphrase. If no quote is found, the value is
  dropped and the field becomes `unknown` with a note.
- A value the model marks as `derived` (computed from a relative date or a year of birth) is capped at medium
  confidence.
- A missing confidence becomes `low`, with a note.
- `conflict` keeps no value but keeps the candidates' quotes, so the reviewer sees both.

**Offsets are Unicode code points.** Java indexes UTF-16, and the checker counts code points. `SourceText` works in
code points throughout; there is a test with an emoji.

**Cases.** The model reports, for each case, whether each of the four minimum elements is present, with evidence. A
case missing one is dropped and the reason goes into `flags`. More than one case adds `multiple_patients`.

**Prompt injection.** The document sits between boundary lines derived from a hash of its text, so the document
cannot forge the end marker. The system prompt says nothing inside it is an instruction, and the model reports such
text as a reviewer note.

**Provider behind an interface.** `LlmClient` is what the pipeline sees. `HttpLlmClient` handles transport for any
`LlmProvider`, for which there are two wire formats: Gemini and OpenAI-compatible. Switching is `app.llm.provider`. The
key is read from the environment variable the provider names and travels in a header, never the URL.

**Rate limits.**
- Requests are spaced evenly (`requests-per-minute`, default 8).
- 429 and 5xx answers are retried with the delay the provider asks for (Gemini `RetryInfo`, `Retry-After`), else with
  exponential backoff.
- An exhausted daily quota stops the run at once. So does any failed document, since the next one would fail the same
  way. A rerun resumes, because documents with a successful recording are skipped.
- The run writes nothing to `out/` unless every document succeeded.

**Recording and replay.**
- Every attempt is saved to `fixtures/<ID>.json`: the request, the response, the status and the error, with the key
  replaced by `[REDACTED]` should a provider echo it. A test scans `fixtures/` for key patterns.
- Recordings are keyed by document id, not by a prompt hash. That is what lets a changed catalog replay against the
  same recordings: fields the recording lacks come out `unknown` with "Not covered by the model answer", and values
  failing the new rules come out `unknown` with the rule.
- A test runs replay and compares the result with the committed `out/` as JSON.

**Duplicates.** Two cases from different documents are compared field by field on everything both extracted, outside
the `report` section (two reporters of one case naturally differ in reporter and report date). They are the same case
when at least 6 fields are comparable and at least 75% agree. Two reporters still disagree on judgement fields such as
onset and seriousness: S01 and S02 agree on 85%. Unrelated cases that share a drug, a reaction and even an age agree on
about 60%: D01 and S04 are at 62%. The thresholds are configuration.

**Review state lives apart from outputs.** `PATCH` writes `reviews/<ID>.json`, so `out/` stays exactly what replay
produces. Edited values are validated against the catalog like model values.

**UI.**
- The reviewer's main question is "what should I look at?". Each field row has a left marker:
  - red: conflict or low confidence
  - amber: medium confidence or rejected
  - dashed grey: unknown
  - none: high confidence or reviewed
- A tally and a "needs attention" filter (`F`) narrow the list.
- Unknown and conflict are shown as statuses, not values.
- Confidence is a three-bar meter.
- Selecting a field highlights all its quotes and scrolls to the first. Every quote of the case is faintly marked, so a
  click on the text selects the field.
- Accept and reject advance to the next field, so a reviewer can work with `J`, `A` and `R` alone.

## What is missing or weak

- S03 is not read; there is no vision model.
- There is no second "verification" pass where the model checks its own answer. Calibration relies on the prompt and
  the resolver's rules.
- `iron`, `multivitamin` and similar items have no generic name; the guidance asks for generic names, so the model may
  list them or leave them out.
- `event_terms` is the weakest field (see the checker section): the model mixes symptoms with diagnoses and keeps
  long wording.
- The duplicate threshold was set after seeing this run's agreement scores. The gap is wide (0.62 vs 0.85), but it
  rests on one positive pair.

## AI tools and how I used them

- **GitHub Copilot (agent mode, VS Code):**
  - read the run's output and the checker report, and set the duplicate threshold from the measured agreement scores;
  - read the brief, catalog, schemas, checker and all twelve documents, and proposed a plan, which I reviewed;
  - generated most of the backend, frontend and tests;
  - debugged with me: the empty `command:` key that made `@ConditionalOnProperty` start the pipeline inside tests,
    the lazy bean that was still created eagerly, and the model that now answers 404.
- I chose the provider, the layout (inputs stay in `case-intake-exercise/`), the single-jar start, plain CSS over a
  component library, and the stretch items.

### Prompt used for the UI design

> Design a review screen for pharmacovigilance case intake. A reviewer checks values a language model extracted from a
> report against the report itself, and must work fast and never miss a risky value. Layout: a dark left rail with the
> document list (id, title, case count, flags, review progress); the main area split into the document text (monospace,
> it has tables) and the fields grouped by catalog section. Selecting a field highlights its quotes in the text and
> scrolls to them; clicking a highlight selects its field. Make risk visible at a glance: conflict and low confidence
> stand out, then medium confidence and unknown, while high-confidence and reviewed fields recede. Unknown and conflict
> are statuses, not values. Confidence is a small three-bar meter. Offer a "needs attention" filter and a tally. Review
> actions are accept, reject and edit, with editors chosen by field type (radio pills for coded, checkbox chips for
> multi, date picker, number with range, tag input for lists); edited values look different from model values. Support
> keyboard review (J/K, A/R/E, [ ] for cases, ? for help). A document with no case, or an unreadable one, should look
> intentional and explain why. Use a calm clinical palette with design tokens, tabular numerals, restrained motion that
> respects reduced-motion settings, and no component library.
