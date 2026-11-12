import { useEffect, useMemo, useRef } from 'react'
import { segmentText, type Highlight } from '../catalog/segments'

interface Props {
  text: string
  highlights: Highlight[]
  selectedFieldId?: string
  labelOf: (fieldId: string) => string
  onSelectField: (fieldId: string) => void
}

const prefersReducedMotion = () =>
  typeof window.matchMedia === 'function' && window.matchMedia('(prefers-reduced-motion: reduce)').matches

/**
 * The document with every quote of the current case marked. The selected field's quotes stand out and scroll into
 * view; clicking any mark selects its field (or the next one, where quotes of several fields overlap).
 */
export function DocumentText({ text, highlights, selectedFieldId, labelOf, onSelectField }: Props) {
  const segments = useMemo(() => segmentText(text, highlights), [text, highlights])
  const container = useRef<HTMLDivElement>(null)

  useEffect(() => {
    const first = container.current?.querySelector('.hl.is-active')
    if (first && typeof first.scrollIntoView === 'function') {
      first.scrollIntoView({ block: 'center', behavior: prefersReducedMotion() ? 'auto' : 'smooth' })
    }
  }, [selectedFieldId])

  return (
    <div className="doc-text" ref={container}>
      <pre className="doc-text__body">
        {segments.map((segment) => {
          if (segment.fieldIds.length === 0) return <span key={segment.start}>{segment.text}</span>
          const active = selectedFieldId !== undefined && segment.fieldIds.includes(selectedFieldId)
          const next = active
            ? segment.fieldIds[(segment.fieldIds.indexOf(selectedFieldId) + 1) % segment.fieldIds.length]
            : segment.fieldIds[0]
          const labels = segment.fieldIds.map(labelOf).join(', ')
          return (
            <mark
              key={segment.start}
              className={`hl${active ? ' is-active' : ''}${segment.fieldIds.length > 1 ? ' is-shared' : ''}`}
              title={labels}
              onClick={() => onSelectField(next)}
            >
              {segment.text}
            </mark>
          )
        })}
      </pre>
    </div>
  )
}
