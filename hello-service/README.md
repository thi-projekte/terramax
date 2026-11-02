# Hello Service

Minimaler Quarkus-Microservice für TerraMax zur Übung von
Projektstruktur, Build und lokaler Entwicklung.

Der Service prüft Bearer-Tokens aus Keycloak und begrüßt den
angemeldeten Benutzer. Fachlogik und Datenbank kommen später hinzu.

## Voraussetzungen

- JDK 25
- Laufende Docker-Umgebung, beispielsweise Docker Desktop
- Internetverbindung für die Downloads beim ersten Start

Maven wird über den mitgelieferten Maven Wrapper ausgeführt.
Eine separate Maven-Installation ist nicht erforderlich.

## Lokal starten

Im Repository in den Service-Ordner wechseln:

```bash
cd hello-service
```

Entwicklungsmodus starten:

```bash
./mvnw quarkus:dev
```

Quarkus Dev Services startet automatisch einen lokalen Keycloak-Container
und importiert den TerraMax-Realm.

Der Service ist unter http://localhost:8080 erreichbar.

Das Terminal bleibt während der Entwicklung geöffnet.
Beenden mit Ctrl + C.

## Konfiguration

Die Datei `src/main/resources/application.properties` enthält:

```properties
quarkus.keycloak.devservices.realm-path=terramax-realm.json
quarkus.oidc.client-id=terramax-hello-world
```

Die Realm-Datei liegt unter:

```text
src/main/resources/terramax-realm.json
```

Eine feste Keycloak-URL ist nicht hinterlegt.
In Entwicklung und Test stellt Dev Services die Verbindung bereit.

## Endpunkte

| Endpunkt | Authentifizierung | Ergebnis |
|---|---|---|
| GET /hello | Gültiges Bearer-Token erforderlich | 200 mit persönlicher JSON-Begrüßung |
| GET /q/health | Ohne Token erreichbar | 200 mit Status UP |

Beispiel für den Benutzer testuser:

```json
{"message":"Hello testuser"}
```

Ohne Token oder mit ungültigem beziehungsweise abgelaufenem Token
antwortet `/hello` mit Status 401.

## Manuell testen

### Ohne Token

```bash
curl -i http://localhost:8080/hello
```

Erwartet:

```text
HTTP/1.1 401 Unauthorized
```

Eine leere Antwort ist dabei normal.

### Health-Check

```bash
curl -i http://localhost:8080/q/health
```

Erwartet: Status 200 und JSON mit `"status":"UP"`.

### Mit lokalem Testbenutzer

1. http://localhost:8080/q/dev-ui/extensions öffnen.
2. Bei OpenID Connect den Keycloak-Anbieter öffnen.
3. Realm `terramax` auswählen.
4. Als Client `terramax-dev-ui` eintragen.
5. Über „Log into Single Page Application“ anmelden.
6. Benutzername `testuser` und die im Team mitgeteilten lokalen
   Zugangsdaten verwenden.
7. Als Dienstpfad `/hello` eintragen.
8. Den Aufruf über „With Access Token“ ausführen.

Erwartet: Status 200.

Der Antwortinhalt des Endpunkts lautet:

```json
{"message":"Hello testuser"}
```

Das Passwort des lokalen Testbenutzers kann vom Passwort auf der
gemeinsamen Keycloak-Serverinstanz abweichen.

Zugriffstokens laufen nach kurzer Zeit ab. Bei einem abgelaufenen
Token erneut anmelden.

### Antwort mit curl prüfen

Den aktuellen Zugriffstoken aus der Dev UI als Bearer-Token mitsenden:

```bash
curl -i \
  -H "Authorization: Bearer <ACCESS_TOKEN>" \
  http://localhost:8080/hello
```

`<ACCESS_TOKEN>` durch den vollständigen aktuellen Zugriffstoken ersetzen.
Den Zugriffstoken verwenden, nicht den ID-Token.

Erwartet: Status 200 und die persönliche JSON-Begrüßung.

Tokens und Passwörter nicht in Dateien oder Commits übernehmen.

## Clients im lokalen Realm

| Client | Aufgabe |
|---|---|
| terramax-hello-world | Client-Konfiguration des Microservices |
| terramax-dev-ui | Anmeldung über die lokale Quarkus Dev UI |
| publicClient | Anmeldung des TerraMax-Frontends |

`terramax-dev-ui` ist ein öffentlicher Client für die lokale Entwicklung.
Er erlaubt die Weiterleitung zur Dev UI auf localhost:8080.

Der Service verwendet in `application.properties` weiterhin
`terramax-hello-world`.

Die PKCE-Vorgabe des Frontend-Clients `publicClient` bleibt unverändert.
Der lokale Dev-UI-Client wird nicht für das produktive Frontend verwendet
und soll nicht in die produktive Keycloak-Konfiguration übernommen werden.

## Automatisierte Tests

Docker muss während des Testlaufs verfügbar sein.

Im Ordner `hello-service` ausführen:

```bash
./mvnw test
```

Die vier Tests prüfen:

- Mit simuliertem Benutzer alice: Status 200 und „Hello alice“
- Ohne Token: Status 401
- Mit ungültigem Token: Status 401
- Health-Check ohne Token: Status 200 und Status UP

Der Benutzer alice wird ausschließlich über `@TestSecurity` simuliert.
Er muss dafür nicht im TerraMax-Realm vorhanden sein.

Dieser Test prüft die Begrüßung für eine angemeldete Identität.
Die echte Anmeldung und Token-Prüfung wird zusätzlich manuell mit
Keycloak und dem Benutzer testuser geprüft.

Die Tests verwenden standardmäßig Port 8081.
Der Entwicklungsservice auf Port 8080 kann parallel weiterlaufen.

## Änderungen am lokalen Realm

Die Realm-Datei ist die Grundlage für einen frischen lokalen
Keycloak-Container.

Änderungen in der Keycloak-Admin-Oberfläche werden nicht automatisch
in die JSON-Datei zurückgeschrieben.

Für dauerhafte Änderungen muss die Realm-Datei im Repository angepasst
und anschließend mit einem frischen lokalen Container geprüft werden.

Beim Neuerstellen des Containers werden manuelle Änderungen aus dem
bisherigen Container nicht übernommen. Dazu gehören auch lokal
zurückgesetzte Benutzerpasswörter.

## Server-Konfiguration

Auf dem Server wird die Keycloak-Realm-URL über folgende
Umgebungsvariable bereitgestellt:

```text
QUARKUS_OIDC_AUTH_SERVER_URL
```

Weitere OIDC-Einstellungen können passend zur Server-Konfiguration
ebenfalls über Umgebungsvariablen gesetzt werden, beispielsweise:

```text
QUARKUS_OIDC_CLIENT_ID
```

Die Realm-Datei wird durch die hier verwendeten Dev Services nur
in Entwicklung und Test importiert. Die gemeinsame Serverinstanz
wird separat verwaltet.

Server-Zugangsdaten und Tokens gehören nicht ins Repository.

## Zusammenarbeit

Änderungen werden auf einem eigenen Branch entwickelt und per
Pull Request mit Review nach main übernommen.

Jedes Teammitglied soll den lokalen Start und einen Aufruf mit
gültigem Token auf dem eigenen Rechner ausprobieren.

Bezug: GitHub-Issue #10 „Hello World Microservice“.