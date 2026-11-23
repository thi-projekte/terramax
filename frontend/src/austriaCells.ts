import { cellToParent } from 'h3-js'

// Alle Zellen der Auflösung 5, die von der Auflösung-6-Grid-Datei abgedeckt werden
export function austriaCellsRes5(res6Indexes: string[]): Set<string> {
  const cells = new Set<string>()
  for (const h3 of res6Indexes) {
    cells.add(cellToParent(h3, 5))
  }
  return cells
}
