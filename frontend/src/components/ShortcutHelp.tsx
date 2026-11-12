const SHORTCUTS: [string, string][] = [
  ['J  /  ↓', 'Next field'],
  ['K  /  ↑', 'Previous field'],
  ['A', 'Accept the selected field'],
  ['R', 'Reject the selected field'],
  ['E', 'Edit the selected field'],
  ['U', 'Undo the decision'],
  ['F', 'Show only fields that need attention'],
  ['[  /  ]', 'Previous / next case'],
  ['Esc', 'Cancel edit, close help'],
  ['?', 'Show this help'],
]

export function ShortcutHelp({ onClose }: { onClose: () => void }) {
  return (
    <div className="overlay" role="presentation" onClick={onClose}>
      <div className="dialog" role="dialog" aria-modal="true" aria-labelledby="shortcut-title" onClick={(e) => e.stopPropagation()}>
        <h2 id="shortcut-title" className="dialog__title">Keyboard shortcuts</h2>
        <dl className="shortcuts">
          {SHORTCUTS.map(([keys, action]) => (
            <div key={keys} className="shortcuts__row">
              <dt><kbd>{keys}</kbd></dt>
              <dd>{action}</dd>
            </div>
          ))}
        </dl>
        <button type="button" className="button" onClick={onClose} autoFocus>
          Close
        </button>
      </div>
    </div>
  )
}
