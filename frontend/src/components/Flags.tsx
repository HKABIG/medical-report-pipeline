import { flagLabel, isFlagCode } from '../catalog/catalog'

/** Flag codes as compact chips; free-text observations as a readable list. */
export function FlagChips({ flags }: { flags: string[] }) {
  const codes = flags.filter(isFlagCode)
  if (codes.length === 0) return null
  return (
    <span className="flag-chips">
      {codes.map((flag) => (
        <span key={flag} className={`flag flag--${flag}`}>
          {flagLabel(flag)}
        </span>
      ))}
    </span>
  )
}

export function FlagNotes({ flags }: { flags: string[] }) {
  const notes = flags.filter((flag) => !isFlagCode(flag))
  if (notes.length === 0) return null
  return (
    <ul className="flag-notes" aria-label="Notes on this document">
      {notes.map((note) => (
        <li key={note}>{note}</li>
      ))}
    </ul>
  )
}
