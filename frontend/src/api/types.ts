/** The catalog as served by GET /api/catalog. Field ids are data, never named in UI code. */
export type FieldType = 'string' | 'integer' | 'number' | 'date' | 'coded' | 'coded_multi' | 'string_list'

export interface CatalogOption {
  code: string
  label: string
}

export interface CatalogField {
  id: string
  label: string
  section: string
  type: FieldType
  options?: CatalogOption[]
  validation?: { pattern?: string; min?: number; max?: number }
  guidance?: string
}

export interface CatalogSection {
  id: string
  label: string
}

export interface Catalog {
  catalog_version: string
  statuses: Record<string, string>
  confidence_levels: string[]
  sections: CatalogSection[]
  fields: CatalogField[]
}

/** The extraction output, as defined by schema/extraction.schema.json. */
export type FieldStatus = 'extracted' | 'unknown' | 'conflict'
export type ConfidenceLevel = 'high' | 'medium' | 'low'
export type FieldValue = string | number | string[]

export interface Provenance {
  quote: string
  start: number
  end: number
  page?: number
}

export interface FieldResult {
  status: FieldStatus
  value?: FieldValue
  confidence?: ConfidenceLevel
  provenance?: Provenance[]
  note?: string
}

export interface ExtractionCase {
  case_index: number
  label?: string
  fields: Record<string, FieldResult>
}

export interface Extraction {
  document_id: string
  catalog_version: string
  model?: string
  flags?: string[]
  cases: ExtractionCase[]
}

/** Review API. */
export type DocumentFormat = 'TEXT' | 'IMAGE_ONLY_PDF'
export type ReviewDecision = 'accepted' | 'rejected' | 'edited'

export interface ReviewProgress {
  reviewed: number
  total: number
}

export interface FieldReview {
  decision: ReviewDecision
  value?: FieldValue
  reviewedAt: string
}

export interface DocumentReview {
  cases: Record<string, Record<string, FieldReview>>
}

export interface DocumentSummary {
  id: string
  title: string
  format: DocumentFormat | null
  caseCount: number
  flags: string[]
  progress: ReviewProgress
}

export interface DocumentDetail {
  id: string
  title: string
  format: DocumentFormat | null
  text: string | null
  extraction: Extraction
  review: DocumentReview
  progress: ReviewProgress
}

export type Outcome = 'MATCH' | 'DIFFER' | 'NOT_COMPARABLE'

export interface CaseRef {
  documentId: string
  caseIndex: number
}

export interface FieldComparison {
  fieldId: string
  outcome: Outcome
  counted: boolean
}

export interface CaseComparison {
  left: CaseRef
  right: CaseRef
  fields: FieldComparison[]
}

export interface DuplicateGroup {
  documentIds: string[]
  confidence: ConfidenceLevel | null
  reason: string | null
  comparisons: CaseComparison[]
}
