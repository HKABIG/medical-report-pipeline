import type { CatalogField, FieldValue } from '../api/types'

const ISO_DATE = /^\d{4}-\d{2}-\d{2}$/

/** Mirrors the backend's catalog validation so the editor can explain a problem before saving. */
export function validateValue(field: CatalogField, value: FieldValue | undefined): string | null {
  if (value === undefined || value === '') return 'A value is required.'
  const rules = field.validation ?? {}
  switch (field.type) {
    case 'string':
      if (typeof value !== 'string') return 'Text expected.'
      if (rules.pattern && !new RegExp(rules.pattern).test(value)) return `Must match ${rules.pattern}.`
      return null
    case 'integer':
    case 'number':
      if (typeof value !== 'number' || Number.isNaN(value)) return 'A number is required.'
      if (field.type === 'integer' && !Number.isInteger(value)) return 'A whole number is required.'
      if (rules.min !== undefined && value < rules.min) return `Must be at least ${rules.min}.`
      if (rules.max !== undefined && value > rules.max) return `Must be at most ${rules.max}.`
      return null
    case 'date':
      return typeof value === 'string' && ISO_DATE.test(value) ? null : 'A date is required.'
    case 'coded':
      return field.options?.some((option) => option.code === value) ? null : 'Choose an option.'
    case 'coded_multi':
      return Array.isArray(value) && value.length > 0 ? null : 'Choose at least one option.'
    case 'string_list':
      return Array.isArray(value) ? null : 'A list is required.'
  }
}
