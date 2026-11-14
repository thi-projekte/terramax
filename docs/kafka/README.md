# Events: Kafka-Topics und Schemas

Ergebnis zu Issue #61. CIB Seven (`process-engine`) und `speech-service` publizieren Events auf Kafka, der `notifications`-Service konsumiert sie und benachrichtigt die SPA (siehe [Architektur](../architecture/terramax-architecture.html)).

## Verbindung

| | |
|---|---|
| Bootstrap-Server | `kafka:9092` (nur im Docker-Netzwerk `kafka-intern` des Stacks) |
| Sicherheit | `PLAINTEXT`, keine Authentifizierung (Netzwerk ist intern) |
| Format | JSON, UTF-8 |

Ein Service in der Root-`docker-compose.yml` bekommt dafür das Netzwerk `kafka-intern`:

```yaml
services:
  mein-service:
    networks:
      - kafka-intern
```

## Topics

Ein Topic je publizierendem Service. Die Art des Events steht im Feld `type`, nicht im Topic-Namen. Neue Event-Typen brauchen so kein neues Topic, und ein Consumer liest alle Events eines Service in der richtigen Reihenfolge.

| Topic | Producer | Consumer | Key | Partitionen | Aufbewahrung |
|---|---|---|---|---|---|
| `terramax.process.events` | `process-engine` (CIB Seven) | `notifications` | `processInstanceId` | 3 | 7 Tage |
| `terramax.speech.events` | `speech-service` | `notifications` | `visitId` | 3 | 7 Tage |

- **Key:** Alle Events einer Tour bzw. eines Besuchs landen in derselben Partition und bleiben dadurch in Reihenfolge.
- **Anlegen:** Topics legt nur der Container `kafka-init` an (Root-`docker-compose.yml`), automatisches Anlegen ist abgeschaltet. Ein neues Topic wird dort **und** hier eingetragen.
- **Consumer-Gruppen:** heißen wie der Service, z. B. `notifications`.

## Umschlag (CloudEvents)

Jede Nachricht ist ein [CloudEvent 1.0](https://github.com/cloudevents/spec/blob/v1.0.2/cloudevents/spec.md) im *structured mode*: Der Kafka-Value ist das komplette JSON unten, der Header `content-type` ist `application/cloudevents+json`. Für Java gibt es dafür fertige Bibliotheken (`io.cloudevents:cloudevents-kafka`), nötig sind sie aber nicht.

| Feld | Pflicht | Inhalt |
|---|---|---|
| `specversion` | ja | immer `"1.0"` |
| `id` | ja | UUID, eindeutig je Event. Consumer erkennen damit Duplikate. |
| `type` | ja | Event-Typ, z. B. `de.thi.terramax.process.tour.started` (Tabellen unten) |
| `source` | ja | `/process-engine` oder `/speech-service` |
| `time` | ja | Zeitpunkt des Ereignisses, ISO 8601 in UTC |
| `subject` | ja | ID des betroffenen Objekts, entspricht dem Kafka-Key |
| `userid` | ja | Keycloak-`sub` des Sales Professional, an den sich das Event richtet. Danach stellt `notifications` zu. |
| `datacontenttype` | ja | immer `"application/json"` |
| `data` | ja | Nutzdaten je Typ, siehe unten |

Beispiel:

```json
{
  "specversion": "1.0",
  "id": "2b0c6a3e-9c1f-4f5e-8d0a-1f7d5f0a9b21",
  "type": "de.thi.terramax.process.task.created",
  "source": "/process-engine",
  "time": "2026-11-12T08:15:30Z",
  "subject": "6f1c2e3a-8b44-11f0-9c2d-0242ac120004",
  "userid": "c3b1e1f2-7a0d-4c6e-9d1a-5e2f8b7a6c4d",
  "datacontenttype": "application/json",
  "data": {
    "processInstanceId": "6f1c2e3a-8b44-11f0-9c2d-0242ac120004",
    "taskId": "7a2d3f4b-8b44-11f0-9c2d-0242ac120004",
    "taskDefinitionKey": "Task_BesuchsplanPruefen",
    "name": "Besuchsplan prüfen",
    "formKey": "terramax:besuchsplan-pruefen"
  }
}
```

### Regeln

- **Schlanke Events:** Events enthalten IDs und Status, keine Gesprächsinhalte. Transkripte, Protokolle und Gründe fürs Überspringen sind personenbezogen und bleiben in CIB Seven bzw. beim Service; wer sie braucht, holt sie per REST.
- **Mindestens einmal:** Ein Event kann doppelt ankommen. Consumer müssen das über `id` abfangen.
- **Versionierung:** Neue optionale Felder in `data` sind erlaubt, Consumer ignorieren unbekannte Felder. Wer ein Feld entfernt, umbenennt oder seine Bedeutung ändert, führt einen neuen Typ mit Suffix ein (z. B. `...tour.completed.v2`) und publiziert beide, bis alle Consumer umgestellt sind.

## Prozess-Events (`terramax.process.events`)

Producer ist die `process-engine` (z. B. per Execution-/Task-Listener). Key und `subject` sind die `processInstanceId` der Besuchstour. Jedes `data` enthält zusätzlich `processInstanceId`.

| `type` (Präfix `de.thi.terramax.process.`) | Wann (BPMN-Element) | `data` |
|---|---|---|
| `tour.started` | Start `StartEvent_TourAngefordert` | `tourDatum` (Datum) |
| `tour.not-possible` | Ende `EndEvent_KeineTour` | `tourDatum`, `kundenAnzahl` (Zahl) |
| `tour.discarded` | Ende `EndEvent_TourVerworfen` | `tourDatum` |
| `tour.cancelled` | Message `TourAbbrechen` empfangen | `tourDatum` |
| `tour.completed` | Ende `EndEvent_TourAbgeschlossen` | `tourDatum`, `dokumentiert`, `uebersprungen`, `offen` (je Anzahl Besuche) |
| `task.created` | User Task wird erzeugt | `taskId`, `taskDefinitionKey`, `name`, `formKey` |
| `visit.skipped` | Ende `EndEvent_BesuchUebersprungen` | `kundeId`, `position` |
| `visit.documented` | Ende `EndEvent_BesuchDokumentiert` | `kundeId`, `position`, `sprachnotizId` (oder `null`) |

Beispiel `data` für `tour.completed`:

```json
{
  "processInstanceId": "6f1c2e3a-8b44-11f0-9c2d-0242ac120004",
  "tourDatum": "2026-11-12",
  "dokumentiert": 4,
  "uebersprungen": 1,
  "offen": 0
}
```

## Speech-Events (`terramax.speech.events`)

Producer ist der `speech-service`. Key und `subject` sind die `visitId` aus der Anfrage. Damit ordnet `notifications` das Ergebnis dem laufenden Besuch zu, auch wenn die SPA zwischendurch geschlossen wurde.

| `type` (Präfix `de.thi.terramax.speech.`) | Wann | `data` |
|---|---|---|
| `transcription.completed` | `POST /transcriptions` erfolgreich | `visitId`, `language`, `durationSeconds` |
| `transcription.failed` | `POST /transcriptions` fehlgeschlagen | `visitId`, `status` |
| `protocol.drafted` | `POST /protocols` erfolgreich | `visitId` |
| `protocol.failed` | `POST /protocols` fehlgeschlagen | `visitId`, `status` |

`status` ist der HTTP-Status, mit dem der Service geantwortet hat (z. B. `503`, solange das Whisper-Modell lädt, oder `502` ohne OpenAI-Key). Die Fehlermeldung selbst steht nicht im Event.

Beispiel `data` für `transcription.completed`:

```json
{
  "visitId": "tour-2026-11-12-kunde-4711",
  "language": "de",
  "durationSeconds": 42.7
}
```

## Betrieb

Kafka läuft im gemeinsamen Stack (Root-`docker-compose.yml`) und besteht aus drei Containern:

| Container | Was er ist | Läuft | Erreichbar |
|---|---|---|---|
| `kafka` | Der eigentliche Kafka-Server (Broker). Nimmt Nachrichten von den Producern an, speichert sie in den Topics und liefert sie an die Consumer aus. Läuft im KRaft-Modus, verwaltet sich also selbst und braucht keinen ZooKeeper. | dauerhaft | nur im Netzwerk `kafka-intern` unter `kafka:9092`, kein Port nach außen |
| `kafka-init` | Hilfscontainer, der beim Deploy die beiden Topics `terramax.process.events` und `terramax.speech.events` anlegt (Bedeutung siehe [Topics](#topics)). Er wartet, bis `kafka` bereit ist, legt fehlende Topics an (vorhandene bleiben unverändert) und beendet sich danach. Der Status *Exited (0)* ist deshalb richtig und kein Fehler. | nur kurz beim Deploy | – |
| `kafka-ui` | Weboberfläche für Entwickler ([Kafbat UI](https://github.com/kafbat/kafka-ui)). Zeigt Topics, Nachrichten und Consumer-Gruppen an und kann zum Testen Nachrichten senden und Topics anlegen. Für den Betrieb der Services nicht nötig. | dauerhaft | über den Nginx Proxy Manager (`kafka-ui:8080`), nur mit Login |

Die Daten von `kafka` liegen im Volume `terramax-kafka-data` und überleben einen Redeploy.

- **UI-Login:** mit `KAFKA_UI_USER` (Standard `terramax`) und `KAFKA_UI_PASSWORD` aus Portainer. Ohne `KAFKA_UI_PASSWORD` startet der Stack nicht.
- **Testen:** In der UI ein Wegwerf-Topic (z. B. `terramax.test`) anlegen, per *Produce Message* senden, unter *Messages* lesen und das Topic danach löschen. Nicht in die echten Topics schreiben, sonst liest `notifications` später ungültige Events.
- **Neues Topic:** in die Schleife von `kafka-init` in der `docker-compose.yml` eintragen, oben unter *Topics* dokumentieren und den Stack neu deployen.

## Offen

- Die Producer in `process-engine` und `speech-service` und der `notifications`-Service selbst sind noch nicht umgesetzt; dieses Dokument ist der Vertrag dafür.
- Fehlgeschlagene Verarbeitung im Consumer (Dead-Letter-Topic) wird festgelegt, sobald `notifications` steht.
