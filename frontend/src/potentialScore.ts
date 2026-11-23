import { cellToLatLng } from 'h3-js'

export const POTENTIAL_CONFIG = {
  fullReachKm: 60,
  partialReachKm: 120,
  partialFactor: 0.5,
  topN: 10,
  maxCoverage: 1
}

export const POTENTIAL_CLASSES = [
  { minHa: 0, points: 0.2 },
  { minHa: 50, points: 0.4 },
  { minHa: 150, points: 0.6 },
  { minHa: 300, points: 0.8 },
  { minHa: 600, points: 1.0 }
]

export type Outlet = { id: string; name: string; lat: number; lng: number }

export type CellScore = {
  h3Index: string
  hectares: number
  distanceKm: number | null
  nearestOutletId: string | null
  reachFactor: number
  potentialPoints: number
  weightedPotential: number
}

export type Coverage = {
  customer: 0 | 1
  territory: 0 | 1
  isochrone: 0 | 1
  total: number
}

export type Highlight = CellScore & { coverage: Coverage }

export function haversineKm(lat1: number, lng1: number, lat2: number, lng2: number): number {
  const earthRadiusKm = 6371
  const toRad = (deg: number) => (deg * Math.PI) / 180
  const dLat = toRad(lat2 - lat1)
  const dLng = toRad(lng2 - lng1)
  const a =
    Math.sin(dLat / 2) ** 2 +
    Math.cos(toRad(lat1)) * Math.cos(toRad(lat2)) * Math.sin(dLng / 2) ** 2
  return 2 * earthRadiusKm * Math.asin(Math.sqrt(a))
}

export function reachFactor(distanceKm: number | null): number {
  if (distanceKm === null) return 0
  if (distanceKm <= POTENTIAL_CONFIG.fullReachKm) return 1
  if (distanceKm <= POTENTIAL_CONFIG.partialReachKm) return POTENTIAL_CONFIG.partialFactor
  return 0
}

export function potentialPoints(hectares: number): number {
  let points = 0
  for (const cls of POTENTIAL_CLASSES) {
    if (hectares >= cls.minHa) points = cls.points
  }
  return points
}

export function scoreCell(h3Index: string, hectares: number, outlets: Outlet[]): CellScore {
  const [lat, lng] = cellToLatLng(h3Index)

  let nearest: { outlet: Outlet; distanceKm: number } | null = null
  for (const outlet of outlets) {
    const distanceKm = haversineKm(lat, lng, outlet.lat, outlet.lng)
    if (!nearest || distanceKm < nearest.distanceKm) {
      nearest = { outlet, distanceKm }
    }
  }

  const distanceKm = nearest ? nearest.distanceKm : null
  const factor = reachFactor(distanceKm)
  const points = potentialPoints(hectares)

  return {
    h3Index,
    hectares,
    distanceKm,
    nearestOutletId: nearest ? nearest.outlet.id : null,
    reachFactor: factor,
    potentialPoints: points,
    weightedPotential: points * factor
  }
}

export function coverageScore(
  h3Index: string,
  customerCells: Set<string>,
  territoryCells: Set<string>,
  isochroneCells: Set<string> | null
): Coverage {
  const customer: 0 | 1 = customerCells.has(h3Index) ? 1 : 0
  const territory: 0 | 1 = territoryCells.has(h3Index) ? 1 : 0
  const isochrone: 0 | 1 = isochroneCells && isochroneCells.has(h3Index) ? 1 : 0
  return { customer, territory, isochrone, total: customer + territory + isochrone }
}

export function pickHighlights(
  scored: CellScore[],
  coverageByCell: Map<string, Coverage>
): Highlight[] {
  return scored
    .map((cell) => ({
      ...cell,
      coverage: coverageByCell.get(cell.h3Index) ?? { customer: 0, territory: 0, isochrone: 0, total: 0 }
    }))
    .filter((cell) => cell.coverage.total <= POTENTIAL_CONFIG.maxCoverage)
    .sort((a, b) => b.weightedPotential - a.weightedPotential || b.hectares - a.hectares)
    .slice(0, POTENTIAL_CONFIG.topN)
}