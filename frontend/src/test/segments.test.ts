import { describe, expect, it } from 'vitest'
import { segmentText } from '../catalog/segments'

describe('segmentText', () => {
  it('splits at every highlight boundary and records the covering fields', () => {
    const segments = segmentText('abcdefgh', [
      { fieldId: 'x', start: 1, end: 5 },
      { fieldId: 'y', start: 3, end: 7 },
    ])
    expect(segments.map((s) => [s.text, s.fieldIds])).toEqual([
      ['a', []],
      ['bc', ['x']],
      ['de', ['x', 'y']],
      ['fg', ['y']],
      ['h', []],
    ])
  })

  it('counts offsets in code points, as the pipeline writes them', () => {
    const segments = segmentText('😀 fever', [{ fieldId: 'x', start: 2, end: 7 }])
    expect(segments.find((s) => s.fieldIds.length)?.text).toBe('fever')
  })

  it('ignores spans outside the text', () => {
    expect(segmentText('abc', [{ fieldId: 'x', start: 2, end: 10 }])).toHaveLength(1)
  })
})
