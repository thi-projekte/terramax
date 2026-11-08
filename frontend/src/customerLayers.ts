import type { Map as MapboxMap } from 'mapbox-gl'
import { cellToBoundary, latLngToCell } from 'h3-js'
import type { Feature, Point, Polygon } from 'geojson'
import { loadCustomers } from './crmExport'

const H3_RESOLUTION = 6

export async function addCustomerLayers(map: MapboxMap) {
  try {
    const customers = (await loadCustomers()).map((c) => ({
      ...c,
      h3Index: latLngToCell(c.lat, c.lng, H3_RESOLUTION)
    }))

    // Kunden pro H3-Zelle zaehlen
    const counts = new Map<string, number>()
    for (const c of customers) {
      counts.set(c.h3Index, (counts.get(c.h3Index) ?? 0) + 1)
    }

    const cellFeatures: Feature<Polygon>[] = []
    for (const [h3Index, count] of counts) {
      const boundary = cellToBoundary(h3Index, true)
      cellFeatures.push({
        type: 'Feature',
        properties: { h3Index, customerCount: count },
        geometry: { type: 'Polygon', coordinates: [[...boundary, boundary[0]]] }
      })
    }

    const pointFeatures: Feature<Point>[] = customers.map((c) => ({
      type: 'Feature',
      properties: {
        id: c.id,
        name: c.name,
        region: c.region,
        segment: c.segment,
        crmUrl: c.crmUrl ?? '',
        h3Index: c.h3Index
      },
      geometry: { type: 'Point', coordinates: [c.lng, c.lat] }
    }))

    map.addSource('customer-cells', {
      type: 'geojson',
      data: { type: 'FeatureCollection', features: cellFeatures }
    })
    map.addSource('customers', {
      type: 'geojson',
      data: { type: 'FeatureCollection', features: pointFeatures }
    })

    map.addLayer({
      id: 'customer-cells-fill',
      type: 'fill',
      source: 'customer-cells',
      paint: { 'fill-color': '#facc15', 'fill-opacity': 0.45 }
    })
    map.addLayer({
      id: 'customer-cells-lines',
      type: 'line',
      source: 'customer-cells',
      paint: { 'line-color': '#facc15', 'line-width': 2 }
    })
    map.addLayer({
      id: 'customers-circles',
      type: 'circle',
      source: 'customers',
      paint: {
        'circle-radius': 5,
        'circle-color': '#ffffff',
        'circle-stroke-color': '#1d4ed8',
        'circle-stroke-width': 2
      }
    })

    console.log('Kunden: ' + customers.length + ', Zellen mit Kunden: ' + counts.size)
  } catch (error) {
    console.error('Fehler bei den Kunden:', error)
  }
}