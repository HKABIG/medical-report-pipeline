import { useEffect, useRef } from 'react'
import type { CatalogField, FieldResult, FieldReview, FieldValue, ReviewDecision } from '../api/types'
import { attentionOf, effectiveValue } from '../catalog/catalog'
import { FieldEditor } from './FieldEditor'
import { FieldValueView } from './FieldValueView'
import { ConfidenceMeter, DecisionBadge, StatusPill } from './Indicators'

interface Props {
  field: CatalogField
  result: FieldResult | undefined
  review: FieldReview | undefined
  statusDescriptions: Record<string, string>
  selected: boolean
  editing: boolean
  saving: boolean
  onSelect: () => void
  onDecision: (decision: ReviewDecision | 'pending', value?: FieldValue) => void
  onEdit: (editing: boolean) => void
}

export function FieldRow({ field, result, review, statusDescriptions, selected, editing, saving, onSelect, onDecision, onEdit }: Props) {
  const row = useRef<HTMLLIElement>(null)
  const status = result?.status ?? 'unknown'
  const value = effectiveValue(result, review)
  const attention = attentionOf(result, review)

  useEffect(() => {
    if (selected && row.current && typeof row.current.scrollIntoView === 'function') {
      row.current.scrollIntoView({ block: 'nearest' })
    }
  }, [selected])

  return (
    <li ref={row} className={`field field--${status} attention--${attention}${selected ? ' is-selected' : ''}`}>
      <button type="button" className="field__summary" aria-expanded={selected} onClick={onSelect}>
        <span className="field__marker" aria-hidden="true" />
        <span className="field__label">{field.label}</span>
        <span className={`field__value${review?.decision === 'edited' ? ' is-edited' : ''}`}>
          {value !== undefined ? (
            <FieldValueView field={field} value={value} />
          ) : (
            <span className="value value--missing">{status === 'conflict' ? 'Conflicting values' : 'Not stated'}</span>
          )}
        </span>
        <span className="field__meta">
          {status === 'extracted' && result?.confidence && review?.decision !== 'edited' ? (
            <ConfidenceMeter level={result.confidence} />
          ) : (
            <StatusPill status={status} description={statusDescriptions[status]} />
          )}
          <DecisionBadge review={review} />
        </span>
      </button>

      {selected && (
        <div className="field__detail">
          {review?.decision === 'edited' && result?.status === 'extracted' && result.value !== undefined && (
            <p className="field__original">
              Model value: <FieldValueView field={field} value={result.value} />
            </p>
          )}
          {result?.note && <p className="field__note">{result.note}</p>}
          {result?.provenance && result.provenance.length > 0 && (
            <ul className="field__quotes" aria-label="Quotes">
              {result.provenance.map((quote) => (
                <li key={`${quote.start}-${quote.end}`}>
                  <q>{quote.quote}</q>
                  <span className="field__pos">
                    {quote.page !== undefined ? `p. ${quote.page} · ` : ''}
                    {quote.start}–{quote.end}
                  </span>
                </li>
              ))}
            </ul>
          )}
          {!result?.provenance?.length && status === 'unknown' && (
            <p className="field__note field__note--muted">The document does not give this value.</p>
          )}

          {editing ? (
            <FieldEditor
              field={field}
              initial={value}
              saving={saving}
              onSave={(edited) => onDecision('edited', edited)}
              onCancel={() => onEdit(false)}
            />
          ) : (
            <div className="field__actions">
              {status === 'extracted' && (
                <button type="button" className="button button--accept" disabled={saving} onClick={() => onDecision('accepted')}>
                  Accept <kbd>A</kbd>
                </button>
              )}
              <button type="button" className="button button--reject" disabled={saving} onClick={() => onDecision('rejected')}>
                Reject <kbd>R</kbd>
              </button>
              <button type="button" className="button" disabled={saving} onClick={() => onEdit(true)}>
                {value === undefined ? 'Enter value' : 'Edit'} <kbd>E</kbd>
              </button>
              {review && (
                <button type="button" className="button button--ghost" disabled={saving} onClick={() => onDecision('pending')}>
                  Undo
                </button>
              )}
            </div>
          )}
        </div>
      )}
    </li>
  )
}
