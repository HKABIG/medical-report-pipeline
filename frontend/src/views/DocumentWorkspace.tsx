import { useEffect, useMemo, useState } from 'react'
import { api } from '../api/client'
import type {
  Catalog,
  DocumentDetail,
  FieldReview,
  FieldValue,
  ReviewDecision,
  ReviewProgress,
} from '../api/types'
import { needsAttention, orderedFields } from '../catalog/catalog'
import type { Highlight } from '../catalog/segments'
import { DocumentText } from '../components/DocumentText'
import { FieldPanel } from '../components/FieldPanel'
import { FlagChips, FlagNotes } from '../components/Flags'
import { ProgressBar } from '../components/Indicators'
import { ShortcutHelp } from '../components/ShortcutHelp'
import { EmptyState, ErrorState, LoadingState } from '../components/States'
import { useHotkeys } from '../hooks/useHotkeys'
import { useResource } from '../hooks/useResource'

interface Props {
  id: string
  catalog: Catalog
  onProgress: (id: string, progress: ReviewProgress) => void
}

export function DocumentWorkspace({ id, catalog, onProgress }: Props) {
  const { resource, reload, setData } = useResource(`document:${id}`, () => api.document(id))
  const progress = resource.status === 'ready' ? resource.data.progress : undefined

  useEffect(() => {
    if (progress) onProgress(id, progress)
  }, [id, progress, onProgress])

  if (resource.status === 'loading') return <LoadingState label={`Loading ${id}…`} />
  if (resource.status === 'error') return <ErrorState error={resource.error} onRetry={reload} />
  return <Workspace detail={resource.data} catalog={catalog} onChange={setData} />
}

function Workspace({ detail, catalog, onChange }: {
  detail: DocumentDetail
  catalog: Catalog
  onChange: (update: (d: DocumentDetail) => DocumentDetail) => void
}) {
  const cases = detail.extraction.cases
  const [caseIndex, setCaseIndex] = useState(0)
  const [selectedFieldId, setSelectedFieldId] = useState<string>()
  const [editingFieldId, setEditingFieldId] = useState<string>()
  const [savingFieldId, setSavingFieldId] = useState<string>()
  const [attentionOnly, setAttentionOnly] = useState(false)
  const [helpOpen, setHelpOpen] = useState(false)
  const [error, setError] = useState<string>()

  const current = cases[caseIndex]
  const reviews: Record<string, FieldReview> = detail.review.cases[String(current?.case_index ?? caseIndex)] ?? {}
  const fields = orderedFields(catalog)
  const visibleFields = attentionOnly
    ? fields.filter((f) => needsAttention(current?.fields[f.id], reviews[f.id]) || f.id === selectedFieldId)
    : fields

  const highlights = useMemo<Highlight[]>(
    () =>
      current
        ? catalog.fields.flatMap((field) =>
            (current.fields[field.id]?.provenance ?? []).map((p) => ({ fieldId: field.id, start: p.start, end: p.end })),
          )
        : [],
    [catalog, current],
  )
  const labelOf = (fieldId: string) => catalog.fields.find((f) => f.id === fieldId)?.label ?? fieldId

  const select = (fieldId: string | undefined) => {
    setSelectedFieldId(fieldId)
    setEditingFieldId(undefined)
    setError(undefined)
  }
  const step = (delta: number) => {
    if (visibleFields.length === 0) return
    const index = visibleFields.findIndex((f) => f.id === selectedFieldId)
    const next = index < 0 ? (delta > 0 ? 0 : visibleFields.length - 1) : (index + delta + visibleFields.length) % visibleFields.length
    select(visibleFields[next].id)
  }
  const switchCase = (index: number) => {
    if (index < 0 || index >= cases.length) return
    setCaseIndex(index)
    select(undefined)
  }

  const decide = async (fieldId: string, decision: ReviewDecision | 'pending', value?: FieldValue) => {
    if (!current) return
    setSavingFieldId(fieldId)
    setError(undefined)
    try {
      const saved = await api.review(detail.id, current.case_index, fieldId, decision, value)
      onChange((d) => {
        const key = String(current.case_index)
        const caseReviews = { ...(d.review.cases[key] ?? {}) }
        if (saved) caseReviews[fieldId] = saved
        else delete caseReviews[fieldId]
        const allCases = { ...d.review.cases, [key]: caseReviews }
        const reviewed = Object.values(allCases).reduce((sum, c) => sum + Object.keys(c).length, 0)
        return { ...d, review: { cases: allCases }, progress: { ...d.progress, reviewed } }
      })
      setEditingFieldId(undefined)
      if (decision === 'accepted' || decision === 'rejected' || decision === 'edited') step(1)
    } catch (e) {
      setError(e instanceof Error ? e.message : String(e))
    } finally {
      setSavingFieldId(undefined)
    }
  }

  const selectedResult = selectedFieldId ? current?.fields[selectedFieldId] : undefined
  useHotkeys(
    {
      j: () => step(1),
      ArrowDown: () => step(1),
      k: () => step(-1),
      ArrowUp: () => step(-1),
      a: () => selectedFieldId && selectedResult?.status === 'extracted' && decide(selectedFieldId, 'accepted'),
      r: () => selectedFieldId && decide(selectedFieldId, 'rejected'),
      e: () => selectedFieldId && setEditingFieldId(selectedFieldId),
      u: () => selectedFieldId && reviews[selectedFieldId] && decide(selectedFieldId, 'pending'),
      f: () => setAttentionOnly((on) => !on),
      '[': () => switchCase(caseIndex - 1),
      ']': () => switchCase(caseIndex + 1),
      '?': () => setHelpOpen(true),
      Escape: () => (helpOpen ? setHelpOpen(false) : editingFieldId ? setEditingFieldId(undefined) : select(undefined)),
    },
    savingFieldId === undefined,
  )

  const flags = detail.extraction.flags ?? []

  return (
    <div className="workspace">
      <header className="workspace__header">
        <div className="workspace__heading">
          <p className="eyebrow">
            {detail.id}
            {detail.extraction.model && <span className="eyebrow__muted"> · {detail.extraction.model}</span>}
          </p>
          <h1 className="workspace__title">{detail.title}</h1>
          <FlagChips flags={flags} />
        </div>
        <div className="workspace__aside">
          {detail.progress.total > 0 && <ProgressBar progress={detail.progress} />}
          <button type="button" className="button button--ghost button--small" onClick={() => setHelpOpen(true)}>
            Shortcuts <kbd>?</kbd>
          </button>
        </div>
      </header>

      {cases.length > 1 && (
        <nav className="case-tabs" aria-label="Cases in this document">
          {cases.map((c, index) => (
            <button
              key={c.case_index}
              type="button"
              className={`case-tabs__tab${index === caseIndex ? ' is-active' : ''}`}
              aria-pressed={index === caseIndex}
              onClick={() => switchCase(index)}
            >
              <span className="case-tabs__index">Case {index + 1}</span>
              {c.label && <span className="case-tabs__label">{c.label}</span>}
            </button>
          ))}
          <span className="case-tabs__hint"><kbd>[</kbd> <kbd>]</kbd></span>
        </nav>
      )}

      {detail.format === 'IMAGE_ONLY_PDF' || detail.text === null ? (
        <EmptyState title="This document was not read" icon="◫">
          <p>It exists only as an image-only PDF, so there is no text to extract from or to quote.</p>
          <FlagNotes flags={flags} />
        </EmptyState>
      ) : (
        <div className="split">
          <section className="split__pane split__pane--document" aria-label="Document text">
            <DocumentText
              text={detail.text}
              highlights={highlights}
              selectedFieldId={selectedFieldId}
              labelOf={labelOf}
              onSelectField={select}
            />
          </section>
          <section className="split__pane split__pane--fields" aria-label="Extracted fields">
            {error && (
              <p className="inline-error" role="alert">
                {error}
              </p>
            )}
            {current ? (
              <>
                <FlagNotes flags={flags} />
                <FieldPanel
                  catalog={catalog}
                  caseResult={current}
                  reviews={reviews}
                  selectedFieldId={selectedFieldId}
                  editingFieldId={editingFieldId}
                  savingFieldId={savingFieldId}
                  attentionOnly={attentionOnly}
                  onToggleAttention={() => setAttentionOnly((on) => !on)}
                  onSelect={(fieldId) => select(fieldId === selectedFieldId ? undefined : fieldId)}
                  onEdit={setEditingFieldId}
                  onDecision={decide}
                />
              </>
            ) : (
              <EmptyState title="No case in this document" icon="∅">
                <p>The pipeline found no patient with an adverse event, so there is nothing to review here.</p>
                <FlagNotes flags={flags} />
              </EmptyState>
            )}
          </section>
        </div>
      )}
      {helpOpen && <ShortcutHelp onClose={() => setHelpOpen(false)} />}
    </div>
  )
}
