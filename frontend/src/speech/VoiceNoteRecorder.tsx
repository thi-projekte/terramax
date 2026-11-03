import { useEffect, useMemo, useState } from 'react'
import { transcribe, type Transcription } from './transcribe'
import { MAX_RECORDING_SECONDS, useVoiceRecorder } from './useVoiceRecorder'

interface VoiceNoteRecorderProps {
    visitId: string
    onTranscribed: (transcription: Transcription) => void
}

function formatTime(totalSeconds: number) {
    const minutes = Math.floor(totalSeconds / 60)
    const seconds = String(totalSeconds % 60).padStart(2, '0')
    return `${minutes}:${seconds}`
}

function VoiceNoteRecorder({ visitId, onTranscribed }: VoiceNoteRecorderProps) {
    const { isRecording, elapsedSeconds, audio, error, start, stop } = useVoiceRecorder()
    const [isUploading, setIsUploading] = useState(false)
    const [uploadError, setUploadError] = useState<string | null>(null)
    const audioUrl = useMemo(() => (audio ? URL.createObjectURL(audio) : null), [audio])

    useEffect(() => {
        return () => {
            if (audioUrl) URL.revokeObjectURL(audioUrl)
        }
    }, [audioUrl])

    async function handleStart() {
        setUploadError(null)
        await start()
    }

    // Die Aufnahme bleibt bei einem Fehler erhalten, damit sie erneut gesendet werden kann.
    async function handleUpload() {
        if (!audio) return
        setIsUploading(true)
        setUploadError(null)
        try {
            onTranscribed(await transcribe(audio, visitId))
        } catch (e) {
            setUploadError(e instanceof Error ? e.message : 'Transkription fehlgeschlagen')
        } finally {
            setIsUploading(false)
        }
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
                <button onClick={handleStart} disabled={isUploading}>
                    {audio ? 'Neu aufnehmen' : 'Sprachnotiz aufnehmen'}
                </button>
            )}

            {audioUrl && !isRecording && (
                <>
                    <audio controls src={audioUrl} />
                    <button onClick={handleUpload} disabled={isUploading}>
                        {isUploading ? 'Wird transkribiert…' : 'Transkribieren'}
                    </button>
                </>
            )}

            {(error ?? uploadError) && <p role="alert">{error ?? uploadError}</p>}
        </section>
    )
}

export default VoiceNoteRecorder
