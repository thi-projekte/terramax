# Speech Service

Quarkus-Microservice für TerraMax, der Sprachnotizen von Kundenbesuchen in Text
umwandelt. Teil der Story „Besuchstour“: Der Sales Professional nimmt nach einem
Besuch eine Sprachnotiz auf (max. ca. 2 Minuten), der Service liefert das
Transkript. Das strukturierte Protokoll erstellt anschließend ein LLM (eigener Task).

Die Spracherkennung übernimmt Whisper über [Speaches](https://github.com/speaches-ai/speaches)
mit einer OpenAI-kompatiblen Schnittstelle. Das Audio verlässt die eigene
Infrastruktur nicht.

```text
Frontend ──POST /transcriptions──► speech-service ──/v1/audio/transcriptions──► Whisper (Speaches)
 (5173)      Audio + Token            (8082)                                      (8000)
```

## Voraussetzungen

- JDK 25
- Laufende Docker-Umgebung, beispielsweise Docker Desktop
- Internetverbindung für die Downloads beim ersten Start
- Port 8000 frei (ein von Hand gestarteter Whisper-Container muss vorher gestoppt werden)

## Lokal starten

```bash
cd speech-service
./mvnw quarkus:dev
```

Quarkus Dev Services startet dabei automatisch:

| Container | Quelle | Port |
|---|---|---|
| Whisper (Speaches) | `compose-devservices.yml` | 8000 |
| Keycloak mit TerraMax-Realm | Realm-Datei aus `hello-service` | 8180 |

Beide Services nutzen Keycloak-Port 8180. Ob sich `hello-service` und
`speech-service` einen laufenden Keycloak teilen, ist noch nicht geprüft.

Beim ersten Start lädt der Service das Whisper-Modell herunter (einige Minuten).
Bis im Log `Whisper-Modell ... ist bereit` steht, antwortet der Endpunkt mit 503.
Das Modell bleibt im Docker-Volume `whisper-models`
(`quarkus.compose.devservices.remove-volumes=false`) und muss danach nicht erneut
geladen werden.

Der Service ist unter http://localhost:8082 erreichbar.

### Gegen den gemeinsamen Keycloak-Server

Ein Backend akzeptiert nur Tokens des Keycloaks, gegen den es konfiguriert ist.
Meldet sich das Frontend am Server an (Standard ohne `.env.local`), muss auch der
Service gegen den Server prüfen. Dann startet kein lokaler Keycloak:

```powershell
$env:QUARKUS_OIDC_AUTH_SERVER_URL = "https://terramax-keycloak.winfprojekt.de/realms/terramax"
./mvnw quarkus:dev
```

Der Benutzer braucht dort die Realm-Rolle `sales_professional`.

### Zusammen mit dem Frontend (lokaler Keycloak)

```bash
# Terminal 1
cd speech-service
./mvnw quarkus:dev

# Terminal 2
cd frontend
cp .env.local.example .env.local   # einmalig: Frontend gegen lokalen Keycloak
npm run dev
```

http://localhost:5173 öffnen, als `sales` / `sales` anmelden und in der Karte
„Sprachnotiz“ aufnehmen und transkribieren.

## Endpunkt

`POST /transcriptions` als `multipart/form-data`, nur mit Rolle `sales_professional`.

| Feld | Inhalt |
|---|---|
| `audio` | Aufnahme, z. B. `audio/webm` (Chrome), `audio/mp4` (Safari), `audio/wav` |
| `visitId` | ID des Kundenbesuchs |

Antwort:

```json
{
  "visitId": "visit-1",
  "transcript": "Besuch beim Hof Maier in Ingolstadt. ...",
  "language": "de",
  "durationSeconds": 20.4
}
```

| Status | Bedeutung |
|---|---|
| 200 | Transkript erstellt |
| 400 | Audiodatei oder `visitId` fehlt |
| 401 | Nicht angemeldet oder Token ungültig |
| 403 | Angemeldet, aber ohne Rolle `sales_professional` |
| 413 | Upload größer als 10 MB |
| 415 | Keine Audiodatei oder Audio nicht lesbar |
| 502 | Whisper nicht erreichbar oder fehlerhaft |
| 503 | Whisper-Modell noch nicht geladen |

Fehler enthalten eine Meldung für die Oberfläche: `{"message": "..."}`
(nicht bei 401, 403 und 413, die Quarkus selbst beantwortet).

## Konfiguration

Alle Werte stehen in `src/main/resources/application.properties` und lassen sich
per Umgebungsvariable überschreiben.

| Eigenschaft | Standard | Zweck |
|---|---|---|
| `quarkus.rest-client.whisper.url` | `http://localhost:8000` | Adresse von Whisper |
| `quarkus.rest-client.whisper.read-timeout` | `120000` | Timeout in ms |
| `whisper.model` | `Systran/faster-whisper-small` | Whisper-Modell |
| `whisper.language` | `de` | Sprache der Aufnahmen |
| `whisper.prompt` | John-Deere-Begriffe | Hinweis für Fachbegriffe |
| `whisper.preload-model` | `true` nur im Dev-Modus | Modell beim Start laden |
| `quarkus.http.limits.max-body-size` | `10M` | Maximale Uploadgröße |

Auf dem Server zum Beispiel:

```text
QUARKUS_REST_CLIENT_WHISPER_URL=http://whisper:8000
QUARKUS_OIDC_AUTH_SERVER_URL=https://<keycloak-host>/realms/terramax
```

### Austauschbarer Dienst

Die Resource kennt nur das Interface `SpeechToTextClient`. `WhisperClient`
spricht die OpenAI-kompatible Schnittstelle. Ein anderer kompatibler Dienst lässt
sich allein über URL und Modell anbinden; für einen anderen Anbieter genügt eine
neue Implementierung des Interfaces.

Vor einer Cloud-Anbindung muss geklärt sein, ob Audio die Infrastruktur
verlassen darf (offene Frage in der Story).

## Deployment (Portainer)

Bei jedem Push mit Änderungen in `speech-service/` baut
`.github/workflows/speech-service.yml` den Service, führt die Tests aus und lädt
das Image nach `ghcr.io/thi-projekte/terramax-speech-service` hoch
(Tag `sha-<commit>`, auf `main` zusätzlich `main`).

Der Service läuft im gemeinsamen Projekt-Stack aus `docker-compose.yml` im
Repo-Root. Dort stehen zwei Container für die Spracherkennung:

| Container | Netzwerke | Von außen erreichbar |
|---|---|---|
| `speech-service` (Port 8080) | `nginx-proxy-manager`, `speech-intern` | über den Nginx Proxy Manager |
| `whisper` | nur `speech-intern` | nein |

Keycloak läuft im selben Stack. Der Service prüft Tokens gegen dessen öffentliche
Adresse, weil sie als Aussteller in den Tokens steht, und wartet beim Start bis zu
3 Minuten, bis Keycloak erreichbar ist (`QUARKUS_OIDC_CONNECTION_DELAY`).

Optionale Variable in Portainer: `SPEECH_SERVICE_TAG` (Standard `main`, z. B. `sha-abc1234`).

Der Service lädt das Modell beim Start und versucht es bis zu 5 Minuten lang
erneut, falls Whisper noch nicht bereit ist.

Im Nginx Proxy Manager nötig, z. B. unter *Advanced* beim Proxy-Host (Standardwerte reichen nicht):

```nginx
client_max_body_size 10m;    # Standard 1m – längere Aufnahmen würden abgelehnt
proxy_read_timeout 120s;     # Standard 60s – eine 2-Minuten-Notiz braucht ca. 45 s
```

Das Frontend muss mit der öffentlichen Adresse gebaut werden
(`VITE_SPEECH_API_URL`). Laufen Frontend und Service auf verschiedenen Domains,
die CORS-Zeilen beim `speech-service` in `docker-compose.yml` aktivieren.

## Datenschutz

- Audio wird nur als temporäre Upload-Datei verarbeitet und nach der Anfrage gelöscht.
- Transkripte werden weder gespeichert noch geloggt.
- Gespeichert wird erst das vom Nutzer bestätigte Protokoll (Tour-Prozess, nicht dieser Service).

## Erkennungsqualität

Ein erster Test mit synthetischen deutschen Notizen (Windows-Sprachausgabe,
CPU, Modell `small`) ergab:

| Aufnahme | Dauer der Transkription |
|---|---|
| 20 Sekunden | ca. 9 Sekunden |
| 109 Sekunden | ca. 46 Sekunden |

Normaler deutscher Text wird fast fehlerfrei erkannt. Fachbegriffe wie
„8R 410“ oder „Fendt“ erst mit der Begriffsliste in `whisper.prompt`.
„Operations Center“, „AutoTrac“ und ein Datum („28. November“ als
„8. und 20. November“) blieben fehlerhaft. Deshalb muss das Protokoll vor dem
Speichern vom Nutzer geprüft werden. Mit echten Aufnahmen wiederholen; bleiben
die Fehler, das Modell `medium` testen (genauer, aber langsamer).

## Automatisierte Tests

```bash
./mvnw test
```

Die Tests simulieren Anmeldung (`@TestSecurity`) und Whisper
(`FakeSpeechToTextClient`). Docker, Keycloak und Whisper sind dafür nicht nötig.

Geprüft werden Erfolg, die Rollen (200 / 401 / 403), fehlende Felder (400),
falsche Dateitypen (415) und Fehler der Spracherkennung (415 / 502 / 503).
Die Uploadgrenze (413) wurde manuell geprüft, weil der Testclient den
Verbindungsabbruch von Quarkus nicht sauber auswerten kann.
