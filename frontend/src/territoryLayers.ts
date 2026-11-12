import type { Map as MapboxMap } from 'mapbox-gl'

export async function addTerritoryLayers(map: MapboxMap) {
  try {
    const response = await fetch('/territories.geojson')
    if (!response.ok) throw new Error('Vertriebsgebiete konnten nicht geladen werden')
    const territories = await response.json()

    map.addSource('territories', { type: 'geojson', data: territories })

    const below = map.getLayer('outlets-circles') ? 'outlets-circles' : undefined

    map.addLayer(
      {
        id: 'territories-fill',
        type: 'fill',
        source: 'territories',
        paint: {
          'fill-color': ['get', 'color'],
          'fill-opacity': 0.3
        }
      },
      below
    )

    map.addLayer(
      {
        id: 'territories-lines',
        type: 'line',
        source: 'territories',
        paint: {
          'line-color': ['get', 'color'],
          'line-width': 2
        }
      },
      below
    )

    map.addLayer({
      id: 'territories-labels',
      type: 'symbol',
      source: 'territories',
      layout: {
        'text-field': ['get', 'salesperson'],
        'text-size': 11
      },
      paint: {
        'text-color': '#ffffff',
        'text-halo-color': '#000000',
        'text-halo-width': 1.5
      }
    })
  } catch (error) {
    console.error('Fehler bei den Vertriebsgebieten:', error)
  }
}