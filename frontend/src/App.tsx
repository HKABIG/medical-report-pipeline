import { useCallback, useEffect } from 'react'
import { api } from './api/client'
import type { ReviewProgress } from './api/types'
import { DocumentList } from './components/DocumentList'
import { EmptyState, ErrorState, LoadingState } from './components/States'
import { documentPath, useRoute } from './hooks/useRoute'
import { useResource } from './hooks/useResource'
import { DocumentWorkspace } from './views/DocumentWorkspace'
import { DuplicatesView } from './views/DuplicatesView'

export default function App() {
  const { route, navigate } = useRoute()
  const catalog = useResource('catalog', api.catalog)
  const documents = useResource('documents', api.documents)
  const setDocuments = documents.setData

  const firstId = documents.resource.status === 'ready' ? documents.resource.data[0]?.id : undefined
  useEffect(() => {
    if (route.view === 'home' && firstId) navigate(documentPath(firstId))
  }, [route.view, firstId, navigate])

  const onProgress = useCallback(
    (id: string, progress: ReviewProgress) =>
      setDocuments((list) => list.map((doc) => (doc.id === id ? { ...doc, progress } : doc))),
    [setDocuments],
  )

  const selectedId = route.view === 'document' ? route.id : undefined

  return (
    <div className="app">
      <aside className="rail">
        <div className="rail__brand">
          <span className="rail__logo" aria-hidden="true">◆</span>
          <div>
            <p className="rail__name">Case intake</p>
            <p className="rail__tag">Reviewer workspace</p>
          </div>
        </div>
        <nav className="rail__nav" aria-label="Views">
          <button type="button" className={`rail__link${route.view !== 'duplicates' ? ' is-active' : ''}`}
            onClick={() => {
              const id = selectedId ?? firstId
              if (id) navigate(documentPath(id))
            }}>
            Documents
          </button>
          <button type="button" className={`rail__link${route.view === 'duplicates' ? ' is-active' : ''}`}
            onClick={() => navigate('/duplicates')}>
            Duplicates
          </button>
        </nav>
        <div className="rail__list">
          {documents.resource.status === 'loading' && <LoadingState label="Loading documents…" />}
          {documents.resource.status === 'error' && <ErrorState error={documents.resource.error} onRetry={documents.reload} />}
          {documents.resource.status === 'ready' && (
            <DocumentList documents={documents.resource.data} selectedId={selectedId} onSelect={(id) => navigate(documentPath(id))} />
          )}
        </div>
      </aside>

      <main className="main">
        {catalog.resource.status === 'loading' && <LoadingState label="Loading the field catalog…" />}
        {catalog.resource.status === 'error' && <ErrorState error={catalog.resource.error} onRetry={catalog.reload} />}
        {catalog.resource.status === 'ready' && route.view === 'document' && (
          <DocumentWorkspace key={route.id} id={route.id} catalog={catalog.resource.data} onProgress={onProgress} />
        )}
        {catalog.resource.status === 'ready' && route.view === 'duplicates' && (
          <DuplicatesView catalog={catalog.resource.data} groupIndex={route.group} navigate={navigate} />
        )}
        {catalog.resource.status === 'ready' && route.view === 'home' && documents.resource.status === 'ready' && !firstId && (
          <EmptyState title="Nothing to review yet">
            <p>There are no extraction outputs. Run <code>./replay.sh</code> to build them from the recordings.</p>
          </EmptyState>
        )}
      </main>
    </div>
  )
}
