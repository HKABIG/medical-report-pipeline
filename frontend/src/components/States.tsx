import type { ReactNode } from 'react'

export function LoadingState({ label }: { label: string }) {
  return (
    <div className="state state--loading" role="status" aria-live="polite">
      <div className="skeleton">
        <span className="skeleton__line skeleton__line--wide" />
        <span className="skeleton__line" />
        <span className="skeleton__line skeleton__line--short" />
      </div>
      <p className="state__caption">{label}</p>
    </div>
  )
}

export function ErrorState({ error, onRetry }: { error: Error; onRetry?: () => void }) {
  return (
    <div className="state state--error" role="alert">
      <h2 className="state__title">Something went wrong</h2>
      <p className="state__body">{error.message}</p>
      {onRetry && (
        <button type="button" className="button" onClick={onRetry}>
          Try again
        </button>
      )}
    </div>
  )
}

export function EmptyState({ title, children, icon }: { title: string; children?: ReactNode; icon?: ReactNode }) {
  return (
    <div className="state state--empty">
      {icon && <div className="state__icon" aria-hidden="true">{icon}</div>}
      <h2 className="state__title">{title}</h2>
      {children && <div className="state__body">{children}</div>}
    </div>
  )
}
