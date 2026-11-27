import { useCallback, useState } from 'react'
import { transcribe } from '../speech/transcribe'
import { MAX_RECORDING_SECONDS, useVoiceRecorder } from '../speech/useVoiceRecorder'
import { readBusinessCard, type BusinessCard } from './businessCard'
import KartenScanner from './KartenScanner'
import { geocode, newCaptureId, submitLead, type LeadContact, type SavedLead } from './lead'
import '../tour/tour.css'
import './lead.css'

/*
 * Schnellerfassung (#134): Visitenkarte scannen (Live-Kamera, löst automatisch aus) → KI liest die Daten (speech-service, POST /business-cards) →
 * prüfen, kurzen Kommentar tippen oder diktieren → speichern (POST /leads, landet als Lead im terramax-service).
 * Gedacht für Messen: wenige Felder, nach dem Speichern sofort bereit für die nächste Karte.
 */

const LEERER_KONTAKT: LeadContact = {
  name: null,
  company: null,
  role: null,
  phone: null,
  email: null,
  street: null,
  postalCode: null,
  city: null,
}

const FELDER: { name: keyof LeadContact; label: string; typ?: string; autoComplete?: string }[] = [
  { name: 'name', label: 'Name', autoComplete: 'name' },
  { name: 'company', label: 'Firma / Betrieb', autoComplete: 'organization' },
  { name: 'role', label: 'Funktion', autoComplete: 'organization-title' },
  { name: 'phone', label: 'Telefon', typ: 'tel', autoComplete: 'tel' },
  { name: 'email', label: 'E-Mail', typ: 'email', autoComplete: 'email' },
  { name: 'street', label: 'Straße', autoComplete: 'street-address' },
  { name: 'postalCode', label: 'PLZ', autoComplete: 'postal-code' },
  { name: 'city', label: 'Ort', autoComplete: 'address-level2' },
]

// Der Messe-Name bleibt für die nächsten Karten stehen (nur in diesem Browser)
const EVENT_KEY = 'terramax.schnellerfassung.event'

function ladeEvent(): string {
  try {
    return localStorage.getItem(EVENT_KEY) ?? ''
  } catch {
    return ''
  }
}

function merkeEvent(name: string) {
  try {
    localStorage.setItem(EVENT_KEY, name)
  } catch {
    // ohne Speicher eben nicht gemerkt
  }
}

function ausKarte(card: BusinessCard): LeadContact {
  return { ...card, name: card.contactName }
}

function leerZuNull(text: string | null): string | null {
  const t = text?.trim() ?? ''
  return t === '' ? null : t
}

function dauer(sekunden: number): string {
  return `${Math.floor(sekunden / 60)}:${String(sekunden % 60).padStart(2, '0')}`
}

type Phase = 'bereit' | 'lesen' | 'transkribieren' | 'speichern'

const PHASE_TEXT: Record<Phase, string> = {
  bereit: '',
  lesen: 'Visitenkarte wird gelesen …',
  transkribieren: 'Kommentar wird erkannt …',
  speichern: 'Wird gespeichert …',
}

export default function Schnellerfassung() {
  const [captureId, setCaptureId] = useState(newCaptureId)
  const [eventName, setEventName] = useState(ladeEvent)
  const [kontakt, setKontakt] = useState<LeadContact>(LEERER_KONTAKT)
  const [kommentar, setKommentar] = useState('')
  const [transkript, setTranskript] = useState<string | null>(null)
  const [vorschau, setVorschau] = useState<string | null>(null)
  const [entwurf, setEntwurf] = useState(false)
  const [phase, setPhase] = useState<Phase>('bereit')
  const [fehler, setFehler] = useState<string | null>(null)
  const [gespeichert, setGespeichert] = useState<SavedLead | null>(null)
  const [scannerOffen, setScannerOffen] = useState(false)
  const recorder = useVoiceRecorder()
  // Bereits übernommene oder verworfene Aufnahme; "Diktat übernehmen" gibt es nur für eine neue
  const [erledigteAufnahme, setErledigteAufnahme] = useState<Blob | null>(null)
  const neueAufnahme = recorder.audio !== null && recorder.audio !== erledigteAufnahme
  const beschaeftigt = phase !== 'bereit'
  const hatNamen = leerZuNull(kontakt.name) !== null || leerZuNull(kontakt.company) !== null

  const fotoGewaehlt = async (file: Blob) => {
    setScannerOffen(false)
    setFehler(null)
    setGespeichert(null)
    if (vorschau) URL.revokeObjectURL(vorschau)
    setVorschau(URL.createObjectURL(file))
    setPhase('lesen')
    try {
      setKontakt(ausKarte(await readBusinessCard(file, captureId)))
      setEntwurf(true)
    } catch (e) {
      setFehler(e instanceof Error ? e.message : 'Visitenkarte konnte nicht gelesen werden')
    } finally {
      setPhase('bereit')
    }
  }

  const scannerSchliessen = useCallback(() => setScannerOffen(false), [])

  const kommentarErkennen = async () => {
    if (!recorder.audio) return
    setFehler(null)
    setPhase('transkribieren')
    try {
      const text = (await transcribe(recorder.audio, captureId)).transcript
      setErledigteAufnahme(recorder.audio)
      setTranskript((alt) => (alt ? `${alt}\n${text}` : text))
      setKommentar((alt) => (alt.trim() === '' ? text : `${alt.trim()}\n${text}`))
    } catch (e) {
      setFehler(e instanceof Error ? e.message : 'Kommentar konnte nicht erkannt werden')
    } finally {
      setPhase('bereit')
    }
  }

  const zuruecksetzen = () => {
    if (vorschau) URL.revokeObjectURL(vorschau)
    setVorschau(null)
    setKontakt(LEERER_KONTAKT)
    setKommentar('')
    setTranskript(null)
    setErledigteAufnahme(recorder.audio)
    setEntwurf(false)
    setCaptureId(newCaptureId())
  }

  const speichern = async () => {
    setFehler(null)
    setPhase('speichern')
    try {
      const bereinigt = Object.fromEntries(
        Object.entries(kontakt).map(([k, v]) => [k, leerZuNull(v)]),
      ) as unknown as LeadContact
      const koordinaten = await geocode(bereinigt)
      const lead = await submitLead({
        captureId,
        source: 'MESSE',
        eventName: leerZuNull(eventName),
        contact: bereinigt,
        latitude: koordinaten?.latitude ?? null,
        longitude: koordinaten?.longitude ?? null,
        comment: leerZuNull(kommentar),
        transcript: transkript,
        existingCustomerId: null,
      })
      merkeEvent(eventName.trim())
      setGespeichert(lead)
      zuruecksetzen()
      // Auf der Messe kommt gleich die nächste Karte: Scanner direkt wieder öffnen
      setScannerOffen(true)
    } catch (e) {
      setFehler(e instanceof Error ? e.message : 'Lead konnte nicht gespeichert werden')
    } finally {
      setPhase('bereit')
    }
  }

  return (
    <section className="tour lead">
      <header className="tour-header">
        <h2>Schnellerfassung</h2>
        <p className="tour-step">Visitenkarte scannen, prüfen, kurz kommentieren – fertig.</p>
      </header>

      <form
        className="tour-card"
        onSubmit={(e) => {
          e.preventDefault()
          void speichern()
        }}
      >
        {gespeichert && (
          <p className="lead-success" role="status">
            „{gespeichert.name}“ wurde als Lead gespeichert.
          </p>
        )}

        <label>
          Messe / Veranstaltung
          <input value={eventName} placeholder="z. B. Agraria Wels" onChange={(e) => setEventName(e.target.value)} />
        </label>

        <div className="lead-foto">
          {vorschau && <img src={vorschau} alt="Foto der Visitenkarte" />}
          <button
            type="button"
            className={entwurf ? undefined : 'primary'}
            disabled={beschaeftigt || recorder.isRecording}
            onClick={() => setScannerOffen(true)}
          >
            {vorschau ? 'Neu scannen' : 'Visitenkarte scannen'}
          </button>
        </div>

        {phase !== 'bereit' && <p role="status">{PHASE_TEXT[phase]}</p>}
        {(recorder.error ?? fehler) && (
          <p className="tour-error" role="alert">
            {recorder.error ?? fehler}
          </p>
        )}
        {entwurf && <p className="tour-hint">KI-Entwurf – bitte prüfen und bei Bedarf korrigieren.</p>}

        <div className="lead-felder">
          {FELDER.map((feld) => (
            <label key={feld.name}>
              {feld.label}
              <input
                type={feld.typ ?? 'text'}
                autoComplete={feld.autoComplete}
                value={kontakt[feld.name] ?? ''}
                onChange={(e) => setKontakt({ ...kontakt, [feld.name]: e.target.value })}
              />
            </label>
          ))}
        </div>

        <label>
          Kommentar
          <textarea
            rows={3}
            maxLength={2000}
            value={kommentar}
            placeholder="z. B. Interesse an 8R 410, Rückruf nach der Ernte"
            onChange={(e) => setKommentar(e.target.value)}
          />
        </label>

        <div className="lead-diktat">
          {recorder.isRecording ? (
            <>
              <span className="tour-recording" role="status">
                Aufnahme läuft: {dauer(recorder.elapsedSeconds)} / {dauer(MAX_RECORDING_SECONDS)}
              </span>
              <button type="button" className="primary" onClick={recorder.stop}>
                Aufnahme beenden
              </button>
            </>
          ) : (
            <>
              <button type="button" disabled={beschaeftigt} onClick={() => void recorder.start()}>
                {neueAufnahme ? 'Neu diktieren' : 'Kommentar diktieren'}
              </button>
              {neueAufnahme && (
                <button type="button" disabled={beschaeftigt} onClick={() => void kommentarErkennen()}>
                  Diktat übernehmen
                </button>
              )}
            </>
          )}
        </div>

        <div className="tour-actions">
          <button type="button" disabled={beschaeftigt || recorder.isRecording} onClick={zuruecksetzen}>
            Verwerfen
          </button>
          <button type="submit" className="primary" disabled={beschaeftigt || recorder.isRecording || !hatNamen}>
            Lead speichern
          </button>
        </div>
        {!hatNamen && <p className="tour-hint">Name oder Firma wird gebraucht.</p>}
      </form>

      {scannerOffen && (
        <KartenScanner
          onAufnahme={(bild) => void fotoGewaehlt(bild)}
          onDatei={(datei) => void fotoGewaehlt(datei)}
          onSchliessen={scannerSchliessen}
        />
      )}
    </section>
  )
}
