import { speechFetch } from '../speech/speechApi'

// Entwurf aus POST /business-cards (BusinessCard im speech-service). Nicht lesbare Felder sind null.
export interface BusinessCard {
    contactName: string | null
    company: string | null
    role: string | null
    phone: string | null
    email: string | null
    street: string | null
    postalCode: string | null
    city: string | null
}

interface BusinessCardResponse {
    captureId: string
    card: BusinessCard
}

// Handyfotos sind oft 4000 px breit und mehrere MB groß. Für die Texterkennung reichen 1600 px,
// das spart Upload-Zeit im Messe-WLAN und bleibt sicher unter dem Upload-Limit (10 MB).
const MAX_EDGE = 1600

async function verkleinern(file: File): Promise<Blob> {
    try {
        const bitmap = await createImageBitmap(file)
        const scale = Math.min(1, MAX_EDGE / Math.max(bitmap.width, bitmap.height))
        const canvas = document.createElement('canvas')
        canvas.width = Math.round(bitmap.width * scale)
        canvas.height = Math.round(bitmap.height * scale)
        canvas.getContext('2d')?.drawImage(bitmap, 0, 0, canvas.width, canvas.height)
        bitmap.close()
        const blob = await new Promise<Blob | null>((resolve) => canvas.toBlob(resolve, 'image/jpeg', 0.85))
        return blob ?? file
    } catch {
        // Format kann der Browser nicht dekodieren: Original schicken, der Service meldet ggf. 415
        return file
    }
}

// Liest die Visitenkarte über den speech-service (KI). Das Foto wird dort nicht gespeichert.
export async function readBusinessCard(file: File, captureId: string): Promise<BusinessCard> {
    const image = await verkleinern(file)
    const body = new FormData()
    body.append('image', image, 'visitenkarte.jpg')
    body.append('captureId', captureId)

    const response = await speechFetch<BusinessCardResponse>(
        '/business-cards',
        { method: 'POST', body },
        'Visitenkarte konnte nicht gelesen werden',
    )
    return response.card
}
