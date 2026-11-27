# Speech Service

Quarkus-Service für die Story „Besuchstour“: Der Sales Professional nimmt nach
einem Kundenbesuch eine Sprachnotiz auf (max. ca. 2 Minuten). Der Service
transkribiert sie und erstellt daraus einen Protokollentwurf mit festen Feldern,
den der Nutzer im Frontend bearbeitet und bestätigt.

- Spracherkennung: OpenAI-API (`POST /v1/audio/transcriptions`) mit dem Modell
  `gpt-transcribe`. Dafür wird das Audio an OpenAI gesendet.
- Protokoll: OpenAI über [LangChain4j](https://docs.quarkiverse.io/quarkus-langchain4j/dev/)
  (`quarkus-langchain4j-openai`). Dafür wird das Transkript an OpenAI gesendet.
- Beides nutzt denselben API-Key (`OPENAI_API_KEY`).

- Übergabe: Das bestätigte Protokoll gibt der Service an den
  [terramax-service](../terramax-service/README.md) weiter, mit dem Token der SPA.
- Events: jeder Schritt landet auf Kafka (`terramax.speech.events`).
- Schnellerfassung (#134): Foto einer Visitenkarte → Entwurf der Kontaktdaten
  (`POST /business-cards`, OpenAI über LangChain4j) → bestätigter Lead an den
  terramax-service (`POST /leads`).

```text
SPA ──POST /transcriptions (oder WS)──► speech-service ──► OpenAI gpt-transcribe
    ──POST /protocols─────────────────►                ──► OpenAI gpt-4o-mini (LangChain4j)
      (Nutzer prüft und bearbeitet den Entwurf)
    ──POST /tours/{tourId}/protocols──►                ──► terramax-service POST /tours/{tourId}/protocols
                                                             (gleicher Authorization-Header)
                                                       ──► Kafka terramax.speech.events
```

### Wie die Anmeldung bei TerraMax funktioniert

Der speech-service hat kein eigenes Konto in Keycloak. Er reicht den
`Authorization`-Header der SPA unverändert an den terramax-service weiter
(`TerraMaxApi`). Der terramax-service prüft das Token selbst gegen denselben
Realm, prüft `dealer_id` und die Sichtbarkeit der Tour und speichert das
Protokoll mit `createdBy` = `sub` des Nutzers. Deshalb müssen SPA,
speech-service und terramax-service denselben Keycloak verwenden.

## Lokal starten

Voraussetzungen: JDK 25, Docker (für Keycloak und Kafka), ein OpenAI-Key.

```bash
cd speech-service
./mvnw quarkus:dev
```

Quarkus Dev Services starten dabei Keycloak mit dem TerraMax-Realm (Port 8180)
und einen Kafka-Broker.

Für Spracherkennung und Protokolle wird ein OpenAI-Key gebraucht. Dafür
`speech-service/.env` anlegen (ist in `.gitignore`):

```text
OPENAI_API_KEY=sk-...
```

Frontend dazu starten:

```bash
cd frontend
cp .env.local.example .env.local   # einmalig: Frontend gegen lokalen Keycloak
npm run dev
```

Unter http://localhost:5173 als `sales` / `sales` anmelden und in der Karte
„Besuchsprotokoll“ eine Notiz aufnehmen.

**Zusammen mit dem terramax-service:** den terramax-service zuerst starten
(`cd terramax-service && ./mvnw quarkus:dev`, Port 8088). Sein Keycloak-Container
(Port 8180) wird vom speech-service mitbenutzt; dessen Realm enthält den Claim
`dealer_id`, den der terramax-service verlangt. Der speech-service erreicht ihn
unter `quarkus.rest-client.terramax.url` (Standard `http://localhost:8088`).
Kafka startet Quarkus im Dev-Modus selbst (Dev Services); die Events zeigt die
Dev UI unter http://localhost:8082/q/dev-ui.

**Gegen den Keycloak-Server testen:** Frontend und Backend müssen denselben
Keycloak verwenden, sonst antwortet der Service mit 401. Zeigt `.env.local` auf
den Server, den Service so starten (dann startet kein lokaler Keycloak):

```powershell
$env:QUARKUS_OIDC_AUTH_SERVER_URL = "https://terramax-keycloak.winfprojekt.de/realms/terramax"
./mvnw quarkus:dev
```

## Endpunkte

Alle nur mit Realm-Rolle `sales_professional` (sonst 401 bzw. 403). Fehler
liefern eine Meldung für die Oberfläche: `{"message": "..."}`.

Die `visitId` bildet die SPA als `<tourId>:<customerId>` (IDs aus dem
terramax-service). Damit gehören alle Events eines Besuchs zusammen.

### `POST /transcriptions`

`multipart/form-data` mit `audio` (z. B. `audio/webm`, `audio/mp4`, `audio/wav`,
max. 10 MB) und `visitId`.

```json
{ "visitId": "visit-1", "transcript": "Besuch beim Hof Maier ...", "language": "de", "durationSeconds": 20.4 }
```

| Status | Bedeutung |
|---|---|
| 400 | Audiodatei oder `visitId` fehlt |
| 413 | Upload größer als 10 MB |
| 415 | Keine Audiodatei oder Audio nicht lesbar |
| 502 | OpenAI nicht erreichbar oder API-Key fehlt/ungültig |
| 503 | Anfragelimit bei OpenAI erreicht |

### `POST /protocols`

JSON `{"visitId": "visit-1", "transcript": "..."}`. Der Entwurf wird nicht
gespeichert; dem Kunden zugeordnet wird erst das bestätigte Protokoll
(Tour-Prozess).

```json
{
  "visitId": "visit-1",
  "protocol": {
    "contactPerson": "Herr Maier (Betriebsleiter)",
    "topics": ["8R 410", "Finanzierung"],
    "outcome": "Interesse an einer Vorführung.",
    "nextSteps": ["Angebot schicken"],
    "followUpDate": "2026-11-28"
  }
}
```

Fehlende Angaben sind `null` bzw. eine leere Liste. Relative Angaben wie
„nächsten Dienstag“ werden anhand des heutigen Datums (Europe/Berlin) aufgelöst.

| Status | Bedeutung |
|---|---|
| 400 | `visitId` fehlt, Transkript leer oder länger als 5 000 Zeichen |
| 502 | OpenAI nicht erreichbar, Key fehlt oder Antwort unbrauchbar |
| 503 | Anfragelimit bei OpenAI erreicht |

### `POST /tours/{tourId}/protocols`

Übergibt das vom Nutzer bestätigte Protokoll an TerraMax. Erst hier wird
etwas gespeichert. Felder wie `VisitProtocol`, dazu `customerId` (Pflicht) und
optional `transcript` (max. 20 000 Zeichen):

```json
{
  "customerId": 5,
  "contactPerson": "Herr Maier (Betriebsleiter)",
  "topics": ["8R 410", "Finanzierung"],
  "outcome": "Interesse an einer Vorführung.",
  "nextSteps": ["Angebot schicken"],
  "followUpDate": "2026-11-28",
  "transcript": "Besuch beim Hof Maier ..."
}
```

Antwort `201` mit dem gespeicherten Protokoll, so wie es der terramax-service
liefert (`id`, `tourId`, `customerId`, `createdBy`, `createdAt`, …).

| Status | Bedeutung |
|---|---|
| 400 | `customerId` fehlt, Transkript zu lang, oder TerraMax lehnt ab (Meldung von TerraMax, z. B. „Kunde nicht auf der Tour: 5“) |
| 401 | Token von TerraMax abgelehnt (abgelaufen) |
| 403 | TerraMax verweigert, z. B. kein `dealer_id` im Token |
| 404 | Tour unbekannt oder für den Nutzer nicht sichtbar |
| 502 | TerraMax nicht erreichbar oder Serverfehler |

### `POST /business-cards`

Schnellerfassung (#134): liest eine fotografierte Visitenkarte und gibt einen
**Entwurf** zurück. Gespeichert wird nichts. `multipart/form-data` mit `image`
(`image/jpeg`, `image/png` oder `image/webp`, max. 10 MB; die SPA verkleinert
Fotos vorher auf 1600 px) und `captureId` (von der SPA gebildet als
`lead:<uuid>`, gilt für die ganze Erfassung).

```json
{
  "captureId": "lead:3f2c…",
  "card": {
    "contactName": "Josef Maier", "company": "Hof Maier GbR", "role": "Betriebsleiter",
    "phone": "+43 664 1234567", "email": "maier@example.at",
    "street": "Hauptstraße 1", "postalCode": "4600", "city": "Wels"
  }
}
```

Nicht lesbare Felder sind `null`. Der Prompt steht in `BusinessCardAssistant`
(gleiches Modell wie das Protokoll, `gpt-4o-mini` kann Bilder lesen).

| Status | Bedeutung |
|---|---|
| 400 | Foto oder `captureId` fehlt, Foto leer |
| 413 | Upload größer als 10 MB |
| 415 | Kein JPEG, PNG oder WebP |
| 502 | OpenAI nicht erreichbar, Key fehlt oder Karte nicht lesbar |
| 503 | Anfragelimit bei OpenAI erreicht |

Ein Kommentar kann diktiert werden: dafür `POST /transcriptions` mit der
`captureId` als `visitId`.

### `POST /leads`

Übergibt den vom Nutzer bestätigten Lead an TerraMax (`POST /leads` im
terramax-service, gleicher `Authorization`-Header). Erst hier wird gespeichert.
Felder wie `CreateLeadRequest` im terramax-service, dazu `captureId` (Pflicht):

```json
{
  "captureId": "lead:3f2c…",
  "source": "MESSE",
  "eventName": "Agraria Wels 2026",
  "contact": { "name": "Josef Maier", "company": "Hof Maier GbR", "city": "Wels" },
  "latitude": 48.16, "longitude": 14.03,
  "comment": "Interesse an 8R 410, Rückruf nach der Ernte.",
  "transcript": null,
  "existingCustomerId": null
}
```

Antwort `201` unverändert so, wie der terramax-service sie liefert (`LeadDto`).

| Status | Bedeutung |
|---|---|
| 400 | `captureId` oder `contact` fehlt, Kommentar (> 2 000) oder Transkript (> 20 000) zu lang, oder TerraMax lehnt ab (Meldung von TerraMax) |
| 401 | Token von TerraMax abgelehnt (abgelaufen) |
| 403 | TerraMax verweigert, z. B. kein `dealer_id` im Token |
| 404 | Händler oder `existingCustomerId` in TerraMax unbekannt |
| 502 | TerraMax nicht erreichbar oder Serverfehler |

### WebSocket `/transcriptions/stream/{visitId}`

Wie `POST /transcriptions`, aber die Aufnahme kann schon während des Sprechens
gesendet werden:

1. SPA schickt die Aufnahme in Stücken als Binary Messages (max. 10 MB),
2. dann `{"type": "stop", "mediaType": "audio/webm"}`,
3. Service meldet `{"type": "progress", "step": "TRANSCRIBING"}`,
4. dann `{"type": "result", "result": {…wie POST /transcriptions…}}` oder
   `{"type": "error", "status": 503, "message": "…"}` und schließt die Verbindung.

Browser können beim Handshake keinen `Authorization`-Header setzen; das Token
kommt als Subprotocol:

```js
new WebSocket(`${wsUrl}/transcriptions/stream/${visitId}`, ['bearer-token-carrier',
  'quarkus-http-upgrade#Authorization#' + encodeURIComponent('Bearer ' + keycloak.token)])
```

Der Prompt steht in `VisitProtocolAssistant`. LangChain4j erzeugt aus dem Record
`VisitProtocol` ein JSON-Schema, an das sich die Antwort halten muss
(OpenAI Structured Outputs).

## Konfiguration

In `src/main/resources/application.properties`, per Umgebungsvariable
überschreibbar:

| Eigenschaft | Standard |
|---|---|
| `OPENAI_API_KEY` | – (ohne Key antworten `/transcriptions` und `/protocols` mit 502) |
| `transcription.model` | `gpt-transcribe` |
| `transcription.languages` | `de` |
| `transcription.prompt` | Kontext für die Erkennung (Besuchsprotokoll) |
| `transcription.keywords` | Fachbegriffe, kommagetrennt (John Deere, 8R 410, …) |
| `quarkus.rest-client.openai-transcription.url` | `https://api.openai.com/v1` |
| `quarkus.langchain4j.openai.chat-model.model-name` | `gpt-4o-mini` |
| `quarkus.rest-client.terramax.url` | `http://localhost:8088` (Server: `http://terramax-service:8080`) |
| `KAFKA_BOOTSTRAP_SERVERS` | lokal Dev Services, auf dem Server `kafka:9092` |

## Events

Topic `terramax.speech.events`, CloudEvents mit Key `visitId`. Typen und Felder
stehen in [docs/kafka/README.md](../docs/kafka/README.md):
`transcription.completed`/`failed`, `protocol.drafted`/`failed`,
`protocol.submitted` und für die Schnellerfassung `business-card.read`/`failed`
und `lead.captured` (Key `captureId`). Transkript, Protokoll und Kontaktdaten
stehen nie im Event. Fällt Kafka
aus, wird das nur geloggt; die Anfrage läuft weiter.

## Deployment

`.github/workflows/speech-service.yml` baut und testet den Service bei
Änderungen im Service-Ordner und lädt das Image nach
`ghcr.io/thi-projekte/terramax-speech-service` (Tags `main` und `sha-<commit>`).
Er läuft im Projekt-Stack aus `docker-compose.yml` im Repo-Root.

In Portainer zu setzen: `OPENAI_API_KEY`.

Im Nginx Proxy Manager beim Proxy-Host **„Websockets Support“** einschalten und
unter *Advanced* eintragen (die Standardwerte reichen für 2-Minuten-Notizen nicht):

```nginx
client_max_body_size 10m;
proxy_read_timeout 120s;
```

Das Frontend wird mit der öffentlichen Adresse gebaut (`VITE_SPEECH_API_URL`,
Standard in `.github/workflows/frontend.yml`: `https://terramax-speech.winfprojekt.de`).
Weil Frontend und Service auf verschiedenen Domains laufen, ist CORS in
`docker-compose.yml` für `https://terramax.winfprojekt.de` freigegeben.

## Datenschutz

- Audio wird nur als temporäre Datei verarbeitet und nach der Anfrage gelöscht.
- Audio (Spracherkennung) und Transkript (Protokollerstellung) werden an OpenAI
  gesendet. Ob das für Kundengespräche zulässig ist, ist eine offene Frage der
  Story.
- Transkripte und Protokolle werden im Service weder gespeichert noch geloggt.
- Fotos von Visitenkarten werden nur im Speicher verarbeitet, an OpenAI gesendet
  und nicht gespeichert oder geloggt; ebenso die gelesenen Kontaktdaten.
  Gespeichert wird erst der bestätigte Lead im terramax-service.

## Tests

```bash
./mvnw verify
```

Anmeldung (`@TestSecurity`), Spracherkennung (`FakeSpeechToTextClient`; der echte
OpenAI-Client läuft in `OpenAiTranscriptionClientTest` gegen `FakeOpenAi`), LLM
(`FakeProtocolGenerator`, `FakeBusinessCardReader`), terramax-service
(`FakeTerraMax`, `FakeTerraMaxLeads`, Resources im
selben Prozess, damit der echte REST-Client mitläuft) und Kafka
(In-Memory-Connector, `EventSink`) werden simuliert; Docker, Keycloak und ein
OpenAI-Key sind nicht nötig. Die Uploadgrenze (413) ist nur manuell geprüft, weil der
Testclient den Verbindungsabbruch nicht auswerten kann.
