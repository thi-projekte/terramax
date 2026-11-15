export type Customer = {
  id: string
  name: string
  region: string
  segment: string
  lat: number
  lng: number
  crmUrl: string | null
}

const CRM_EXPORT_URL = '/crm_export_mock.csv'

export async function loadCustomers(): Promise<Customer[]> {
  const response = await fetch(CRM_EXPORT_URL)
  if (!response.ok) throw new Error('CRM-Export konnte nicht geladen werden')

  const text = (await response.text()).replace(/^\uFEFF/, '').trim()
  const lines = text.split(/\r?\n/)
  const headers = lines[0].split(';').map((h) => h.trim())

  const idCol = headers.indexOf('customer_id')
  const nameCol = headers.indexOf('name')
  const regionCol = headers.indexOf('region')
  const latCol = headers.indexOf('lat')
  const lonCol = headers.indexOf('lon')
  const segmentCol = headers.indexOf('segment')
  const urlCol = headers.indexOf('crm_url')

  if (idCol === -1 || latCol === -1 || lonCol === -1) {
    throw new Error('CRM-Export: Spalten customer_id, lat oder lon fehlen')
  }

  const customers: Customer[] = []
  for (const line of lines.slice(1)) {
    if (!line.trim()) continue
    const values = line.split(';').map((v) => v.trim())
    const lat = Number(values[latCol])
    const lng = Number(values[lonCol])
    if (!values[idCol] || !Number.isFinite(lat) || !Number.isFinite(lng)) continue

    customers.push({
      id: values[idCol],
      name: values[nameCol] ?? '',
      region: values[regionCol] ?? '',
      segment: values[segmentCol] ?? '',
      lat,
      lng,
      crmUrl: values[urlCol] || null
    })
  }
  return customers
}