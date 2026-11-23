import type { Protokoll } from '../tour/engineApi'
import type { VisitProtocol } from './protocol'
import { speechFetch } from './speechApi'

// Antwort von POST /tours/{tourId}/protocols (ProtocolDto im terramax-service). Weitere Felder werden ignoriert.
export interface SavedProtocol {
    id: number
    tourId: number
    customerId: number
    createdBy: string
    createdAt: string
}

// Übergibt ein bestätigtes Protokoll an TerraMax. Der speech-service reicht das Token des Nutzers durch,
// TerraMax speichert das Protokoll mit ihm als Ersteller.
export function submitProtocol(
    tourId: number,
    customerId: number,
    protocol: VisitProtocol,
    transcript: string | null,
): Promise<SavedProtocol> {
    return speechFetch<SavedProtocol>(
        `/tours/${tourId}/protocols`,
        {
            method: 'POST',
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify({ customerId, ...protocol, transcript }),
        },
        'Protokoll konnte nicht an TerraMax übergeben werden',
    )
}

// --- Umrechnung zwischen speech-service (Listen) und Tour-Formular (ein Eintrag pro Zeile) ---

function lines(text: string): string[] {
    return text
        .split('\n')
        .map((line) => line.trim())
        .filter((line) => line !== '')
}

function blankToNull(text: string): string | null {
    const trimmed = text.trim()
    return trimmed === '' ? null : trimmed
}

export function toProtokoll(protocol: VisitProtocol): Protokoll {
    return {
        gespraechspartner: protocol.contactPerson ?? '',
        themen: protocol.topics.join('\n'),
        ergebnis: protocol.outcome ?? '',
        naechsteSchritte: protocol.nextSteps.join('\n'),
        followUpDatum: protocol.followUpDate ?? '',
    }
}

export function toVisitProtocol(protokoll: Protokoll): VisitProtocol {
    return {
        contactPerson: blankToNull(protokoll.gespraechspartner),
        topics: lines(protokoll.themen),
        outcome: blankToNull(protokoll.ergebnis),
        nextSteps: lines(protokoll.naechsteSchritte),
        followUpDate: blankToNull(protokoll.followUpDatum),
    }
}
