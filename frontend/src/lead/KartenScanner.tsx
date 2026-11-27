import { useEffect, useRef, useState } from 'react'

/*
 * Live-Scanner für Visitenkarten (#134), bedient sich wie ein QR-Scanner: Kamera geht sofort auf, Karte in den
 * Rahmen halten, sobald das Bild ruhig und scharf ist, wird automatisch ausgelöst. Der Auslöser-Knopf geht immer.
 * Geschickt wird nur der Ausschnitt im Rahmen (plus etwas Rand), das macht die Erkennung genauer und den Upload klein.
 *
 * Kamera per getUserMedia gibt es nur über HTTPS oder localhost. Sonst (oder ohne Erlaubnis) bleibt der Weg über
 * die Kamera-App bzw. die Galerie (<input type="file" capture>).
 */

// Seitenverhältnis einer Visitenkarte (ISO/IEC 7810 ID-1, 85,6 × 54 mm)
const KARTE = 85.6 / 54
// Stichproben fürs automatische Auslösen; Werte sind Erfahrungswerte und bei Bedarf hier nachzustellen
const PRUEF_INTERVALL_MS = 200
const ANALYSE_BREITE = 160
const RUHIG_BIS = 4 // mittlere Helligkeitsänderung zwischen zwei Stichproben (0–255)
const SCHARF_AB = 7 // mittlerer Kontrast benachbarter Pixel; Schrift ergibt deutlich mehr als eine leere Fläche
const RUHIGE_PROBEN = 4 // so viele Stichproben in Folge ruhig und scharf → auslösen (≈ 0,8 s)
const AUFWAERMEN_MS = 800 // Autofokus und Belichtung nach dem Öffnen abwarten
const MAX_KANTE = 1600
const RAND = 0.06 // etwas Rand um den Rahmen, falls die Karte nicht ganz genau drin liegt

interface Props {
  onAufnahme: (bild: Blob) => void
  onDatei: (datei: File) => void
  onSchliessen: () => void
}

type Zustand = 'startet' | 'sucht' | 'haelt' | 'fertig' | 'fehler'

function beschreibeFehler(e: unknown): string {
  if (e instanceof DOMException && e.name === 'NotAllowedError') {
    return 'Der Zugriff auf die Kamera wurde nicht erlaubt. Du kannst stattdessen ein Foto auswählen.'
  }
  if (e instanceof DOMException && (e.name === 'NotFoundError' || e.name === 'OverconstrainedError')) {
    return 'Es wurde keine Kamera gefunden. Du kannst stattdessen ein Foto auswählen.'
  }
  return 'Die Kamera konnte nicht gestartet werden. Du kannst stattdessen ein Foto auswählen.'
}

// Rahmen im sichtbaren Bereich: möglichst breit, aber mit Platz für Hinweis und Knöpfe
function rahmenImBild(breite: number, hoehe: number) {
  const w = Math.min(breite * 0.9, hoehe * 0.55 * KARTE)
  const h = w / KARTE
  return { x: (breite - w) / 2, y: (hoehe - h) / 2, w, h }
}

// Rahmen in Pixeln des Kamerabilds. Das Video füllt die Fläche mit object-fit: cover, ist also mittig beschnitten.
function rahmenImVideo(video: HTMLVideoElement, rand: number) {
  const { clientWidth: cw, clientHeight: ch, videoWidth: vw, videoHeight: vh } = video
  const scale = Math.max(cw / vw, ch / vh)
  const offX = (vw - cw / scale) / 2
  const offY = (vh - ch / scale) / 2
  const r = rahmenImBild(cw, ch)
  const mx = r.w * rand
  const my = r.h * rand
  const x = Math.max(0, offX + (r.x - mx) / scale)
  const y = Math.max(0, offY + (r.y - my) / scale)
  const w = Math.min(vw - x, (r.w + 2 * mx) / scale)
  const h = Math.min(vh - y, (r.h + 2 * my) / scale)
  return { x, y, w, h }
}

// Fehlt ausserhalb von HTTPS und localhost
function kameraVerfuegbar(): boolean {
  return typeof navigator.mediaDevices?.getUserMedia === 'function'
}

function stoppe(stream: MediaStream | null) {
  stream?.getTracks().forEach((t) => t.stop())
}

export default function KartenScanner({ onAufnahme, onDatei, onSchliessen }: Props) {
  const videoRef = useRef<HTMLVideoElement>(null)
  const dateiRef = useRef<HTMLInputElement>(null)
  const streamRef = useRef<MediaStream | null>(null)
  const ausgeloestRef = useRef(false)
  const [zustand, setZustand] = useState<Zustand>(() => (kameraVerfuegbar() ? 'startet' : 'fehler'))
  const [fehler, setFehler] = useState<string | null>(() =>
    kameraVerfuegbar() ? null : 'Die Live-Kamera geht nur über HTTPS. Du kannst stattdessen ein Foto aufnehmen oder auswählen.',
  )
  const [blitz, setBlitz] = useState<boolean | null>(null) // null = Gerät hat kein Licht
  const [fortschritt, setFortschritt] = useState(0)

  // Callbacks über Refs, damit der Kamera-Effekt nicht bei jedem Rendern der Elternkomponente neu startet
  const onAufnahmeRef = useRef(onAufnahme)
  useEffect(() => {
    onAufnahmeRef.current = onAufnahme
  }, [onAufnahme])

  const ausloesen = async () => {
    const video = videoRef.current
    if (!video || ausgeloestRef.current || video.videoWidth === 0) return
    ausgeloestRef.current = true
    setZustand('fertig')
    const r = rahmenImVideo(video, RAND)
    const scale = Math.min(1, MAX_KANTE / Math.max(r.w, r.h))
    const canvas = document.createElement('canvas')
    canvas.width = Math.round(r.w * scale)
    canvas.height = Math.round(r.h * scale)
    canvas.getContext('2d')?.drawImage(video, r.x, r.y, r.w, r.h, 0, 0, canvas.width, canvas.height)
    const bild = await new Promise<Blob | null>((resolve) => canvas.toBlob(resolve, 'image/jpeg', 0.9))
    stoppe(streamRef.current)
    streamRef.current = null
    if (bild) {
      onAufnahmeRef.current(bild)
    } else {
      ausgeloestRef.current = false
      setFehler('Das Bild konnte nicht aufgenommen werden.')
      setZustand('fehler')
    }
  }
  const ausloesenRef = useRef(ausloesen)
  useEffect(() => {
    ausloesenRef.current = ausloesen
  })

  // Kamera öffnen
  useEffect(() => {
    let abgebrochen = false
    if (!kameraVerfuegbar()) return
    navigator.mediaDevices
      .getUserMedia({
        audio: false,
        video: { facingMode: { ideal: 'environment' }, width: { ideal: 1920 }, height: { ideal: 1080 } },
      })
      .then(async (stream) => {
        if (abgebrochen) return stoppe(stream)
        streamRef.current = stream
        const video = videoRef.current
        if (!video) return
        video.srcObject = stream
        await video.play().catch(() => undefined)
        const track = stream.getVideoTracks()[0]
        const caps = track?.getCapabilities?.() as (MediaTrackCapabilities & { torch?: boolean }) | undefined
        if (caps?.torch) setBlitz(false)
        if (!abgebrochen) setZustand('sucht')
      })
      .catch((e: unknown) => {
        if (abgebrochen) return
        setFehler(beschreibeFehler(e))
        setZustand('fehler')
      })
    return () => {
      abgebrochen = true
      stoppe(streamRef.current)
      streamRef.current = null
    }
  }, [])

  const laeuft = zustand === 'sucht' || zustand === 'haelt'

  // Automatisch auslösen: kleiner Graustufen-Ausschnitt des Rahmens, Schärfe und Bewegung gegen die letzte Probe
  useEffect(() => {
    if (!laeuft) return
    const canvas = document.createElement('canvas')
    const ctx = canvas.getContext('2d', { willReadFrequently: true })
    let vorher: Uint8Array | null = null
    let ruhig = 0
    const startedAt = performance.now()

    const id = window.setInterval(() => {
      const video = videoRef.current
      if (!ctx || !video || video.videoWidth === 0 || document.hidden || ausgeloestRef.current) return
      if (performance.now() - startedAt < AUFWAERMEN_MS) return
      const r = rahmenImVideo(video, 0)
      const w = ANALYSE_BREITE
      const h = Math.max(1, Math.round((ANALYSE_BREITE * r.h) / r.w))
      canvas.width = w
      canvas.height = h
      ctx.drawImage(video, r.x, r.y, r.w, r.h, 0, 0, w, h)
      const rgba = ctx.getImageData(0, 0, w, h).data
      const grau = new Uint8Array(w * h)
      for (let i = 0; i < grau.length; i++) {
        grau[i] = (rgba[i * 4] * 3 + rgba[i * 4 + 1] * 6 + rgba[i * 4 + 2]) / 10
      }
      let kontrast = 0
      for (let y = 0; y < h - 1; y++) {
        for (let x = 0; x < w - 1; x++) {
          const i = y * w + x
          kontrast += Math.abs(grau[i] - grau[i + 1]) + Math.abs(grau[i] - grau[i + w])
        }
      }
      kontrast /= (w - 1) * (h - 1)
      let bewegung = 255
      if (vorher) {
        bewegung = 0
        for (let i = 0; i < grau.length; i++) bewegung += Math.abs(grau[i] - vorher[i])
        bewegung /= grau.length
      }
      vorher = grau

      ruhig = bewegung < RUHIG_BIS && kontrast > SCHARF_AB ? ruhig + 1 : 0
      setFortschritt(Math.min(1, ruhig / RUHIGE_PROBEN))
      setZustand(ruhig > 0 ? 'haelt' : 'sucht')
      if (ruhig >= RUHIGE_PROBEN) {
        navigator.vibrate?.(40)
        void ausloesenRef.current()
      }
    }, PRUEF_INTERVALL_MS)
    return () => window.clearInterval(id)
  }, [laeuft])

  // Escape schließt den Scanner
  useEffect(() => {
    const taste = (e: KeyboardEvent) => e.key === 'Escape' && onSchliessen()
    window.addEventListener('keydown', taste)
    return () => window.removeEventListener('keydown', taste)
  }, [onSchliessen])

  const blitzUmschalten = async () => {
    const track = streamRef.current?.getVideoTracks()[0]
    if (!track || blitz === null) return
    try {
      await track.applyConstraints({ advanced: [{ torch: !blitz } as MediaTrackConstraintSet] })
      setBlitz(!blitz)
    } catch {
      setBlitz(null)
    }
  }

  const hinweis =
    zustand === 'startet'
      ? 'Kamera wird geöffnet …'
      : zustand === 'haelt'
        ? 'Ruhig halten …'
        : zustand === 'fertig'
          ? 'Aufgenommen'
          : 'Visitenkarte in den Rahmen halten'

  return (
    <div className="scanner" role="dialog" aria-modal="true" aria-label="Visitenkarte scannen">
      <video ref={videoRef} className="scanner-video" playsInline muted autoPlay />

      {zustand !== 'fehler' && (
        <div
          className={`scanner-rahmen${zustand === 'haelt' ? ' haelt' : ''}${zustand === 'fertig' ? ' fertig' : ''}`}
          style={{ ['--fortschritt' as string]: fortschritt }}
          aria-hidden="true"
        />
      )}

      <div className="scanner-oben">
        <button type="button" onClick={onSchliessen}>
          Abbrechen
        </button>
        {blitz !== null && (
          <button type="button" aria-pressed={blitz} onClick={() => void blitzUmschalten()}>
            {blitz ? 'Licht aus' : 'Licht an'}
          </button>
        )}
      </div>

      <div className="scanner-unten">
        {zustand === 'fehler' ? (
          <p className="scanner-fehler" role="alert">
            {fehler}
          </p>
        ) : (
          <p role="status">{hinweis}</p>
        )}
        <div className="scanner-knoepfe">
          <button type="button" onClick={() => dateiRef.current?.click()}>
            {zustand === 'fehler' ? 'Foto aufnehmen / auswählen' : 'Aus Galerie'}
          </button>
          {zustand !== 'fehler' && (
            <button
              type="button"
              className="scanner-ausloeser"
              aria-label="Jetzt aufnehmen"
              disabled={zustand === 'startet' || zustand === 'fertig'}
              onClick={() => void ausloesen()}
            />
          )}
        </div>
        <input
          ref={dateiRef}
          type="file"
          accept="image/*"
          capture={zustand === 'fehler' ? 'environment' : undefined}
          hidden
          onChange={(e) => {
            const datei = e.target.files?.[0]
            if (datei) onDatei(datei)
          }}
        />
      </div>
    </div>
  )
}
