import type { Catalog, ExtractionCase, FieldReview, FieldValue, ReviewDecision } from '../api/types'
import { groupBySection, needsAttention } from '../catalog/catalog'
import { FieldRow } from './FieldRow'

interface Props {
  catalog: Catalog
  caseResult: ExtractionCase
  reviews: Record<string, FieldReview>
  selectedFieldId?: string
  editingFieldId?: string
  savingFieldId?: string
  attentionOnly: boolean
  onToggleAttention: () => void
  onSelect: (fieldId: string) => void
  onEdit: (fieldId: string | undefined) => void
  onDecision: (fieldId: string, decision: ReviewDecision | 'pending', value?: FieldValue) => void
}

export function FieldPanel(props: Props) {
  const { catalog, caseResult, reviews, attentionOnly } = props
  const results = catalog.fields.map((field) => caseResult.fields[field.id])
  const counts = {
    conflict: results.filter((r) => r?.status === 'conflict').length,
    unknown: results.filter((r) => !r || r.status === 'unknown').length,
    lowMedium: results.filter((r) => r?.status === 'extracted' && r.confidence !== 'high').length,
    attention: catalog.fields.filter((f) => needsAttention(caseResult.fields[f.id], reviews[f.id])).length,
  }

  return (
    <div className="panel">
      <div className="panel__toolbar">
        <div className="tally" aria-label="Field summary">
          <span className="tally__item tally__item--conflict">{counts.conflict} conflict</span>
          <span className="tally__item tally__item--uncertain">{counts.lowMedium} not high</span>
          <span className="tally__item tally__item--unknown">{counts.unknown} unknown</span>
        </div>
        <label className="toggle">
          <input type="checkbox" checked={attentionOnly} onChange={props.onToggleAttention} />
          <span>Needs attention ({counts.attention})</span>
          <kbd>F</kbd>
        </label>
      </div>

      <div className="panel__sections">
        {groupBySection(catalog).map(({ section, fields }) => {
          const visible = attentionOnly
            ? fields.filter((f) => needsAttention(caseResult.fields[f.id], reviews[f.id]) || f.id === props.selectedFieldId)
            : fields
          if (visible.length === 0) return null
          return (
            <section key={section.id} className="section" aria-labelledby={`section-${section.id}`}>
              <h3 className="section__title" id={`section-${section.id}`}>
                {section.label}
              </h3>
              <ul className="section__fields">
                {visible.map((field) => (
                  <FieldRow
                    key={field.id}
                    field={field}
                    result={caseResult.fields[field.id]}
                    review={reviews[field.id]}
                    statusDescriptions={catalog.statuses}
                    selected={field.id === props.selectedFieldId}
                    editing={field.id === props.editingFieldId}
                    saving={field.id === props.savingFieldId}
                    onSelect={() => props.onSelect(field.id)}
                    onEdit={(editing) => props.onEdit(editing ? field.id : undefined)}
                    onDecision={(decision, value) => props.onDecision(field.id, decision, value)}
                  />
                ))}
              </ul>
            </section>
          )
        })}
        {attentionOnly && counts.attention === 0 && (
          <p className="panel__done">Every field in this case is high confidence or reviewed.</p>
        )}
      </div>
    </div>
  )
}
