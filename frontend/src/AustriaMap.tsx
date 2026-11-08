
import type { Feature, FeatureCollection, Polygon, MultiPolygon, Point } from 'geojson'
import { useEffect, useRef, useState } from 'react'
import mapboxgl from 'mapbox-gl'
import 'mapbox-gl/dist/mapbox-gl.css'
import { getIsochrone } from './IsochroneLayer'

const COLORS = {
  unknown: '#9ca3af',
  veryLow: '#e4f4da',
  low: '#b6dfaa',
  medium: '#79bf83',
  high: '#3c9765',
  veryHigh: '#166843'
}

const AREA_CLASSES = [
  { max: 50, color: COLORS.veryLow, label: 'Unter 50 ha' },
  { max: 150, color: COLORS.low, label: '50 bis unter 150 ha' },
  { max: 300, color: COLORS.medium, label: '150 bis unter 300 ha' },
  { max: 600, color: COLORS.high, label: '300 bis unter 600 ha' },
  { max: Infinity, color: COLORS.veryHigh, label: 'Ab 600 ha' }
]

function getAreaColor(hektar: number | undefined) {
  if (hektar === undefined) return COLORS.unknown

  return (
    AREA_CLASSES.find((item) => hektar < item.max)?.color ??
    COLORS.veryHigh
  )
}


// Ticket #19: Fahrzeitabdeckung – die Koordinaten liegen in [Längengrad, Breitengrad] vor.
type SiteProperties = { id: string; name: string }
type SiteCollection = FeatureCollection<Point, SiteProperties>
type AreaGeometry = Polygon | MultiPolygon
type AreaCollection = FeatureCollection<AreaGeometry>
type CoverageStatus = {
  text: string
  outsideCells: number | null
  outsideCropHectares: number | null
  outsideCustomers: number | null
}

function insideRing(point: number[], ring: number[][]): boolean {
  let inside = false
  const x = point[0]
  const y = point[1]
  for (let i = 0, j = ring.length - 1; i < ring.length; j = i++) {
    const a = ring[i]
    const b = ring[j]
    if ((a[1] > y) !== (b[1] > y) &&
        x < ((b[0] - a[0]) * (y - a[1])) / (b[1] - a[1]) + a[0]) {
      inside = !inside
    }
  }
  return inside
}

function insidePolygon(point: number[], rings: number[][][]): boolean {
  return rings.length > 0 && insideRing(point, rings[0]) &&
    !rings.slice(1).some((hole) => insideRing(point, hole))
}

function insideArea(point: number[], geometry: AreaGeometry): boolean {
  return geometry.type === 'Polygon'
    ? insidePolygon(point, geometry.coordinates)
    : geometry.coordinates.some((polygon) => insidePolygon(point, polygon))
}

function covered(point: number[], areas: AreaCollection): boolean {
  return areas.features.some((f) => insideArea(point, f.geometry))
}

// Für den MVP wird jede H3-Zelle anhand ihres Mittelpunkts klassifiziert.
// Das ist eine Näherung, keine exakte Verschneidung entlang einer Fahrzeitgrenze.
function cellCenter(feature: Feature<Polygon>): number[] {
  const ring = feature.geometry.coordinates[0]
  const points = ring.length > 1 &&
    ring[0][0] === ring[ring.length - 1][0] &&
    ring[0][1] === ring[ring.length - 1][1]
    ? ring.slice(0, -1)
    : ring
  return [
    points.reduce((sum, p) => sum + p[0], 0) / points.length,
    points.reduce((sum, p) => sum + p[1], 0) / points.length
  ]
}

function combineAreas(collections: AreaCollection[]): AreaCollection {
  return {
    type: 'FeatureCollection',
    features: collections.flatMap((collection) => collection.features)
  }
}

function styleLayerSource(map: mapboxgl.Map, sourceName: string, data: FeatureCollection) {
  const source = map.getSource(sourceName) as mapboxgl.GeoJSONSource | undefined
  if (source) source.setData(data)
  else map.addSource(sourceName, { type: 'geojson', data })
}

export default function AustriaMap() {
  const mapContainer = useRef<HTMLDivElement>(null)
  const simulationModeRef = useRef(false)
  const [simulationMode, setSimulationMode] = useState(false)
  const [coverageStatus, setCoverageStatus] = useState<CoverageStatus>({
    text: 'Fahrzeitzonen werden geladen …',
    outsideCells: null,
    outsideCropHectares: null,
    outsideCustomers: null
  })
  const [candidateLocation, setCandidateLocation] = useState<string | null>(null)

  useEffect(() => {
    if (!mapContainer.current) return

    const token = import.meta.env.VITE_MAPBOX_TOKEN

    if (!token) {
      console.error('Mapbox-Token fehlt')
      return
    }

    let disposed = false
    let animationTimer: ReturnType<typeof setTimeout> | undefined
    let marker: mapboxgl.Marker | undefined

    // Zuerst die gesamte Erdkugel anzeigen
    const map = new mapboxgl.Map({
      container: mapContainer.current,
      accessToken: token,
      style: 'mapbox://styles/mapbox/satellite-streets-v12',
      projection: 'globe',
      center: [5, 20],
      zoom: 1.5,
      pitch: 0,
      bearing: 0,
      antialias: true
    })

    map.addControl(new mapboxgl.NavigationControl(), 'top-right')

    map.on('load', async () => {
      try {
        // H3-Raster mit Auflösung 6 laden
        const gridResponse = await fetch(
          '/austria_h3_resolution6.geojson'
        )

        if (!gridResponse.ok) {
          throw new Error('H3-Raster konnte nicht geladen werden')
        }

        const grid: FeatureCollection<Polygon> =
          await gridResponse.json()

        // Echte Weizen- und Sojabohnenwerte laden
        const csvResponse = await fetch(
          '/TerraMax_H3_Weizen_Soja_Res6.csv'
        )

        if (!csvResponse.ok) {
          throw new Error('CSV-Datei konnte nicht geladen werden')
        }

        const csvText = await csvResponse.text()
        const rows = csvText.trim().split(/\r?\n/)
        const headers = rows[0]
          .replace(/^\uFEFF/, '')
          .split(',')
          .map((header) => header.trim().replace(/^"|"$/g, ''))

        const h3Column = headers.indexOf('h3Index')
        const sumColumn = headers.indexOf('sum')

        if (h3Column === -1 || sumColumn === -1) {
          throw new Error('CSV-Spalten h3Index oder sum fehlen')
        }

        const hectares = new Map<string, number>()

        for (const row of rows.slice(1)) {
          if (!row.trim()) continue

          const values = row.split(',')
          const h3Index = values[h3Column]
            ?.trim()
            .replace(/^"|"$/g, '')

          const rawSum = values[sumColumn]
            ?.trim()
            .replace(/^"|"$/g, '')

          if (!h3Index || !rawSum) continue

          const value = Number(rawSum)

          if (Number.isFinite(value) && value >= 0) {
            hectares.set(h3Index, value)
          }
        }

        if (disposed) return

        // Raster und Agrardaten miteinander verbinden
        const features: Feature<Polygon>[] = grid.features.map(
          (feature) => {
            const h3Index = String(
              feature.properties?.h3Index ?? ''
            )

            const hektar = hectares.get(h3Index)

            return {
              ...feature,
              properties: {
                ...feature.properties,
                h3Index,
                acker_ha: hektar ?? null,
                status:
                  hektar === undefined ? 'unbekannt' : 'vorläufig',
                color: getAreaColor(hektar)
              }
            }
          }
        )

        map.addSource('h3-grid', {
          type: 'geojson',
          data: {
            type: 'FeatureCollection',
            features
          }
        })

        // H3-Sechsecke zunächst unsichtbar halten
        map.addLayer({
          id: 'h3-fill',
          type: 'fill',
          source: 'h3-grid',
          paint: {
            'fill-color': ['get', 'color'],
            'fill-opacity': 0,
            'fill-opacity-transition': {
              duration: 1200,
              delay: 0
            }
          }
        })

        map.addLayer({
          id: 'h3-lines',
          type: 'line',
          source: 'h3-grid',
          paint: {
            'line-color': '#146b50',
            'line-width': 0.6,
            'line-opacity': 0,
            'line-opacity-transition': {
              duration: 1200,
              delay: 0
            }
          }
        })

        // Pop-up mit echten Hektarwerten
        map.on('click', 'h3-fill', (event) => {
          if (simulationModeRef.current) return
          const cell = event.features?.[0]
          if (!cell) return

          const h3Index = String(
            cell.properties?.h3Index ?? 'unbekannt'
          )

          const value = cell.properties?.acker_ha

          const hektar =
            typeof value === 'number' &&
            Number.isFinite(value)
              ? value
              : null

          const popup = document.createElement('div')
          popup.style.cssText =
            'color:#222;padding:6px;text-align:center;min-width:180px'

          const title = document.createElement('strong')
          title.textContent = 'H3-Zelle – Resolution 6'

          const id = document.createElement('p')
          id.textContent = `ID: ${h3Index}`

          const area = document.createElement('p')
          area.textContent =
            hektar === null
              ? 'Weizen- und Sojabohnenfläche: unbekannt'
              : `Weizen- und Sojabohnenfläche: ${hektar.toLocaleString(
                  'de-DE',
                  {
                    minimumFractionDigits: 2,
                    maximumFractionDigits: 2
                  }
                )} ha`

          const source = document.createElement('p')
          source.textContent =
            hektar === null
              ? 'Datenquelle: kein Flächenwert vorhanden'
              : 'Datenquelle: INVEKOS Österreich 2024 (vorläufig)'

          popup.append(title, id, area, source)

          new mapboxgl.Popup({
            closeButton: true,
            maxWidth: '300px'
          })
            .setLngLat(event.lngLat)
            .setDOMContent(popup)
            .addTo(map)
        })

        map.on('mouseenter', 'h3-fill', () => {
          map.getCanvas().style.cursor = 'pointer'
        })

        map.on('mouseleave', 'h3-fill', () => {
          map.getCanvas().style.cursor = ''
        })


        // Ticket #19: Standorte laden und 60-Minuten-Fahrzeitzonen darstellen.
        // Dieser Block ist unabhängig von der bisherigen H3-Karte.
        const loadCoverage = async () => {
          try {
            const sitesResponse = await fetch('/john_deere_at.geojson')
            if (!sitesResponse.ok) throw new Error('standorte.geojson nicht gefunden')
            const sites = (await sitesResponse.json()) as SiteCollection
            if (!sites.features.length) throw new Error('Keine Teststandorte vorhanden')

            const validSites = sites.features.filter((site) =>
              site.geometry?.type === 'Point' &&
              site.geometry.coordinates.length >= 2
            )
            if (validSites.length !== sites.features.length) {
              throw new Error('Ein Standort hat keine gültigen Koordinaten')
            }

            // Alle Standorte müssen erfolgreich geladen sein, sonst wäre die
            // angezeigte Versorgungslücke fälschlich zu groß.
            const originalAreas = combineAreas(await Promise.all(
              validSites.map((site) => getIsochrone(
                site.geometry.coordinates[0],
                site.geometry.coordinates[1],
                token
              ))
            ))
            if (disposed) return

            styleLayerSource(map, 'ticket19-sites', {
              type: 'FeatureCollection', features: validSites
            })
            map.addLayer({
              id: 'ticket19-sites-points', type: 'circle',
              source: 'ticket19-sites',
              paint: {
                'circle-radius': 7, 'circle-color': '#2563eb',
                'circle-stroke-color': '#fff', 'circle-stroke-width': 2
              }
            })
            map.on('click', 'ticket19-sites-points', (event) => {
              if (simulationModeRef.current) return
              const name = event.features?.[0]?.properties?.name ?? 'Teststandort'
              new mapboxgl.Popup().setLngLat(event.lngLat)
                .setText(String(name)).addTo(map)
            })

            styleLayerSource(map, 'ticket19-areas', originalAreas)
            map.addLayer({
              id: 'ticket19-isochrones', type: 'fill', source: 'ticket19-areas',
              paint: { 'fill-color': '#3b82f6', 'fill-opacity': 0.20 }
            }, 'ticket19-sites-points')
            map.addLayer({
              id: 'ticket19-borders', type: 'line', source: 'ticket19-areas',
              paint: { 'line-color': '#1d4ed8', 'line-width': 2 }
            }, 'ticket19-sites-points')

            // Optional: Kundendaten werden erst erwartet, wenn Nico sie liefert.
            // Bis dahin werden keine erfundenen Kundenzahlen angezeigt.
            let customers: FeatureCollection<Point> | null = null
            const customersResponse = await fetch('/kunden.geojson')
            if (customersResponse.ok) {
              customers = await customersResponse.json() as FeatureCollection<Point>
            }
            if (disposed) return
            if (customers) {
              styleLayerSource(map, 'ticket19-customers', customers)
              map.addLayer({
                id: 'ticket19-customer-points', type: 'circle',
                source: 'ticket19-customers',
                paint: { 'circle-radius': 4, 'circle-color': [
                  'case', ['get', 'outside'], '#ef4444', '#f59e0b'
                ],
                  'circle-stroke-color': '#fff', 'circle-stroke-width': 1 }
              })
            }

            // Ein Kandidaten-Standort wird durch Klick in den Simulationsmodus gesetzt.
            const evaluate = (areas: AreaCollection, simulated: boolean) => {
              const outside = features.filter((f) => !covered(cellCenter(f), areas))
              const outsideKnownHectares = outside.reduce((sum, f) =>
                sum + (typeof f.properties?.acker_ha === 'number'
                  ? f.properties.acker_ha : 0), 0)
              const outsideCustomers = customers
                ? customers.features.filter((customer) =>
                    customer.geometry?.type === 'Point' &&
                    !covered(customer.geometry.coordinates, areas)).length
                : null
              if (customers) {
                const annotatedCustomers: FeatureCollection<Point> = {
                  type: 'FeatureCollection',
                  features: customers.features.map((customer) => ({
                    ...customer,
                    properties: {
                      ...customer.properties,
                      outside: !covered(customer.geometry.coordinates, areas)
                    }
                  }))
                }
                styleLayerSource(map, 'ticket19-customers', annotatedCustomers)
              }

              styleLayerSource(map, 'ticket19-gaps', {
                type: 'FeatureCollection', features: outside
              })
              if (!map.getLayer('ticket19-gap-fill')) {
                map.addLayer({
                  id: 'ticket19-gap-fill', type: 'fill', source: 'ticket19-gaps',
                  paint: { 'fill-color': '#ef4444', 'fill-opacity': 0.23 }
                }, 'h3-lines')
              }
              setCoverageStatus({
                text: simulated ? 'Mit neuem Teststandort' : 'Bestehende Standorte',
                outsideCells: outside.length,
                outsideCropHectares: outsideKnownHectares,
                outsideCustomers
              })
            }
            evaluate(originalAreas, false)

            map.on('click', async (event) => {
              if (!simulationModeRef.current) return
              const { lng, lat } = event.lngLat
              simulationModeRef.current = false
              setSimulationMode(false)
              setCoverageStatus((current) => ({
                ...current, text: 'Neuer Standort: Fahrzeit wird berechnet …'
              }))
              try {
                const candidateArea = await getIsochrone(lng, lat, token)
                if (disposed) return
                marker?.remove()
                marker = new mapboxgl.Marker({ color: '#e11d48' })
                  .setLngLat([lng, lat]).addTo(map)
                const combined = combineAreas([originalAreas, candidateArea])
                styleLayerSource(map, 'ticket19-areas', combined)
                evaluate(combined, true)
                setCandidateLocation(`${lat.toFixed(4)}, ${lng.toFixed(4)}`)
              } catch (error) {
                console.error('Teststandort konnte nicht simuliert werden:', error)
                setCoverageStatus((current) => ({
                  ...current, text: 'Simulation fehlgeschlagen – bitte erneut versuchen'
                }))
              }
            })
          } catch (error) {
            if (disposed) return
            console.error('Ticket #19:', error)
            setCoverageStatus({
              text: 'Fahrzeitzonen nicht verfügbar – API/Token prüfen',
              outsideCells: null, outsideCropHectares: null, outsideCustomers: null
            })
          }
        }
        void loadCoverage()

        // Nach dem Kameraflug die H3-Daten weich einblenden
        const showH3Grid = () => {
          if (disposed) return

          map.setPaintProperty('h3-fill', 'fill-opacity', 0.65)
          map.setPaintProperty('h3-lines', 'line-opacity', 1)
        }

        // Kameraflug vom Globus nach Österreich
        const startFlight = () => {
          if (disposed) return

          map.once('moveend', showH3Grid)

          map.flyTo({
            center: [14.2, 47.6],
            zoom: 6.1,
            pitch: 0,
            bearing: 0,
            duration: 6000,
            curve: 1.5,
            essential: true
          })
        }

        // Die Erdkugel zuerst kurz sichtbar lassen
        animationTimer = setTimeout(startFlight, 1500)

        console.log(
          `H3-Raster geladen: ${features.length} Zellen`
        )
        console.log(
          `Zellen mit Agrardaten: ${hectares.size}`
        )
      } catch (error) {
        if (!disposed) {
          console.error('Fehler beim Laden der Daten:', error)
        }
      }
    })

    return () => {
      disposed = true
      if (animationTimer) clearTimeout(animationTimer)
      marker?.remove()
      map.remove()
    }
  }, [])

  return (
    <div style={{ position: 'relative', width: '100%' }}>
      <div
        ref={mapContainer}
        style={{
          width: '100%',
          height: '600px',
          background: '#101827'
        }}
      />

      <div
        style={{
          position: 'absolute', top: '12px', left: '12px', zIndex: 3,
          background: 'rgba(255,255,255,0.96)', color: '#222',
          borderRadius: '8px', padding: '12px', fontSize: '12px',
          maxWidth: '280px', boxShadow: '0 2px 10px rgba(0,0,0,0.2)'
        }}
      >
        <strong>Ticket #19 – Fahrzeitabdeckung (60 Min.)</strong>
        <p style={{ margin: '8px 0' }}>{coverageStatus.text}</p>
        {coverageStatus.outsideCells !== null && (
          <>
            <div>H3-Zellen außerhalb: <strong>{coverageStatus.outsideCells}</strong></div>
            <div>Weizen + Soja in diesen Zellen: <strong>{
              coverageStatus.outsideCropHectares?.toLocaleString('de-DE', {
                maximumFractionDigits: 0
              }) ?? '–'} ha</strong></div>
            <div>Kunden außerhalb: <strong>{
              coverageStatus.outsideCustomers === null
                ? 'Noch keine Kundendaten' : coverageStatus.outsideCustomers
            }</strong></div>
            <p style={{ fontSize: '10px', color: '#555' }}>
              H3-Zellen über Mittelpunkt klassifiziert (Näherung).
              Hektar beziehen sich nur auf bekannte Weizen- und Sojaflächen.
            </p>
          </>
        )}
        <button type="button" onClick={() => {
          simulationModeRef.current = !simulationModeRef.current
          setSimulationMode(simulationModeRef.current)
        }} style={{ marginTop: '6px', padding: '8px', cursor: 'pointer' }}>
          {simulationMode ? 'Simulation abbrechen' : 'Neuen Standort auf Karte setzen'}
        </button>
        {simulationMode && <p>Jetzt auf die Karte klicken.</p>}
        {candidateLocation && <p>Teststandort: {candidateLocation}</p>}
      </div>

      <div
        style={{
          position: 'absolute',
          bottom: '20px',
          left: '15px',
          zIndex: 2,
          background: 'rgba(255,255,255,0.95)',
          color: '#222',
          padding: '12px',
          borderRadius: '8px',
          boxShadow: '0 2px 10px rgba(0,0,0,0.2)',
          fontSize: '12px',
          textAlign: 'left',
          maxWidth: '230px'
        }}
      >
        <strong>Weizen + Sojabohnen pro H3-Zelle</strong>

        {AREA_CLASSES.map((item) => (
          <div
            key={item.label}
            style={{
              display: 'flex',
              alignItems: 'center',
              gap: '8px',
              marginTop: '7px'
            }}
          >
            <span
              style={{
                display: 'inline-block',
                width: '16px',
                height: '16px',
                flexShrink: 0,
                background: item.color,
                border: '1px solid #aaa'
              }}
            />
            {item.label}
          </div>
        ))}

        <div
          style={{
            display: 'flex',
            alignItems: 'center',
            gap: '8px',
            marginTop: '7px'
          }}
        >
          <span
            style={{
              display: 'inline-block',
              width: '16px',
              height: '16px',
              flexShrink: 0,
              background: COLORS.unknown,
              border: '1px solid #aaa'
            }}
          />
          Unbekannt
        </div>

        <div
          style={{
            marginTop: '10px',
            fontSize: '10px',
            color: '#666'
          }}
        >
          INVEKOS 2024 – vorläufige Auswertung
        </div>
      </div>
    </div>
  )
}

