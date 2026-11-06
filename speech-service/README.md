# Speech Service

Quarkus-Service für die Story „Besuchstour“: Der Sales Professional nimmt nach
einem Kundenbesuch eine Sprachnotiz auf (max. ca. 2 Minuten). Der Service
transkribiert sie und erstellt daraus einen Protokollentwurf mit festen Feldern,
den der Nutzer im Frontend bearbeitet und bestätigt.

- Spracherkennung: Whisper über [Speaches](https://github.com/speaches-ai/speaches),
  läuft im eigenen Stack. Das Audio verlässt die eigene Infrastruktur nicht.
- Protokoll: OpenAI über [LangChain4j](https://docs.quarkiverse.io/quarkus-langchain4j/dev/)
  (`quarkus-langchain4j-openai`). Dafür wird das Transkript an OpenAI gesendet.

```text
Frontend ──POST /transcriptions──► speech-service ──► Whisper (im Stack)
         ──POST /protocols───────►                ──► OpenAI (LangChain4j)
```

## Lokal starten

Voraussetzungen: JDK 25, Docker, Port 8000 frei.

```bash
cd speech-service
./mvnw quarkus:dev
```

Quarkus Dev Services starten dabei Whisper (`compose-devservices.yml`, Port 8000)
und Keycloak mit dem TerraMax-Realm (Port 8180). Beim ersten Start lädt der
Service das Whisper-Modell herunter; bis im Log `Whisper-Modell ... ist bereit`
steht, antwortet `/transcriptions` mit 503. Das Modell bleibt im Docker-Volume
`whisper-models`.

Für Protokolle wird ein OpenAI-Key gebraucht. Dafür `speech-service/.env`
anlegen (ist in `.gitignore`):

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

**Gegen den Keycloak-Server testen:** Frontend und Backend müssen denselben
Keycloak verwenden, sonst antwortet der Service mit 401. Zeigt `.env.local` auf
den Server, den Service so starten (dann startet kein lokaler Keycloak):

```powershell
$env:QUARKUS_OIDC_AUTH_SERVER_URL = "https://terramax-keycloak.winfprojekt.de/realms/terramax"
./mvnw quarkus:dev
```

## Endpunkte

Beide nur mit Realm-Rolle `sales_professional` (sonst 401 bzw. 403). Fehler
liefern eine Meldung für die Oberfläche: `{"message": "..."}`.

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
| 502 | Whisper nicht erreichbar |
| 503 | Whisper-Modell noch nicht geladen |

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

Der Prompt steht in `VisitProtocolAssistant`. LangChain4j erzeugt aus dem Record
`VisitProtocol` ein JSON-Schema, an das sich die Antwort halten muss
(OpenAI Structured Outputs).

## Konfiguration

In `src/main/resources/application.properties`, per Umgebungsvariable
überschreibbar:

| Eigenschaft | Standard |
|---|---|
| `quarkus.rest-client.whisper.url` | `http://localhost:8000` |
| `whisper.model` | `Systran/faster-whisper-small` |
| `whisper.prompt` | Fachbegriffe als Hilfe für die Erkennung |
| `whisper.preload-model` | `true` im Dev-Modus |
| `OPENAI_API_KEY` | – (ohne Key antwortet `/protocols` mit 502) |
| `quarkus.langchain4j.openai.chat-model.model-name` | `gpt-4o-mini` |

## Deployment

`.github/workflows/speech-service.yml` baut und testet den Service bei jedem
Push und lädt das Image nach `ghcr.io/thi-projekte/terramax-speech-service`.
Er läuft im Projekt-Stack aus `docker-compose.yml` im Repo-Root, zusammen mit
Whisper. Whisper hängt nur im internen Netz `speech-intern`.

In Portainer zu setzen: `OPENAI_API_KEY`.

Im Nginx Proxy Manager beim Proxy-Host unter *Advanced* (die Standardwerte
reichen für 2-Minuten-Notizen nicht):

```nginx
client_max_body_size 10m;
proxy_read_timeout 120s;
```

Das Frontend wird mit der öffentlichen Adresse gebaut (`VITE_SPEECH_API_URL`).
Laufen Frontend und Service auf verschiedenen Domains, die CORS-Zeilen in
`docker-compose.yml` aktivieren.

## Datenschutz

- Audio wird nur als temporäre Datei verarbeitet und nach der Anfrage gelöscht.
- Das Transkript wird zur Protokollerstellung an OpenAI gesendet. Ob das für
  Kundengespräche zulässig ist, ist eine offene Frage der Story.
- Transkripte und Protokolle werden im Service weder gespeichert noch geloggt.

## Tests

```bash
./mvnw verify
```

Anmeldung (`@TestSecurity`), Whisper (`FakeSpeechToTextClient`) und LLM
(`FakeProtocolGenerator`) werden simuliert; Docker, Keycloak und ein OpenAI-Key
sind nicht nötig. Die Uploadgrenze (413) ist nur manuell geprüft, weil der
Testclient den Verbindungsabbruch nicht auswerten kann.
