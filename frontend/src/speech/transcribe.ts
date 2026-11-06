import { speechFetch } from './speechApi'

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

export function transcribe(audio: Blob, visitId: string): Promise<Transcription> {
    const extension = FILE_EXTENSIONS[audio.type.split(';')[0]] ?? 'webm'
    const body = new FormData()
    body.append('audio', audio, `voice-note.${extension}`)
    body.append('visitId', visitId)

    return speechFetch('/transcriptions', { method: 'POST', body }, 'Transkription fehlgeschlagen')
}
