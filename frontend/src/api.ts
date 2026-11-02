import keycloak from './keycloak'

const API_URL: string = import.meta.env.VITE_API_URL ?? 'http://localhost:8080'

export type ApiResult = {
  status: number
  body: unknown
}

// Ruft das Backend mit dem Access Token auf (Authorization: Bearer).
// Vorher wird der Token erneuert, falls er in weniger als 30 Sekunden abläuft.
export async function apiGet(path: string): Promise<ApiResult> {
  try {
    await keycloak.updateToken(30)
  } catch {
    // Sitzung abgelaufen -> neu anmelden
    await keycloak.login()
  }

  const response = await fetch(`${API_URL}${path}`, {
    headers: { Authorization: `Bearer ${keycloak.token}` },
  })

  const text = await response.text()
  let body: unknown = text
  try {
    body = text ? JSON.parse(text) : null
  } catch {
    // keine JSON-Antwort, Text wird so angezeigt
  }
  return { status: response.status, body }
}
