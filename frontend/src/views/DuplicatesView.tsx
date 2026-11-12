import { api } from '../api/client'
import type { Catalog, CaseComparison, DocumentDetail, DuplicateGroup, Outcome } from '../api/types'
import { effectiveValue, groupBySection } from '../catalog/catalog'
import { FieldValueView } from '../components/FieldValueView'
import { EmptyState, ErrorState, LoadingState } from '../components/States'
import { documentPath } from '../hooks/useRoute'
import { useResource } from '../hooks/useResource'

interface Props {
  catalog: Catalog
  groupIndex?: number
  navigate: (path: string) => void
}

export function DuplicatesView({ catalog, groupIndex, navigate }: Props) {
  const { resource, reload } = useResource('duplicates', api.duplicates)
  if (resource.status === 'loading') return <LoadingState label="Loading duplicate groups…" />
  if (resource.status === 'error') return <ErrorState error={resource.error} onRetry={reload} />
  const groups = resource.data
  if (groups.length === 0) {
    return (
      <EmptyState title="No duplicates found" icon="≡">
        <p>No two documents describe the same patient, drug and reaction.</p>
      </EmptyState>
    )
  }
  const index = groupIndex !== undefined && groupIndex < groups.length ? groupIndex : 0
  const group = groups[index]
  return (
    <div className="workspace">
      <header className="workspace__header">
        <div className="workspace__heading">
          <p className="eyebrow">Possible duplicates</p>
          <h1 className="workspace__title">{group.documentIds.join(' and ')} describe the same case</h1>
          {group.reason && <p className="workspace__subtitle">{group.reason}</p>}
        </div>
        {groups.length > 1 && (
          <nav className="case-tabs" aria-label="Duplicate groups">
            {groups.map((g, i) => (
              <button key={g.documentIds.join()} type="button" className={`case-tabs__tab${i === index ? ' is-active' : ''}`}
                onClick={() => navigate(`/duplicates/${i}`)}>
                {g.documentIds.join(' · ')}
              </button>
            ))}
          </nav>
        )}
      </header>
      {group.comparisons.map((comparison) => (
        <Comparison key={`${comparison.left.documentId}-${comparison.right.documentId}`} catalog={catalog}
          group={group} comparison={comparison} navigate={navigate} />
      ))}
    </div>
  )
}

const OUTCOME_TEXT: Record<Outcome, string> = { MATCH: 'Match', DIFFER: 'Differs', NOT_COMPARABLE: '—' }

function Comparison({ catalog, comparison, navigate }: { catalog: Catalog; group: DuplicateGroup; comparison: CaseComparison; navigate: (path: string) => void }) {
  const key = `${comparison.left.documentId}+${comparison.right.documentId}`
  const { resource, reload } = useResource(key, () =>
    Promise.all([api.document(comparison.left.documentId), api.document(comparison.right.documentId)]),
  )
  if (resource.status === 'loading') return <LoadingState label="Loading both documents…" />
  if (resource.status === 'error') return <ErrorState error={resource.error} onRetry={reload} />
  const [left, right] = resource.data
  const outcomes = new Map(comparison.fields.map((f) => [f.fieldId, f]))
  const count = (outcome: Outcome) => comparison.fields.filter((f) => f.outcome === outcome).length

  return (
    <div className="compare">
      <div className="compare__summary">
        <span className="tally__item tally__item--match">{count('MATCH')} match</span>
        <span className="tally__item tally__item--conflict">{count('DIFFER')} differ</span>
        <span className="tally__item tally__item--unknown">{count('NOT_COMPARABLE')} not comparable</span>
      </div>
      <table className="compare__table">
        <thead>
          <tr>
            <th scope="col">Field</th>
            <DocumentHeading detail={left} navigate={navigate} />
            <th scope="col" className="compare__outcome-col">Comparison</th>
            <DocumentHeading detail={right} navigate={navigate} />
          </tr>
        </thead>
        {groupBySection(catalog).map(({ section, fields }) => (
          <tbody key={section.id}>
            <tr className="compare__section">
              <th colSpan={4} scope="colgroup">{section.label}</th>
            </tr>
            {fields.map((field) => {
              const outcome = outcomes.get(field.id)
              return (
                <tr key={field.id} className={`compare__row compare__row--${outcome?.outcome ?? 'NOT_COMPARABLE'}`}>
                  <th scope="row">
                    {field.label}
                    {outcome && !outcome.counted && <span className="compare__aside">report detail</span>}
                  </th>
                  <ValueCell detail={left} caseIndex={comparison.left.caseIndex} fieldId={field.id} catalog={catalog} />
                  <td className="compare__outcome">{OUTCOME_TEXT[outcome?.outcome ?? 'NOT_COMPARABLE']}</td>
                  <ValueCell detail={right} caseIndex={comparison.right.caseIndex} fieldId={field.id} catalog={catalog} />
                </tr>
              )
            })}
          </tbody>
        ))}
      </table>
    </div>
  )
}

function DocumentHeading({ detail, navigate }: { detail: DocumentDetail; navigate: (path: string) => void }) {
  return (
    <th scope="col">
      <button type="button" className="link" onClick={() => navigate(documentPath(detail.id))}>
        {detail.id}
      </button>
      <span className="compare__doc-title">{detail.title}</span>
    </th>
  )
}

function ValueCell({ detail, caseIndex, fieldId, catalog }: { detail: DocumentDetail; caseIndex: number; fieldId: string; catalog: Catalog }) {
  const field = catalog.fields.find((f) => f.id === fieldId)
  const caseResult = detail.extraction.cases.find((c) => c.case_index === caseIndex)
  const result = caseResult?.fields[fieldId]
  const value = effectiveValue(result, detail.review.cases[String(caseIndex)]?.[fieldId])
  return (
    <td>
      {field && value !== undefined ? (
        <FieldValueView field={field} value={value} />
      ) : (
        <span className="value value--missing">{result?.status === 'conflict' ? 'Conflict' : 'Not stated'}</span>
      )}
    </td>
  )
}
