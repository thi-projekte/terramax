import type { Feature, FeatureCollection, Polygon } from 'geojson'
import { cellToLatLng, latLngToCell } from 'h3-js'
import { fetchLocations } from './locationLayer'
import { loadCustomers } from './crmExport'
import { coverageScore, pickHighlights, scoreCell, type Coverage, type Highlight, type Outlet, type CellScore } from './potentialScore'

const FARMLAND_RESOLUTION = 5

export type PotentialResult = {
  farmland: Map<string, number>
  customerCells: Set<string>
  territoryCells: Set<string>
  coverageByCell: Map<string, Coverage>
  rankByCell: Map<string, number>
  highlights: Highlight[]
  outlets: Outlet[]
}

async function loadFarmland(): Promise<Map<string, number>> {
  const response = await fetch('/TerraMax_H3_Ackerflaeche.csv')
  if (!response.ok) throw new Error('Ackerflaeche konnte nicht geladen werden')
  const lines = (await response.text()).trim().split(/\r?\n/)
  const headers = lines[0].replace(/^\uFEFF/, '').split(',').map((h) => h.trim())
  const h3Column = headers.indexOf('h3Index')
  const sumColumn = headers.indexOf('sum')
  if (h3Column === -1 || sumColumn === -1) throw new Error('CSV-Spalten h3Index oder sum fehlen')
  const farmland = new Map<string, number>()
  for (const line of lines.slice(1)) {
    if (!line.trim()) continue
    const values = line.split(',')
    const h3Index = values[h3Column]?.trim()
    const hectares = Number(values[sumColumn])
    if (h3Index && Number.isFinite(hectares) && hectares > 0) farmland.set(h3Index, hectares)
  }
  return farmland
}

async function loadTerritoryRings(): Promise<number[][][]> {
  const response = await fetch('/territories.geojson')
  if (!response.ok) throw new Error('Vertriebsgebiete konnten nicht geladen werden')
  const data = (await response.json()) as FeatureCollection<Polygon>
  return data.features.map((f: Feature<Polygon>) => f.geometry.coordinates[0])
}

function pointInRing(lat: number, lng: number, ring: number[][]): boolean {
  let inside = false
  for (let i = 0, j = ring.length - 1; i < ring.length; j = i++) {
    const [xi, yi] = ring[i]
    const [xj, yj] = ring[j]
    if ((yi > lat) !== (yj > lat) && lng < ((xj - xi) * (lat - yi)) / (yj - yi) + xi) {
      inside = !inside
    }
  }
  return inside
}

export async function computePotential(isochroneCells: Set<string> | null = null): Promise<PotentialResult> {
  const [farmland, customers, rings, locations] = await Promise.all([
    loadFarmland(),
    loadCustomers(),
    loadTerritoryRings(),
    fetchLocations()
  ])

  const outlets: Outlet[] = locations.features
    .filter((f) => f.properties?.category === 'john_deere')
    .map((f) => ({
      id: String(f.properties?.id ?? ''),
      name: String(f.properties?.name ?? ''),
      lng: f.geometry.coordinates[0],
      lat: f.geometry.coordinates[1]
    }))

  const customerCells = new Set(customers.map((c) => latLngToCell(c.lat, c.lng, FARMLAND_RESOLUTION)))

  const territoryCells = new Set<string>()
  const isochroneSet = new Set<string>()
  for (const h3Index of farmland.keys()) {
    const [lat, lng] = cellToLatLng(h3Index)
    if (rings.some((ring) => pointInRing(lat, lng, ring))) territoryCells.add(h3Index)
    if (isochroneCells && isochroneCells.has(h3Index)) isochroneSet.add(h3Index)
  }

  const scored: CellScore[] = [...farmland].map(([h3Index, hectares]) => scoreCell(h3Index, hectares, outlets))

  const coverageByCell = new Map<string, Coverage>()
  for (const h3Index of farmland.keys()) {
    const coverage = coverageScore(h3Index, customerCells, territoryCells, isochroneCells ? isochroneSet : null)
    if (!isochroneCells) {
      const nearest = scored.find((s) => s.h3Index === h3Index)
      const near = nearest && nearest.distanceKm !== null && nearest.distanceKm <= 60 ? 1 : 0
      coverage.isochrone = near
      coverage.total = coverage.customer + coverage.territory + near
    }
    coverageByCell.set(h3Index, coverage)
  }

  const ranked = [...scored].sort((a, b) => b.weightedPotential - a.weightedPotential || b.hectares - a.hectares)
  const rankByCell = new Map<string, number>(ranked.map((cell, index) => [cell.h3Index, index + 1]))

  const highlights = pickHighlights(scored, coverageByCell).filter((h) => h.weightedPotential > 0)

  return { farmland, customerCells, territoryCells, coverageByCell, rankByCell, highlights, outlets }
}