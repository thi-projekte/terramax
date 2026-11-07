# Location Service

Quarkus-Microservice für TerraMax, der Händlerstandorte verwaltet.

Unterstützte Standorttypen:

- `competitor`: Standorte von Wettbewerbern
- `mixed_competitor`: Standorte von Händlern mit gemischtem Markenportfolio
- `john_deere`: John-Deere-Händler (folgt später)

## Voraussetzungen

- JDK 25
- Laufende Docker-Umgebung, beispielsweise Docker Desktop (für Keycloak Dev Services)
- Im Dev-Modus das Repository `keycloak-ws2627` im selben Ordner wie `terramax`
  (der Realm wird aus `../../keycloak-ws2627/terramax-realm.json` geladen)

Maven wird über den mitgelieferten Maven Wrapper ausgeführt.
Eine separate Maven-Installation ist nicht erforderlich.

## Lokal starten

```bash
cd location-service
./mvnw quarkus:dev
```

Der Service ist unter http://localhost:8082 erreichbar.
Beenden mit Ctrl + C.

## Health-Check

```bash
curl http://localhost:8082/q/health
```

Liveness und Readiness gibt es auch einzeln unter `/q/health/live` und
`/q/health/ready`.

## Build und Prüfung

```bash
./mvnw verify
```

Dabei wird unter anderem die Formatierung mit
`../config/formatter/eclipse-java.xml` geprüft.

## Endpunkte und API-Doku

Alle Endpunkte unter `/locations` brauchen einen gültigen Keycloak-Token
(`Authorization: Bearer <token>`) und liefern GeoJSON.

| Methode und Pfad | Liefert |
| --- | --- |
| `GET /locations` | Alle Standorte als `FeatureCollection`, sortiert nach Name |
| `GET /locations?category=competitor,mixed_competitor` | Nur diese Kategorien (400 bei unbekannter Kategorie) |
| `GET /locations/{id}` | Ein Standort als `Feature` (404 bei unbekannter ID) |

- Swagger UI (Dev-Modus): http://localhost:8082/q/swagger-ui
  (Token über den Knopf **Authorize** eintragen)
- OpenAPI-Dokument: http://localhost:8082/q/openapi

## Keycloak: lokal oder Team-Keycloak

Der Service ist ein reiner Resource-Server: Er prüft nur Bearer-Tokens und
leitet nicht auf eine Login-Seite weiter.

**Ohne Variable (Standard):** Quarkus startet im Dev-Modus einen lokalen
Keycloak über Dev Services mit dem Testrealm.

```powershell
cd location-service
./mvnw quarkus:dev
```

**Mit Team-Keycloak:** Tokens werden gegen den gemeinsamen Keycloak geprüft.
Dann wird kein lokaler Keycloak-Container gestartet.

```powershell
cd location-service
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
startet der Service nicht. Auf dem Server kann statt `OIDC_AUTH_SERVER_URL`
auch `QUARKUS_OIDC_AUTH_SERVER_URL` gesetzt werden (wie im `hello-service`).
`./mvnw verify` nutzt immer den lokalen Testrealm und braucht kein Netzwerk
zum Team-Keycloak. Secrets sind nicht nötig und gehören nicht ins Repository.

## CORS

Standardmäßig ist nur der lokale Vite-Server `http://localhost:5173`
freigegeben (Methoden `GET`, `OPTIONS`; Header `Authorization`, `Content-Type`).
Weitere Origins kommagetrennt über `CORS_ORIGINS`:

```powershell
$env:CORS_ORIGINS = "http://localhost:5173,https://<frontend-host>"
./mvnw quarkus:dev
```

`*` ist nicht erlaubt.
