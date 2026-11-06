import keycloak from '../keycloak'

const SPEECH_API_URL: string = import.meta.env.VITE_SPEECH_API_URL ?? 'http://localhost:8082'

// Ruft den speech-service mit gültigem Token auf. Bei Fehlern liefert der Service { message }.
export async function speechFetch<T>(path: string, init: RequestInit, fallbackError: string): Promise<T> {
    try {
        await keycloak.updateToken(30)
    } catch {
        // Sitzung abgelaufen -> neu anmelden
        await keycloak.login()
    }

    const response = await fetch(`${SPEECH_API_URL}${path}`, {
        ...init,
        headers: { ...init.headers, Authorization: `Bearer ${keycloak.token}` },
    })

    if (!response.ok) {
        const error = await response.json().catch(() => null)
        throw new Error(error?.message ?? `${fallbackError} (Status ${response.status})`)
    }
    return response.json()
}
