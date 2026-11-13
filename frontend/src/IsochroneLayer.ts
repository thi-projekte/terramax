
import type { FeatureCollection, Polygon, MultiPolygon } from 'geojson'

export async function getIsochrone(
  longitude: number,
  latitude: number,
  token: string
): Promise<FeatureCollection<Polygon | MultiPolygon>> {
  const url = new URL(
    `https://api.mapbox.com/isochrone/v1/mapbox/driving/${longitude},${latitude}`
  )

  url.searchParams.set('contours_minutes', '60')
  url.searchParams.set('polygons', 'true')
  url.searchParams.set('access_token', token)

  const response = await fetch(url.toString())

  if (!response.ok) {
    throw new Error(
      `Isochrone konnte nicht geladen werden: ${response.status}`
    )
  }

  return await response.json()
}
