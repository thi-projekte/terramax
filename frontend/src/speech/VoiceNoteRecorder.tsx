import { useEffect, useMemo, useState } from 'react'
import { EMPTY_PROTOCOL, generateProtocol, type VisitProtocol } from './protocol'
import { transcribe } from './transcribe'
import { MAX_RECORDING_SECONDS, useVoiceRecorder } from './useVoiceRecorder'
import VisitProtocolForm from './VisitProtocolForm'

interface VoiceNoteRecorderProps {
    visitId: string
    onConfirmed: (protocol: VisitProtocol) => void
}

type Step = 'idle' | 'transcribing' | 'generating'

const STEP_TEXT: Record<Step, string> = {
    idle: 'Protokoll erstellen',
    transcribing: 'Wird transkribiert…',
    generating: 'Protokoll wird erstellt…',
}

function formatTime(totalSeconds: number) {
    const minutes = Math.floor(totalSeconds / 60)
    const seconds = String(totalSeconds % 60).padStart(2, '0')
    return `${minutes}:${seconds}`
}

// Ablauf: aufnehmen -> transkribieren -> AI-Entwurf -> bearbeiten -> bestätigen
function VoiceNoteRecorder({ visitId, onConfirmed }: VoiceNoteRecorderProps) {
    const { isRecording, elapsedSeconds, audio, error, start, stop } = useVoiceRecorder()
    const [step, setStep] = useState<Step>('idle')
    const [stepError, setStepError] = useState<string | null>(null)
    const [transcript, setTranscript] = useState<string | null>(null)
    const [draft, setDraft] = useState<VisitProtocol | null>(null)
    const [isManual, setIsManual] = useState(false)
    const audioUrl = useMemo(() => (audio ? URL.createObjectURL(audio) : null), [audio])
    const isBusy = step !== 'idle'

    useEffect(() => {
        return () => {
            if (audioUrl) URL.revokeObjectURL(audioUrl)
        }
    }, [audioUrl])

    async function handleStart() {
        setStepError(null)
        setTranscript(null)
        setDraft(null)
        await start()
    }

    // Aufnahme und Transkript bleiben bei einem Fehler erhalten. Ein erneuter Versuch
    // transkribiert deshalb nicht noch einmal, wenn nur die AI fehlgeschlagen ist.
    async function handleCreate() {
        if (!audio) return
        setStepError(null)
        try {
            let text = transcript
            if (text === null) {
                setStep('transcribing')
                text = (await transcribe(audio, visitId)).transcript
                setTranscript(text)
            }
            setStep('generating')
            const protocol = await generateProtocol(visitId, text)
            setIsManual(false)
            setDraft(protocol)
        } catch (e) {
            setStepError(e instanceof Error ? e.message : 'Protokoll konnte nicht erstellt werden')
        } finally {
            setStep('idle')
        }
    }

    if (draft) {
        return (
            <VisitProtocolForm
                initial={draft}
                transcript={transcript}
                aiGenerated={!isManual}
                onConfirm={(protocol) => {
                    setDraft(null)
                    onConfirmed(protocol)
                }}
                onDiscard={() => setDraft(null)}
            />
        )
    }

    return (
        <section>
            {isRecording ? (
                <>
                    <p role="status">
                        Aufnahme läuft: {formatTime(elapsedSeconds)} / {formatTime(MAX_RECORDING_SECONDS)}
                    </p>
                    <button onClick={stop}>Aufnahme beenden</button>
                </>
            ) : (
                <button onClick={handleStart} disabled={isBusy}>
                    {audio ? 'Neu aufnehmen' : 'Sprachnotiz aufnehmen'}
                </button>
            )}

            {audioUrl && !isRecording && (
                <>
                    <audio controls src={audioUrl} />
                    <button onClick={handleCreate} disabled={isBusy}>
                        {stepError && !isBusy ? 'Erneut versuchen' : STEP_TEXT[step]}
                    </button>
                </>
            )}

            {isBusy && <p role="status">{STEP_TEXT[step]}</p>}
            {(error ?? stepError) && <p role="alert">{error ?? stepError}</p>}

            {/* Fällt die AI aus, kann das Protokoll trotzdem erfasst werden */}
            {stepError && !isBusy && (
                <button
                    onClick={() => {
                        setIsManual(true)
                        setDraft(EMPTY_PROTOCOL)
                    }}
                >
                    Von Hand ausfüllen
                </button>
            )}
        </section>
    )
}

export default VoiceNoteRecorder
