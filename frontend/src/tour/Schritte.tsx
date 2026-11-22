import { useState } from 'react'
import { generateProtocol } from '../speech/protocol'
import { toProtokoll } from '../speech/tourProtocol'
import { transcribe } from '../speech/transcribe'
import { MAX_RECORDING_SECONDS, useVoiceRecorder } from '../speech/useVoiceRecorder'
import type { Kunde, Protokoll, Touruebersicht } from './engineApi'

// Je Aufgabe (User Task) im BPMN-Modell "Besuchstour" ein Bildschirm.
// Die Komponenten zeigen nur an und melden Eingaben über onSubmit; die Engine-Aufrufe macht Besuchstour.tsx.

type Busy = { readonly busy: boolean }

function heute(): string {
  const now = new Date()
  return new Date(now.getTime() - now.getTimezoneOffset() * 60000).toISOString().slice(0, 10)
}

function datum(iso: string | null | undefined): string {
  if (!iso) return 'noch nie'
  const [y, m, d] = iso.slice(0, 10).split('-')
  return `${d}.${m}.${y}`
}

function KundeInfo({ kunde }: Readonly<{ kunde: Kunde }>) {
  return (
    <dl className="tour-kunde">
      <dt>Kunde</dt>
      <dd>
        <strong>{kunde.name}</strong> ({kunde.kundeId})
      </dd>
      <dt>Ort</dt>
      <dd>{kunde.ort}</dd>
      <dt>Letzter Besuch</dt>
      <dd>{datum(kunde.letzterBesuch)}</dd>
      <dt>Anfahrt</dt>
      <dd>{kunde.entfernungKm} km Luftlinie vom vorherigen Halt</dd>
    </dl>
  )
}

// --- Tour starten (Startereignis, Formular terramax:tour-starten) ---

export function TourStarten({ onSubmit, busy }: Readonly<{ onSubmit: (tourDatum: string) => void } & Busy>) {
  const [tourDatum, setTourDatum] = useState(heute())
  return (
    <form
      className="tour-card"
      onSubmit={(e) => {
        e.preventDefault()
        onSubmit(tourDatum)
      }}
    >
      <h3>Neue Besuchstour</h3>
      <p>TerraMax schlägt dir für den gewählten Tag Kunden in fester Reihenfolge vor.</p>
      <label>
        <span>Tag der Tour</span>
        <input type="date" required value={tourDatum} onChange={(e) => setTourDatum(e.target.value)} />
      </label>
      <div className="tour-actions">
        <button type="submit" className="primary" disabled={busy}>
          Tour starten
        </button>
      </div>
    </form>
  )
}

// --- Besuchsplan prüfen (terramax:besuchsplan-pruefen) ---

export function BesuchsplanPruefen({
  besuchsplan,
  onSubmit,
  busy,
}: Readonly<{ besuchsplan: Kunde[]; onSubmit: (plan: Kunde[], bestaetigt: boolean) => void } & Busy>) {
  const [plan, setPlan] = useState(besuchsplan)
  const zuWenig = plan.length < 2

  const verschieben = (index: number, richtung: -1 | 1) => {
    const neu = [...plan]
    const [kunde] = neu.splice(index, 1)
    neu.splice(index + richtung, 0, kunde)
    setPlan(neu)
  }

  return (
    <div className="tour-card">
      <h3>Besuchsplan prüfen</h3>
      <p>Kunden mit dem am längsten zurückliegenden Besuch, sortiert nach Luftlinie. Du kannst Kunden entfernen oder umsortieren.</p>
      <ol className="tour-plan">
        {plan.map((kunde, index) => (
          <li key={kunde.kundeId}>
            <span className="tour-plan-name">
              <strong>{kunde.name}</strong> · {kunde.ort}
              <small>letzter Besuch: {datum(kunde.letzterBesuch)}</small>
            </span>
            <span className="tour-plan-buttons">
              <button type="button" aria-label="nach oben" disabled={busy || index === 0} onClick={() => verschieben(index, -1)}>
                ↑
              </button>
              <button
                type="button"
                aria-label="nach unten"
                disabled={busy || index === plan.length - 1}
                onClick={() => verschieben(index, 1)}
              >
                ↓
              </button>
              <button
                type="button"
                aria-label={`${kunde.name} entfernen`}
                disabled={busy}
                onClick={() => setPlan(plan.filter((k) => k.kundeId !== kunde.kundeId))}
              >
                ✕
              </button>
            </span>
          </li>
        ))}
      </ol>
      {zuWenig && <p className="tour-hint">Eine Tour braucht mindestens zwei Kunden.</p>}
      <div className="tour-actions">
        <button type="button" disabled={busy} onClick={() => onSubmit(plan, false)}>
          Tour verwerfen
        </button>
        <button type="button" className="primary" disabled={busy || zuWenig} onClick={() => onSubmit(plan, true)}>
          Plan bestätigen
        </button>
      </div>
    </div>
  )
}

// --- Besuch vorbereiten (terramax:besuch-vorbereiten) ---

export function BesuchVorbereiten({
  kunde,
  onBeginnen,
  onUeberspringen,
  busy,
}: Readonly<{ kunde: Kunde; onBeginnen: () => void; onUeberspringen: (grund: string) => void } & Busy>) {
  const [ueberspringen, setUeberspringen] = useState(false)
  const [grund, setGrund] = useState('')

  return (
    <div className="tour-card">
      <h3>Besuch vorbereiten</h3>
      <KundeInfo kunde={kunde} />
      {ueberspringen ? (
        <form
          onSubmit={(e) => {
            e.preventDefault()
            onUeberspringen(grund.trim())
          }}
        >
          <label>
            <span>Grund für das Überspringen</span>
            <textarea required value={grund} onChange={(e) => setGrund(e.target.value)} rows={2} />
          </label>
          <div className="tour-actions">
            <button type="button" disabled={busy} onClick={() => setUeberspringen(false)}>
              Zurück
            </button>
            <button type="submit" className="primary" disabled={busy || grund.trim() === ''}>
              Besuch überspringen
            </button>
          </div>
        </form>
      ) : (
        <div className="tour-actions">
          <button type="button" disabled={busy} onClick={() => setUeberspringen(true)}>
            Überspringen …
          </button>
          <button type="button" className="primary" disabled={busy} onClick={onBeginnen}>
            Besuch beginnen
          </button>
        </div>
      )}
    </div>
  )
}

// --- Besuch durchführen (terramax:besuch-durchfuehren) ---

export function BesuchDurchfuehren({
  kunde,
  begonnenAm,
  onSubmit,
  busy,
}: Readonly<{ kunde: Kunde; begonnenAm?: string; onSubmit: () => void } & Busy>) {
  const seit = begonnenAm ? new Date(begonnenAm).toLocaleTimeString('de-AT', { hour: '2-digit', minute: '2-digit' }) : null
  return (
    <div className="tour-card">
      <h3>Besuch läuft</h3>
      <p>
        Bei <strong>{kunde.name}</strong> ({kunde.ort}){seit ? ` seit ${seit} Uhr` : ''}.
      </p>
      <div className="tour-actions">
        <button type="button" className="primary" disabled={busy} onClick={onSubmit}>
          Besuch abschließen
        </button>
      </div>
    </div>
  )
}

// --- Sprachnotiz aufnehmen (terramax:sprachnotiz) ---

type Verarbeitung = 'bereit' | 'transkribieren' | 'entwerfen'

const VERARBEITUNG_TEXT: Record<Verarbeitung, string> = {
  bereit: 'Protokoll erstellen',
  transkribieren: 'Wird transkribiert …',
  entwerfen: 'KI erstellt den Entwurf …',
}

function dauer(sekunden: number): string {
  return `${Math.floor(sekunden / 60)}:${String(sekunden % 60).padStart(2, '0')}`
}

/**
 * Aufnahme → Transkription (speech-service, POST /transcriptions) → KI-Entwurf (POST /protocols). Geprüft und
 * bestätigt wird der Entwurf im nächsten Schritt. Ohne Aufnahme (oder wenn der speech-service ausfällt) geht es mit
 * "Ohne Sprachnotiz weiter" und einem leeren Protokoll weiter.
 */
export function SprachnotizAufnehmen({
  visitId,
  onSubmit,
  busy,
}: Readonly<{ visitId: string; onSubmit: (entwurf?: Protokoll, transkript?: string) => void } & Busy>) {
  const { isRecording, elapsedSeconds, audio, audioUrl, error, start, stop } = useVoiceRecorder()
  const [verarbeitung, setVerarbeitung] = useState<Verarbeitung>('bereit')
  const [fehler, setFehler] = useState<string | null>(null)
  // Bleibt bei einem Fehler der KI erhalten, damit ein neuer Versuch nicht noch einmal transkribiert
  const [transkript, setTranskript] = useState<string | null>(null)
  const beschaeftigt = busy || verarbeitung !== 'bereit'

  const neuAufnehmen = async () => {
    setFehler(null)
    setTranskript(null)
    await start()
  }

  const erstellen = async () => {
    if (!audio) return
    setFehler(null)
    try {
      let text = transkript
      if (text === null) {
        setVerarbeitung('transkribieren')
        text = (await transcribe(audio, visitId)).transcript
        setTranskript(text)
      }
      setVerarbeitung('entwerfen')
      const entwurf = await generateProtocol(visitId, text)
      onSubmit(toProtokoll(entwurf), text)
    } catch (e) {
      setFehler(e instanceof Error ? e.message : 'Protokoll konnte nicht erstellt werden')
    } finally {
      setVerarbeitung('bereit')
    }
  }

  return (
    <div className="tour-card">
      <h3>Sprachnotiz</h3>
      <p>
        Sprich kurz ein, was beim Besuch besprochen wurde (höchstens {dauer(MAX_RECORDING_SECONDS)} Minuten). Die KI
        erstellt daraus einen Protokollentwurf, den du im nächsten Schritt prüfst.
      </p>

      {isRecording ? (
        <p className="tour-recording" role="status">
          Aufnahme läuft: {dauer(elapsedSeconds)} / {dauer(MAX_RECORDING_SECONDS)}
        </p>
      ) : (
        audioUrl && <audio className="tour-audio" controls src={audioUrl} />
      )}

      {verarbeitung !== 'bereit' && <p role="status">{VERARBEITUNG_TEXT[verarbeitung]}</p>}
      {(error ?? fehler) && (
        <p className="tour-error" role="alert">
          {error ?? fehler}
        </p>
      )}
      {transkript !== null && (
        <details>
          <summary>Transkript</summary>
          <p className="tour-transkript">{transkript}</p>
        </details>
      )}

      <div className="tour-actions">
        <button type="button" disabled={beschaeftigt || isRecording} onClick={() => onSubmit()}>
          Ohne Sprachnotiz weiter
        </button>
        {isRecording ? (
          <button type="button" className="primary" onClick={stop}>
            Aufnahme beenden
          </button>
        ) : (
          <button type="button" className={audio ? undefined : 'primary'} disabled={beschaeftigt} onClick={neuAufnehmen}>
            {audio ? 'Neu aufnehmen' : 'Aufnahme starten'}
          </button>
        )}
        {audio && !isRecording && (
          <button type="button" className="primary" disabled={beschaeftigt} onClick={erstellen}>
            {fehler ? 'Erneut versuchen' : VERARBEITUNG_TEXT[verarbeitung]}
          </button>
        )}
      </div>
    </div>
  )
}

// --- Protokoll prüfen und bestätigen (terramax:protokoll-pruefen) ---

const PROTOKOLL_FELDER: { name: keyof Protokoll; label: string; mehrzeilig?: boolean; typ?: string }[] = [
  { name: 'gespraechspartner', label: 'Gesprächspartner' },
  { name: 'themen', label: 'Themen', mehrzeilig: true },
  { name: 'ergebnis', label: 'Ergebnis', mehrzeilig: true },
  { name: 'naechsteSchritte', label: 'Nächste Schritte', mehrzeilig: true },
  { name: 'followUpDatum', label: 'Follow-up am', typ: 'date' },
]

export function ProtokollPruefen({
  kunde,
  entwurf,
  transkript,
  onSubmit,
  busy,
}: Readonly<
  {
    kunde: Kunde
    entwurf?: Partial<Protokoll>
    transkript?: string
    onSubmit: (protokoll: Protokoll) => void
  } & Busy
>) {
  const [protokoll, setProtokoll] = useState<Protokoll>({
    gespraechspartner: entwurf?.gespraechspartner ?? '',
    themen: entwurf?.themen ?? '',
    ergebnis: entwurf?.ergebnis ?? '',
    naechsteSchritte: entwurf?.naechsteSchritte ?? '',
    followUpDatum: entwurf?.followUpDatum ?? '',
  })

  return (
    <form
      className="tour-card"
      onSubmit={(e) => {
        e.preventDefault()
        onSubmit(protokoll)
      }}
    >
      <h3>Protokoll – {kunde.name}</h3>
      {entwurf && <p className="tour-hint">KI-Entwurf – bitte prüfen und bei Bedarf korrigieren.</p>}
      {transkript && (
        <details>
          <summary>Transkript der Sprachnotiz</summary>
          <p className="tour-transkript">{transkript}</p>
        </details>
      )}
      {PROTOKOLL_FELDER.map((feld) => (
        <label key={feld.name}>
          {feld.label}
          {feld.mehrzeilig ? (
            <textarea
              rows={2}
              value={protokoll[feld.name]}
              onChange={(e) => setProtokoll({ ...protokoll, [feld.name]: e.target.value })}
            />
          ) : (
            <input
              type={feld.typ ?? 'text'}
              required={feld.name === 'gespraechspartner'}
              value={protokoll[feld.name]}
              onChange={(e) => setProtokoll({ ...protokoll, [feld.name]: e.target.value })}
            />
          )}
        </label>
      ))}
      <div className="tour-actions">
        <button type="submit" className="primary" disabled={busy}>
          Protokoll bestätigen
        </button>
      </div>
    </form>
  )
}

// --- Tour abschließen (terramax:tour-uebersicht) ---

const STATUS_TEXT = { DOKUMENTIERT: 'dokumentiert', UEBERSPRUNGEN: 'übersprungen', OFFEN: 'offen' } as const

export function TourAbschliessen({
  uebersicht,
  onSubmit,
  busy,
}: Readonly<{ uebersicht: Touruebersicht; onSubmit: () => void } & Busy>) {
  return (
    <div className="tour-card">
      <h3>Tourübersicht {datum(uebersicht.tourDatum)}</h3>
      <p>
        {uebersicht.anzahlDokumentiert} dokumentiert · {uebersicht.anzahlUebersprungen} übersprungen ·{' '}
        {uebersicht.anzahlOffen} offen
      </p>
      <ol className="tour-plan">
        {uebersicht.besuche.map((besuch) => (
          <li key={besuch.kundeId}>
            <span className="tour-plan-name">
              <strong>{besuch.name}</strong> · {besuch.ort}
              {besuch.grund && <small>Grund: {besuch.grund}</small>}
              {besuch.protokoll?.ergebnis && <small>Ergebnis: {besuch.protokoll.ergebnis}</small>}
            </span>
            <span className={`tour-status ${besuch.status.toLowerCase()}`}>{STATUS_TEXT[besuch.status]}</span>
          </li>
        ))}
      </ol>
      <div className="tour-actions">
        <button type="button" className="primary" disabled={busy} onClick={onSubmit}>
          Tour abschließen
        </button>
      </div>
    </div>
  )
}
