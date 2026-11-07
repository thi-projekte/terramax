import type { Feature, FeatureCollection, Point } from 'geojson'
import mapboxgl from 'mapbox-gl'
import keycloak from './keycloak'

// Händlerstandorte aus dem location-service als geclusterte Kreis-Layer (eine Source pro Kategorie)

const LOCATION_SERVICE_URL: string = (
  import.meta.env.VITE_LOCATION_SERVICE_URL || 'http://localhost:8082'
).replace(/\/+$/, '')

export const LOCATION_CATEGORIES = [
  'competitor',
  'mixed_competitor',
  'john_deere'
] as const

export type LocationCategory = (typeof LOCATION_CATEGORIES)[number]

export const CATEGORY_STYLE: Record<
  LocationCategory,
  { color: string; label: string; suffix?: string }
> = {
  competitor: { color: '#dc2626', label: 'Wettbewerber' },
  mixed_competitor: { color: '#f97316', label: 'Mehrmarken/unbekannt' },
  john_deere: { color: '#367C2B', label: 'John Deere', suffix: 'Beispieldaten' }
}

/** properties eines Standort-Features laut API-Vertrag (Felder können fehlen oder null sein). */
export type LocationProperties = {
  id?: string | null
  name?: string | null
  category?: string | null
  brands?: string | null
  brand_known?: boolean | null
  street?: string | null
  postcode?: string | null
  city?: string | null
  source_type?: string | null
  source_url?: string | null
  more_sources?: string | null
  retrieved_at?: string | null
  verified?: boolean | null
  note?: string | null
}

export type LocationCollection = FeatureCollection<Point, LocationProperties>

/** Lädt alle Standorte. Wirft bei Netzwerkfehler, abgelaufener Sitzung oder HTTP-Fehler (z. B. 401). */
export async function fetchLocations(): Promise<LocationCollection> {
  // Token erneuern, falls er in weniger als 30 Sekunden abläuft
  await keycloak.updateToken(30)

  const response = await fetch(`${LOCATION_SERVICE_URL}/locations`, {
    headers: { Authorization: `Bearer ${keycloak.token}` }
  })

  if (!response.ok) {
    throw new Error(`location-service antwortet mit HTTP ${response.status}`)
  }

  const data: unknown = await response.json()
  if (!isFeatureCollection(data)) {
    throw new Error('location-service liefert keine FeatureCollection')
  }
  return data
}

function isFeatureCollection(data: unknown): data is LocationCollection {
  return (
    typeof data === 'object' &&
    data !== null &&
    (data as { type?: unknown }).type === 'FeatureCollection' &&
    Array.isArray((data as { features?: unknown }).features)
  )
}

// ---------- Reine Funktionen (ohne Karte und ohne DOM) ----------

/** true nur für gültige absolute https-URLs. */
export function isSafeHttpsUrl(value: unknown): value is string {
  if (typeof value !== 'string') return false
  try {
    return new URL(value).protocol === 'https:'
  } catch {
    return false
  }
}

/** Anzahl der Standorte je Kategorie; unbekannte Kategorien werden ignoriert. */
export function countByCategory(
  features: Feature<Point, LocationProperties | null>[]
): Record<LocationCategory, number> {
  const counts: Record<LocationCategory, number> = {
    competitor: 0,
    mixed_competitor: 0,
    john_deere: 0
  }
  for (const feature of features) {
    const category = feature.properties?.category
    if (isLocationCategory(category)) counts[category]++
  }
  return counts
}

export function isLocationCategory(value: unknown): value is LocationCategory {
  return (LOCATION_CATEGORIES as readonly unknown[]).includes(value)
}

export type PopupContent = {
  name: string
  brands: string | null
  address: string | null
  source: { text: string; href: string | null } | null
  retrieved: string
  note: string | null
}

function text(value: unknown): string | null {
  if (value === null || value === undefined) return null
  const result = String(value).trim()
  return result === '' ? null : result
}

/** Wandelt YYYY-MM-DD in TT.MM.JJJJ um, andere Werte bleiben unverändert. */
function formatDate(value: string): string {
  const match = /^(\d{4})-(\d{2})-(\d{2})$/.exec(value)
  return match ? `${match[3]}.${match[2]}.${match[1]}` : value
}

/** Bereitet die Popup-Texte auf. Fehlende Teile werden weggelassen bzw. als null geliefert. */
export function buildPopupContent(props: LocationProperties): PopupContent {
  const brands = text(props.brands)
  const street = text(props.street)
  const place = [text(props.postcode), text(props.city)]
    .filter(Boolean)
    .join(' ')
  const address = [street, place].filter(Boolean).join(', ')

  const sourceUrl = text(props.source_url)
  const retrievedAt = text(props.retrieved_at)
  const verified = props.verified === true ? '(geprüft)' : '(ungeprüft)'

  return {
    name: text(props.name) ?? 'Unbenannter Standort',
    brands: brands
      ? `Marken: ${brands}`
      : props.brand_known === false
        ? 'Marken: unbekannt'
        : null,
    address: address || null,
    source: sourceUrl
      ? {
          text: sourceUrl,
          href: isSafeHttpsUrl(sourceUrl) ? sourceUrl : null
        }
      : null,
    retrieved: retrievedAt
      ? `Abgerufen am ${formatDate(retrievedAt)} ${verified}`
      : verified,
    note: text(props.note)
  }
}

// ---------- DOM ----------

/** Baut das Popup ausschließlich über DOM-APIs und textContent (kein innerHTML). */
export function createPopupElement(content: PopupContent): HTMLElement {
  const root = document.createElement('div')
  root.style.cssText = 'color:#222;padding:4px;min-width:200px;font-size:12px'

  const title = document.createElement('strong')
  title.textContent = content.name
  root.append(title)

  const line = (value: string) => {
    const p = document.createElement('p')
    p.style.margin = '4px 0 0'
    p.textContent = value
    root.append(p)
    return p
  }

  if (content.brands) line(content.brands)
  if (content.address) line(content.address)

  if (content.source) {
    const p = line('Quelle: ')
    if (content.source.href) {
      const link = document.createElement('a')
      link.href = content.source.href
      link.target = '_blank'
      link.rel = 'noopener noreferrer'
      link.textContent = new URL(content.source.href).hostname
      p.append(link)
    } else {
      p.append(document.createTextNode(content.source.text))
    }
  }

  line(content.retrieved)

  if (content.note) {
    const note = line(content.note)
    note.style.color = '#555'
    note.style.fontStyle = 'italic'
  }

  return root
}

// ---------- Karte ----------

const sourceId = (c: LocationCategory) => `locations-${c}`
const clusterLayerId = (c: LocationCategory) => `locations-${c}-clusters`
const countLayerId = (c: LocationCategory) => `locations-${c}-count`
const pointLayerId = (c: LocationCategory) => `locations-${c}-points`

const layerIds = (c: LocationCategory) => [
  clusterLayerId(c),
  countLayerId(c),
  pointLayerId(c)
]

/** Fügt pro Kategorie eine geclusterte Source mit Cluster-, Zahl- und Punkt-Layer hinzu. */
export function addLocationLayers(
  map: mapboxgl.Map,
  data: LocationCollection
): void {
  for (const category of LOCATION_CATEGORIES) {
    const { color } = CATEGORY_STYLE[category]

    map.addSource(sourceId(category), {
      type: 'geojson',
      data: {
        type: 'FeatureCollection',
        features: data.features.filter(
          (f) => f.properties?.category === category
        )
      },
      cluster: true,
      clusterRadius: 40,
      clusterMaxZoom: 12
    })

    map.addLayer({
      id: clusterLayerId(category),
      type: 'circle',
      source: sourceId(category),
      filter: ['has', 'point_count'],
      paint: {
        'circle-color': color,
        'circle-opacity': 0.85,
        'circle-radius': ['step', ['get', 'point_count'], 14, 10, 18, 30, 24],
        'circle-stroke-color': '#ffffff',
        'circle-stroke-width': 2
      }
    })

    map.addLayer({
      id: countLayerId(category),
      type: 'symbol',
      source: sourceId(category),
      filter: ['has', 'point_count'],
      layout: {
        'text-field': ['get', 'point_count_abbreviated'],
        'text-font': ['DIN Pro Medium', 'Arial Unicode MS Bold'],
        'text-size': 12,
        'text-allow-overlap': true
      },
      paint: { 'text-color': '#ffffff' }
    })

    map.addLayer({
      id: pointLayerId(category),
      type: 'circle',
      source: sourceId(category),
      filter: ['!', ['has', 'point_count']],
      paint: {
        'circle-color': color,
        'circle-radius': 7,
        'circle-stroke-color': '#ffffff',
        'circle-stroke-width': 2
      }
    })

    for (const layer of [clusterLayerId(category), pointLayerId(category)]) {
      map.on('mouseenter', layer, () => {
        map.getCanvas().style.cursor = 'pointer'
      })
      map.on('mouseleave', layer, () => {
        map.getCanvas().style.cursor = ''
      })
    }
  }

  // Ein gemeinsamer Klick-Handler, damit überlappende Kategorien nicht mehrere Popups öffnen
  map.on('click', (event) => {
    const feature = getLocationAt(map, event.point)
    if (!feature) return

    const category = LOCATION_CATEGORIES.find(
      (c) => feature.layer?.id === clusterLayerId(c)
    )
    if (category) {
      zoomIntoCluster(map, category, feature)
      return
    }

    const coordinates = (feature.geometry as Point).coordinates
    new mapboxgl.Popup({ closeButton: true, maxWidth: '300px' })
      .setLngLat([coordinates[0], coordinates[1]])
      .setDOMContent(
        createPopupElement(
          buildPopupContent((feature.properties ?? {}) as LocationProperties)
        )
      )
      .addTo(map)
  })
}

function zoomIntoCluster(
  map: mapboxgl.Map,
  category: LocationCategory,
  feature: mapboxgl.GeoJSONFeature
): void {
  const clusterId = feature.properties?.cluster_id
  const source = map.getSource(sourceId(category)) as
    | mapboxgl.GeoJSONSource
    | undefined
  if (typeof clusterId !== 'number' || !source) return

  source.getClusterExpansionZoom(clusterId, (error, zoom) => {
    if (error || zoom === null || zoom === undefined) return
    const coordinates = (feature.geometry as Point).coordinates
    map.easeTo({ center: [coordinates[0], coordinates[1]], zoom })
  })
}

/**
 * Oberster sichtbarer Standort (Punkt oder Cluster) an der Bildschirmposition, sonst undefined. Wird auch vom
 * H3-Layer genutzt: Liegt dort ein Standort, hat dieser Vorrang.
 */
export function getLocationAt(
  map: mapboxgl.Map,
  point: mapboxgl.PointLike
): mapboxgl.GeoJSONFeature | undefined {
  const layers = LOCATION_CATEGORIES.flatMap((c) => [
    pointLayerId(c),
    clusterLayerId(c)
  ]).filter((id) => map.getLayer(id))
  if (layers.length === 0) return undefined
  return map.queryRenderedFeatures(point, { layers })[0]
}

/** Blendet alle Layer einer Kategorie ein oder aus (für die Checkboxen der Legende). */
export function setCategoryVisible(
  map: mapboxgl.Map,
  category: LocationCategory,
  visible: boolean
): void {
  for (const id of layerIds(category)) {
    if (map.getLayer(id)) {
      map.setLayoutProperty(id, 'visibility', visible ? 'visible' : 'none')
    }
  }
}
