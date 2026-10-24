# Hello Service

Minimaler Quarkus-Microservice für TerraMax.
Er dient als Einstieg in Projektstruktur, Build und lokale Entwicklung
sowie als Gegenstelle für die Token-Prüfung mit Keycloak.

## Voraussetzungen

- JDK 25
- Laufende Docker-Umgebung
- Beim ersten Start eine Internetverbindung für Downloads

Maven wird über den mitgelieferten Maven Wrapper ausgeführt.

## Lokal starten

Alle folgenden Befehle werden im Ordner `hello-service` ausgeführt:

```bash
./mvnw quarkus:dev
```

Quarkus Dev Services startet automatisch einen lokalen Keycloak-Container.
Eine feste Keycloak-URL ist in `application.properties` nicht hinterlegt.

Der Service ist unter http://localhost:8080 erreichbar.

Beenden: Ctrl + C im Terminal.

## Endpunkte

| Endpunkt | Authentifizierung | Antwort |
|---|---|---|
| GET /hello | Gültiges Bearer-Token erforderlich | 200 mit persönlicher JSON-Begrüßung |
| GET /q/health | Ohne Token erreichbar | 200 mit Status UP |

Beispielantwort für den Benutzer alice:

```json
{"message":"Hello alice"}
```

Ohne Token oder mit ungültigem Token liefert `/hello` den Status 401.

## Manuell testen

Ohne Token:

```bash
curl -i http://localhost:8080/hello
```

Erwartet: 401 Unauthorized.

Health-Check:

```bash
curl -i http://localhost:8080/q/health
```

Erwartet: 200 und Status UP.

### Mit lokalem Testbenutzer

1. http://localhost:8080/q/dev-ui/extensions öffnen.
2. Bei OpenID Connect den Keycloak-Anbieter öffnen.
3. Realm `quarkus` und Client `quarkus-app` verwenden.
4. Über „Log into Single Page Application“ anmelden.
5. Benutzername `alice`, Passwort `alice` verwenden.
6. Dienstpfad `/hello` mit dem Zugriffstoken aufrufen.

Erwartet: Status 200.

Diese Zugangsdaten gelten für den automatisch erzeugten lokalen
Entwicklungs-Realm, solange kein eigener Realm importiert wird.

## Automatisierte Tests

Docker muss auch für den Testlauf verfügbar sein.

```bash
./mvnw test
```

Die Tests prüfen:

- Mit simuliertem Benutzer alice: 200 und „Hello alice“
- Ohne Token: 401
- Mit ungültigem Token: 401
- Health-Check ohne Token: 200 und Status UP

Der erfolgreiche Benutzeraufruf wird mit `@TestSecurity` simuliert.
Die echte Anmeldung und Token-Prüfung kann zusätzlich über die Dev UI
manuell getestet werden.

## Gemeinsamer TerraMax-Realm

Sobald die Realm-Datei aus der Login-Story #8 im Repository verfügbar ist,
wird sie lokal über folgende Einstellung eingebunden:

`quarkus.keycloak.devservices.realm-path`

Der konkrete Dateipfad wird ergänzt, sobald die Datei vorliegt.

## Server-Konfiguration

Auf dem Server wird die Keycloak-Realm-URL über die Umgebungsvariable
`QUARKUS_OIDC_AUTH_SERVER_URL` gesetzt. Weitere benötigte OIDC-Einstellungen
werden passend zur gemeinsamen Realm- und Client-Konfiguration gesetzt.

Server-Zugangsdaten und Tokens gehören nicht ins Repository.