export type CellColour = 'yellow' | 'green' | 'white' | 'red'

export const CELL_COLOURS: Record<CellColour, string> = {
  yellow: '#a3e635',
  green: '#22c55e',
  white: '#ffffff',
  red: '#ef4444'
}

export function classifyCell(input: {
  isRecommendation: boolean
  hasCustomer: boolean
  hasFarmland: boolean
}): CellColour {
  if (input.isRecommendation) return 'yellow'
  if (input.hasCustomer) return 'green'
  if (input.hasFarmland) return 'white'
  return 'red'
}