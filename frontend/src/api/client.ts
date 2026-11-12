import type {
  Catalog,
  DocumentDetail,
  DocumentSummary,
  DuplicateGroup,
  FieldReview,
  FieldValue,
  ReviewDecision,
} from './types'

export class ApiError extends Error {
  readonly status: number

  constructor(status: number, message: string) {
    super(message)
    this.name = 'ApiError'
    this.status = status
  }
}

async function request<T>(path: string, init?: RequestInit): Promise<T> {
  let response: Response
  try {
    response = await fetch(path, {
      ...init,
      headers: { Accept: 'application/json', ...(init?.body ? { 'Content-Type': 'application/json' } : {}) },
    })
  } catch {
    throw new ApiError(0, 'The review service cannot be reached. Is the backend running?')
  }
  if (!response.ok) {
    let detail = response.statusText
    try {
      const problem = (await response.json()) as { detail?: string; title?: string }
      detail = problem.detail ?? problem.title ?? detail
    } catch {
      // The body is not a problem document; the status text is all there is.
    }
    throw new ApiError(response.status, detail || `Request failed (${response.status})`)
  }
  if (response.status === 204) {
    return undefined as T
  }
  return (await response.json()) as T
}

export const api = {
  catalog: () => request<Catalog>('/api/catalog'),
  documents: () => request<DocumentSummary[]>('/api/documents'),
  document: (id: string) => request<DocumentDetail>(`/api/documents/${encodeURIComponent(id)}`),
  duplicates: () => request<{ groups: DuplicateGroup[] }>('/api/duplicates').then((body) => body.groups),
  review: (id: string, caseIndex: number, fieldId: string, decision: ReviewDecision | 'pending', value?: FieldValue) =>
    request<FieldReview | undefined>(
      `/api/documents/${encodeURIComponent(id)}/cases/${caseIndex}/fields/${encodeURIComponent(fieldId)}`,
      { method: 'PATCH', body: JSON.stringify({ decision, value: value ?? null }) },
    ),
}
