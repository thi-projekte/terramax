import { speechFetch } from './speechApi'

// Felder wie im speech-service (VisitProtocol). followUpDate im Format JJJJ-MM-TT.
export interface VisitProtocol {
    contactPerson: string | null
    topics: string[]
    outcome: string | null
    nextSteps: string[]
    followUpDate: string | null
}

export const EMPTY_PROTOCOL: VisitProtocol = {
    contactPerson: null,
    topics: [],
    outcome: null,
    nextSteps: [],
    followUpDate: null,
}

interface ProtocolResponse {
    visitId: string
    protocol: VisitProtocol
}

// Erstellt per AI einen Entwurf. Gespeichert wird erst das bestätigte Protokoll.
export async function generateProtocol(visitId: string, transcript: string): Promise<VisitProtocol> {
    const response = await speechFetch<ProtocolResponse>(
        '/protocols',
        {
            method: 'POST',
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify({ visitId, transcript }),
        },
        'Protokoll konnte nicht erstellt werden',
    )
    return response.protocol
}
