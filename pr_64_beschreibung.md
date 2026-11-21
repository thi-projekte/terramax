# #64 terramax-service: Andockstellen für die process-engine

## Worum es geht

Laut Team-Entscheidung ist **TerraMax die Single Source of Truth für alle
Tourdaten**: welche Kunden besucht wurden, Status je Halt, Gründe fürs
Überspringen, Zeiten und Protokolle. Die process-engine (CIB Seven) stößt Abläufe
nur an und hält im Prozesskontext nur die `tourId` und Flags für Gateways.
Aufrufe gehen **nur von der Engine zu TerraMax**, nie umgekehrt.

Dieser PR macht die Andockstellen in TerraMax fertig. Delegates und BPMN in der
process-engine sind **nicht** geändert, die Umstellung übernehmen Lukas und der
CIB-Seven-Entwickler. Übergabe mit allen Schritten:
[docs/process/terramax-engine-schnittstelle.md](docs/process/terramax-engine-schnittstelle.md).

## Welche Endpunkte die Engine wofür aufruft

| BPMN-Schritt / Zweck | Endpunkt | Ersetzt |
| --- | --- | --- |
| Besuchsplan erzeugen: Plan holen | `GET /tours/plan?date=…&maxVisits=…` (Format exakt wie `TourPlan`, nur `TOUR_SERVICE_URL` umstellen) | tour-service |
| Besuchsplan erzeugen: Tour anlegen | `POST /tours` mit `processInstanceId`, idempotent (zweiter Aufruf: `200` mit der bestehenden Tour) | `besuchsplan` |
| Tour zur Prozessinstanz finden | `GET /tours/by-process/{processInstanceId}` | – |
| Gateways „Mindestens zwei Kunden?“, „Noch mindestens zwei Kunden?“, Schleifenende | `GET /tours/{id}/status` (`anzahlHalte`, `offeneHalte`, `naechsterHalt`, `alleErledigt`) | Größe von `besuchsplan` |
| Besuchsplan prüfen | `PATCH /tours/{id}` `customerOrder`, `DELETE /tours/{id}/stops/{customerId}` | `besuchsplan` bearbeiten |
| Besuch durchführen / beginnen | `POST /tours/{id}/stops/{customerId}/start` | `besuchBegonnenAm` in `besuchsergebnisse` |
| Grund für Überspringen speichern | `POST /tours/{id}/stops/{customerId}/skip` mit `reason` | `besuchsergebnisse` (`UEBERSPRUNGEN`) |
| Protokoll dem Kunden zuordnen | `POST /tours/{id}/stops/{customerId}/complete` (Protokoll speichert vorher der speech-service per `POST /tours/{id}/protocols`) | `besuchsergebnisse` (`DOKUMENTIERT`) |
| Tourübersicht erstellen | `GET /tours/{id}/overview` (gleiche Feldnamen wie bisher) | `touruebersicht` |
| Tour abschließen / verworfen / abgebrochen | `PATCH /tours/{id}` `status` = `completed` bzw. `discarded` | – |
| Protokoll lesen, korrigieren, löschen | `GET`, `PUT`, `PATCH`, `DELETE /protocols/{id}` | `protokoll` |

Alle Schreibaufrufe sind idempotent; ungültige Statusübergänge geben `409`.
Swagger UI mit Beispielen zu allen Endpunkten: `/q/swagger-ui` (jetzt auch im Container).

## Änderungen

- **Merge von `main`**, Konflikt in `docker-compose.yml` (Kommentar zu
  `OPENAI_API_KEY`/`SPEECH_TERRAMAX_URL`) zusammengeführt.
- **`ProcessEngineClient` entfernt** (Stub, REST-Client, Config
  `terramax.engine.*`, `TERRAMAX_ENGINE_*`, REST-Client-Abhängigkeiten). TerraMax
  kennt die Engine nicht mehr.
- **Flyway `V3__tour_stop_status.sql`:** `tour_customer.status`
  (`OFFEN`/`DOKUMENTIERT`/`UEBERSPRUNGEN`) statt `visited`, Altdaten
  `visited = true` → `DOKUMENTIERT`; `skip_reason` (Pflicht bei `UEBERSPRUNGEN`),
  `started_at`, `finished_at`; Unique-Index auf `tour.process_instance_id`.
- **Engine-Endpunkte** wie oben, Regeln der Übergänge in `StopTransitions`.
  `PATCH /tours/{id}` hat das Feld `visited` nicht mehr; der Halt-Status ändert sich
  nur über `/stops/{customerId}/start|skip|complete`. `TourStop` liefert `status`,
  `skipReason`, `startedAt`, `finishedAt` statt `visited`.
- **Tourplanung aus dem tour-service (PR #53)** nach `de.thi.terramax.tour`
  übernommen und mit `TourPlanner`/`RoutePlanner` zusammengeführt: DB-Kunden statt
  Mocks, gleiche Regel (nie/am längsten nicht besucht, am Tourtag oder später
  Besuchte fallen raus, Nearest Neighbour ab Händler), `maxVisits` 2–10 (Standard
  5), Rolle `sales_professional`. Kunden ohne Koordinaten kommen nicht in den Plan.
- **Protokoll-CRUD:** `GET/PUT/PATCH/DELETE /protocols/{id}`. Ändern und löschen
  nur der Ersteller oder das Service-Konto. `POST /tours/{id}/protocols` ist
  unverändert (Vertrag mit `TerraMaxApi` im speech-service).
- **Auth:** neben dem Nutzer-Token (Token Propagation) ein Service-Konto-Token mit
  Realm-Rolle `process_engine`. Es hat kein `dealer_id`, der Händler kommt dann aus
  der Tour (bei `POST /tours` aus `dealerId`). Im lokalen Realm sind Rolle und
  Client `terramax-process-engine` angelegt. Token-TODO in `TourProtocolResource`
  entfernt und dokumentiert.
- **OpenAI auf Eis:** `terramax.prioritizer.mode` bleibt `stub`,
  `OPENAI_API_KEY` optional, im Code, in der Config und in der README so markiert.
- **OpenAPI:** Beispiele für Requests und Responses aller Engine-Endpunkte,
  Swagger UI auch in prod (`quarkus.swagger-ui.always-include=true`).
- **CORS:** `DELETE` zusätzlich erlaubt (neue DELETE-Endpunkte).
- README und Übergabedoku aktualisiert.

## Tests

`./mvnw verify`: 307 Tests, alle grün. Neu:

- `TourProcessEndpointsTest`: Idempotenz von `POST /tours`, `409` bei fremder
  `processInstanceId`, `by-process`, `status`, `overview`
- `TourStopResourceTest`, `StopTransitionsTest`: alle Statusübergänge,
  Idempotenz, `409`, Aufruf ohne Body
- `ProtocolCrudTest`: Rechte für Lesen, Ändern und Löschen
- `ServiceAccountTest`: Service-Konto ohne `dealer_id`
- `TourPlanResourceTest`: Format streng gegen eine Kopie von `TourPlan` der Engine,
  Parameter, Rollen
- `OpenApiTest`: Engine-Endpunkte dokumentiert, mit Beispielen; Swagger UI erreichbar

Manuell: Image gebaut und mit dem gemeinsamen `docker-compose.yml`
(`terramax-service` + `terramax-db`) gestartet. Flyway V1–V3 laufen durch, der
Testdaten-Import läuft, `/q/health` ist `UP` und `/q/swagger-ui` erreichbar.

## Abstimmen vor oder nach dem Merge

- [ ] Team-Keycloak: Realm-Rolle `process_engine` und Confidential Client mit
      Service Account für die Engine anlegen
- [ ] Abgebrochene Tour: `completed` (Vorschlag) oder eigener Status `aborted`
- [ ] Protokoll speichert nur der speech-service; `protokollZuordnen` schließt den Halt nur ab
- [ ] Engine im Stack im selben Netzwerk wie `terramax-service`, `TOUR_SERVICE_URL=http://terramax-service:8080`
- [ ] Danach kann der tour-service entfallen

🤖 Generated with [Claude Code](https://claude.com/claude-code)
