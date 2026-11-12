/** A run of document text and the fields whose quotes cover it. Offsets are Unicode code points. */
export interface Segment {
  text: string
  start: number
  end: number
  fieldIds: string[]
}

export interface Highlight {
  fieldId: string
  start: number
  end: number
}

/**
 * Splits the text at every highlight boundary. Offsets count code points, as the pipeline writes them, so the
 * text is split by code point rather than by UTF-16 unit.
 */
export function segmentText(text: string, highlights: Highlight[]): Segment[] {
  const codePoints = Array.from(text)
  const length = codePoints.length
  const valid = highlights.filter((h) => h.start >= 0 && h.end <= length && h.start < h.end)
  const cuts = new Set<number>([0, length])
  valid.forEach((h) => {
    cuts.add(h.start)
    cuts.add(h.end)
  })
  const points = [...cuts].sort((a, b) => a - b)
  const segments: Segment[] = []
  for (let i = 0; i < points.length - 1; i++) {
    const start = points[i]
    const end = points[i + 1]
    const fieldIds = [...new Set(valid.filter((h) => h.start <= start && h.end >= end).map((h) => h.fieldId))]
    segments.push({ text: codePoints.slice(start, end).join(''), start, end, fieldIds })
  }
  return segments
}
