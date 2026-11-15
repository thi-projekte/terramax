# TerraMax-Service

Fachlicher Kern-Service von TerraMax (Issue #64): Händler, Kunden, Besuchstouren,
Besuchsprotokolle und die Händlerstandorte der Marktkarte. Die SPA und der
speech-service rufen ihn per REST auf. Er hat eine eigene PostgreSQL-Datenbank
und ist für CIB Seven (process-engine) und die OpenAI-LLM-API vorbereitet.
Architektur: [docs/architecture/terramax-architecture.html](../docs/architecture/terramax-architecture.html),
Datenmodell: [docs/architecture/terramax-db-schema.md](../docs/architecture/terramax-db-schema.md).

Der Service ist aus dem früheren `location-service` hervorgegangen
(`git mv`, Historie bleibt erhalten). Die Standort-Funktion liegt weiter im
Package `de.thi.terramax.location`.

> TODO: Das Frontend liest die Adresse noch aus `VITE_LOCATION_SERVICE_URL`
> (Standard `http://localhost:8082`). Die Variable wird später umbenannt; sie
> zeigt jetzt auf diesen Service, Port und Pfad `/locations` sind gleich geblieben.

## Aufbau

| Package | Inhalt |
| --- | --- |
| `de.thi.terramax.location` | Händlerstandorte (Wettbewerber, John Deere) als GeoJSON, Import beim Start |
| `de.thi.terramax.dealer` | Händler und Mitarbeiter, `GET /dealers/me` |
| `de.thi.terramax.customer` | Kunden, auch als GeoJSON |
| `de.thi.terramax.tour` | Touren, `TourAccess` (Sichtbarkeit), Platzhalter `RoutePlanner` und `TourPlanner` |
| `de.thi.terramax.protocol` | Besuchsprotokolle |
| `de.thi.terramax.ai` | Platzhalter `VisitPrioritizer` und OpenAI-Anbindung (LangChain4j) |
| `de.thi.terramax.engine` | Platzhalter `ProcessEngineClient` und REST-Client zu CIB Seven |
| `de.thi.terramax.security` | Rollen, `CurrentUser`, `GET /me` |
| `de.thi.terramax.common` | `ErrorResponse`, OpenAPI-Definition |

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

- Service: http://localhost:8082, Swagger UI: http://localhost:8082/q/swagger-ui
- PostgreSQL und Keycloak starten automatisch als Dev Services. Keycloak läuft
  auf http://localhost:8180 mit dem Realm aus `src/main/resources/terramax-realm.json`
  (Kopie aus dem `hello-service`, Nutzer `sales`/`sales` und `dealer`/`dealer`,
  beide mit `dealer_id = D-100`).
- Die Datenbank ist anfangs bis auf die Standorte leer. Händler, Mitarbeiter und
  Kunden lassen sich lokal per SQL anlegen (Dev UI → Datasource) oder Kunden über
  `POST /customers`, sobald der Händler existiert.

Beenden mit Ctrl + C.

## Endpunkte und API-Doku

Alle Endpunkte brauchen ein Keycloak-Token (`Authorization: Bearer <token>`):
ohne oder mit ungültigem Token `401`, ohne passende Rolle `403`. Fehler kommen
im Format `{"error": "…"}`.

| Methode und Pfad | Rolle | Liefert |
| --- | --- | --- |
| `GET /me` | jeder angemeldete Nutzer | Nutzer mit Rollen, `dealerId`, Händler und Mitarbeiter-ID |
| `GET /dealers/me` | beide | eigener Händler (`404`, wenn noch nicht angelegt) |
| `GET /customers` | beide | Kunden des eigenen Händlers; mit `Accept: application/geo+json` als `FeatureCollection` (Eigenschaften wie `frontend/public/kunden.geojson`) |
| `GET /customers/{id}` | beide | ein Kunde |
| `POST /customers`, `PUT /customers/{id}` | beide | Kunde anlegen bzw. ändern (`409` bei vergebener Kundennummer) |
| `GET /customers/{id}/protocols` | beide | Besuchsprotokolle des Kunden, neueste zuerst |
| `GET /tours` | beide | Touren, neueste zuerst |
| `GET /tours/{id}` | beide | Tour mit Kunden in Reihenfolge |
| `POST /tours` | beide | Tour anlegen (Status `planned`) |
| `PATCH /tours/{id}` | beide | `status`, `customerOrder` (neue Reihenfolge), `visited` (`{"<kundenId>": true}`) |
| `GET /tours/{id}/protocols` | beide | Besuchsprotokolle der Tour |
| `POST /tours/{id}/protocols` | beide | Protokoll speichern; Format wie `VisitProtocol` im speech-service plus `customerId` und optional `transcript` |
| `GET /locations` | angemeldet | Standorte als `FeatureCollection`, optional `?category=competitor,mixed_competitor` |
| `GET /locations/{id}` | angemeldet | ein Standort als `Feature` |

„beide“ heißt `sales_professional` oder `dealer_principal`. Für diese
Endpunkte gilt außerdem:

- Ohne Claim `dealer_id` im Token: `403`.
- Daten anderer Händler sind nie sichtbar; fremde IDs antworten mit `404`.
- `sales_professional` sieht alle Kunden seines Händlers, aber nur Touren, die er
  laut `tour_employee` fährt, und nur deren Protokolle.
- `dealer_principal` sieht alle Kunden, Touren und Protokolle seines Händlers.
- Die Zuordnung Nutzer → Mitarbeiter läuft über den Claim `sub`
  (`dealer_employee.keycloak_user_id`). Ohne Mitarbeiter-Eintrag sieht ein
  `sales_professional` keine Touren und kann keine anlegen.

Ohne Token erreichbar:

- Health-Check: http://localhost:8082/q/health (einzeln `/q/health/live`, `/q/health/ready`)
- OpenAPI-Dokument: http://localhost:8082/q/openapi (`?format=json` für JSON)
- Swagger UI nur im Dev-Modus: http://localhost:8082/q/swagger-ui
  (Token über den Knopf **Authorize** eintragen)

## Datenbank

PostgreSQL 17; das Schema verwaltet ausschließlich Flyway
(`src/main/resources/db/migration`, `migrate-at-start`). Hibernate ändert am
Schema nichts.

- `V1__create_location.sql`: Standorte der Marktkarte (unverändert aus dem location-service)
- `V2__create_terramax_core.sql`: `dealer`, `dealer_employee`, `customer`,
  `tour`, `tour_employee`, `tour_customer`, `visitor_protocol` nach dem ER-Modell.
  `dealer.id` entspricht dem Claim `dealer_id`, `dealer.location_id` verknüpft
  optional mit dem Kartenpunkt, `tour.process_instance_id` mit CIB Seven.

Schemaänderungen immer als **neue** Migration anlegen (`V3__…sql`), nie eine
bereits ausgerollte Migration ändern, sonst startet der Service auf dem Server
nicht mehr (Checksumme).

Beim Start werden außerdem die Standorte aus den Dateien in
`location.import.files` importiert (Standard: `data/competitors_at_v2.geojson`
und `data/john_deere_at.geojson`). Angelegt werden nur Standorte, deren ID noch
nicht in der Datenbank steht; bestehende Einträge bleiben unverändert.

## Platzhalter-Schnittstellen

Alle mit `TODO(#64-meeting)` markiert, siehe [Offene Punkte](#offene-punkte-64-meeting).

| Schnittstelle | Platzhalter (Standard) | Vorbereitet |
| --- | --- | --- |
| `VisitPrioritizer` | `LongestUnvisitedPrioritizer`: nie besuchte Kunden zuerst, dann am längsten nicht besuchte | `LlmVisitPrioritizer` (OpenAI), `terramax.prioritizer.mode=llm` |
| `RoutePlanner` | `NearestNeighbourRoutePlanner`: Luftlinie ab Händler, Fahrzeit `null` | – |
| `TourPlanner` | `DefaultTourPlanner`: Priorisierung, dann Route; noch kein Endpunkt | – |
| `ProcessEngineClient` | `StubProcessEngineClient`: loggt nur, Fake-ID `stub-…` | `RestProcessEngineClient` gegen `/engine-rest`, `terramax.engine.mode=rest` |

### OpenAI

LangChain4j (`quarkus-langchain4j-openai`) wie im speech-service, Modell
`gpt-4o-mini`, strukturierte Antwort (`VisitPrioritization`). Der Key kommt
ausschließlich aus `OPENAI_API_KEY` und liegt nie im Repo. Ohne Key startet der
Service trotzdem. An das LLM gehen nur Kunden-ID, Region, Segment und das
Datum des letzten Besuchs, keine Namen, Kontaktdaten oder Koordinaten. Die Tests
ersetzen OpenAI durch ein Fake-ChatModel (`RecordingChatModel`).

### CIB Seven

`CibSevenApi` ist ein MicroProfile-REST-Client auf die REST-API der
process-engine (`TERRAMAX_ENGINE_URL`, Standard `http://localhost:8086/engine-rest`).
Mit `@AccessToken` geht das Token des Aufrufers mit; die Engine prüft die
Rollen selbst. `startTour` startet den Prozess `Besuchstour`
(`terramax.engine.process-key`) mit der Tour-ID als Business Key.

Kafka ist bewusst nicht angebunden.

## Keycloak: lokal oder Team-Keycloak

Der Service ist ein reiner Resource-Server: Er prüft nur Bearer-Tokens und
leitet nicht auf eine Login-Seite weiter.

**Ohne Variable (Standard):** Quarkus startet im Dev-Modus einen lokalen
Keycloak über Dev Services mit dem Testrealm.

**Mit Team-Keycloak:** Tokens werden gegen den gemeinsamen Keycloak geprüft.
Dann wird kein lokaler Keycloak-Container gestartet.

```powershell
cd terramax-service
$env:OIDC_AUTH_SERVER_URL = "https://terramax-keycloak.winfprojekt.de/realms/terramax"
./mvnw quarkus:dev
# zurück zum lokalen Keycloak:
Remove-Item Env:OIDC_AUTH_SERVER_URL
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
   # optional, Standard ist http://localhost:8082
   VITE_LOCATION_SERVICE_URL=http://localhost:8082
   ```

3. `cd frontend && npm install && npm run dev`, dann http://localhost:5173
   öffnen und anmelden. Auf der Karte erscheinen die Standorte in drei Kategorien.

## CORS

Standardmäßig ist nur der lokale Vite-Server `http://localhost:5173`
freigegeben (Methoden `GET`, `POST`, `PUT`, `PATCH`, `OPTIONS`; Header
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

Der Service läuft im Container auf Port **8082**.

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
| `OPENAI_API_KEY` | nein | nur für `TERRAMAX_PRIORITIZER_MODE=llm` |
| `TERRAMAX_PRIORITIZER_MODE` | nein | `stub` (Standard) oder `llm` |
| `TERRAMAX_ENGINE_MODE` | nein | `stub` (Standard) oder `rest` |
| `TERRAMAX_ENGINE_URL` | nein | REST-API der process-engine (Standard `http://localhost:8086/engine-rest`) |

Im Portainer-Stack: `TERRAMAX_DB_PASSWORD`, `TERRAMAX_CORS_ORIGINS`,
`OPENAI_API_KEY` und optional `TERRAMAX_SERVICE_TAG` (Standard `main`).

### Container lokal starten

```powershell
docker run --rm -p 8082:8082 --env-file .env terramax-terramax-service
```

Danach erreichbar unter http://localhost:8082/q/health.

## Offene Punkte (#64-meeting)

Im Code mit `TODO(#64-meeting)` markiert (`grep -rn "TODO(#64-meeting)"`):

| Thema | Offen | Stelle |
| --- | --- | --- |
| Netzwerk | Proxy-Netzwerk `nginx-proxy-manager` (wie speech-service) oder `nginx-reverse-proxy` (Issue) | `docker-compose.yml` |
| speech-service | Ruft der speech-service `POST /tours/{id}/protocols` auf, und wie wird das Token weitergegeben? | `TourProtocolResource` |
| Priorisierung | Kriterien (Umsatz, offene Angebote, Follow-ups, Saison) und Prompt abstimmen, dann `llm` als Standard | `VisitPrioritizer`, `LongestUnvisitedPrioritizer`, `LlmVisitPrioritizer`, `application.properties` |
| Routen | Mapbox Optimization API für Reihenfolge und Fahrzeiten | `RoutePlanner`, `NearestNeighbourRoutePlanner` |
| tour-service | Übernahme des tour-service aus PR #53 mit Mohamed klären; dann `GET /tours/plan` im bisherigen Format für die process-engine | `TourPlanner`, `DefaultTourPlanner` |
| process-engine | Aufrufrichtung und Prozess-Key; CIB Seven auf dem Server kommt mit #63 | `ProcessEngineClient`, `RestProcessEngineClient`, `StubProcessEngineClient`, `application.properties` |
| Stammdaten | Wie Händler und Mitarbeiter angelegt werden (Import, Admin-Endpunkt, beim ersten Login) | `CurrentUser` |

## Vor dem Merge / Nach dem Merge

**Vor dem Merge (Portainer):**

- [ ] Variablen im Stack gesetzt: `TERRAMAX_DB_PASSWORD`, `TERRAMAX_CORS_ORIGINS`
      (z. B. `https://terramax.winfprojekt.de`), `OPENAI_API_KEY`
- [ ] Netzwerkname geklärt (`nginx-proxy-manager` oder `nginx-reverse-proxy`) und im
      `docker-compose.yml` angepasst
- [ ] Proxy-Host `<subdomain>.winfprojekt.de` → `terramax-terramax-service-1:8082`
      mit Let's-Encrypt-Zertifikat angelegt
- [ ] Geklärt, ob der Stack nach dem Merge automatisch aktualisiert wird oder von Hand

**Nach dem Merge:**

- [ ] Workflow `terramax-service` auf `main` grün, Image `:main` in GHCR
- [ ] Stack in Portainer aktualisiert (Image neu ziehen)
- [ ] `https://<subdomain>/q/health` antwortet von außen mit `UP`
- [ ] `https://<subdomain>/customers` ohne Token antwortet mit `401`
