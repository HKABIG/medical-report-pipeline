import { useCallback, useEffect, useState } from 'react'

export type Route =
  | { view: 'home' }
  | { view: 'document'; id: string }
  | { view: 'duplicates'; group?: number }

export function parseRoute(path: string): Route {
  const document = path.match(/^\/documents\/([^/]+)\/?$/)
  if (document) return { view: 'document', id: decodeURIComponent(document[1]) }
  const duplicates = path.match(/^\/duplicates(?:\/(\d+))?\/?$/)
  if (duplicates) return { view: 'duplicates', group: duplicates[1] ? Number(duplicates[1]) : undefined }
  return { view: 'home' }
}

export function documentPath(id: string): string {
  return `/documents/${encodeURIComponent(id)}`
}

/** A tiny history-API router: three views do not need a routing library. */
export function useRoute() {
  const [path, setPath] = useState(() => window.location.pathname)

  useEffect(() => {
    const onPop = () => setPath(window.location.pathname)
    window.addEventListener('popstate', onPop)
    return () => window.removeEventListener('popstate', onPop)
  }, [])

  const navigate = useCallback((to: string) => {
    if (to !== window.location.pathname) {
      window.history.pushState(null, '', to)
      setPath(to)
    }
  }, [])

  return { route: parseRoute(path), navigate }
}
