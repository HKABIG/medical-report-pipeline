import { useId, useState, type FormEvent, type KeyboardEvent } from 'react'
import type { CatalogField, FieldValue } from '../api/types'
import { validateValue } from '../catalog/validate'

interface Props {
  field: CatalogField
  initial: FieldValue | undefined
  onSave: (value: FieldValue) => void
  onCancel: () => void
  saving: boolean
}

/** The control for a value comes from its catalog type: options, date picker, number range, pattern, list. */
export function FieldEditor({ field, initial, onSave, onCancel, saving }: Props) {
  const [value, setValue] = useState<FieldValue | undefined>(initial ?? emptyValue(field))
  const [touched, setTouched] = useState(false)
  const id = useId()
  const problem = validateValue(field, value)

  const submit = (event: FormEvent) => {
    event.preventDefault()
    setTouched(true)
    if (!problem && value !== undefined) onSave(value)
  }
  const onKeyDown = (event: KeyboardEvent) => {
    if (event.key === 'Escape') {
      event.stopPropagation()
      onCancel()
    }
  }

  return (
    <form className="editor" onSubmit={submit} onKeyDown={onKeyDown} aria-label={`Edit ${field.label}`}>
      <Control field={field} id={id} value={value} onChange={setValue} />
      {field.guidance && <p className="editor__hint">{field.guidance}</p>}
      {touched && problem && (
        <p className="editor__error" role="alert">
          {problem}
        </p>
      )}
      <div className="editor__actions">
        <button type="submit" className="button button--primary" disabled={saving}>
          {saving ? 'Saving…' : 'Save edit'}
        </button>
        <button type="button" className="button button--ghost" onClick={onCancel} disabled={saving}>
          Cancel
        </button>
      </div>
    </form>
  )
}

function emptyValue(field: CatalogField): FieldValue | undefined {
  return field.type === 'coded_multi' || field.type === 'string_list' ? [] : undefined
}

interface ControlProps {
  field: CatalogField
  id: string
  value: FieldValue | undefined
  onChange: (value: FieldValue | undefined) => void
}

function Control({ field, id, value, onChange }: ControlProps) {
  const label = <label htmlFor={id} className="visually-hidden">{field.label}</label>
  switch (field.type) {
    case 'coded':
      return (
        <fieldset className="choice-group">
          <legend className="visually-hidden">{field.label}</legend>
          {field.options?.map((option) => (
            <label key={option.code} className={`choice${value === option.code ? ' is-on' : ''}`}>
              <input
                type="radio"
                name={id}
                value={option.code}
                checked={value === option.code}
                onChange={() => onChange(option.code)}
              />
              {option.label}
            </label>
          ))}
        </fieldset>
      )
    case 'coded_multi': {
      const selected = Array.isArray(value) ? value : []
      return (
        <fieldset className="choice-group">
          <legend className="visually-hidden">{field.label}</legend>
          {field.options?.map((option) => {
            const on = selected.includes(option.code)
            return (
              <label key={option.code} className={`choice choice--multi${on ? ' is-on' : ''}`}>
                <input
                  type="checkbox"
                  value={option.code}
                  checked={on}
                  onChange={() =>
                    onChange(on ? selected.filter((code) => code !== option.code) : [...selected, option.code])
                  }
                />
                {option.label}
              </label>
            )
          })}
        </fieldset>
      )
    }
    case 'date':
      return (
        <>
          {label}
          <input
            id={id}
            className="input"
            type="date"
            value={typeof value === 'string' ? value : ''}
            onChange={(event) => onChange(event.target.value || undefined)}
            autoFocus
          />
        </>
      )
    case 'integer':
    case 'number':
      return (
        <>
          {label}
          <input
            id={id}
            className="input"
            type="number"
            step={field.type === 'integer' ? 1 : 'any'}
            min={field.validation?.min}
            max={field.validation?.max}
            value={typeof value === 'number' ? value : ''}
            onChange={(event) => onChange(event.target.value === '' ? undefined : Number(event.target.value))}
            autoFocus
          />
        </>
      )
    case 'string_list':
      return <ListInput id={id} label={field.label} items={Array.isArray(value) ? value : []} onChange={onChange} />
    case 'string':
      return (
        <>
          {label}
          <input
            id={id}
            className="input"
            type="text"
            pattern={field.validation?.pattern}
            value={typeof value === 'string' ? value : ''}
            onChange={(event) => onChange(event.target.value)}
            autoFocus
          />
        </>
      )
  }
}

function ListInput({ id, label, items, onChange }: { id: string; label: string; items: string[]; onChange: (v: string[]) => void }) {
  const [draft, setDraft] = useState('')
  const add = () => {
    const item = draft.trim()
    if (item && !items.includes(item)) onChange([...items, item])
    setDraft('')
  }
  return (
    <div className="list-input">
      <ul className="list-input__items">
        {items.map((item) => (
          <li key={item} className="value__item">
            {item}
            <button type="button" className="list-input__remove" aria-label={`Remove ${item}`} onClick={() => onChange(items.filter((i) => i !== item))}>
              ×
            </button>
          </li>
        ))}
      </ul>
      <label htmlFor={id} className="visually-hidden">{`Add to ${label}`}</label>
      <input
        id={id}
        className="input"
        type="text"
        placeholder="Type and press Enter to add"
        value={draft}
        onChange={(event) => setDraft(event.target.value)}
        onKeyDown={(event) => {
          if (event.key === 'Enter') {
            event.preventDefault()
            add()
          }
        }}
        onBlur={add}
        autoFocus
      />
    </div>
  )
}
