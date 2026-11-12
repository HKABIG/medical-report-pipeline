import type {
  Catalog,
  CatalogField,
  CatalogSection,
  ConfidenceLevel,
  FieldResult,
  FieldReview,
  FieldValue,
} from '../api/types'

export interface SectionGroup {
  section: CatalogSection
  fields: CatalogField[]
}

/** Fields grouped by section, both in catalog order. Sections without fields are left out. */
export function groupBySection(catalog: Catalog): SectionGroup[] {
  return catalog.sections
    .map((section) => ({ section, fields: catalog.fields.filter((field) => field.section === section.id) }))
    .filter((group) => group.fields.length > 0)
}

/** Field order a reviewer walks through: section by section, as the catalog lists them. */
export function orderedFields(catalog: Catalog): CatalogField[] {
  return groupBySection(catalog).flatMap((group) => group.fields)
}

export function optionLabel(field: CatalogField, code: string): string {
  return field.options?.find((option) => option.code === code)?.label ?? code
}

const DATE_FORMAT = new Intl.DateTimeFormat('en-GB', { day: 'numeric', month: 'short', year: 'numeric', timeZone: 'UTC' })

export function formatDate(iso: string): string {
  const date = new Date(`${iso}T00:00:00Z`)
  return Number.isNaN(date.getTime()) ? iso : DATE_FORMAT.format(date)
}

/** A value as a reviewer reads it: option labels instead of codes, dates written out, lists as items. */
export function displayItems(field: CatalogField, value: FieldValue): string[] {
  switch (field.type) {
    case 'coded':
      return [optionLabel(field, String(value))]
    case 'coded_multi':
      return (value as string[]).map((code) => optionLabel(field, code))
    case 'string_list':
      return value as string[]
    case 'date':
      return [formatDate(String(value))]
    default:
      return [String(value)]
  }
}

/** The value the reviewer has settled on: their edit when there is one, else the model's. */
export function effectiveValue(result: FieldResult | undefined, review: FieldReview | undefined): FieldValue | undefined {
  if (review?.decision === 'edited') return review.value
  return result?.status === 'extracted' ? result.value : undefined
}

export type Attention = 'critical' | 'warning' | 'info' | 'none'

/**
 * How much a field needs the reviewer: conflicts and low confidence first, then unknowns and medium confidence.
 * A field the reviewer accepted or edited needs nothing more; a rejected one stays on the list.
 */
export function attentionOf(result: FieldResult | undefined, review: FieldReview | undefined): Attention {
  if (review?.decision === 'accepted' || review?.decision === 'edited') return 'none'
  if (review?.decision === 'rejected') return 'warning'
  if (!result || result.status === 'conflict') return 'critical'
  if (result.status === 'unknown') return 'info'
  const confidence: ConfidenceLevel = result.confidence ?? 'high'
  if (confidence === 'low') return 'critical'
  if (confidence === 'medium') return 'warning'
  return 'none'
}

export function needsAttention(result: FieldResult | undefined, review: FieldReview | undefined): boolean {
  return attentionOf(result, review) !== 'none'
}

/** A snake_case flag code as words; free-text flags pass through unchanged. */
export function flagLabel(flag: string): string {
  return /^[a-z]+(_[a-z]+)*$/.test(flag) ? flag.replace(/_/g, ' ') : flag
}

export function isFlagCode(flag: string): boolean {
  return /^[a-z]+(_[a-z]+)*$/.test(flag)
}
