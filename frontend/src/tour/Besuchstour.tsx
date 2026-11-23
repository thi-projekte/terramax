import { useCallback, useEffect, useRef, useState } from 'react'
import { submitProtocol, toVisitProtocol } from '../speech/tourProtocol'
import {
  abschliessen,
  aktuelleAufgabe,
  boolean,
  json,
  string,
  tourAbbrechen,
  tourStarten,
  variablen,
  type Kunde,
  type Protokoll,
  type Task,
  type TourVariablen,
} from './engineApi'
import {
  BesuchDurchfuehren,
  BesuchVorbereiten,
  BesuchsplanPruefen,
  ProtokollPruefen,
  SprachnotizAufnehmen,
  TourAbschliessen,
  TourStarten,
} from './Schritte'
import './tour.css'

// Geführte Besuchstour (#29, Teil 3). Die Oberfläche fragt die Engine nach der offenen Aufgabe und zeigt den
// passenden Schritt. Der Zustand liegt in der Engine; wer die App schließt, landet beim nächsten Öffnen wieder hier.

type Zustand = { task: Task; vars: TourVariablen } | null

const SCHRITTE: Record<string, string> = {
  Task_BesuchsplanPruefen: 'Plan prüfen',
  Task_BesuchVorbereiten: 'Besuch vorbereiten',
  Task_BesuchDurchfuehren: 'Besuch läuft',
  Task_SprachnotizAufnehmen: 'Sprachnotiz',
  Task_ProtokollPruefen: 'Protokoll',
  Task_TourAbschliessen: 'Abschluss',
}

const jetzt = () => new Date().toISOString()

/**
 * Kennung des Besuchs für den speech-service: "<tourId>:<kundeId>", wie beim Speichern des Protokolls, damit alle
 * Speech-Events eines Besuchs zusammengehören. Ohne tourId (Engine legt die Tour noch nicht in TerraMax an) die
 * Prozessinstanz.
 */
function visitId(tourId: number | undefined, task: Task, kunde: Kunde): string {
  return `${tourId ?? task.processInstanceId}:${kunde.kundeId}`
}

async function ladeZustand(): Promise<Zustand> {
  const task = await aktuelleAufgabe()
  return task ? { task, vars: await variablen(task.id) } : null
}

export default function Besuchstour() {
  const [zustand, setZustand] = useState<Zustand>(null)
  const [laden, setLaden] = useState(true)
  const [busy, setBusy] = useState(false)
  const [fehler, setFehler] = useState<string | null>(null)
  const [hinweis, setHinweis] = useState<string | null>(null)
  // Tasks, deren Protokoll TerraMax schon hat
  const anTerraMaxUebergeben = useRef(new Set<string>())

  const aktualisieren = useCallback(async () => {
    const naechster = await ladeZustand()
    setZustand(naechster)
    return naechster?.task ?? null
  }, [])

  // Beim Öffnen: laufende Tour aus der Engine laden (Tour-Status bleibt erhalten, auch nach Schließen der App)
  useEffect(() => {
    let aktiv = true
    ladeZustand()
      .then((geladen) => aktiv && setZustand(geladen))
      .catch((e: Error) => aktiv && setFehler(e.message))
      .finally(() => aktiv && setLaden(false))
    return () => {
      aktiv = false
    }
  }, [])

  // Führt eine Aktion aus und lädt danach den neuen Stand aus der Engine
  const ausfuehren = async (aktion: () => Promise<unknown>, nachher?: (task: Task | null) => void) => {
    setBusy(true)
    setFehler(null)
    setHinweis(null)
    try {
      await aktion()
      nachher?.(await aktualisieren())
    } catch (e) {
      setFehler((e as Error).message)
    } finally {
      setBusy(false)
    }
  }

  const fertig = (task: Task, variables: Parameters<typeof abschliessen>[1] = {}, text?: string) =>
    ausfuehren(
      () => abschliessen(task.id, variables),
      (naechste) => {
        if (!naechste && text) setHinweis(text)
      },
    )

  // Bestätigtes Protokoll erst über den speech-service an TerraMax übergeben, dann den Task abschließen.
  // Scheitert danach nur die Engine, schickt ein erneuter Klick das Protokoll nicht noch einmal (TerraMax würde es
  // sonst doppelt speichern).
  const protokollBestaetigen = (task: Task, kunde: Kunde, vars: TourVariablen, protokoll: Protokoll) => {
    const kundeId = Number(kunde.kundeId)
    const anTerraMax = vars.tourId != null && Number.isInteger(kundeId)
    return ausfuehren(
      async () => {
        if (anTerraMax && !anTerraMaxUebergeben.current.has(task.id)) {
          await submitProtocol(vars.tourId!, kundeId, toVisitProtocol(protokoll), vars.transkript ?? null)
          anTerraMaxUebergeben.current.add(task.id)
        }
        await abschliessen(task.id, { protokoll: json(protokoll) })
      },
      () => {
        // Bis die Engine die Tour im terramax-service anlegt (#64), gibt es keine tourId
        if (!anTerraMax) setHinweis('Protokoll nur in der Tour gespeichert: Die Tour ist noch nicht in TerraMax angelegt.')
      },
    )
  }

  if (laden) return <section className="tour">Besuchstour wird geladen …</section>

  const task = zustand?.task
  const vars = zustand?.vars ?? {}
  const plan = vars.besuchsplan ?? []
  const kunde = vars.kunde
  const nummer = kunde ? plan.findIndex((k) => k.kundeId === kunde.kundeId) + 1 : 0
  const imBesuch = task && kunde && task.taskDefinitionKey !== 'Task_BesuchsplanPruefen'

  return (
    <section className="tour">
      <header className="tour-header">
        <h2>Besuchstour</h2>
        {task && (
          <p className="tour-step">
            Aktueller Schritt: <strong>{SCHRITTE[task.taskDefinitionKey] ?? task.name}</strong>
            {imBesuch && nummer > 0 && (
              <>
                {' '}
                · Kunde {nummer} von {plan.length}
              </>
            )}
          </p>
        )}
      </header>

      {fehler && (
        <p className="tour-error" role="alert">
          {fehler}
        </p>
      )}
      {hinweis && <p className="tour-hint">{hinweis}</p>}

      {!task && (
        <TourStarten
          busy={busy}
          onSubmit={(tourDatum) =>
            ausfuehren(
              () => tourStarten(tourDatum),
              (naechste) => {
                if (!naechste) setHinweis('Keine Tour möglich: Für diesen Tag kommen weniger als zwei Kunden in Frage.')
              },
            )
          }
        />
      )}

      {task?.taskDefinitionKey === 'Task_BesuchsplanPruefen' && (
        <BesuchsplanPruefen
          key={task.id}
          besuchsplan={plan}
          busy={busy}
          onSubmit={(neuerPlan, bestaetigt) =>
            fertig(
              task,
              { besuchsplan: json(neuerPlan), planBestaetigt: boolean(bestaetigt) },
              bestaetigt ? undefined : 'Tour verworfen.',
            )
          }
        />
      )}

      {task?.taskDefinitionKey === 'Task_BesuchVorbereiten' && kunde && (
        <BesuchVorbereiten
          key={task.id}
          kunde={kunde}
          busy={busy}
          onBeginnen={() => fertig(task, { besuchUeberspringen: boolean(false), besuchBegonnenAm: string(jetzt()) })}
          onUeberspringen={(grund) =>
            fertig(task, { besuchUeberspringen: boolean(true), ueberspringenGrund: string(grund) })
          }
        />
      )}

      {task?.taskDefinitionKey === 'Task_BesuchDurchfuehren' && kunde && (
        <BesuchDurchfuehren
          kunde={kunde}
          begonnenAm={vars.besuchBegonnenAm}
          busy={busy}
          onSubmit={() => fertig(task, { besuchAbgeschlossenAm: string(jetzt()) })}
        />
      )}

      {task?.taskDefinitionKey === 'Task_SprachnotizAufnehmen' && kunde && (
        <SprachnotizAufnehmen
          key={task.id}
          visitId={visitId(vars.tourId, task, kunde)}
          busy={busy}
          onSubmit={(entwurf, transkript) =>
            fertig(task, {
              // Bewusst false: Bei true wartet das Modell auf die External Tasks "sprachnotiz-transkribieren" und
              // "protokollentwurf-erstellen", für die es keinen Worker gibt. Transkription und Entwurf erledigt die
              // SPA direkt beim speech-service; der Entwurf kommt als protokollEntwurf in den nächsten Schritt.
              sprachnotizVorhanden: boolean(false),
              ...(entwurf ? { protokollEntwurf: json(entwurf) } : {}),
              ...(transkript ? { transkript: string(transkript) } : {}),
            })
          }
        />
      )}

      {task?.taskDefinitionKey === 'Task_ProtokollPruefen' && kunde && (
        <ProtokollPruefen
          key={task.id}
          kunde={kunde}
          entwurf={vars.protokollEntwurf}
          transkript={vars.transkript}
          busy={busy}
          onSubmit={(protokoll) => protokollBestaetigen(task, kunde, vars, protokoll)}
        />
      )}

      {task?.taskDefinitionKey === 'Task_TourAbschliessen' && vars.touruebersicht && (
        <TourAbschliessen
          uebersicht={vars.touruebersicht}
          busy={busy}
          onSubmit={() => fertig(task, {}, 'Tour abgeschlossen. Gute Fahrt!')}
        />
      )}

      {imBesuch && task.taskDefinitionKey !== 'Task_TourAbschliessen' && (
        <div className="tour-abort">
          <button
            type="button"
            disabled={busy}
            onClick={() => {
              if (window.confirm('Tour wirklich abbrechen? Offene Besuche bleiben offen.')) {
                // ausfuehren fängt Fehler selbst ab und zeigt sie an; das Ergebnis wird hier nicht gebraucht
                void ausfuehren(() => tourAbbrechen(task.processInstanceId))
              }
            }}
          >
            Tour abbrechen
          </button>
        </div>
      )}
    </section>
  )
}
