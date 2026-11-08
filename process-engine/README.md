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

## Tests

```bash
./mvnw verify
```

15 Tests: der komplette Prozess mit echter Engine (normaler Ablauf,
Überspringen, Abbruch, zu wenige Kunden, Plan verworfen, Grund ist Pflicht),
die Absicherung der REST-API und der Aufruf des tour-service.

## Offen (mit Team/Prof klären)

- Wo und wie die zentrale Instanz auf dem Server läuft (Portainer, PostgreSQL)
- Service-Konto in Keycloak, falls „Besuchsplan erzeugen“ einmal asynchron läuft
- Anbindung der Sprach-Schritte an den speech-service (External Tasks)
- Berechtigungen in der Engine: Derzeit darf jeder Sales Professional über die
  REST-API alle Touren sehen; das Frontend filtert nach `assignee`
