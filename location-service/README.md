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

## In Arbeit

- **REST-Endpoints** für Händlerstandorte
- **Datenbank** zur Speicherung der Standorte
