import { useState } from 'react'
import { apiGet, type ApiResult } from './api'
import { ROLES, ROLE_LABELS, currentRoles, hasRole } from './roles'
import './RoleSections.css'
import type { VisitProtocol } from './speech/protocol'
import VoiceNoteRecorder from './speech/VoiceNoteRecorder'

const STATUS_TEXT: Record<number, string> = {
  200: 'OK',
  401: 'Unauthorized – nicht angemeldet',
  403: 'Forbidden – angemeldet, aber keine Berechtigung',
}

function ResultBox({ result }: { result: ApiResult | null }) {
  if (!result) return null
  const ok = result.status >= 200 && result.status < 300
  return (
    <pre className={`role-result ${ok ? 'ok' : 'error'}`}>
      HTTP {result.status} {STATUS_TEXT[result.status] ?? ''}
      {result.body !== null && result.body !== '' ? '\n' + JSON.stringify(result.body, null, 2) : ''}
    </pre>
  )
}

function FeatureCard({ title, description, path }: { title: string; description: string; path: string }) {
  const [result, setResult] = useState<ApiResult | null>(null)
  return (
    <section className="role-card">
      <h2>{title}</h2>
      <p>{description}</p>
      <button onClick={async () => setResult(await apiGet(path))}>Laden</button>
      <ResultBox result={result} />
    </section>
  )
}

function ProtocolSummary({ protocol }: { protocol: VisitProtocol }) {
  const empty = '–'
  return (
    <dl className="protocol-summary">
      <dt>Gesprächspartner</dt>
      <dd>{protocol.contactPerson ?? empty}</dd>
      <dt>Themen</dt>
      <dd>{protocol.topics.join(', ') || empty}</dd>
      <dt>Ergebnis</dt>
      <dd>{protocol.outcome ?? empty}</dd>
      <dt>Nächste Schritte</dt>
      <dd>{protocol.nextSteps.join(', ') || empty}</dd>
      <dt>Follow-up</dt>
      <dd>{protocol.followUpDate ? new Date(protocol.followUpDate).toLocaleDateString('de-DE') : empty}</dd>
    </dl>
  )
}

// Vorläufig mit fester Besuchs-ID, bis der Besuchsschritt der Tour existiert.
// Das bestätigte Protokoll wird noch nicht gespeichert: Die Zuordnung zum Kunden folgt mit dem Tour-Prozess.
function VoiceNoteCard() {
  const [confirmed, setConfirmed] = useState<VisitProtocol | null>(null)
  return (
    <section className="role-card">
      <h2>Besuchsprotokoll</h2>
      <p>Besuch per Sprachnotiz protokollieren (max. 2 Minuten).</p>
      {confirmed ? (
        <>
          <p role="status">Protokoll bestätigt. Die Zuordnung zum Kunden folgt mit der Besuchstour.</p>
          <ProtocolSummary protocol={confirmed} />
          <button onClick={() => setConfirmed(null)}>Neues Protokoll</button>
        </>
      ) : (
        <VoiceNoteRecorder visitId="demo-visit" onConfirmed={setConfirmed} />
      )}
    </section>
  )
}

// Demo für die Abnahme: ruft alle Endpunkte auf – auch die, die für die eigene Rolle gesperrt sind.
// So sieht man, dass das Backend selbst mit 403 ablehnt und nicht nur die Oberfläche etwas ausblendet.
function BackendCheck() {
  const [results, setResults] = useState<Record<string, ApiResult>>({})
  const paths = ['/me', '/sales', '/dealer']
  return (
    <details className="role-card backend-check">
      <summary>Backend-Prüfung (Demo)</summary>
      <div className="backend-check-buttons">
        {paths.map((path) => (
          <button
            key={path}
            onClick={async () => {
              const result = await apiGet(path)
              setResults((prev) => ({ ...prev, [path]: result }))
            }}
          >
            GET {path}
          </button>
        ))}
      </div>
      {paths.map((path) =>
        results[path] ? (
          <div key={path}>
            <strong>{path}</strong>
            <ResultBox result={results[path]} />
          </div>
        ) : null,
      )}
    </details>
  )
}

export default function RoleSections() {
  const roles = currentRoles()

  return (
    <div className="role-sections">
      <p className="role-badges">
        Rolle:{' '}
        {roles.length > 0
          ? roles.map((role) => (
              <span key={role} className="role-badge">
                {ROLE_LABELS[role]}
              </span>
            ))
          : 'keine'}
      </p>

      {roles.length === 0 && (
        <section className="role-card notice">
          <h2>Kein Zugriff auf TerraMax-Funktionen</h2>
          <p>Deinem Konto ist keine TerraMax-Rolle zugewiesen. Bitte wende dich an deinen Administrator.</p>
        </section>
      )}

      {hasRole(ROLES.SALES_PROFESSIONAL) && (
        <FeatureCard
          title="Meine Hinweise & Aufgaben"
          description="Hinweise und Aufgaben für dein Gebiet."
          path="/sales"
        />
      )}

      {hasRole(ROLES.SALES_PROFESSIONAL) && <VoiceNoteCard />}

      {hasRole(ROLES.DEALER_PRINCIPAL) && (
        <FeatureCard
          title="Marktübersicht"
          description="Markt, Wettbewerber und Vertriebsgebiete deines Händlers."
          path="/dealer"
        />
      )}

      {/* Demo-Werkzeug nur in der lokalen Entwicklung (npm run dev), nicht im Produktions-Build */}
      {import.meta.env.DEV && <BackendCheck />}
    </div>
  )
}
