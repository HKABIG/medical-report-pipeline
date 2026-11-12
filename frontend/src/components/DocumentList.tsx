import type { DocumentSummary } from '../api/types'
import { FlagChips } from './Flags'
import { ProgressBar } from './Indicators'

interface Props {
  documents: DocumentSummary[]
  selectedId?: string
  onSelect: (id: string) => void
}

export function DocumentList({ documents, selectedId, onSelect }: Props) {
  if (documents.length === 0) {
    return <p className="rail__empty">No outputs yet. Run the pipeline or ./replay.sh to create them.</p>
  }
  return (
    <ul className="doc-list">
      {documents.map((doc) => {
        const caseText = doc.caseCount === 0 ? 'No case' : doc.caseCount === 1 ? '1 case' : `${doc.caseCount} cases`
        return (
          <li key={doc.id}>
            <button
              type="button"
              className={`doc-list__item${doc.id === selectedId ? ' is-selected' : ''}`}
              aria-current={doc.id === selectedId ? 'page' : undefined}
              onClick={() => onSelect(doc.id)}
            >
              <span className="doc-list__head">
                <span className="doc-list__id">{doc.id}</span>
                <span className={`doc-list__cases${doc.caseCount === 0 ? ' is-none' : ''}`}>{caseText}</span>
              </span>
              <span className="doc-list__title">{doc.title}</span>
              <FlagChips flags={doc.flags} />
              {doc.progress.total > 0 && <ProgressBar progress={doc.progress} compact />}
            </button>
          </li>
        )
      })}
    </ul>
  )
}
