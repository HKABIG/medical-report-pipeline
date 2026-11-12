import type { CatalogField, FieldValue } from '../api/types'
import { displayItems } from '../catalog/catalog'

/** A value rendered by its catalog type. Lists show as items; an empty list reads as "None". */
export function FieldValueView({ field, value }: { field: CatalogField; value: FieldValue }) {
  const items = displayItems(field, value)
  if (field.type === 'coded_multi' || field.type === 'string_list') {
    if (items.length === 0) return <span className="value value--none">None</span>
    return (
      <span className="value value--list">
        {items.map((item) => (
          <span key={item} className="value__item">
            {item}
          </span>
        ))}
      </span>
    )
  }
  return (
    <span className="value" title={field.type === 'date' ? String(value) : undefined}>
      {items[0]}
    </span>
  )
}
