import { useCallback, useEffect, useState } from 'react'

export type Resource<T> =
  | { status: 'loading' }
  | { status: 'error'; error: Error }
  | { status: 'ready'; data: T }

/** Loads data when the key changes; reload() fetches again, setData() applies a local update. */
export function useResource<T>(key: string, load: () => Promise<T>) {
  const [state, setState] = useState<{ key: string; resource: Resource<T> }>({ key, resource: { status: 'loading' } })
  const [attempt, setAttempt] = useState(0)

  useEffect(() => {
    let cancelled = false
    load().then(
      (data) => !cancelled && setState({ key, resource: { status: 'ready', data } }),
      (error: unknown) =>
        !cancelled &&
        setState({ key, resource: { status: 'error', error: error instanceof Error ? error : new Error(String(error)) } }),
    )
    return () => {
      cancelled = true
    }
    // load is recreated on every render; the key says when the data changes.
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [key, attempt])

  const reload = useCallback(() => {
    setState({ key, resource: { status: 'loading' } })
    setAttempt((n) => n + 1)
  }, [key])

  const setData = useCallback(
    (update: (data: T) => T) =>
      setState((current) =>
        current.resource.status === 'ready'
          ? { key: current.key, resource: { status: 'ready', data: update(current.resource.data) } }
          : current,
      ),
    [],
  )

  const resource: Resource<T> = state.key === key ? state.resource : { status: 'loading' }
  return { resource, reload, setData }
}
