import { useCallback, useEffect, useRef, useState } from 'react'

export const MAX_RECORDING_SECONDS = 120

// Chrome und Firefox nehmen WebM oder Ogg auf, Safari MP4.
const PREFERRED_MIME_TYPES = ['audio/webm;codecs=opus', 'audio/webm', 'audio/mp4', 'audio/ogg;codecs=opus']

function describeError(error: unknown): string {
    if (error instanceof DOMException && error.name === 'NotAllowedError') {
        return 'Der Zugriff auf das Mikrofon wurde nicht erlaubt.'
    }
    if (error instanceof DOMException && error.name === 'NotFoundError') {
        return 'Es wurde kein Mikrofon gefunden.'
    }
    return 'Die Aufnahme konnte nicht gestartet werden.'
}

export function useVoiceRecorder() {
    const [isRecording, setIsRecording] = useState(false)
    const [elapsedSeconds, setElapsedSeconds] = useState(0)
    const [audio, setAudio] = useState<Blob | null>(null)
    const [error, setError] = useState<string | null>(null)
    const recorderRef = useRef<MediaRecorder | null>(null)
    const timerRef = useRef<number | undefined>(undefined)

    const stop = useCallback(() => {
        window.clearInterval(timerRef.current)
        if (recorderRef.current?.state === 'recording') {
            recorderRef.current.stop()
        }
    }, [])

    const start = useCallback(async () => {
        setError(null)
        setAudio(null)
        setElapsedSeconds(0)

        // Fehlt ausserhalb von HTTPS und localhost.
        if (!navigator.mediaDevices || typeof MediaRecorder === 'undefined') {
            setError('Aufnahmen sind nur über HTTPS und in einem aktuellen Browser möglich.')
            return
        }

        try {
            const stream = await navigator.mediaDevices.getUserMedia({ audio: true })
            const mimeType = PREFERRED_MIME_TYPES.find((type) => MediaRecorder.isTypeSupported(type))
            const recorder = new MediaRecorder(stream, mimeType ? { mimeType } : undefined)
            const chunks: Blob[] = []

            recorder.ondataavailable = (event) => chunks.push(event.data)
            recorder.onstop = () => {
                stream.getTracks().forEach((track) => track.stop())
                setAudio(new Blob(chunks, { type: recorder.mimeType }))
                setIsRecording(false)
            }

            recorderRef.current = recorder
            recorder.start()
            setIsRecording(true)

            const startedAt = Date.now()
            timerRef.current = window.setInterval(() => {
                const seconds = Math.floor((Date.now() - startedAt) / 1000)
                setElapsedSeconds(Math.min(seconds, MAX_RECORDING_SECONDS))
                if (seconds >= MAX_RECORDING_SECONDS) {
                    stop()
                }
            }, 250)
        } catch (e) {
            setError(describeError(e))
        }
    }, [stop])

    useEffect(() => stop, [stop])

    return { isRecording, elapsedSeconds, audio, error, start, stop }
}
