import type { Map as MapboxMap } from 'mapbox-gl'
import { cellToBoundary, latLngToCell } from 'h3-js'
import type { Feature, Polygon } from 'geojson'
import { loadCustomers } from './crmExport'

const FARMLAND_RESOLUTION = 5

async function loadFarmland(): Promise<Map<string, number>> {
    const response = await fetch('/TerraMax_H3_Ackerflaeche.csv')
    if (!response.ok) throw new Error('Ackerflaeche konnte nicht geladen werden')

    const lines = (await response.text()).trim().split(/\r?\n/)
    const headers = lines[0].replace(/^\uFEFF/, '').split(',').map((h) => h.trim())
    const h3Column = headers.indexOf('h3Index')
    const sumColumn = headers.indexOf('sum')
    if (h3Column === -1 || sumColumn === -1) {
        throw new Error('CSV-Spalten h3Index oder sum fehlen')
    }

    const farmland = new Map<string, number>()
    for (const line of lines.slice(1)) {
        if (!line.trim()) continue
        const values = line.split(',')
        const h3Index = values[h3Column]?.trim()
        const hectares = Number(values[sumColumn])
        if (h3Index && Number.isFinite(hectares) && hectares > 0) {
            farmland.set(h3Index, hectares)
        }
    }
    return farmland
}

export async function addWhiteSpotLayers(map: MapboxMap) {
    try {
        const farmland = await loadFarmland()
        const customers = await loadCustomers()

        // Zellen (Aufloesung 5), in denen mindestens ein Kunde sitzt
        const customerCells = new Set<string>()
        for (const c of customers) {
            customerCells.add(latLngToCell(c.lat, c.lng, FARMLAND_RESOLUTION))
        }

        const features: Feature<Polygon>[] = []
        for (const [h3Index, hectares] of farmland) {
            if (customerCells.has(h3Index)) continue
            const boundary = cellToBoundary(h3Index, true)
            features.push({
                type: 'Feature',
                properties: { h3Index, hectares },
                geometry: { type: 'Polygon', coordinates: [[...boundary, boundary[0]]] }
            })
        }

        map.addSource('white-spots', {
            type: 'geojson',
            data: { type: 'FeatureCollection', features }
        })

        map.addLayer({
            id: 'white-spots-fill',
            type: 'fill',
            source: 'white-spots',
            paint: { 'fill-color': '#ffffff', 'fill-opacity': 0.3 }
        })
        map.addLayer({
            id: 'white-spots-lines',
            type: 'line',
            source: 'white-spots',
            paint: { 'line-color': '#ffffff', 'line-width': 1.5 }
        })

        console.log(
            'Zellen mit Ackerflaeche: ' + farmland.size +
            ', Zellen mit Kunden: ' + customerCells.size +
            ', weisse Flecken: ' + features.length
        )
    } catch (error) {
        console.error('Fehler bei den weissen Flecken:', error)
    }
}