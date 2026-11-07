# Entwicklungs-Workflow auf GitHub

Gemeinsame Regeln für die Arbeit im TerraMax-Repository (Ergebnis aus Spike #28).
Ziel: `main` ist immer lauffähig, jede Änderung ist nachvollziehbar und einem Issue zugeordnet.

## Kurzfassung

1. Issue auf dem Board übernehmen und sich als Assignee eintragen
2. Branch von `main` anlegen: `<typ>/<issue-nr>-<kurzbeschreibung>`
3. Committen, pushen, Pull Request öffnen (Vorlage ausfüllen, `Closes #<nr>`)
4. Checks müssen grün sein, mindestens eine Person reviewt
5. „Squash and merge“, danach Branch löschen

## 1. Branches

Wir arbeiten nach **GitHub Flow**: Es gibt nur `main` und kurzlebige Branches.
Jeder Branch startet vom aktuellen `main` und wird nach dem Merge gelöscht.

**Namensschema:** `<typ>/<issue-nr>-<kurzbeschreibung>` (kleingeschrieben, Bindestriche)

| Typ | Wofür | Beispiel |
|---|---|---|
| `feature` | neue Funktion, User Story | `feature/19-fahrzeit-isochronen` |
| `fix` | Fehlerbehebung | `fix/23-login-redirect` |
| `docs` | Dokumentation, Research, Folien | `docs/28-dev-workflow` |
| `spike` | Ausprobieren, Prototyp | `spike/26-osint-quellen` |
| `chore` | Wartung, Konfiguration, CI | `chore/ci-frontend-trigger` |

```bash
git checkout main
git pull
git checkout -b feature/19-fahrzeit-isochronen
```

Branches von Dependabot (`dependabot/...`) werden automatisch angelegt und folgen nicht diesem Schema.

## 2. Commits

- Kurze, aussagekräftige Nachricht im Imperativ, z. B. `Isochronen für Standorte anzeigen`
- Gerne mit Präfix wie in den bestehenden PRs: `feat:`, `fix:`, `docs:`, `chore:`
- Java-Code vor dem Commit formatieren: `./mvnw formatter:format` (sonst schlägt der Build fehl, siehe README)
- Keine Passwörter, Tokens oder `.env`-Dateien committen

## 3. Pull Requests

- Für **jede** Änderung an `main` gibt es einen Pull Request, auch für kleine Änderungen
- Die PR-Vorlage (`.github/pull_request_template.md`) erscheint automatisch und wird ausgefüllt
- Im PR steht `Closes #<issue-nr>`. Dann wird das Issue beim Merge automatisch geschlossen
- Noch nicht fertig? Als **Draft** öffnen und später auf „Ready for review“ stellen
- PRs klein halten: lieber mehrere kleine PRs als einen großen

## 4. Review und Checks

- Mindestens **eine andere Person** reviewt den PR, bevor er gemergt wird
- Wer reviewt: jemand, der am gleichen Bereich arbeitet (Frontend/Karte, Java/Backend, Login)
- Review innerhalb von **2 Werktagen**. Wer gebeten wird und keine Zeit hat, sagt kurz Bescheid
- Alle automatischen Checks müssen grün sein:
  - **TerraMax Frontend CI**: `npm ci` und `npm run build` (inkl. TypeScript-Prüfung) bei Änderungen in `frontend/`
  - **hello-service**: `./mvnw -B verify` (Build, Tests, Formatierung) bei Änderungen in `hello-service/`
  - **SonarQube Cloud**: Quality Gate muss bestanden sein
- Ist ein Check rot: nicht mergen, Fehler beheben oder im PR beschreiben

## 5. Mergen

- Wir nutzen **„Squash and merge“**: Alle Commits eines PRs werden zu einem Commit auf `main` zusammengefasst
- Danach im PR auf **„Delete branch“** klicken
- Gemergt wird von der Person, die den PR geöffnet hat, sobald Review und Checks passen

## 6. Dependabot

Dependabot prüft **wöchentlich**, ob es neue Versionen gibt, und öffnet dafür automatisch PRs
(Konfiguration in `.github/dependabot.yml`: GitHub Actions, Docker, npm im `frontend/`, Maven im `hello-service/`).

**Zuständigkeit:**

- Der **Scrum Master** verteilt neue Dependabot-PRs einmal pro Woche als Assignee
- Verteilt wird nach Bereich: Frontend-Updates an Leute, die im Frontend arbeiten, Java-Updates an Leute aus dem Backend, Login-Updates (`keycloak-js`) an die Person, die am Login arbeitet
- Wer gerade allein an einer großen Story sitzt, bekommt weniger
- Ziel: Dependabot-PRs werden **innerhalb einer Woche** erledigt

**Ablauf pro PR (ca. 10–20 Minuten):**

1. Checks ansehen. ✅ grün → weiter, ❌ rot → Fehler im PR kommentieren, nicht mergen
2. Bei Login-Updates, großen Versionssprüngen (z. B. `24 → 26`) und Maven selbst kurz lokal testen:
   - Frontend: `cd frontend && npm ci && npm run lint && npm run dev`
   - Java: `cd hello-service && ./mvnw verify`
3. „Squash and merge“, Branch löschen
4. PRs nacheinander mergen. Bei Konflikt kurz warten oder im PR `@dependabot rebase` kommentieren

Für Dependabot-PRs reicht der grüne Check, ein zusätzliches Review ist nicht nötig.

## 7. GitHub Actions

| Workflow | Datei | Läuft bei | Macht |
|---|---|---|---|
| TerraMax Frontend CI | `.github/workflows/frontend.yml` | PR und Push auf `main` mit Änderungen in `frontend/` | Build, danach Docker-Image in die GitHub Container Registry |
| hello-service | `.github/workflows/hello-service.yml` | PR und Push auf `main` mit Änderungen in `hello-service/` | Build, Tests, Formatierungsprüfung, danach Docker-Image in die GitHub Container Registry |

**Neuer Service:** Workflow von `hello-service.yml` kopieren und Ordnernamen sowie Image-Namen anpassen.

## 8. Secrets und Variablen

- Zugangsdaten stehen **nie** im Code und nie in Issues oder PRs (das Repository ist öffentlich)
- Lokal: in einer `.env`-Datei im jeweiligen Ordner (ist über `.gitignore` ausgeschlossen)
- Für die Pipeline: im Repository unter *Settings → Secrets and variables → Actions*
  - **Secrets** für echte Geheimnisse (Passwörter, private Schlüssel)
  - **Variables** für öffentliche Werte, z. B. `VITE_MAPBOX_TOKEN` (Mapbox-Public-Token, Team-Konto `terramax-thi`, siehe #18)
- Eintragen kann das nur ein Repository-Admin

## 9. Schutz von `main`

Aktuell aktiv (Ruleset „protect main“):

- `main` kann nicht gelöscht werden
- Kein Force-Push auf `main`

Empfohlen zusätzlich (Einrichtung durch Admin):

- Pull Request vor dem Merge verpflichtend, mindestens 1 Approval
- Status-Checks müssen grün sein
