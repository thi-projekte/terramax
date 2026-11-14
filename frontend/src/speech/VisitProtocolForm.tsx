import { useId, useState, type FormEvent } from 'react'
import type { VisitProtocol } from './protocol'

interface VisitProtocolFormProps {
    initial: VisitProtocol
    transcript: string | null
    aiGenerated: boolean
    onConfirm: (protocol: VisitProtocol) => void
    onDiscard: () => void
}

// Listen werden als eine Zeile pro Eintrag bearbeitet
function toLines(items: string[]) {
    return items.join('\n')
}

function fromLines(text: string) {
    return text
        .split('\n')
        .map((line) => line.trim())
        .filter((line) => line !== '')
}

function orNull(text: string) {
    const trimmed = text.trim()
    return trimmed === '' ? null : trimmed
}

function VisitProtocolForm({ initial, transcript, aiGenerated, onConfirm, onDiscard }: VisitProtocolFormProps) {
    const id = useId()
    const [contactPerson, setContactPerson] = useState(initial.contactPerson ?? '')
    const [topics, setTopics] = useState(toLines(initial.topics))
    const [outcome, setOutcome] = useState(initial.outcome ?? '')
    const [nextSteps, setNextSteps] = useState(toLines(initial.nextSteps))
    const [followUpDate, setFollowUpDate] = useState(initial.followUpDate ?? '')

    function handleSubmit(event: FormEvent) {
        event.preventDefault()
        onConfirm({
            contactPerson: orNull(contactPerson),
            topics: fromLines(topics),
            outcome: orNull(outcome),
            nextSteps: fromLines(nextSteps),
            followUpDate: orNull(followUpDate),
        })
    }

    return (
        <form className="protocol-form" onSubmit={handleSubmit}>
            {aiGenerated && <p className="protocol-hint">Von der AI vorausgefüllt. Bitte prüfen und korrigieren.</p>}
            {transcript && (
                <details>
                    <summary>Transkript anzeigen</summary>
                    <p className="protocol-transcript">{transcript}</p>
                </details>
            )}

            <label htmlFor={`${id}-contact`}>Gesprächspartner</label>
            <input
                id={`${id}-contact`}
                value={contactPerson}
                onChange={(e) => setContactPerson(e.target.value)}
            />

            <label htmlFor={`${id}-topics`}>Themen (eine Zeile pro Thema)</label>
            <textarea id={`${id}-topics`} rows={3} value={topics} onChange={(e) => setTopics(e.target.value)} />

            <label htmlFor={`${id}-outcome`}>Ergebnis</label>
            <textarea id={`${id}-outcome`} rows={3} value={outcome} onChange={(e) => setOutcome(e.target.value)} />

            <label htmlFor={`${id}-steps`}>Nächste Schritte (eine Zeile pro Schritt)</label>
            <textarea
                id={`${id}-steps`}
                rows={3}
                value={nextSteps}
                onChange={(e) => setNextSteps(e.target.value)}
            />

            <label htmlFor={`${id}-followup`}>Follow-up-Datum</label>
            <input
                id={`${id}-followup`}
                type="date"
                value={followUpDate}
                onChange={(e) => setFollowUpDate(e.target.value)}
            />

            <div className="protocol-actions">
                <button type="submit">Protokoll bestätigen</button>
                <button type="button" onClick={onDiscard}>
                    Verwerfen
                </button>
            </div>
        </form>
    )
}

export default VisitProtocolForm
