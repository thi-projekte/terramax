import keycloak from '../keycloak'

// REST-API der CIB-seven-Engine (process-engine). Lokal läuft sie auf Port 8086.
const ENGINE_URL: string = import.meta.env.VITE_ENGINE_URL ?? 'http://localhost:8086/engine-rest'

export const PROCESS_KEY = 'Besuchstour'

export type Kunde = {
  position: number
  kundeId: string
  name: string
  ort: string
  latitude: number | null
  longitude: number | null
  letzterBesuch: string | null
  entfernungKm: number
}

export type Protokoll = {
  gespraechspartner: string
  themen: string
  ergebnis: string
  naechsteSchritte: string
  followUpDatum: string
}

export type Besuch = {
  position: number
  kundeId: string
  name: string
  ort: string
  status: 'DOKUMENTIERT' | 'UEBERSPRUNGEN' | 'OFFEN'
  grund?: string
  protokoll?: Partial<Protokoll>
}

export type Touruebersicht = {
  tourDatum: string
  besuche: Besuch[]
  anzahlDokumentiert: number
  anzahlUebersprungen: number
  anzahlOffen: number
}

export type Task = {
  id: string
  name: string
  taskDefinitionKey: string
  processInstanceId: string
  assignee: string
}

/** Prozessvariablen, die die Oberfläche liest (Namen wie im BPMN-Modell). */
export type TourVariablen = {
  tourDatum?: string
  besuchsplan?: Kunde[]
  kunde?: Kunde
  besuchBegonnenAm?: string
  protokollEntwurf?: Partial<Protokoll>
  touruebersicht?: Touruebersicht
}

type EngineVariable = { value: unknown; type: string; valueInfo?: Record<string, unknown> }

export class EngineError extends Error {
  readonly status: number

  constructor(status: number, message: string) {
    super(message)
    this.status = status
  }
}

async function request<T>(method: string, path: string, body?: unknown): Promise<T> {
  try {
    await keycloak.updateToken(30)
  } catch {
    await keycloak.login()
  }

  const response = await fetch(`${ENGINE_URL}${path}`, {
    method,
    headers: {
      Authorization: `Bearer ${keycloak.token}`,
      ...(body === undefined ? {} : { 'Content-Type': 'application/json' }),
    },
    body: body === undefined ? undefined : JSON.stringify(body),
  })

  const text = await response.text()
  const data: unknown = text ? JSON.parse(text) : null
  if (!response.ok) {
    const message =
      data && typeof data === 'object' && 'message' in data ? String(data.message) : `HTTP ${response.status}`
    throw new EngineError(response.status, message)
  }
  return data as T
}

// --- Variablen in das Format der Engine bringen ---

export const string = (value: string): EngineVariable => ({ value, type: 'String' })
export const boolean = (value: boolean): EngineVariable => ({ value, type: 'Boolean' })

/** Listen und Objekte speichert die Engine als JSON (siehe process-engine/README.md). */
export const json = (value: object): EngineVariable => ({
  type: 'Object',
  value: JSON.stringify(value),
  valueInfo: {
    objectTypeName: Array.isArray(value) ? 'java.util.ArrayList' : 'java.util.LinkedHashMap',
    serializationDataFormat: 'application/json',
  },
})

// --- Aufrufe ---

/** Offene Aufgabe der laufenden Tour des angemeldeten Benutzers (oder null, wenn keine Tour läuft). */
export async function aktuelleAufgabe(): Promise<Task | null> {
  const user = keycloak.tokenParsed?.preferred_username as string | undefined
  const tasks = await request<Task[]>(
    'GET',
    `/task?processDefinitionKey=${PROCESS_KEY}&assignee=${encodeURIComponent(user ?? '')}&sortBy=created&sortOrder=desc`,
  )
  return tasks[0] ?? null
}

/** Startet eine Tour. Liefert die ID der Prozessinstanz. */
export async function tourStarten(tourDatum: string): Promise<string> {
  const instance = await request<{ id: string }>('POST', `/process-definition/key/${PROCESS_KEY}/start`, {
    variables: tourDatum ? { tourDatum: string(tourDatum) } : {},
  })
  return instance.id
}

/** Alle Variablen, die für eine Aufgabe sichtbar sind (z. B. kunde, besuchsplan, touruebersicht). */
export async function variablen(taskId: string): Promise<TourVariablen> {
  const raw = await request<Record<string, EngineVariable>>('GET', `/task/${taskId}/variables`)
  return Object.fromEntries(Object.entries(raw).map(([name, variable]) => [name, variable.value])) as TourVariablen
}

export async function abschliessen(taskId: string, variables: Record<string, EngineVariable> = {}): Promise<void> {
  await request<void>('POST', `/task/${taskId}/complete`, { variables })
}

/** Bricht die Tour ab (Message "TourAbbrechen" im Modell); danach folgt die Tourübersicht. */
export async function tourAbbrechen(processInstanceId: string): Promise<void> {
  await request<void>('POST', '/message', { messageName: 'TourAbbrechen', processInstanceId })
}
