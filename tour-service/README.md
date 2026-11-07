# Tour Service

Quarkus-Microservice für die Besuchstour des Sales Professionals (Issue #29,
Teil 2 „Besuchsplan generieren“). Der Service schlägt für einen Tag einen
Besuchsplan mit Kunden in fester Reihenfolge vor.

Kundendaten sind im MVP Mock-Daten (`MockCustomerRepository`, frei erfundene
Betriebe in Oberösterreich). Eine BPMN-Engine ist noch nicht angebunden; der
`VisitPlanner` ist so gebaut, dass ihn später der Delegate
`besuchsplanErzeugen` aus dem BPMN-Modell `Besuchstour` aufrufen kann.

## Planregel (MVP)

Entspricht der Annotation am Service Task „Besuchsplan erzeugen“:

1. **Auswahl:** Kunden mit dem am längsten zurückliegenden Besuch, noch nie
   besuchte Kunden zuerst. Wer am Tourtag oder danach schon besucht wurde,
   fällt heraus.
2. **Reihenfolge:** nach Luftlinie ab Standort, immer der nächstgelegene noch
   offene Kunde. Keine Routenoptimierung nach Fahrzeit.

Der Standort ist über `tour.start.latitude` / `tour.start.longitude`
einstellbar (Standard: Wels).

## Schnittstelle

Nur mit Rolle `sales_professional` (sonst 403, ohne Token 401).

```http
GET /tours/plan?date=2026-11-10&maxVisits=5
Authorization: Bearer <Access Token>
```

| Parameter   | Pflicht | Standard | Bedeutung                       |
|-------------|---------|----------|---------------------------------|
| `date`      | nein    | heute    | Tourtag im Format `JJJJ-MM-TT`  |
| `maxVisits` | nein    | 5        | Anzahl Besuche, 2 bis 10        |

Antwort (gekürzt):

```json
{
  "date": "2026-11-10",
  "start": { "latitude": 48.1575, "longitude": 14.0289 },
  "visits": [
    {
      "position": 1,
      "customer": {
        "id": "K-1001",
        "name": "Hofgut Aigner",
        "location": "Gunskirchen",
        "position": { "latitude": 48.1333, "longitude": 13.9500 },
        "lastVisit": "2026-03-12"
      },
      "distanceFromPreviousKm": 6.4
    }
  ],
  "totalDistanceKm": 63.2
}
```

Ungültige Parameter liefern 400 mit `{ "message": "..." }`. Kommen weniger als
zwei Kunden in Frage, enthält `visits` entsprechend weniger Einträge; im
BPMN-Modell endet die Tour dann mit „Keine Tour möglich“.

## Lokal starten

Voraussetzungen wie beim `hello-service` (JDK 25, Docker).

```bash
cd tour-service
./mvnw quarkus:dev
```

Der Service läuft unter http://localhost:8084. Keycloak startet über Dev
Services unter http://localhost:8180 mit dem Realm aus dem `hello-service`.

## Tests

```bash
./mvnw verify
```

Die Anmeldung wird in den Tests mit `@TestSecurity` simuliert, Keycloak ist
dafür nicht nötig.
