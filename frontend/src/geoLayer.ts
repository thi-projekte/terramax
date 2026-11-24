import type { FeatureCollection, Polygon, MultiPolygon } from 'geojson'
import keycloak from './keycloak'

const GEO_SERVICE_URL: string =
  import.meta.env.VITE_GEO_SERVICE_URL || 'http://localhost:8083'

export type GeoProperties = {
  h3Index: string
  cropType: string | null
  areaHa: number | null
  source: string | null
  dataDate: string | null
  confidence: string | null
}

export type GeoCollection = FeatureCollection<
  Polygon | MultiPolygon,
  GeoProperties
>

export async function fetchGeoCells(): Promise<GeoCollection> {
  await keycloak.updateToken(30)

  const response = await fetch(`${GEO_SERVICE_URL}/api/geo/h3`, {
    headers: {
      Authorization: `Bearer ${keycloak.token}`
    }
  })

  if (!response.ok) {
    throw new Error(`Geo-Service antwortet mit HTTP ${response.status}`)
  }

  const data = await response.json()

  if (data?.type !== 'FeatureCollection' || !Array.isArray(data.features)) {
    throw new Error('Geo-Service liefert keine FeatureCollection')
  }

  return data
}