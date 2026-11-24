import type { Map as MapboxMap } from 'mapbox-gl'
import { cellToBoundary, latLngToCell } from 'h3-js'
import type { Feature, Polygon } from 'geojson'
import { loadCustomers } from './crmExport'
import { fetchGeoCells } from './geoLayer'

const FARMLAND_RESOLUTION = 6

async function loadFarmland(): Promise<Map<string, number>> {
    const data = await fetchGeoCells()
    const farmland = new Map<string, number>()

    for (const feature of data.features) {
        const { h3Index, areaHa, cropType } = feature.properties

        if (
            h3Index &&
            cropType === 'wheat_soy_combined' &&
            typeof areaHa === 'number' &&
            Number.isFinite(areaHa) &&
            areaHa > 0
        ) {
            farmland.set(h3Index, areaHa)
        }
    }

    return farmland
}

export async function addWhiteSpotLayers(map: MapboxMap) {
    try {
        const farmland = await loadFarmland()
        const customers = await loadCustomers()

        // Zellen (Aufloesung 6), in denen mindestens ein Kunde sitzt
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
            paint: { 'fill-color': '#ffffff', 'fill-opacity': 0.45 }
        })

        map.addLayer({
            id: 'white-spots-lines',
            type: 'line',
            source: 'white-spots',
            paint: { 'line-color': '#ffffff', 'line-width': 2 }
        })

        console.log(
            'Zellen mit Weizen- und Sojaflaeche: ' + farmland.size +
            ', Zellen mit Kunden: ' + customerCells.size +
            ', weisse Flecken: ' + features.length
        )
    } catch (error) {
        console.error('Fehler bei den weissen Flecken:', error)
    }
}