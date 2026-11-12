import type { ConfidenceLevel, FieldReview, FieldStatus, ReviewProgress } from '../api/types'

const CONFIDENCE_BARS: Record<ConfidenceLevel, number> = { low: 1, medium: 2, high: 3 }

export function ConfidenceMeter({ level }: { level: ConfidenceLevel }) {
  const filled = CONFIDENCE_BARS[level]
  return (
    <span className={`confidence confidence--${level}`} title={`${level} confidence`} aria-label={`${level} confidence`}>
      {[1, 2, 3].map((bar) => (
        <span key={bar} className={`confidence__bar${bar <= filled ? ' is-filled' : ''}`} />
      ))}
      <span className="confidence__text">{level}</span>
    </span>
  )
}

export function StatusPill({ status, description }: { status: FieldStatus; description?: string }) {
  return (
    <span className={`status status--${status}`} title={description}>
      {status}
    </span>
  )
}

const DECISION_TEXT = { accepted: 'Accepted', rejected: 'Rejected', edited: 'Edited' } as const

export function DecisionBadge({ review }: { review: FieldReview | undefined }) {
  if (!review) return null
  return <span className={`decision decision--${review.decision}`}>{DECISION_TEXT[review.decision]}</span>
}

export function ProgressBar({ progress, compact = false }: { progress: ReviewProgress; compact?: boolean }) {
  const ratio = progress.total === 0 ? 0 : progress.reviewed / progress.total
  const label = progress.total === 0 ? 'Nothing to review' : `${progress.reviewed} of ${progress.total} reviewed`
  return (
    <div className={`progress${compact ? ' progress--compact' : ''}`} title={label}>
      <div
        className="progress__track"
        role="progressbar"
        aria-label="Review progress"
        aria-valuemin={0}
        aria-valuemax={progress.total}
        aria-valuenow={progress.reviewed}
      >
        <div className="progress__fill" style={{ width: `${Math.round(ratio * 100)}%` }} />
      </div>
      {!compact && <span className="progress__label">{label}</span>}
    </div>
  )
}
