import { render, screen, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { describe, expect, it, vi } from 'vitest'
import { FieldPanel } from '../components/FieldPanel'
import { catalog, detail } from './fixtures'

const caseResult = detail.extraction.cases[0]

function renderPanel(overrides: Partial<Parameters<typeof FieldPanel>[0]> = {}) {
  const props = {
    catalog,
    caseResult,
    reviews: {},
    attentionOnly: false,
    onToggleAttention: vi.fn(),
    onSelect: vi.fn(),
    onEdit: vi.fn(),
    onDecision: vi.fn(),
    ...overrides,
  }
  return { props, ...render(<FieldPanel {...props} />) }
}

describe('FieldPanel', () => {
  it('renders sections, labels and values from the catalog', () => {
    renderPanel()
    expect(screen.getByRole('heading', { name: 'Who' })).toBeInTheDocument()
    expect(screen.getByRole('heading', { name: 'What happened' })).toBeInTheDocument()
    expect(screen.queryByRole('heading', { name: 'Unused section' })).not.toBeInTheDocument()
    expect(screen.getByText('Second kind')).toBeInTheDocument()
    expect(screen.getByText('4 Mar 2026')).toBeInTheDocument()
    expect(screen.getByText('Conflicting values')).toBeInTheDocument()
    expect(screen.getByText('1 conflict')).toBeInTheDocument()
    expect(screen.getByText('2 not high')).toBeInTheDocument()
  })

  it('shows only risky fields when filtering', () => {
    renderPanel({ attentionOnly: true })
    expect(screen.queryByText('Alpha code')).not.toBeInTheDocument()
    expect(screen.getByText('Beta count')).toBeInTheDocument()
    expect(screen.getByText('Epsilon set')).toBeInTheDocument()
    expect(screen.queryByText('Zeta items')).not.toBeInTheDocument()
  })

  it.each([
    ['Delta day', (row: HTMLElement) => expect(row.querySelector('input[type="date"]')).not.toBeNull()],
    ['Gamma kind', (row: HTMLElement) => expect(within(row).getAllByRole('radio')).toHaveLength(2)],
    ['Epsilon set', (row: HTMLElement) => expect(within(row).getAllByRole('checkbox')).toHaveLength(2)],
    ['Beta count', (row: HTMLElement) => expect(within(row).getByRole('spinbutton')).toHaveAttribute('max', '9')],
  ])('edits %s with the control its catalog type calls for', (label, check) => {
    const field = catalog.fields.find((f) => f.label === label)!
    renderPanel({ selectedFieldId: field.id, editingFieldId: field.id })
    check(screen.getByRole('form', { name: `Edit ${label}` }))
  })

  it('sends accept and reject for the selected field', async () => {
    const field = catalog.fields.find((f) => f.label === 'Gamma kind')!
    const { props } = renderPanel({ selectedFieldId: field.id })
    await userEvent.click(screen.getByRole('button', { name: /Accept/ }))
    await userEvent.click(screen.getByRole('button', { name: /Reject/ }))
    expect(props.onDecision).toHaveBeenNthCalledWith(1, field.id, 'accepted', undefined)
    expect(props.onDecision).toHaveBeenNthCalledWith(2, field.id, 'rejected', undefined)
  })

  it('marks an edited value apart from the model value', () => {
    const field = catalog.fields.find((f) => f.label === 'Gamma kind')!
    renderPanel({
      selectedFieldId: field.id,
      reviews: { [field.id]: { decision: 'edited', value: 'g1', reviewedAt: '2026-01-01T00:00:00Z' } },
    })
    expect(screen.getByText('Edited')).toBeInTheDocument()
    expect(screen.getByText('First kind').closest('.field__value')).toHaveClass('is-edited')
    expect(screen.getByText(/Model value/)).toHaveTextContent('Second kind')
  })
})
