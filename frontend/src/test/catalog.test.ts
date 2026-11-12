import { describe, expect, it } from 'vitest'
import { attentionOf, displayItems, groupBySection, orderedFields } from '../catalog/catalog'
import { validateValue } from '../catalog/validate'
import { catalog } from './fixtures'

const field = (label: string) => catalog.fields.find((f) => f.label === label)!

describe('catalog helpers', () => {
  it('groups fields by section in catalog order and drops empty sections', () => {
    expect(groupBySection(catalog).map((g) => [g.section.label, g.fields.length])).toEqual([
      ['Who', 2],
      ['What happened', 4],
    ])
    expect(orderedFields(catalog).map((f) => f.label)[0]).toBe('Alpha code')
  })

  it('shows option labels and written-out dates', () => {
    expect(displayItems(field('Gamma kind'), 'g2')).toEqual(['Second kind'])
    expect(displayItems(field('Epsilon set'), ['e1', 'e2'])).toEqual(['E one', 'E two'])
    expect(displayItems(field('Delta day'), '2026-03-04')).toEqual(['4 Mar 2026'])
  })

  it('ranks conflicts and low confidence above medium, unknown and high', () => {
    expect(attentionOf({ status: 'conflict' }, undefined)).toBe('critical')
    expect(attentionOf({ status: 'extracted', value: 'x', confidence: 'low' }, undefined)).toBe('critical')
    expect(attentionOf({ status: 'extracted', value: 'x', confidence: 'medium' }, undefined)).toBe('warning')
    expect(attentionOf({ status: 'unknown' }, undefined)).toBe('info')
    expect(attentionOf({ status: 'extracted', value: 'x', confidence: 'high' }, undefined)).toBe('none')
    expect(attentionOf({ status: 'conflict' }, { decision: 'edited', value: 'x', reviewedAt: '' })).toBe('none')
  })

  it('validates edits with the catalog rules', () => {
    expect(validateValue(field('Alpha code'), 'abc')).toMatch(/Must match/)
    expect(validateValue(field('Alpha code'), 'AB')).toBeNull()
    expect(validateValue(field('Beta count'), 12)).toMatch(/at most 9/)
    expect(validateValue(field('Beta count'), 2.5)).toMatch(/whole number/)
    expect(validateValue(field('Epsilon set'), [])).toMatch(/at least one/)
    expect(validateValue(field('Zeta items'), [])).toBeNull()
  })
})
