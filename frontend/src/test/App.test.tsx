import { render, screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import App from '../App'
import type { DocumentDetail } from '../api/types'
import { catalog, detail, summaries, text } from './fixtures'

const noCase: DocumentDetail = {
  ...detail,
  id: 'T02',
  title: 'A complaint',
  text: 'The capsules were discoloured. Nobody took them.',
  extraction: { document_id: 'T02', catalog_version: 'test', flags: ['no_adverse_event', 'No patient took the product.'], cases: [] },
  progress: { reviewed: 0, total: 0 },
}

function respond(body: unknown, status = 200) {
  return Promise.resolve(new Response(status === 204 ? null : JSON.stringify(body), { status, headers: { 'Content-Type': 'application/json' } }))
}

let fetchMock: ReturnType<typeof vi.fn>

beforeEach(() => {
  window.history.pushState(null, '', '/')
  fetchMock = vi.fn((input: RequestInfo | URL, init?: RequestInit) => {
    const url = String(input)
    if (url === '/api/catalog') return respond(catalog)
    if (url === '/api/documents') return respond(summaries)
    if (url === '/api/documents/T01') return respond(detail)
    if (url === '/api/documents/T02') return respond(noCase)
    if (url.startsWith('/api/documents/T01/cases/0/fields/') && init?.method === 'PATCH') {
      const body = JSON.parse(String(init.body)) as { decision: string }
      return respond({ decision: body.decision, reviewedAt: '2026-01-01T00:00:00Z' })
    }
    return respond({ detail: 'not found' }, 404)
  })
  vi.stubGlobal('fetch', fetchMock)
})

afterEach(() => vi.unstubAllGlobals())

describe('App', () => {
  it('opens the first document with its text and fields side by side', async () => {
    render(<App />)
    expect(await screen.findByRole('heading', { name: 'A test report' })).toBeInTheDocument()
    expect(screen.getByLabelText('Document text')).toHaveTextContent(text.slice(0, 20))
    expect(screen.getByText('Alpha code')).toBeInTheDocument()
    expect(window.location.pathname).toBe('/documents/T01')
  })

  it('highlights the quote of the selected field, and a click on a quote selects its field', async () => {
    render(<App />)
    await userEvent.click(await screen.findByText('Gamma kind'))
    const active = document.querySelectorAll('mark.is-active')
    expect(Array.from(active).map((m) => m.textContent)).toEqual(['Second kind'])

    await userEvent.click(screen.getByText('4 March 2026'))
    expect(screen.getByText('Delta day').closest('.field')).toHaveClass('is-selected')
  })

  it('reviews with the keyboard: next field, accept, and progress follows', async () => {
    render(<App />)
    await screen.findByRole('heading', { name: 'A test report' })
    await userEvent.keyboard('j')
    expect(screen.getByText('Alpha code').closest('.field')).toHaveClass('is-selected')
    await userEvent.keyboard('a')
    await waitFor(() => expect(screen.getByText('Accepted')).toBeInTheDocument())
    expect(fetchMock).toHaveBeenCalledWith(
      '/api/documents/T01/cases/0/fields/alpha',
      expect.objectContaining({ method: 'PATCH', body: JSON.stringify({ decision: 'accepted', value: null }) }),
    )
    expect(screen.getByText('Beta count').closest('.field')).toHaveClass('is-selected')
    expect(screen.getByText('1 of 6 reviewed')).toBeInTheDocument()
  })

  it('presents a document with no case as intentional, with the reason', async () => {
    render(<App />)
    await userEvent.click(await screen.findByText('A complaint'))
    expect(await screen.findByRole('heading', { name: 'No case in this document' })).toBeInTheDocument()
    expect(screen.getByText('No patient took the product.')).toBeInTheDocument()
  })

  it('shows an error with a retry when the backend is down', async () => {
    fetchMock.mockImplementation(() => Promise.reject(new TypeError('network')))
    render(<App />)
    expect((await screen.findAllByRole('alert'))[0]).toHaveTextContent('cannot be reached')
    expect(screen.getAllByRole('button', { name: 'Try again' }).length).toBeGreaterThan(0)
  })
})
