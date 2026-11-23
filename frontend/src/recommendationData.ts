import { cellToLatLng } from 'h3-js'
import type { PotentialResult } from './potentialLayer'

export type Recommendation = {
  rank: number
  h3Index: string
  hectares: number
  points: number
  weighted: number
  distanceKm: number | null
  lat: number
  lng: number
}

export function buildRecommendations(result: PotentialResult): Recommendation[] {
  return result.highlights.map((h, index) => {
    const [lat, lng] = cellToLatLng(h.h3Index)
    return {
      rank: index + 1,
      h3Index: h.h3Index,
      hectares: h.hectares,
      points: h.potentialPoints,
      weighted: h.weightedPotential,
      distanceKm: h.distanceKm,
      lat,
      lng
    }
  })
}