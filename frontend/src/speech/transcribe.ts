import keycloak from '../keycloak'

export interface Transcription {
    visitId: string
    transcript: string
    language: string
    durationSeconds: number
}

const FILE_EXTENSIONS: Record<string, string> = {
    'audio/webm': 'webm',
    'audio/mp4': 'mp4',
    'audio/ogg': 'ogg',
}

const SPEECH_API_URL: string = import.meta.env.VITE_SPEECH_API_URL ?? 'http://localhost:8082'

export async function transcribe(audio: Blob, visitId: string): Promise<Transcription> {
    try {
        await keycloak.updateToken(30)
    } catch {
        // Sitzung abgelaufen -> neu anmelden
        await keycloak.login()
    }

    const extension = FILE_EXTENSIONS[audio.type.split(';')[0]] ?? 'webm'
    const body = new FormData()
    body.append('audio', audio, `voice-note.${extension}`)
    body.append('visitId', visitId)

    const response = await fetch(`${SPEECH_API_URL}/transcriptions`, {
        method: 'POST',
        headers: { Authorization: `Bearer ${keycloak.token}` },
        body,
    })

    if (!response.ok) {
        // Der speech-service liefert bei Fehlern { message }, z. B. wenn das Modell noch lädt
        const error = await response.json().catch(() => null)
        throw new Error(error?.message ?? `Transkription fehlgeschlagen (Status ${response.status})`)
    }
    return response.json()
}
