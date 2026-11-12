import type { Catalog, DocumentDetail, DocumentSummary } from '../api/types'

/** A made-up catalog: the UI must render any catalog, not only the committed one. */
export const catalog: Catalog = {
  catalog_version: 'test',
  statuses: { extracted: 'found', unknown: 'not stated', conflict: 'contradicts itself' },
  confidence_levels: ['high', 'medium', 'low'],
  sections: [
    { id: 'who', label: 'Who' },
    { id: 'what', label: 'What happened' },
    { id: 'unused', label: 'Unused section' },
  ],
  fields: [
    { id: 'alpha', label: 'Alpha code', section: 'who', type: 'string', validation: { pattern: '^[A-Z]{2}$' } },
    { id: 'beta', label: 'Beta count', section: 'who', type: 'integer', validation: { min: 0, max: 9 } },
    {
      id: 'gamma',
      label: 'Gamma kind',
      section: 'what',
      type: 'coded',
      options: [
        { code: 'g1', label: 'First kind' },
        { code: 'g2', label: 'Second kind' },
      ],
    },
    { id: 'delta', label: 'Delta day', section: 'what', type: 'date' },
    {
      id: 'epsilon',
      label: 'Epsilon set',
      section: 'what',
      type: 'coded_multi',
      options: [
        { code: 'e1', label: 'E one' },
        { code: 'e2', label: 'E two' },
      ],
    },
    { id: 'zeta', label: 'Zeta items', section: 'what', type: 'string_list' },
  ],
}

export const text = 'Patient AB, 7 doses.\nSecond kind on 4 March 2026. Itch and rash.'

export const detail: DocumentDetail = {
  id: 'T01',
  title: 'A test report',
  format: 'TEXT',
  text,
  extraction: {
    document_id: 'T01',
    catalog_version: 'test',
    model: 'fake/model',
    flags: [],
    cases: [
      {
        case_index: 0,
        label: 'AB',
        fields: {
          alpha: { status: 'extracted', value: 'AB', confidence: 'high', provenance: [{ quote: 'AB', start: 8, end: 10 }] },
          beta: { status: 'conflict', note: 'Seven or eight', provenance: [{ quote: '7 doses', start: 12, end: 19 }] },
          gamma: { status: 'extracted', value: 'g2', confidence: 'medium', provenance: [{ quote: 'Second kind', start: 21, end: 32 }] },
          delta: { status: 'extracted', value: '2026-03-04', confidence: 'low', provenance: [{ quote: '4 March 2026', start: 36, end: 48 }] },
          epsilon: { status: 'unknown' },
          zeta: { status: 'extracted', value: ['itch', 'rash'], confidence: 'high', provenance: [{ quote: 'Itch and rash', start: 50, end: 63 }] },
        },
      },
    ],
  },
  review: { cases: {} },
  progress: { reviewed: 0, total: 6 },
}

export const summaries: DocumentSummary[] = [
  { id: 'T01', title: 'A test report', format: 'TEXT', caseCount: 1, flags: [], progress: { reviewed: 0, total: 6 } },
  { id: 'T02', title: 'A complaint', format: 'TEXT', caseCount: 0, flags: ['no_adverse_event'], progress: { reviewed: 0, total: 0 } },
]
