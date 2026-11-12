import { describe, expect, it } from 'vitest'

interface CatalogFile {
  fields: { id: string }[]
}

const catalogs = import.meta.glob<CatalogFile>('../../../case-intake-exercise/catalog/*.json', {
  eager: true,
  import: 'default',
})
const sources = import.meta.glob<string>(['../**/*.{ts,tsx}', '!../test/**'], {
  eager: true,
  query: '?raw',
  import: 'default',
})

describe('UI source', () => {
  it('names no catalog field id: labels, order and controls all come from the catalog', () => {
    const ids = Object.values(catalogs).flatMap((catalog) => catalog.fields.map((field) => field.id))
    expect(ids.length).toBeGreaterThan(0)
    expect(Object.keys(sources).length).toBeGreaterThan(5)
    for (const [path, source] of Object.entries(sources)) {
      for (const id of ids) {
        expect(source.includes(id), `${path} mentions ${id}`).toBe(false)
      }
    }
  })
})
