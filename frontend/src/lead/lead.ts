import { speechFetch } from '../speech/speechApi'

export type LeadSource = 'MESSE' | 'BESUCH' | 'MANUELL'

export interface LeadContact {
    name: string | null
    company: string | null
    role: string | null
    phone: string | null
    email: string | null
    street: string | null
    postalCode: string | null
    city: string | null
}

export interface LeadSubmission {
    captureId: string
    source: LeadSource
    eventName: string | null
    contact: LeadContact
    latitude: number | null
    longitude: number | null
    comment: string | null
    transcript: string | null
    existingCustomerId: number | null
}

// Antwort von POST /leads (LeadDto im terramax-service). Weitere Felder werden ignoriert.
export interface SavedLead {
    customerId: number
    newCustomer: boolean
    status: 'LEAD' | 'CUSTOMER'
    name: string
}

// Übergibt den bestätigten Lead an TerraMax. Der speech-service reicht das Token des Nutzers durch,
// TerraMax speichert den Lead mit ihm als Ersteller.
export function submitLead(lead: LeadSubmission): Promise<SavedLead> {
    return speechFetch<SavedLead>(
        '/leads',
        {
            method: 'POST',
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify(lead),
        },
        'Lead konnte nicht an TerraMax übergeben werden',
    )
}

// Kennung einer Erfassung für Events (speech-service); geht auch als visitId an /transcriptions.
export function newCaptureId(): string {
    return `lead:${crypto.randomUUID()}`
}

const MAPBOX_TOKEN: string | undefined = import.meta.env.VITE_MAPBOX_TOKEN

// Adresse → Koordinaten über die Mapbox-Geocoding-API (gleicher Token wie die Karte). Die Karte und die
// Empfehlungen (#126) brauchen Koordinaten. Ohne Token, ohne Adresse oder bei einem Fehler: null.
export async function geocode(contact: LeadContact): Promise<{ latitude: number; longitude: number } | null> {
    const query = [contact.street, contact.postalCode, contact.city].filter(Boolean).join(', ')
    if (!MAPBOX_TOKEN || !contact.city || query === '') return null
    try {
        const params = new URLSearchParams({ q: query, country: 'at,de', limit: '1', access_token: MAPBOX_TOKEN })
        const response = await fetch(`https://api.mapbox.com/search/geocode/v6/forward?${params}`)
        if (!response.ok) return null
        const data = await response.json()
        const coordinates: unknown = data?.features?.[0]?.geometry?.coordinates
        if (!Array.isArray(coordinates) || coordinates.length < 2) return null
        const [longitude, latitude] = coordinates as number[]
        return { latitude, longitude }
    } catch {
        return null
    }
}
