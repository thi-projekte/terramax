import mapboxgl from 'mapbox-gl'
import type { Map as MapboxMap } from 'mapbox-gl'
import type { Feature, Polygon } from 'geojson'
import { cellToParent, cellToLatLng } from 'h3-js'
import { computePotential } from './potentialLayer'
import { classifyCell, CELL_COLOURS, type CellColour } from './cellColors'
import { haversineKm, reachFactor, potentialPoints, type Highlight } from './potentialScore'

const STATUS_TEXT: Record<CellColour, string> = {
  yellow: 'Empfehlung',
  green: 'Kunde vorhanden',
  white: 'Ackerflaeche ohne Kunde',
  red: 'keine Ackerflaeche'
}

export async function addCellColourLayer(
  map: MapboxMap,
  grid: Feature<Polygon>[],
  isochroneCells: Set<string> | null
): Promise<Highlight[]> {
  try {
    const { farmland, customerCells, territoryCells, coverageByCell, rankByCell, highlights, outlets } = await computePotential(isochroneCells)
    const recommended = new Set(highlights.map((h) => h.h3Index))

    const features: Feature<Polygon, Record<string, unknown>>[] = []
    for (const feature of grid) {
      const h3Index = String(feature.properties?.h3Index ?? '')
      if (!h3Index) continue

      const parent = cellToParent(h3Index, 5)
      const hectares = farmland.get(parent)
      const hasFarmland = hectares !== undefined
      const hasCustomer = customerCells.has(parent)
      const colour = classifyCell({
        isRecommendation: recommended.has(parent),
        hasCustomer,
        hasFarmland
      })
      if (colour === 'white' && !hasFarmland) continue

      const [lat, lng] = cellToLatLng(h3Index)
      let distanceKm: number | null = null
      for (const o of outlets) {
        const d = haversineKm(lat, lng, o.lat, o.lng)
        if (distanceKm === null || d < distanceKm) distanceKm = d
      }
      const factor = reachFactor(distanceKm)
      const coverage = coverageByCell.get(parent)
      const rank = rankByCell.get(parent)

      features.push({
        type: 'Feature' as const,
        properties: {
          colour: CELL_COLOURS[colour],
          opacity: colour === 'yellow' ? 0.55 : 0,
          status: STATUS_TEXT[colour],
          h3Index,
          hectares: hasFarmland ? Math.round(hectares) : -1,
          weighted: hasFarmland ? Math.round(potentialPoints(hectares) * factor * 100) / 100 : -1,
          distanceKm: distanceKm === null ? -1 : Math.round(distanceKm),
          customer: hasCustomer ? 1 : 0,
          territory: territoryCells.has(parent) ? 1 : 0,
          coverage: coverage ? coverage.total : 0,
          isochrone: coverage ? coverage.isochrone : 0,
          rank: rank ?? -1
        },
        geometry: feature.geometry
      })
    }

    map.addSource('cell-colours', {
      type: 'geojson',
      data: { type: 'FeatureCollection', features }
    })
    map.addLayer({
      id: 'cell-colours-fill',
      type: 'fill',
      source: 'cell-colours',
      paint: { 'fill-color': ['get', 'colour'], 'fill-opacity': ['get', 'opacity'] }
    })


    map.on('click', 'cell-colours-fill', (event) => {
      const props = event.features?.[0]?.properties
      if (!props) return

      const box = document.createElement('div')
      box.style.cssText = 'color:#222;padding:6px;text-align:left;font-size:13px;line-height:1.4;word-break:break-word'

      const title = document.createElement('strong')
      title.textContent = 'Zelle: ' + String(props.status)
      box.append(title)

      const noFarmland = Number(props.hectares) < 0
      const rank = Number(props.rank)

      const rows = [
        ['Ackerflaeche', noFarmland ? 'unbekannt' : String(props.hectares) + ' ha'],
        ['Gewichtetes Potenzial', noFarmland ? 'unbekannt' : String(props.weighted) + ' Punkte'],
        ['Entfernung zum Outlet', Number(props.distanceKm) < 0 ? 'unbekannt' : String(props.distanceKm) + ' km (Zellmitte)'],
        ['Abdeckung', String(props.coverage) + ' von 3 (Kunde ' + String(props.customer) + ', Vertriebsgebiet ' + String(props.territory) + ', Outlet-Naehe ' + String(props.isochrone) + ')'],
        ['Rang', rank < 0 ? 'nicht bewertet (keine Ackerflaeche)' : 'Rang ' + String(rank) + ' nach Potenzial'],
        ['Datenquelle', noFarmland ? 'kein Wert vorhanden' : 'Ackerflaeche (QGIS-Auswertung, Herkunft unbestaetigt)'],
          ['Konfidenz', noFarmland ? 'unbekannt' : (Number(props.coverage) > 0 || (Number(props.rank) >= 1 && Number(props.rank) <= 10) ? '▮▯▯ niedrig' : '▮▮▯ mittel')],
          ['Begruendung', noFarmland ? 'kein Flaechenwert vorhanden' : (Number(props.coverage) > 0 || (Number(props.rank) >= 1 && Number(props.rank) <= 10) ? 'schwächste Quelle sind die Testdaten (Kunden, Gebiete)' : 'Ackerflaeche, vorlaeufige Auswertung')],
        ['H3-Zelle', String(props.h3Index)]
      ]
      for (const [label, value] of rows) {
        const p = document.createElement('p')
        p.textContent = label + ': ' + value
        box.append(p)
      }

      setTimeout(() => {
        new mapboxgl.Popup({ closeButton: true, maxWidth: '300px' })
          .setLngLat(event.lngLat)
          .setDOMContent(box)
          .addTo(map)
      }, 0)
    })
    return highlights
  } catch (error) {
    console.error('Fehler bei den Zellfarben:', error)
    return []
  }
}