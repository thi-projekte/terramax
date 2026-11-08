import { useCallback, useEffect, useState } from 'react'
import {
  abschliessen,
  aktuelleAufgabe,
  boolean,
  json,
  string,
  tourAbbrechen,
  tourStarten,
  variablen,
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

      {task?.taskDefinitionKey === 'Task_SprachnotizAufnehmen' && (
        <SprachnotizAufnehmen busy={busy} onSubmit={() => fertig(task, { sprachnotizVorhanden: boolean(false) })} />
      )}

      {task?.taskDefinitionKey === 'Task_ProtokollPruefen' && kunde && (
        <ProtokollPruefen
          key={task.id}
          kunde={kunde}
          entwurf={vars.protokollEntwurf}
          busy={busy}
          onSubmit={(protokoll) => fertig(task, { protokoll: json(protokoll) })}
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
                ausfuehren(() => tourAbbrechen(task.processInstanceId))
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
