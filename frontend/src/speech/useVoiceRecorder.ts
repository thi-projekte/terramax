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

function stopTracks(stream: MediaStream) {
    stream.getTracks().forEach((track) => track.stop())
}

export function useVoiceRecorder() {
    const [isRecording, setIsRecording] = useState(false)
    const [elapsedSeconds, setElapsedSeconds] = useState(0)
    const [audio, setAudio] = useState<Blob | null>(null)
    // Abspiel-URL für <audio>; wird freigegeben, sobald neu aufgenommen oder die Komponente verlassen wird
    const [audioUrl, setAudioUrl] = useState<string | null>(null)
    const audioUrlRef = useRef<string | null>(null)
    const [error, setError] = useState<string | null>(null)
    const recorderRef = useRef<MediaRecorder | null>(null)
    const timerRef = useRef<number | undefined>(undefined)
    // Verhindert parallele Starts, solange der Browser noch nach dem Mikrofon fragt
    const startingRef = useRef(false)
    const mountedRef = useRef(false)

    const releaseAudioUrl = useCallback(() => {
        if (audioUrlRef.current) URL.revokeObjectURL(audioUrlRef.current)
        audioUrlRef.current = null
    }, [])

    const stop = useCallback(() => {
        window.clearInterval(timerRef.current)
        if (recorderRef.current?.state === 'recording') {
            recorderRef.current.stop()
        }
    }, [])

    const start = useCallback(async () => {
        if (startingRef.current || recorderRef.current?.state === 'recording') return
        setError(null)
        releaseAudioUrl()
        setAudio(null)
        setAudioUrl(null)
        setElapsedSeconds(0)

        // Fehlt ausserhalb von HTTPS und localhost.
        if (!navigator.mediaDevices || typeof MediaRecorder === 'undefined') {
            setError('Aufnahmen sind nur über HTTPS und in einem aktuellen Browser möglich.')
            return
        }

        startingRef.current = true
        let stream: MediaStream | null = null
        try {
            stream = await navigator.mediaDevices.getUserMedia({ audio: true })
            if (!mountedRef.current) {
                // Komponente wurde während der Mikrofon-Abfrage verlassen
                stopTracks(stream)
                return
            }

            const mimeType = PREFERRED_MIME_TYPES.find((type) => MediaRecorder.isTypeSupported(type))
            const recorder = new MediaRecorder(stream, mimeType ? { mimeType } : undefined)
            const chunks: Blob[] = []
            const recordedStream = stream

            recorder.ondataavailable = (event) => chunks.push(event.data)
            recorder.onstop = () => {
                stopTracks(recordedStream)
                if (!mountedRef.current) return
                const blob = new Blob(chunks, { type: recorder.mimeType })
                audioUrlRef.current = URL.createObjectURL(blob)
                setAudio(blob)
                setAudioUrl(audioUrlRef.current)
                setIsRecording(false)
            }

            recorder.start()
            recorderRef.current = recorder
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
            // Ohne laufende Aufnahme gibt sonst niemand das Mikrofon frei
            if (stream) stopTracks(stream)
            setError(describeError(e))
        } finally {
            startingRef.current = false
        }
    }, [stop, releaseAudioUrl])

    useEffect(() => {
        mountedRef.current = true
        return () => {
            mountedRef.current = false
            stop()
            releaseAudioUrl()
        }
    }, [stop, releaseAudioUrl])

    return { isRecording, elapsedSeconds, audio, audioUrl, error, start, stop }
}
