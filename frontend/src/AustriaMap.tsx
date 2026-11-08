
import type { Feature, FeatureCollection, Polygon } from 'geojson'
import { useEffect, useRef } from 'react'
import mapboxgl from 'mapbox-gl'
import 'mapbox-gl/dist/mapbox-gl.css'

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

export default function AustriaMap() {
  const mapContainer = useRef<HTMLDivElement>(null)

  useEffect(() => {
    if (!mapContainer.current) return

    const token = import.meta.env.VITE_MAPBOX_TOKEN

    if (!token) {
      console.error('Mapbox-Token fehlt')
      return
    }

    let disposed = false
    let animationTimer: ReturnType<typeof setTimeout> | undefined

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
