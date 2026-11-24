export type Customer = {
  id: string
  name: string
  region: string
  segment: string
  lat: number
  lng: number
  crmUrl: string | null
}

type CustomerFeature = {
  properties?: Record<string, unknown>
  geometry?: { type?: string; coordinates?: number[] }
}

const CUSTOMER_URL = '/kunden.geojson'

export async function loadCustomers(): Promise<Customer[]> {
  const response = await fetch(CUSTOMER_URL)
  if (!response.ok) throw new Error('Kundendaten konnten nicht geladen werden')

  const data = (await response.json()) as { features?: CustomerFeature[] }
  const customers: Customer[] = []

  for (const feature of data.features ?? []) {
    const [lng, lat] = feature.geometry?.coordinates ?? []
    const props = feature.properties ?? {}
    const id = String(props.customer_id ?? props.id ?? '')
    if (!id || typeof lat !== 'number' || typeof lng !== 'number') continue

    customers.push({
      id,
      name: String(props.name ?? ''),
      region: String(props.region ?? ''),
      segment: String(props.segment ?? ''),
      lat,
      lng,
      crmUrl: typeof props.crm_url === 'string' && props.crm_url ? props.crm_url : null
    })
  }
  return customers
}