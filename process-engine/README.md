# Process Engine (CIB seven)

BPMN-Engine für TerraMax. Sie führt den Prozess **Besuchstour** aus (Issue #29,
Modell von Erion: [docs/process/besuchstour.bpmn](../docs/process/besuchstour.bpmn))
und stellt die REST-API von CIB seven unter `/engine-rest` bereit.

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

## Wie die Bausteine zusammenspielen

```
React-Frontend ──(Bearer-Token)──> process-engine /engine-rest   Tour starten, Aufgaben abfragen/abschließen
                                        │
                                        │ Service Task "Besuchsplan erzeugen"
                                        ▼
                                   tour-service GET /tours/plan   (#29 Teil 2, gleiches Token)
```

- Jeder Aufruf von `/engine-rest` braucht ein Keycloak-Token mit Rolle
  `sales_professional` (sonst 403, ohne Token 401).
- Der Benutzername aus dem Token (`preferred_username`) wird in der Engine zur
  Benutzer-ID. Dadurch gehört eine gestartete Tour dem Startenden
  (`initiator`), und alle Aufgaben sind ihm zugewiesen.
- Das BPMN-Modell wird direkt aus `docs/process/` gebaut (siehe `pom.xml`),
  es gibt also keine Kopie davon.

## Aufrufrichtung TerraMax ↔ CIB seven (#63)

| Wer | ruft wen | Wie | Wofür |
|---|---|---|---|
| React-Frontend (SPA) | process-engine | REST `/engine-rest`, Token des Nutzers | Tour starten, Aufgaben lesen und abschließen, `TourAbbrechen` |
| process-engine | TerraMax-Services (heute tour-service) | REST, synchron im Service Task, gleiches Token | Daten für einen Schritt holen, z. B. `GET /tours/plan` |
| speech-service | process-engine | External Tasks (`fetchAndLock`), noch nicht angebunden | `sprachnotiz-transkribieren`, `protokollentwurf-erstellen` |
| process-engine | Kafka `terramax.process.events` | Events nach jedem Commit | `notifications` informiert die SPA (siehe unten) |

Festlegung: **Die Engine steuert, die Services liefern zu.** TerraMax-Services
rufen die Engine nicht auf und kennen keine Prozess-IDs. Wenn der Ablauf
weitergehen soll, schließt die SPA eine Aufgabe ab oder die Engine fragt einen
Service. Wer den Zustand einer Tour wissen will, liest ihn per REST aus der
Engine oder hört auf die Kafka-Events.

## Prozess-Events (Kafka)

Die Engine publiziert die Events aus [docs/kafka/README.md](../docs/kafka/README.md)
auf `terramax.process.events` (Paket `de.thi.terramax.process.events`):

- Ein Engine-Plugin hängt beim Deployen Listener an die BPMN-Elemente. Das
  Modell selbst bleibt unverändert.
- Gesendet wird erst **nach dem Commit** der Engine-Transaktion. Schlägt ein
  Schritt fehl (z. B. Überspringen ohne Grund), entsteht kein Event.
- `userid` ist der Keycloak-`sub` des Startenden. Die Engine merkt ihn sich beim
  Start in der Prozessvariablen `initiatorId`, weil sie sonst nur den
  Benutzernamen kennt.
- Ist Kafka nicht erreichbar, wird der Fehler geloggt und der Prozess läuft
  weiter. Das Event fehlt dann. Für garantierte Zustellung wäre später eine
  Outbox-Tabelle nötig.
- Lokal und in Tests ist Kafka aus (`KAFKA_ENABLED=false`), die Events landen
  nur im Log.

## Die Platzhalter aus dem Modell

| Delegate im Modell | Klasse | Was passiert |
|---|---|---|
| `besuchsplanErzeugen` | `BesuchsplanErzeugen` | holt den Plan vom tour-service, setzt `besuchsplan` |
| `besuchUeberspringenSpeichern` | `BesuchUeberspringenSpeichern` | speichert Kunde + Grund (Pflicht) in `besuchsergebnisse` |
| `protokollZuordnen` | `ProtokollZuordnen` | speichert das bestätigte `protokoll` in `besuchsergebnisse` |
| `touruebersichtErstellen` | `TouruebersichtErstellen` | setzt `touruebersicht`: dokumentiert / übersprungen / offen |

Noch nicht angebunden sind die External Tasks `sprachnotiz-transkribieren`
und `protokollentwurf-erstellen` (Teile 4–5, speech-service). Bis dahin
setzt das Frontend `sprachnotizVorhanden=false`; das Modell sieht diesen Weg vor.

Ergebnisse werden im MVP in der Tour gespeichert (`besuchsergebnisse`), weil es
noch keine Kundendatenbank gibt.

**Offener Punkt aus dem Modell behoben:** In `docs/process/README.md` steht,
dass Daten eines Besuchs in den nächsten überlaufen. Die Engine entfernt nach
jedem Besuch `ueberspringenGrund`, `protokoll`, `protokollEntwurf`,
`transkript`, `sprachnotizId` und die Zeitstempel (siehe
`BesuchstourVariablen#besuchsdatenEntfernen`). Eine Korrektur direkt im Modell
bleibt trotzdem sinnvoll.

## Prozessvariablen für das Frontend

| Variable | Gesetzt von | Inhalt |
|---|---|---|
| `tourDatum` | Start | `JJJJ-MM-TT`, optional (sonst heute) |
| `besuchsplan` | Engine / „Besuchsplan prüfen“ | Liste von Kunden: `position`, `kundeId`, `name`, `ort`, `latitude`, `longitude`, `letzterBesuch`, `entfernungKm` |
| `planBestaetigt` | „Besuchsplan prüfen“ | Boolean |
| `kunde` | Engine (je Besuch) | aktueller Eintrag aus `besuchsplan` |
| `besuchUeberspringen`, `ueberspringenGrund` | „Besuch vorbereiten“ | Boolean, Text |
| `besuchBegonnenAm`, `besuchAbgeschlossenAm` | „Besuch vorbereiten“ / „durchführen“ | Zeitstempel als Text |
| `sprachnotizVorhanden` | „Sprachnotiz aufnehmen“ | Boolean (vorerst `false`) |
| `protokoll` | „Protokoll prüfen“ | Objekt, z. B. `gespraechspartner`, `themen`, `ergebnis`, `naechsteSchritte`, `followUpDatum` |
| `touruebersicht` | Engine | `besuche` (mit `status`), `anzahlDokumentiert`, `anzahlUebersprungen`, `anzahlOffen` |
| `initiatorId` | Engine (Start) | Keycloak-`sub` des Startenden, Empfänger der Kafka-Events |

Listen und Objekte werden als JSON gespeichert. Beim Abschließen einer Aufgabe
über REST schickt das Frontend sie so:

```json
{ "variables": { "besuchsplan": {
    "type": "Object",
    "value": "[{\"kundeId\":\"K-1001\", …}]",
    "valueInfo": { "objectTypeName": "java.util.ArrayList", "serializationDataFormat": "application/json" }
} } }
```

Abbrechen der Tour: Message `TourAbbrechen` per
`POST /engine-rest/message` mit `processInstanceId`.

## Lokal starten

Voraussetzungen: JDK 25, Docker.

1. **Keycloak und tour-service** starten (der tour-service bringt über Dev
   Services einen Keycloak auf Port 8180 mit):
   ```bash
   cd tour-service
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

Für einen echten Login braucht der Realm die Rolle `sales_professional`
(kommt mit `feature/23-sales-login`).

## Konfiguration (Umgebungsvariablen)

| Variable | Standard | Bedeutung |
|---|---|---|
| `KEYCLOAK_ISSUER_URL` | `http://localhost:8180/realms/terramax` | Aussteller der Tokens |
| `TOUR_SERVICE_URL` | `http://localhost:8084` | Adresse des tour-service |
| `CORS_ORIGINS` | `http://localhost:5173` | erlaubte Frontend-Adressen |
| `SPRING_DATASOURCE_URL` / `_USERNAME` / `_PASSWORD` | H2-Datei | auf dem Server PostgreSQL |
| `KAFKA_ENABLED` | `false` | `true`: Prozess-Events nach Kafka senden |
| `KAFKA_BOOTSTRAP_SERVERS` | `localhost:9092` | auf dem Server `kafka:9092` |

## Server (Portainer)

Die Engine läuft im gemeinsamen Stack (Root-`docker-compose.yml`) mit einer
eigenen PostgreSQL-Datenbank:

| Container | Netzwerke | Erreichbar |
|---|---|---|
| `process-engine` | `nginx-proxy-manager`, `process-engine-intern`, `kafka-intern` | nur über den Nginx Proxy Manager (`terramax-process-engine-1:8086`) |
| `process-engine-db` (PostgreSQL 17) | `process-engine-intern` | nur intern, Daten im Volume `terramax-process-engine-db` |

Die Engine legt ihre Tabellen (Runtime und History) beim ersten Start selbst
an und deployt dabei das Modell `besuchstour.bpmn`.

**Einrichten (einmalig):**

1. Portainer → Stack `terramax` → Variable `PROCESS_ENGINE_DB_PASSWORD` setzen
   (zufälliges Passwort, nirgends sonst ablegen) → *Update the stack*.
2. Nginx Proxy Manager → Proxy Host anlegen: Domain z. B.
   `terramax-engine.winfprojekt.de`, Forward `terramax-process-engine-1`,
   Port `8086`, Tab *SSL*: Let's-Encrypt-Zertifikat anfordern, *Force SSL*.

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

25 Tests: der komplette Prozess mit echter Engine (normaler Ablauf,
Überspringen, Abbruch, zu wenige Kunden, Plan verworfen, Grund ist Pflicht),
die Prozess-Events (Reihenfolge, Inhalte, kein Event bei Fehler), das
Kafka-Format, der Start mit Kafka, die Absicherung der REST-API und der Aufruf des tour-service.

## Offen (mit Team/Prof klären)

- Der tour-service ist noch nicht im Server-Stack (#64). Bis dahin schlägt
  „Besuchsplan erzeugen“ auf dem Server fehl.
- Webapps (Cockpit/Tasklist) mit Keycloak-Login sind nicht eingebaut (siehe
  Tabelle oben: nur REST-API). Falls das Team sie braucht, kommen dafür
  `cibseven-bpm-spring-boot-starter-webapp` und ein OIDC-Login dazu.
- Service-Konto in Keycloak, falls „Besuchsplan erzeugen“ einmal asynchron läuft
- Anbindung der Sprach-Schritte an den speech-service (External Tasks)
- Berechtigungen in der Engine: Derzeit darf jeder Sales Professional über die
  REST-API alle Touren sehen; das Frontend filtert nach `assignee`
