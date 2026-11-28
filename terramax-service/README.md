# TerraMax-Service

Fachlicher Kern-Service von TerraMax (Issue #64): Händler, Kunden, Besuchstouren,
Besuchsprotokolle, schnell erfasste Leads (#134) und die Händlerstandorte der Marktkarte. Die SPA, der
speech-service und die process-engine (CIB Seven) rufen ihn per REST auf. Er hat
eine eigene PostgreSQL-Datenbank.

**TerraMax ist die Single Source of Truth für alle Tourdaten**: welche Kunden auf
einer Tour sind, Status je Halt, Gründe fürs Überspringen, Zeiten und Protokolle.
Die process-engine stößt Abläufe nur an und hält im Prozesskontext nur die
`tourId` und Flags für Gateways. Aufrufe gehen ausschließlich von der Engine zu
TerraMax, nie umgekehrt. Welche Endpunkte die Engine in welchem BPMN-Schritt
aufruft, steht in
[docs/process/terramax-engine-schnittstelle.md](../docs/process/terramax-engine-schnittstelle.md).
Architektur: [docs/architecture/terramax-architecture.html](../docs/architecture/terramax-architecture.html),
Datenmodell: [docs/architecture/terramax-db-schema.md](../docs/architecture/terramax-db-schema.md).

Der Service ist aus dem früheren `location-service` hervorgegangen
(`git mv`, Historie bleibt erhalten). Die Standort-Funktion liegt weiter im
Package `de.thi.terramax.location`.

> TODO: Das Frontend liest die Adresse noch aus `VITE_LOCATION_SERVICE_URL`
> (Standard `http://localhost:8088`). Die Variable wird später umbenannt; sie
> zeigt jetzt auf diesen Service, Port und Pfad `/locations` sind gleich geblieben.

## Aufbau

| Package | Inhalt |
| --- | --- |
| `de.thi.terramax.location` | Händlerstandorte (Wettbewerber, John Deere) als GeoJSON, Import beim Start |
| `de.thi.terramax.dealer` | Händler und Mitarbeiter, `GET /dealers/me` |
| `de.thi.terramax.customer` | Kunden, auch als GeoJSON |
| `de.thi.terramax.tour` | Touren, Halte mit Status (`StopTransitions`), `TourAccess` (Sichtbarkeit), Tourplanung `GET /tours/plan` (`TourPlanner`, `RoutePlanner`) |
| `de.thi.terramax.protocol` | Besuchsprotokolle (Anlegen über die Tour, CRUD unter `/protocols/{id}`) |
| `de.thi.terramax.lead` | Schnellerfassung von Leads (#134): Ansprechpartner (`CustomerContact`), Kommentar (`LeadNote`), `POST/GET /leads` |
| `de.thi.terramax.ai` | `VisitPrioritizer`: Standard „am längsten nicht besucht“; OpenAI-Anbindung (LangChain4j) **auf Eis** |
| `de.thi.terramax.security` | Rollen (auch Service-Konto `process_engine`), `CurrentUser`, `GET /me` |
| `de.thi.terramax.seed` | Import der Testdaten für Händler und Kunden beim Start |
| `de.thi.terramax.common` | `ErrorResponse`, OpenAPI-Definition, GeoJSON-Dateien lesen |

## Voraussetzungen

- JDK 25
- Laufende Docker-Umgebung, beispielsweise Docker Desktop (für die Dev Services
  PostgreSQL und Keycloak)

Maven wird über den mitgelieferten Maven Wrapper ausgeführt.
Eine separate Maven-Installation ist nicht erforderlich.

## Lokal starten

```bash
cd terramax-service
./mvnw quarkus:dev
```

- Service: http://localhost:8088, Swagger UI: http://localhost:8088/q/swagger-ui
- PostgreSQL und Keycloak starten automatisch als Dev Services. Keycloak läuft
  auf http://localhost:8180 mit dem Realm aus `src/main/resources/terramax-realm.json`
  (Kopie aus dem `hello-service`, Nutzer `sales`/`sales` und `dealer`/`dealer`,
  beide mit `dealer_id = D-100`).
- Beim Start werden Testdaten importiert (siehe [Testdaten](#testdaten)). Mit
  `sales` oder `dealer` angemeldet zeigt `GET /customers` sofort die 18 Mock-Kunden.
- Lokal belegte Ports: 8080 hello-service, 8082 speech-service, 8084 tour-service,
  8086 process-engine, 8088 terramax-service, 8180 Keycloak.

Beenden mit Ctrl + C.

## Endpunkte und API-Doku

Alle Endpunkte brauchen ein Keycloak-Token (`Authorization: Bearer <token>`):
ohne oder mit ungültigem Token `401`, ohne passende Rolle `403`. Fehler kommen
im Format `{"error": "…"}`.

| Methode und Pfad | Rolle | Liefert |
| --- | --- | --- |
| `GET /me` | jeder angemeldete Nutzer | Nutzer mit Rollen, `dealerId`, Händler und Mitarbeiter-ID |
| `GET /dealers/me` | beide | eigener Händler (`404`, wenn noch nicht angelegt) |
| `GET /customers` | beide | Bestandskunden (`status = CUSTOMER`, ohne Leads) des eigenen Händlers; mit `Accept: application/geo+json` als `FeatureCollection` (Eigenschaften wie `frontend/public/kunden.geojson`) |
| `GET /customers/{id}` | beide | ein Kunde |
| `POST /customers`, `PUT /customers/{id}` | beide | Kunde anlegen bzw. ändern (`409` bei vergebener Kundennummer) |
| `GET /customers/{id}/protocols` | beide | Besuchsprotokolle des Kunden, neueste zuerst |
| `GET /tours` | beide | Touren, neueste zuerst |
| `GET /tours/plan` | `sales_professional` | Besuchsplan für `?date=JJJJ-MM-TT` (Standard heute) und `maxVisits` (2–10, Standard 5), exakt im Format von `TourPlan` der process-engine |
| `GET /tours/{id}` | beide, Engine | Tour mit Halten in Reihenfolge (Status, Grund, Zeiten, Region und letzter Besuch je Halt) |
| `GET /tours/by-process/{processInstanceId}` | beide, Engine | Tour zur Prozessinstanz |
| `GET /tours/{id}/status` | beide, Engine | kompakter Stand für Gateways: `anzahlHalte`, `offeneHalte`, `naechsterHalt`, `alleErledigt` |
| `GET /tours/{id}/overview` | beide, Engine | Tourübersicht (ersetzt die Prozessvariable `touruebersicht`) |
| `POST /tours` | beide, Engine | Tour anlegen (Status `planned`, Halte `OFFEN`); mit `processInstanceId` idempotent (`200` mit der bestehenden Tour) |
| `PATCH /tours/{id}` | beide, Engine | `status` und `customerOrder` (neue Reihenfolge); endgültige Status sind nicht mehr änderbar (`409`) |
| `POST /tours/{id}/abort` | beide, Engine | Tour abbrechen (`aborted`), optional mit `reason`; idempotent, `409` bei abgeschlossener oder verworfener Tour |
| `POST /tours/{id}/stops/{customerId}/start` | beide, Engine | Besuch beginnen (`startedAt`); idempotent |
| `POST /tours/{id}/stops/{customerId}/skip` | beide, Engine | überspringen mit `reason` (Pflicht); idempotent bei gleichem Grund |
| `POST /tours/{id}/stops/{customerId}/complete` | beide, Engine | als `DOKUMENTIERT` abschließen; idempotent |
| `DELETE /tours/{id}/stops/{customerId}` | beide, Engine | Kunden aus einer geplanten Tour streichen („Besuchsplan prüfen“) |
| `GET /tours/{id}/protocols` | beide, Engine | Besuchsprotokolle der Tour |
| `GET /leads` | beide | Leads des eigenen Händlers, zuletzt erfasste zuerst, mit Ansprechpartner und Kommentar der neuesten Erfassung |
| `POST /leads` | beide | Lead erfassen (Aufrufer: speech-service nach `POST /business-cards`). Ohne `existingCustomerId` neuer Kunde mit `status = LEAD` (Name = Firma, sonst Person); mit ihr Ansprechpartner und Kommentar an den bestehenden Kunden (`404`, wenn fremd oder unbekannt) |
| `POST /tours/{id}/protocols` | beide, Engine | Protokoll speichern (Aufrufer: speech-service, nicht die Engine); Format wie `VisitProtocol` im speech-service plus `customerId` und optional `transcript` (Vertrag mit `TerraMaxApi` im speech-service, unverändert) |
| `GET /protocols/{id}` | beide, Engine | ein Protokoll |
| `PUT /protocols/{id}`, `PATCH /protocols/{id}` | Ersteller, Engine | Protokoll ersetzen bzw. teilweise ändern |
| `DELETE /protocols/{id}` | Ersteller, Engine | Protokoll löschen |
| `GET /locations` | angemeldet | Standorte als `FeatureCollection`, optional `?category=competitor,mixed_competitor` |
| `GET /locations/{id}` | angemeldet | ein Standort als `Feature` |

„beide“ heißt `sales_professional` oder `dealer_principal`, „Engine“ das
Service-Konto der process-engine (Rolle `process_engine`), „Ersteller“ der
Nutzer, dessen `sub` in `createdBy` steht. Für diese Endpunkte gilt außerdem:

- Nutzer ohne Claim `dealer_id` im Token: `403`.
- Daten anderer Händler sind nie sichtbar; fremde IDs antworten mit `404`.
- `sales_professional` sieht alle Kunden seines Händlers, aber nur Touren, die er
  laut `tour_employee` fährt, und nur deren Protokolle.
- `dealer_principal` sieht alle Kunden, Touren und Protokolle seines Händlers.
- Die Zuordnung Nutzer → Mitarbeiter läuft über den Claim `sub`
  (`dealer_employee.keycloak_user_id`). Der Eintrag entsteht beim ersten Aufruf
  automatisch (Name aus `name` bzw. `preferred_username`, Rolle aus dem Token),
  sofern der Händler in der Datenbank steht. Bestehende Einträge werden nicht
  geändert. Ohne Eintrag (Händler unbekannt) sieht ein `sales_professional` keine
  Touren und kann keine anlegen.
- Protokolle ändern oder löschen darf nur der Ersteller (auch nicht der
  `dealer_principal`) oder das Service-Konto; sonst `403`.

### Halte und Statusübergänge

Jeder Halt (`tour_customer`) hat einen Status `OFFEN`, `DOKUMENTIERT` oder
`UEBERSPRUNGEN` (Namen wie `BesuchstourVariablen.STATUS_*` in der Engine), dazu
`skipReason`, `startedAt` und `finishedAt`.

| Aufruf | OFFEN | OFFEN, begonnen | DOKUMENTIERT | UEBERSPRUNGEN |
| --- | --- | --- | --- | --- |
| `start` | setzt `startedAt` | unverändert (`200`) | `409` | `409` |
| `skip` | UEBERSPRUNGEN | UEBERSPRUNGEN | `409` | gleicher Grund `200`, sonst `409` |
| `complete` | DOKUMENTIERT | DOKUMENTIERT | unverändert (`200`) | `409` |

Der erste Übergang setzt die Tour von `planned` auf `in_progress`. Bei Touren mit
Status `completed`, `discarded` oder `aborted` ergibt jeder Übergang `409`. `start`
und `complete` nehmen optional `{"at": "<ISO-8601>"}` (sonst die Serverzeit) und
gehen auch ohne Body und ohne Content-Type. Regeln im Code: `StopTransitions`.

### Tour-Status und Abbruch

`planned` → `in_progress` → `completed`, daneben `discarded` (Plan verworfen) und
`aborted` (abgebrochen, BPMN-Message `TourAbbrechen`). `completed`, `discarded` und
`aborted` sind endgültig: Ein Wechsel daraus ergibt `409`, derselbe Status `200`.

- `POST /tours/{id}/abort`, optional `{"reason": "…"}` (auch ohne Body und ohne
  Content-Type): setzt `aborted`, `abortReason` und `abortedAt`. Offene Halte
  bleiben `OFFEN`, erledigte unverändert. Wiederholt ohne oder mit demselben Grund:
  `200` ohne Änderung; anderer Grund: `409`. Eine abgeschlossene oder verworfene
  Tour lässt sich nicht abbrechen (`409`).
- `PATCH /tours/{id}` mit `status: aborted` ergibt `400`; abbrechen geht nur über
  `/abort`. Eine abgebrochene Tour kann nicht mehr `completed` werden (`409`).
- `GET /tours/{id}/status` zeigt `beendet`, `abgebrochen` und `abbruchGrund`
  (`naechsterHalt` ist bei beendeter Tour `null`), die Übersicht `abbruchGrund`
  und `abgebrochenAm`.

Regeln im Code: `TourTransitions`.

### Besuchsprotokolle

**TerraMax speichert die Besuchsprotokolle.** Der speech-service schickt das
bestätigte Protokoll an `POST /tours/{id}/protocols` (Format unverändert). Die
process-engine legt keine Protokolle an; sie liest sie bei Bedarf nur
(`GET /tours/{id}/protocols`, `GET /protocols/{id}`, `protokollIds` in der
Übersicht). Ändern und löschen dürfen nur der Ersteller und das Service-Konto.

### Anmeldung der process-engine

Die Engine ruft mit einem von zwei Tokens auf:

1. **Nutzer-Token (Token Propagation):** Sie reicht den `Authorization`-Header des
   Sales Professional durch, solange der Schritt in dessen Request läuft. Es
   gelten die Regeln des Nutzers.
2. **Service-Konto:** Für Schritte ohne Nutzer ein Client-Credentials-Token mit
   der Realm-Rolle `process_engine`. Es hat kein `dealer_id`; `CurrentUser.requireDealerId()`
   wird dafür nicht aufgerufen, der Händler kommt aus der Tour (`TourAccess`). Nur
   `POST /tours` braucht dann `dealerId` im Body. Gesperrt bleiben `GET /tours`,
   `GET /tours/plan` und die Kunden-Endpunkte.

Der speech-service hat kein eigenes Konto. Er reicht beim Speichern eines Protokolls
(`TerraMaxApi.createProtocol`) den `Authorization`-Header des Nutzers durch,
`createdBy` ist also dessen `sub`.

Im lokalen Realm (`terramax-realm.json`) gibt es dafür die Rolle `process_engine`
und den Client `terramax-process-engine` (Secret `local-dev-process-engine-secret`,
nur lokal). Auf dem Team-Keycloak müssen Rolle und Client noch angelegt werden.

Ohne Token erreichbar:

- Health-Check: http://localhost:8088/q/health (einzeln `/q/health/live`, `/q/health/ready`)
- OpenAPI-Dokument: http://localhost:8088/q/openapi (`?format=json` für JSON)
- Swagger UI, auch im Container: http://localhost:8088/q/swagger-ui. Alle
  Engine-Endpunkte haben dort Beispiele. Aufrufe daraus brauchen ein Token (Knopf
  **Authorize**)

## Datenbank

PostgreSQL 17; das Schema verwaltet ausschließlich Flyway
(`src/main/resources/db/migration`, `migrate-at-start`). Hibernate ändert am
Schema nichts.

- `V1__create_location.sql`: Standorte der Marktkarte (unverändert aus dem location-service)
- `V2__create_terramax_core.sql`: `dealer`, `dealer_employee`, `customer`,
  `tour`, `tour_employee`, `tour_customer`, `visitor_protocol` nach dem ER-Modell.
  `dealer.id` entspricht dem Claim `dealer_id`, `dealer.location_id` verknüpft
  optional mit dem Kartenpunkt, `tour.process_instance_id` mit CIB Seven.
- `V3__tour_stop_status.sql`: `tour_customer.status` (`OFFEN`, `DOKUMENTIERT`,
  `UEBERSPRUNGEN`) statt `visited BOOLEAN` (bisher besuchte Kunden werden
  `DOKUMENTIERT`), dazu `skip_reason` (Pflicht bei `UEBERSPRUNGEN`), `started_at`,
  `finished_at` und der Unique-Index `uk_tour_process_instance` auf
  `tour.process_instance_id`.
- `V5__lead_capture.sql`: `customer.status` (`CUSTOMER`/`LEAD`, Standard
  `CUSTOMER`), `customer.source` (`MESSE`, `BESUCH`, `MANUELL`), Adresse
  (`street`, `postal_code`, `city`), Tabellen `customer_contact` und `lead_note`.
  Kundenliste, Karte und Tourplanung nehmen nur `CUSTOMER`; Leads einbeziehen ist #126.
- `V4__tour_aborted.sql`: Tour-Status `aborted` (CHECK-Constraint erweitert),
  `tour.abort_reason` und `tour.aborted_at`.

Schemaänderungen immer als **neue** Migration anlegen (`V5__…sql`), nie eine
bereits ausgerollte Migration ändern, sonst startet der Service auf dem Server
nicht mehr (Checksumme).

Beim Start werden außerdem die Standorte aus den Dateien in
`location.import.files` importiert (Standard: `data/competitors_at_v2.geojson`
und `data/john_deere_at.geojson`). Angelegt werden nur Standorte, deren ID noch
nicht in der Datenbank steht; bestehende Einträge bleiben unverändert.

## Testdaten

Beim Start legt `SeedImporter` nach Flyway und dem Standort-Import Testdaten an.
Wie beim Standort-Import wird nur angelegt, was noch nicht existiert; bestehende
Einträge werden nie überschrieben, ungültige Einträge geloggt und übersprungen.

| Was | Quelle | Ergebnis |
| --- | --- | --- |
| Händler | `data/john_deere_at.geojson` | 16 John-Deere-Händler, ID wie im GeoJSON (z. B. `jd-ltc-korneuburg`), mit dem Standort verknüpft (`location_id`), ohne Kunden |
| Testhändler | `data/test_dealers.geojson` | `D-100` „Testhändler (fiktiv)“ in St. Pölten, ohne Standort |
| Kunden | `data/mock_customers.geojson` (Kopie von `frontend/public/kunden.geojson`) | 18 Mock-Kunden `MOCK-001` bis `MOCK-018`, alle bei `D-100` |

`D-100` ist der Wert des Claims `dealer_id` der Testnutzer `sales` und `dealer`
im Realm. Wer sich lokal mit `sales`/`sales` oder `dealer`/`dealer` anmeldet,
sieht also sofort die 18 Mock-Kunden. Den Mitarbeiter-Eintrag legt der erste
Aufruf an. Ein Nutzer mit `dealer_id = jd-ltc-korneuburg` sieht keine Kunden.

Konfiguration (`application.properties`): `terramax.seed.enabled`,
`terramax.seed.dealer-files`, `terramax.seed.customer-files`,
`terramax.seed.customer-dealer-id`. Abschalten mit `TERRAMAX_SEED_ENABLED=false`
(auch im Portainer-Stack), sobald echte CRM-Daten kommen. Bereits importierte
Daten bleiben dabei in der Datenbank.

## Tourplanung (`GET /tours/plan`)

Übernommen aus dem tour-service (PR #53, `VisitPlanner`) und mit den bisherigen
Platzhaltern zusammengeführt (`DefaultTourPlanner`):

1. **Kandidaten:** Kunden des eigenen Händlers aus der Datenbank (statt
   Mock-Kunden) mit Koordinaten. Wer am Tourtag oder danach schon besucht wurde,
   fällt heraus. Letzter Besuch ist das spätere Datum aus dokumentierten Halten
   (Tourdatum) und Besuchsprotokollen.
2. **Auswahl** (`VisitPrioritizer`, Standard `LongestUnvisitedPrioritizer`): nie
   besuchte zuerst, dann am längsten nicht besuchte, höchstens `maxVisits`.
3. **Reihenfolge** (`RoutePlanner`, Standard `NearestNeighbourRoutePlanner`):
   Luftlinie ab dem Standort des Händlers, immer zum nächstgelegenen offenen Kunden.

Die Antwort hat exakt das Format von `TourPlan` der process-engine (`date`,
`visits[position, customer{id, name, location, position{latitude, longitude},
lastVisit}, distanceFromPreviousKm]`, `totalDistanceKm`). `customer.id` ist die
Datenbank-ID als String, `location` die Region des Kunden, Entfernungen in km auf
eine Nachkommastelle. Die Engine muss nur `TOUR_SERVICE_URL` auf diesen Service
umstellen. Ein Test hält eine Kopie von `TourPlan` und prüft die Felder streng
(`TourPlanResourceTest`). Den Startpunkt (`start`) des tour-service liefert der
Endpunkt nicht mehr, weil `TourPlan` ihn nicht hat.

Unterschiede zum tour-service: Kunden ohne Koordinaten kommen nicht in den Plan;
Fehler kommen als `{"error": …}` statt `{"message": …}` (die Engine wertet den
Body nicht aus).

## Platzhalter-Schnittstellen

Mit `TODO(#64-meeting)` markiert, siehe [Offene Punkte](#offene-punkte-64-meeting).

| Schnittstelle | Standard | Vorbereitet |
| --- | --- | --- |
| `VisitPrioritizer` | `LongestUnvisitedPrioritizer`: nie besuchte Kunden zuerst, dann am längsten nicht besuchte | `LlmVisitPrioritizer` (OpenAI), `terramax.prioritizer.mode=llm`, **auf Eis** |
| `RoutePlanner` | `NearestNeighbourRoutePlanner`: Luftlinie ab Händler, Fahrzeit `null` | Mapbox Optimization API (offen) |

### OpenAI (auf Eis)

Die LLM-Priorisierung ist vorbereitet und getestet, aber **auf Eis**: Sie ist
nicht im Einsatz, `terramax.prioritizer.mode` bleibt `stub`, und
`OPENAI_API_KEY` ist optional (ohne Key startet der Service normal).

Technik für später: LangChain4j (`quarkus-langchain4j-openai`) wie im
speech-service, Modell `gpt-4o-mini`, strukturierte Antwort (`VisitPrioritization`).
Der Key kommt ausschließlich aus `OPENAI_API_KEY` und liegt nie im Repo. An das LLM
gehen nur Kunden-ID, Region, Segment und das Datum des letzten Besuchs, keine
Namen, Kontaktdaten oder Koordinaten. Die Tests ersetzen OpenAI durch ein
Fake-ChatModel (`RecordingChatModel`).

### CIB Seven

TerraMax ruft die Engine nicht auf. Den früheren `ProcessEngineClient` (Stub und
REST-Client auf `/engine-rest`) gibt es nicht mehr, ebenso die Variablen
`TERRAMAX_ENGINE_MODE` und `TERRAMAX_ENGINE_URL`. Die Engine ruft die Endpunkte
oben auf; Übergabe:
[docs/process/terramax-engine-schnittstelle.md](../docs/process/terramax-engine-schnittstelle.md).

Kafka ist bewusst nicht angebunden.

## Keycloak: lokal oder Team-Keycloak

Der Service ist ein reiner Resource-Server: Er prüft nur Bearer-Tokens und
leitet nicht auf eine Login-Seite weiter.

**Ohne Variable (Standard):** Quarkus startet im Dev-Modus einen lokalen
Keycloak über Dev Services mit dem Testrealm.

**Mit Team-Keycloak:** Tokens werden gegen den gemeinsamen Keycloak geprüft (Variable
`QUARKUS_OIDC_AUTH_SERVER_URL`; `OIDC_AUTH_SERVER_URL` wirkt nur im Prod-Profil).
Dann wird kein lokaler Keycloak-Container gestartet.

```powershell
cd terramax-service
$env:QUARKUS_OIDC_AUTH_SERVER_URL = "https://terramax-keycloak.winfprojekt.de/realms/terramax"
./mvnw quarkus:dev
# zurück zum lokalen Keycloak:
Remove-Item Env:QUARKUS_OIDC_AUTH_SERVER_URL
```

Das Frontend muss sich dann ebenfalls am Team-Keycloak anmelden
(Realm `terramax`, Client `publicClient`). Der Issuer im Token muss genau
`https://terramax-keycloak.winfprojekt.de/realms/terramax` lauten.
Eine Audience-Prüfung findet nicht statt.

Im Prod-Betrieb ist die Variable Pflicht: Ohne `OIDC_AUTH_SERVER_URL`
(oder `QUARKUS_OIDC_AUTH_SERVER_URL`, wie im Stack) startet der Service nicht.
`./mvnw verify` nutzt immer den lokalen Testrealm und braucht kein Netzwerk
zum Team-Keycloak. Secrets sind nicht nötig und gehören nicht ins Repository.

## Lokal mit dem Frontend testen (Marktkarte)

1. Service mit Team-Keycloak starten (siehe oben).
2. In `frontend/.env.local` (wird nicht eingecheckt) Mapbox-Token und
   Keycloak-Werte setzen:

   ```env
   VITE_MAPBOX_TOKEN=<dein-mapbox-token>
   VITE_KEYCLOAK_URL=https://terramax-keycloak.winfprojekt.de
   VITE_KEYCLOAK_REALM=terramax
   VITE_KEYCLOAK_CLIENT_ID=publicClient
   # optional, Standard ist http://localhost:8088
   VITE_LOCATION_SERVICE_URL=http://localhost:8088
   ```

3. `cd frontend && npm install && npm run dev`, dann http://localhost:5173
   öffnen und anmelden. Auf der Karte erscheinen die Standorte in drei Kategorien.

## CORS

Standardmäßig ist nur der lokale Vite-Server `http://localhost:5173`
freigegeben (Methoden `GET`, `POST`, `PUT`, `PATCH`, `DELETE`, `OPTIONS`; Header
`Authorization`, `Content-Type`). Weitere Origins kommagetrennt über
`CORS_ORIGINS` (im Stack: `TERRAMAX_CORS_ORIGINS`). `*` ist nicht erlaubt.

## Build und Prüfung

```bash
./mvnw verify
```

Dabei wird unter anderem die Formatierung mit
`../config/formatter/eclipse-java.xml` geprüft. Vorher formatieren:
`./mvnw formatter:format`.

## Betrieb

Der Service läuft im Container auf Port **8080** (Quarkus-Standard), lokal im Dev-Modus auf **8088**.

### Image

Die GitHub Action `.github/workflows/terramax-service.yml` läuft bei
Änderungen unter `terramax-service/**`, `config/formatter/**` oder am Workflow
selbst. Sie führt `./mvnw verify` aus und baut das Image aus
`src/main/docker/Dockerfile.jvm`. Schlägt ein Test fehl, wird kein Image gebaut.

- Pull Request: Das Image wird nur gebaut, nicht veröffentlicht.
- Push auf `main`: Das Image wird nach
  `ghcr.io/thi-projekte/terramax-terramax-service` gepusht
  (Tags `main` und `sha-<commit>`).

Lokal bauen:

```powershell
cd terramax-service
./mvnw package -DskipTests
docker build -f src/main/docker/Dockerfile.jvm -t terramax-terramax-service .
```

### Portainer-Stack

Der Service steht im gemeinsamen [`docker-compose.yml`](../docker-compose.yml)
als `terramax-service` (ohne `ports:`, im Proxy-Netzwerk und in
`terramax-intern`) und `terramax-db` (`postgres:17`, Volume `terramax-db-data`,
nur in `terramax-intern`). Der Service startet erst, wenn der Healthcheck der
Datenbank grün ist.

### Umgebungsvariablen

Alle Namen stehen in [`.env.example`](.env.example). Für den lokalen Betrieb
die Datei als `.env` kopieren und befüllen. `.env` wird nicht committet.

| Variable | Pflicht | Bedeutung |
| --- | --- | --- |
| `OIDC_AUTH_SERVER_URL` | ja | Keycloak-Realm, z. B. `https://terramax-keycloak.winfprojekt.de/realms/terramax`. Ohne Wert startet der Service nicht |
| `CORS_ORIGINS` | nein | Erlaubte Frontend-Origins, kommagetrennt (Standard `http://localhost:5173`) |
| `QUARKUS_DATASOURCE_JDBC_URL` | ja | z. B. `jdbc:postgresql://<host>:5432/<db>` |
| `QUARKUS_DATASOURCE_USERNAME` | ja | Datenbank-Benutzer |
| `QUARKUS_DATASOURCE_PASSWORD` | ja | Datenbank-Passwort |
| `OPENAI_API_KEY` | nein | optional; nur für `TERRAMAX_PRIORITIZER_MODE=llm`, das auf Eis ist |
| `TERRAMAX_PRIORITIZER_MODE` | nein | `stub` (Standard) oder `llm` (auf Eis) |
| `TERRAMAX_SEED_ENABLED` | nein | Testdaten beim Start importieren (Standard `true`) |

Im Portainer-Stack: `TERRAMAX_DB_PASSWORD`, `TERRAMAX_CORS_ORIGINS` und optional
`OPENAI_API_KEY`, `TERRAMAX_SERVICE_TAG` (Standard `main`) sowie
`TERRAMAX_SEED_ENABLED` (Standard `true`).

### Container lokal starten

```powershell
docker run --rm -p 8080:8080 --env-file .env terramax-terramax-service
```

Danach erreichbar unter http://localhost:8080/q/health.

## Offene Punkte (#64-meeting)

Im Code mit `TODO(#64-meeting)` markiert (`grep -rn "TODO(#64-meeting)"`):

| Thema | Offen | Stelle |
| --- | --- | --- |
| Netzwerk | Proxy-Netzwerk `nginx-proxy-manager` (wie speech-service) oder `nginx-reverse-proxy` (Issue) | `docker-compose.yml` |
| Priorisierung (auf Eis) | Kriterien (Umsatz, offene Angebote, Follow-ups, Saison) und Prompt abstimmen, erst dann `llm` als Standard | `VisitPrioritizer`, `LongestUnvisitedPrioritizer`, `LlmVisitPrioritizer` |
| Routen | Mapbox Optimization API für Reihenfolge und Fahrzeiten | `RoutePlanner`, `NearestNeighbourRoutePlanner` |
| process-engine | Delegates umstellen (Lukas, CIB-Seven-Entwickler) | [docs/process/terramax-engine-schnittstelle.md](../docs/process/terramax-engine-schnittstelle.md) |
| Keycloak | Rolle `process_engine` und Client `terramax-process-engine` (Service-Konto) im Team-Realm anlegen | – |
| tour-service | Nach der Umstellung der Engine kann der tour-service entfallen; `GET /tours/plan` liefert jetzt dieser Service | – |
| Stammdaten | Woher echte Händler- und Kundendaten kommen (CRM-Import, Admin-Endpunkt); bis dahin Testdaten. Mitarbeiter entstehen automatisch beim ersten Aufruf | `SeedImporter` |
| Vorschlag: KI-Besuchsvorbereitung | Siehe unten; noch nicht umgesetzt | – |

Geklärt:

- Der speech-service ruft `POST /tours/{id}/protocols` mit dem durchgereichten
  Nutzer-Token auf (siehe [Anmeldung der process-engine](#anmeldung-der-process-engine)).
- **Protokolle:** TerraMax speichert sie; der speech-service schickt sie, die Engine
  legt keine an und liest sie nur. Doppelte Protokolle aus Engine und speech-service
  sind damit ausgeschlossen ([Besuchsprotokolle](#besuchsprotokolle)).
- **Abbruch:** eigener Status `aborted` über `POST /tours/{id}/abort`
  ([Tour-Status und Abbruch](#tour-status-und-abbruch)).
- Die Tourplanung aus PR #53 ist übernommen ([Tourplanung](#tourplanung-get-toursplan)),
  die Aufrufrichtung ist Engine → TerraMax.

### Vorschlag: KI-Besuchsvorbereitung (Briefing)

Idee aus einem früheren Entwurf, fürs Meeting: `POST /customers/{id}/briefing`
fasst die letzten Besuchsprotokolle eines Kunden per OpenAI zu höchstens 8
Stichpunkten zusammen, offene Punkte und fällige Follow-ups zuerst. Ohne
Protokolle schlägt es Gesprächseinstiege vor. Das Ergebnis wird nicht
gespeichert und als KI-Text gekennzeichnet (`aiGenerated: true`).

Vor einer Umsetzung zu klären:

- **Datenschutz:** Protokolltexte enthalten Namen von Gesprächspartnern und gehen
  dabei an OpenAI. Das widerspricht der bisherigen Regel „nur IDs an das LLM“ und
  braucht eine bewusste Entscheidung (z. B. nur Themen, Ergebnis, nächste Schritte
  und Follow-up-Datum senden, keine Stammdaten).
- **Zugriff:** gleiche Regeln wie `GET /customers/{id}/protocols`, also für einen
  `sales_professional` nur Protokolle aus eigenen Touren.

## Vor dem Merge / Nach dem Merge

**Vor dem Merge (Portainer):**

- [ ] Variablen im Stack gesetzt: `TERRAMAX_DB_PASSWORD`, `TERRAMAX_CORS_ORIGINS`
      (z. B. `https://terramax.winfprojekt.de`); `OPENAI_API_KEY` ist optional
- [ ] Im Team-Keycloak Rolle `process_engine` und Service-Konto-Client für die Engine angelegt
- [ ] Netzwerkname geklärt (`nginx-proxy-manager` oder `nginx-reverse-proxy`) und im
      `docker-compose.yml` angepasst
- [ ] Proxy-Host `<subdomain>.winfprojekt.de` → `terramax-terramax-service-1:8080`
      mit Let's-Encrypt-Zertifikat angelegt
- [ ] Geklärt, ob der Stack nach dem Merge automatisch aktualisiert wird oder von Hand

**Nach dem Merge:**

- [ ] Workflow `terramax-service` auf `main` grün, Image `:main` in GHCR
- [ ] Stack in Portainer aktualisiert (Image neu ziehen)
- [ ] `https://<subdomain>/q/health` antwortet von außen mit `UP`
- [ ] `https://<subdomain>/customers` ohne Token antwortet mit `401`
- [ ] Log zeigt den Testdaten-Import (17 Händler, 18 Kunden); später mit
      `TERRAMAX_SEED_ENABLED=false` abschalten, sobald echte CRM-Daten kommen
