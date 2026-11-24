# Process Engine (CIB seven)

BPMN-Engine für TerraMax. Sie führt den Prozess **Besuchstour** aus (Issue #29,
Modell: [docs/process/besuchstour.bpmn](../docs/process/besuchstour.bpmn))
und stellt die REST-API von CIB seven unter `/engine-rest` bereit. Bereitstellung
auf dem Server: Issue #63.

Aufbau nach der Vorlage des Kurses
([winfprojekt.de → CIB seven](https://winfprojekt.de/docs/technik/cibseven),
[d135-1r43/cibseven-template](https://github.com/d135-1r43/cibseven-template)):
Spring Boot mit CIB seven. Abweichungen von der Vorlage:

| Vorlage | Hier | Warum |
|---|---|---|
| CIB seven Enterprise 2.1.4-ee | freie Version 2.2.0 von Maven Central | keine Zugangsdaten für das Enterprise-Repository nötig |
| Webapps (Cockpit, Tasklist) | nur REST-API | auf dem Server laut Kursseite ohnehin nur REST; Bedienung über das React-Frontend |
| Keycloak-Identity-Plugin (Gruppen) | Keycloak-Token direkt, Realm-Rollen werden zu Engine-Gruppen | euer Realm arbeitet mit Rollen (`sales_professional`), nicht mit Gruppen; kein Admin-Client-Secret nötig |
| H2 im Speicher | H2 als Datei, PostgreSQL per Umgebungsvariable | Tour-Status bleibt erhalten (Akzeptanzkriterium #29) |
| Java 17 | Java 25 | wie die übrigen Services im Repo |

## Grundsatz: Die Tour liegt in TerraMax, die Engine speichert nur die Ausführung

Fachliche Daten (Kunden, Halte, Status je Halt, Gründe, Protokolle) liegen im
**terramax-service** (Single Source of Truth). Die Engine hält im Prozess nur:

- **Referenzen:** `tourId`, `kundenIds` (Collection der Schleife), `kundeId` (aktueller Besuch)
- **Flags für Gateways:** `anzahlHalte`, `planBestaetigt`, `besuchUeberspringen`, `sprachnotizVorhanden`
- **Ausführung:** wer die Tour wann gestartet hat (`initiator`, `initiatorId`, Historie der Engine)
- **Eingaben des Nutzers**, bis sie an TerraMax übergeben sind (`ueberspringenGrund`,
  `besuchBegonnenAm`, `besuchAbgeschlossenAm`), lokal je Besuch

Braucht eine User Task das volle Objekt (z. B. den Kunden für die Anzeige), lädt
es ein **ExecutionListener** am `start` der Aufgabe per ID aus TerraMax und
entfernt es am `end` wieder. Geladen wird nur an den Aufgaben, die es anzeigen
(Lazy Loading). Damit sieht der Nutzer immer den aktuellen Stand aus TerraMax,
und in `ACT_RU_VARIABLE` liegen keine veralteten Kopien.

```
React-Frontend ──(Bearer-Token)──> process-engine /engine-rest     Tour starten, Aufgaben lesen/abschließen, TourAbbrechen
                                        │
                                        │ Service Tasks und ExecutionListener (gleiches Token)
                                        ▼
                                   terramax-service                Tour anlegen, Halte ändern, Daten per ID laden
```

## Aufrufrichtung TerraMax ↔ CIB seven

| Wer | ruft wen | Wie | Wofür |
|---|---|---|---|
| React-Frontend (SPA) | process-engine | REST `/engine-rest`, Token des Nutzers | Tour starten, Aufgaben lesen und abschließen, `TourAbbrechen` |
| process-engine | terramax-service | REST, synchron, Token des Nutzers wird weitergereicht | Plan holen, Tour anlegen, Halte ändern, Daten per `tourId` laden |
| speech-service | terramax-service | REST, Token des Nutzers | bestätigtes Protokoll speichern (`POST /tours/{tourId}/protocols`) |
| process-engine | Kafka `terramax.process.events` | Events nach jedem Commit | `notifications` informiert die SPA |

Festlegung: **Die Engine steuert, TerraMax hält die Daten.** Der terramax-service
ruft die Engine nie auf und kennt ihre URL nicht. Welcher BPMN-Schritt welchen
Endpunkt aufruft: [docs/process/terramax-engine-schnittstelle.md](../docs/process/terramax-engine-schnittstelle.md).

Alle Aufrufe der Engine an TerraMax laufen synchron im Request des Sales
Professional (Tour starten, Aufgabe abschließen, Tour abbrechen). Deshalb reicht
das Token des Nutzers; ein Service-Konto braucht die Engine erst, wenn ein
Schritt einmal asynchron läuft.

## Was im Modell passiert

| Element im Modell | Bean / Klasse | Was passiert |
|---|---|---|
| Service Task „Besuchsplan erzeugen“ | `besuchsplanErzeugen` | `GET /tours/plan`, bei mind. zwei Kunden `POST /tours` (idempotent über die Prozessinstanz); setzt `tourId`, `kundenIds`, `anzahlHalte` |
| User Task „Besuchsplan prüfen“ | Listener `besuchsplanLaden` (start), `besuchsplanUebernehmen` (end) | lädt den Plan (ein Aufruf `GET /tours/{id}`, inkl. Region und letztem Besuch); überträgt gestrichene Kunden und neue Reihenfolge nach TerraMax |
| Ende „Tour verworfen“ | Listener `tourVerwerfen` (start) | Tour in TerraMax auf `discarded` |
| User Tasks eines Besuchs | Listener `kundeLaden` (start) | lädt `kunde` und `besuchsplan` per `tourId`/`kundeId` |
| Service Task „Grund für Überspringen speichern“ | `besuchUeberspringenSpeichern` | `POST …/stops/{kundeId}/skip`; Grund ist Pflicht |
| Service Task „Protokoll dem Kunden zuordnen“ | `protokollZuordnen` | `POST …/start` und `…/complete`; das Protokoll selbst speichert der speech-service in TerraMax |
| Fluss nach „Tour abgebrochen“ | Listener `tourAbbrechen` (take) | `POST /tours/{id}/abort`, optional mit `abbruchGrund` |
| User Task „Tour abschließen“ | Listener `touruebersichtLaden` (start), `tourAbschliessen` (end) | lädt die Übersicht; setzt die Tour auf `completed`, außer sie ist abgebrochen |
| alle User Tasks mit geladenen Daten | Listener `geladeneDatenEntfernen` (end) | entfernt `besuchsplan`, `kunde`, `touruebersicht` wieder |

Die Listener sind Spring-Beans (`TourListener`) und im Modell per
`delegateExpression` eingebunden, damit der TerraMax-Client injiziert wird.

## Fehlerbehandlung

Jede Aktivität, die TerraMax aufruft, unterscheidet zwei Fälle
(`BesuchstourFehler`, `RestTerraMaxClient`):

| Fall | Auslöser | Verhalten |
|---|---|---|
| **Fachlich** (`TerraMaxFachlicherFehler`) | HTTP 400, 404, 409 (z. B. Halt schon erledigt, Tour abgebrochen) | Service Task wirft einen `BpmnError`; ein Error-Boundary-Event im Modell führt auf einen eigenen Pfad |
| **Technisch** (`TerraMaxTechnischerFehler`) | terramax-service nicht erreichbar, 5xx, 401/403, Token fehlt | Exception läuft durch, die Transaktion wird zurückgerollt. Der Prozess bleibt im letzten Wartezustand, der Nutzer sieht die Fehlermeldung und kann es wiederholen |

| Service Task | BPMN-Fehler | Pfad im Modell |
|---|---|---|
| Besuchsplan erzeugen | `BESUCHSPLAN_FEHLGESCHLAGEN` | Ende „Keine Tour möglich“ |
| Grund für Überspringen speichern | `UEBERSPRINGEN_ABGELEHNT` | Ende „Besuch nicht gespeichert“, die Tour geht mit dem nächsten Kunden weiter |
| Protokoll dem Kunden zuordnen | `BESUCHSABSCHLUSS_ABGELEHNT` | Ende „Besuch nicht abgeschlossen“, die Tour geht mit dem nächsten Kunden weiter |

Der Stand in TerraMax bleibt in allen Fällen maßgeblich und erscheint so in der
Tourübersicht. Alle Schreibaufrufe an TerraMax sind idempotent; ein Wiederholen
nach einem technischen Fehler schadet also nicht. Fehler in den
ExecutionListenern sind immer technisch zu behandeln (Rollback), weil ein
Listener keinen BPMN-Pfad hat.

## Prozessvariablen für das Frontend

| Variable | Gesetzt von | Inhalt |
|---|---|---|
| `tourDatum` | Start | `JJJJ-MM-TT`, optional (sonst heute) |
| `tourId` | Engine | ID der Tour im terramax-service |
| `kundenIds`, `anzahlHalte` | Engine | Kunden-IDs in Besuchsreihenfolge, deren Anzahl |
| `besuchsplan` | Listener (nur während einer Aufgabe) | Liste von Kunden: `position`, `kundeId`, `name`, `ort`, `latitude`, `longitude`, `letzterBesuch`, `entfernungKm` (beim ersten Halt `null`), `status` |
| `planBestaetigt` | „Besuchsplan prüfen“ | Boolean; dazu optional `besuchsplan` (angepasst: Kunden gestrichen oder umsortiert) |
| `kunde` | Listener (nur während einer Aufgabe) | aktueller Kunde, Felder wie im `besuchsplan` |
| `besuchUeberspringen`, `ueberspringenGrund` | „Besuch vorbereiten“ | Boolean, Text |
| `besuchBegonnenAm`, `besuchAbgeschlossenAm` | „Besuch vorbereiten“ / „durchführen“ | ISO-Zeitstempel, z. B. `2026-11-10T09:00:00Z` |
| `sprachnotizVorhanden` | „Sprachnotiz aufnehmen“ | Boolean |
| `touruebersicht` | Listener (nur während „Tour abschließen“) | `besuche` (mit `status`, `grund`, `protokollIds`), `anzahlDokumentiert`, `anzahlUebersprungen`, `anzahlOffen`, `tourStatus` |

Listen und Objekte werden als JSON gespeichert. Beim Abschließen einer Aufgabe
über REST schickt das Frontend sie so:

```json
{ "variables": { "besuchsplan": {
    "type": "Object",
    "value": "[{\"kundeId\":\"3\", …}]",
    "valueInfo": { "objectTypeName": "java.util.ArrayList", "serializationDataFormat": "application/json" }
} } }
```

Abbrechen der Tour: Message `TourAbbrechen` per `POST /engine-rest/message` mit
`processInstanceId`, optional mit der Variablen `abbruchGrund`.

## Prozess-Events (Kafka)

Die Engine publiziert die Events aus [docs/kafka/README.md](../docs/kafka/README.md)
auf `terramax.process.events` (Paket `de.thi.terramax.process.events`):

- Ein Engine-Plugin hängt beim Deployen Listener an die BPMN-Elemente; dafür
  braucht das Modell keine zusätzlichen Einträge.
- Gesendet wird erst **nach dem Commit** der Engine-Transaktion. Schlägt ein
  Schritt fehl (z. B. Überspringen ohne Grund), entsteht kein Event.
  `visit.documented` entsteht erst am Ende „Besuch dokumentiert“, also nicht,
  wenn TerraMax den Abschluss ablehnt.
- Events enthalten nur IDs, Status und Zähler, zusätzlich die `tourId`. Die
  Zähler von `tour.completed` kommen aus TerraMax.
- `userid` ist der Keycloak-`sub` des Startenden. Die Engine merkt ihn sich beim
  Start in der Prozessvariablen `initiatorId`, weil sie sonst nur den
  Benutzernamen kennt.
- Ist Kafka nicht erreichbar, wird der Fehler geloggt und der Prozess läuft
  weiter. Das Event fehlt dann. Für garantierte Zustellung wäre später eine
  Outbox-Tabelle nötig.
- Lokal und in Tests ist Kafka aus (`KAFKA_ENABLED=false`), die Events landen
  nur im Log.

## Lokal starten

Voraussetzungen: JDK 25, Docker.

1. **terramax-service** starten (bringt über Dev Services PostgreSQL und einen
   Keycloak auf Port 8180 mit):
   ```bash
   cd terramax-service
   ./mvnw quarkus:dev
   ```
2. **Engine** starten (zweites Terminal):
   ```bash
   cd process-engine
   ./mvnw spring-boot:run
   ```

Die Engine läuft unter http://localhost:8086, die REST-API unter
http://localhost:8086/engine-rest, der Health-Check unter
http://localhost:8086/actuator/health. Der Prozesszustand liegt in
`process-engine/data/` (nicht im Git).

## Konfiguration (Umgebungsvariablen)

| Variable | Standard | Bedeutung |
|---|---|---|
| `KEYCLOAK_ISSUER_URL` | `http://localhost:8180/realms/terramax` | Aussteller der Tokens |
| `TERRAMAX_SERVICE_URL` | `http://localhost:8088` | Adresse des terramax-service, im Stack `http://terramax-service:8080` |
| `CORS_ORIGINS` | `http://localhost:5173` | erlaubte Frontend-Adressen |
| `SPRING_DATASOURCE_URL` / `_USERNAME` / `_PASSWORD` | H2-Datei | auf dem Server PostgreSQL |
| `KAFKA_ENABLED` | `false` | `true`: Prozess-Events nach Kafka senden |
| `KAFKA_BOOTSTRAP_SERVERS` | `localhost:9092` | auf dem Server `kafka:9092` |

## Server (Portainer)

Die Engine läuft im gemeinsamen Stack (Root-`docker-compose.yml`) mit einer
eigenen PostgreSQL-Datenbank:

| Container | Netzwerke | Erreichbar |
|---|---|---|
| `process-engine` | `nginx-proxy-manager`, `process-engine-intern`, `kafka-intern` | nur über den Nginx Proxy Manager (`terramax-process-engine-1:8086`); erreicht den terramax-service über `nginx-proxy-manager` |
| `process-engine-db` (PostgreSQL 17) | `process-engine-intern` | nur intern, kein `ports:`, Daten im Volume `terramax-process-engine-db` |

Die Engine legt ihre Tabellen (Runtime und History) beim ersten Start selbst
an und deployt dabei das Modell `besuchstour.bpmn`.

**Einrichten (einmalig, nach dem Merge):** Das Image der Engine baut GitHub erst,
wenn die Änderung auf `main` ist (`.github/workflows/process-engine.yml`).

1. Portainer → Stack `terramax` → Variablen `PROCESS_ENGINE_DB_PASSWORD`
   (zufälliges Passwort, nirgends sonst ablegen) und
   `PROCESS_ENGINE_CORS_ORIGINS` (Adresse des Frontends) setzen → *Update the stack*
   mit *Re-pull image*.
2. Nginx Proxy Manager → Proxy Host anlegen: Domain z. B.
   `terramax-engine.winfprojekt.de`, Forward `process-engine`
   (oder `terramax-process-engine-1`), Port `8086`, Schema `http`,
   Tab *SSL*: Let's-Encrypt-Zertifikat anfordern, *Force SSL*.
3. GitHub → Repo → Settings → Secrets and variables → Actions → *Variables*:
   `VITE_ENGINE_URL` = `https://terramax-engine.winfprojekt.de/engine-rest`.
   Das Frontend bekommt die Adresse beim Build fest eingebaut; ohne die Variable
   zeigt es auf `http://localhost:8086/engine-rest`. Danach den Workflow
   `frontend` neu laufen lassen und das Frontend in Portainer neu ziehen.

**Prüfen:**

```bash
curl -i https://terramax-engine.winfprojekt.de/actuator/health        # 200, {"status":"UP"}
curl -i https://terramax-engine.winfprojekt.de/engine-rest/engine      # 401 ohne Token
curl -i -H "Authorization: Bearer $TOKEN" \
  https://terramax-engine.winfprojekt.de/engine-rest/engine            # 200, [{"name":"default"}]
```

Das Token braucht die Realm-Rolle `sales_professional`, sonst kommt `403`.
Die Events sieht man in der Kafka-UI unter `terramax.process.events`.

## Tests

```bash
./mvnw verify
```

38 Tests:

- der komplette Prozess mit echter Engine gegen ein nachgebautes TerraMax
  (`FakeTerraMax` mit den Statusregeln aus der Schnittstellen-Doku): normaler
  Ablauf, Plan ändern, zu wenige Kunden, Plan verworfen, Abbruch, Grund ist
  Pflicht, geladene Daten werden wieder entfernt, nur Referenzen im Prozess
- die drei BPMN-Fehlerpfade und der Rollback bei technischem Fehler
- die Prozess-Events (Reihenfolge, Inhalte, kein Event bei Fehler), das
  Kafka-Format und der Start mit Kafka
- die Absicherung der REST-API und die REST-Aufrufe an den terramax-service
  (URL, Body, Token, Einordnung der Fehler)

## Offen (mit Team/Prof klären)

- Service-Konto in Keycloak (`terramax-process-engine`), falls ein Schritt
  einmal asynchron läuft (Job Executor mit Retries und Incidents)
- Anbindung der Sprach-Schritte an den speech-service (External Tasks)
- Berechtigungen in der Engine: Derzeit darf jeder Sales Professional über die
  REST-API alle Touren sehen; das Frontend filtert nach `assignee`
- Webapps (Cockpit/Tasklist) mit Keycloak-Login sind nicht eingebaut. Falls das
  Team sie braucht, kommen dafür `cibseven-bpm-spring-boot-starter-webapp` und
  ein OIDC-Login dazu
- Der tour-service wird von der Engine nicht mehr benutzt; seine Aufgabe
  (`GET /tours/plan`) hat der terramax-service übernommen. Ob er samt Workflow
  entfernt wird, entscheidet das Team
