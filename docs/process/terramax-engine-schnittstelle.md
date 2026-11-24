# Übergabe: process-engine → terramax-service

Für Lukas und den CIB-Seven-Entwickler. **Umgesetzt mit #63:** Delegates,
ExecutionListener und BPMN-Modell rufen die Endpunkte so auf, wie hier
beschrieben (siehe [process-engine/README.md](../../process-engine/README.md)).
Die Spalte „Heute“ zeigt den Stand vor dem Umbau.

## Grundsatz

- **TerraMax ist die Single Source of Truth** für alle Tourdaten: Welche Kunden
  auf der Tour sind, Status je Halt, Gründe fürs Überspringen, Zeiten und Protokolle.
- **Die Engine stößt Abläufe nur an.** Im Prozesskontext steht so wenig wie möglich:
  die `tourId` und Flags für Gateways (`planBestaetigt`, `besuchUeberspringen`,
  `sprachnotizVorhanden`, …). Die Variablen `besuchsergebnisse` und
  `touruebersicht` entfallen.
- **Aufrufrichtung nur Engine → TerraMax.** TerraMax ruft die Engine nie auf und
  kennt ihre URL nicht.
- **TerraMax speichert die Besuchsprotokolle.** Der speech-service schickt das
  bestätigte Protokoll an `POST /tours/{tourId}/protocols`. Die Engine legt
  **keine** Protokolle an; bei Bedarf liest sie sie nur (`GET /protocols/{id}`,
  `GET /tours/{tourId}/protocols`, `protokollIds` in der Übersicht).
- Alle Schreibaufrufe sind **idempotent**. Die Engine darf einen Service Task nach
  einem Fehler einfach wiederholen. Ungültige Statusübergänge antworten mit **409**.

Swagger UI mit Beispielen zu jedem Endpunkt:
lokal http://localhost:8088/q/swagger-ui, auf dem Server `https://<terramax-subdomain>/q/swagger-ui`.

## Adresse und Anmeldung

| | Lokal | Server (Stack) |
| --- | --- | --- |
| Basis-URL | `http://localhost:8088` | `http://terramax-service:8080` (Container im selben Netzwerk) |
| `TERRAMAX_SERVICE_URL` der Engine | `http://localhost:8088` | `http://terramax-service:8080` |

Zwei Wege, wie die Engine sich anmeldet. Beide gehen an jedem Engine-Endpunkt:

1. **Nutzer-Token (Token Propagation).** Solange ein Schritt im Request des Sales
   Professional läuft (z. B. direkt nach dem Start der Tour oder beim Abschließen
   eines User Tasks), reicht die Engine dessen `Authorization`-Header durch, so wie
   `RestTerraMaxClient` es tut. Es gelten die Regeln des Nutzers: Er
   sieht nur eigene Touren, der Händler kommt aus `dealer_id`.
2. **Service-Konto.** Für Schritte ohne Nutzer (asynchron, Timer, Wiederholung
   durch den Job Executor) holt sich die Engine per Client Credentials ein eigenes
   Token. Das Token braucht die Realm-Rolle **`process_engine`** und hat kein
   `dealer_id`. TerraMax nimmt den Händler dann aus der Tour. Nur bei
   `POST /tours` muss das Service-Konto `dealerId` mitschicken.

Keycloak (Team-Realm `terramax`, vom Keycloak-Verantwortlichen anzulegen):

- Realm-Rolle `process_engine`
- Confidential Client, z. B. `terramax-process-engine`, mit *Service accounts
  enabled*, ohne Standard Flow. Dem Service-Account-Nutzer die Rolle
  `process_engine` zuweisen. Die Client-Scopes `roles` und `basic` müssen dabei
  sein, damit `realm_access.roles` und `sub` im Token stehen.
- Lokal ist beides schon im Dev-Realm `terramax-service/src/main/resources/terramax-realm.json`
  (Secret `local-dev-process-engine-secret`, gilt nur lokal):

  ```bash
  curl -s -d grant_type=client_credentials -d client_id=terramax-process-engine \
    -d client_secret=local-dev-process-engine-secret \
    http://localhost:8180/realms/terramax/protocol/openid-connect/token
  ```

Was das Service-Konto **nicht** darf: `GET /tours` (Liste, braucht einen Händler),
`GET /tours/plan` (nur `sales_professional`, Händler aus dem Token) sowie die
Kunden-Endpunkte.

## Schritt für Schritt (BPMN `Besuchstour`)

`{tourId}` ist die ID aus der Antwort von `POST /tours`, `{kundeId}` die Kunden-ID
(`customerId`) aus TerraMax.

| BPMN-Schritt | Heute (Prozessvariablen) | Künftig: TerraMax-Endpunkt | Ergebnis im Prozesskontext |
| --- | --- | --- | --- |
| **Besuchsplan erzeugen** (`besuchsplanErzeugen`) | `GET /tours/plan` beim tour-service, Plan als `besuchsplan`, `besuchsergebnisse = []` | 1. `GET /tours/plan?date={tourDatum}` (Format unverändert).<br>2. `POST /tours` mit `{"tourDate", "customerIds": [visits[].customer.id], "processInstanceId": execution.getProcessInstanceId()}` | `tourId`. Optional `kundenIds` (nur IDs) für die Multi-Instance-Schleife, siehe unten |
| Gateway **Mindestens zwei Kunden?** | Größe von `besuchsplan` | `GET /tours/{tourId}/status` → `anzahlHalte >= 2` | – |
| **Besuchsplan prüfen** (User Task) | `besuchsplan` umsortieren oder kürzen | Reihenfolge: `PATCH /tours/{tourId}` `{"customerOrder": [...]}`. Kunde streichen: `DELETE /tours/{tourId}/stops/{kundeId}` (nur bei geplanter Tour und offenem Halt) | Flag `planBestaetigt` |
| Gateway **Noch mindestens zwei Kunden?** | Größe von `besuchsplan` | `GET /tours/{tourId}/status` → `anzahlHalte >= 2` | – |
| **Tour verworfen** (Ende) | – | `PATCH /tours/{tourId}` `{"status": "discarded"}` | – |
| **Besuch vorbereiten** (User Task, je Kunde) | Element `kunde` aus `besuchsplan` | Daten lesen: `GET /tours/{tourId}` (Halt), `GET /customers/{kundeId}/protocols` (frühere Besuche) | `kundeId`, Flag `besuchUeberspringen` |
| **Grund für Überspringen speichern** (`besuchUeberspringenSpeichern`) | Eintrag in `besuchsergebnisse` mit `status = UEBERSPRUNGEN`, `grund` | `POST /tours/{tourId}/stops/{kundeId}/skip` `{"reason": ueberspringenGrund}` | – (`ueberspringenGrund` kann danach weg) |
| **Besuch durchführen** (User Task) | `besuchBegonnenAm` | Optional sofort beim Beginnen: `POST /tours/{tourId}/stops/{kundeId}/start` `{"at": besuchBegonnenAm}`. Sonst im nächsten Schritt | – |
| Sprachnotiz aufnehmen / transkribieren / Protokoll per KI entwerfen | External Tasks, speech-service | unverändert, nicht TerraMax | `protokollEntwurf` (bis zur Bestätigung) |
| **Protokoll prüfen und bestätigen** (User Task) | `protokoll` | Der speech-service speichert das bestätigte Protokoll in TerraMax mit `POST /tours/{tourId}/protocols` (sein `TerraMaxApi`, Token des Nutzers). Format bleibt unverändert. Die Engine ruft hier nichts auf | – |
| **Protokoll dem Kunden zuordnen** (`protokollZuordnen`) | Eintrag in `besuchsergebnisse` mit `status = DOKUMENTIERT`, `protokoll`, Zeiten | **Kein** `POST .../protocols`: Das Protokoll liegt schon in TerraMax. Nur den Halt abschließen: `POST /tours/{tourId}/stops/{kundeId}/start` `{"at": besuchBegonnenAm}`, dann `POST /tours/{tourId}/stops/{kundeId}/complete` `{"at": besuchAbgeschlossenAm}`. Beide idempotent, `start` darf also schon vorher gelaufen sein. Lesen bei Bedarf: `GET /tours/{tourId}/protocols` | – (`protokoll`, `protokollEntwurf`, Zeiten können weg) |
| Gateway am Schleifenende / **Alle Kunden besucht?** | – | `GET /tours/{tourId}/status` → `alleErledigt`, `naechsterHalt` | – |
| Message **TourAbbrechen** → Tour abgebrochen | – | `POST /tours/{tourId}/abort`, optional `{"reason": "…"}` (Body darf fehlen). Status `aborted`; offene Halte bleiben `OFFEN` und erscheinen so in der Übersicht. Idempotent; 409, wenn die Tour schon `completed` oder `discarded` ist | – |
| **Tourübersicht erstellen** (`touruebersichtErstellen`) | baut `touruebersicht` aus `besuchsplan` und `besuchsergebnisse` | `GET /tours/{tourId}/overview`. Gleiche Feldnamen wie bisher (`tourDatum`, `besuche[]`, `anzahlDokumentiert`, `anzahlUebersprungen`, `anzahlOffen`); statt des Protokollinhalts `protokollIds`. Der Schritt kann auch ganz entfallen, wenn das Formular `terramax:tour-uebersicht` die Übersicht direkt lädt | – |
| **Tour abschließen** (User Task) | `touruebersicht` anzeigen | `PATCH /tours/{tourId}` `{"status": "completed"}` | – |
| Tour wiederfinden (z. B. nach einem Neustart, `tourId` fehlt) | – | `GET /tours/by-process/{processInstanceId}` | `tourId` |

### Schleife „Kunden nacheinander besuchen“

Die Multi-Instance-Schleife braucht eine Collection. Zwei Möglichkeiten, beide
ohne Kundendaten im Prozess:

- **`kundenIds`:** Liste der Kunden-IDs aus der Antwort von `POST /tours`
  (`customers[].customerId`), nach „Besuchsplan prüfen“ neu aus
  `GET /tours/{tourId}` gelesen. Element-Variable `kundeId`.
- **Ohne Collection:** Schleife über ein Gateway mit `GET /tours/{tourId}/status`.
  Solange `alleErledigt == false`, ist `naechsterHalt.kundeId` der nächste Kunde.

## Antworten, die die Engine auswerten sollte

| Status | Bedeutung | Vorschlag |
| --- | --- | --- |
| `200`/`201` | Erfolg; bei Wiederholung `200` ohne Änderung | weiter |
| `400` | Angaben fehlen oder sind ungültig, z. B. leerer Grund beim Überspringen | Incident, Fehler im Modell oder Formular |
| `401`/`403` | Token fehlt, ist abgelaufen oder hat nicht die nötige Rolle | Incident; bei abgelaufenem Nutzer-Token mit dem Service-Konto wiederholen |
| `404` | Tour oder Halt unbekannt oder für diesen Nutzer nicht sichtbar | Incident |
| `409` | Ungültiger Statusübergang, z. B. Halt schon dokumentiert und jetzt übersprungen, oder Tour bereits abgeschlossen | BPMN-Fehler oder Incident, nicht blind wiederholen |

Fehler kommen als `{"error": "…"}`. Der tour-service hat `{"message": "…"}`
geliefert; `RestTerraMaxClient` liest `error` und ordnet den Status ein (400/404/409 fachlich, sonst technisch), für die Engine ändert
sich also nichts.

## Statusmodell je Halt

`OFFEN` → `DOKUMENTIERT` (complete) oder `UEBERSPRUNGEN` (skip, Grund Pflicht).
`start` setzt nur `startedAt`, der Status bleibt `OFFEN`. `DOKUMENTIERT` und
`UEBERSPRUNGEN` sind endgültig. Der erste Übergang setzt die Tour von `planned` auf
`in_progress`. Auf Touren mit Status `completed`, `discarded` oder `aborted` ist
kein Übergang mehr möglich (409). Die Namen entsprechen `BesuchstourVariablen.STATUS_*`.

| Aufruf | OFFEN | OFFEN, begonnen | DOKUMENTIERT | UEBERSPRUNGEN |
| --- | --- | --- | --- | --- |
| `start` | setzt `startedAt` | unverändert (200) | 409 | 409 |
| `skip` | UEBERSPRUNGEN | UEBERSPRUNGEN | 409 | gleicher Grund: unverändert (200), anderer Grund: 409 |
| `complete` | DOKUMENTIERT | DOKUMENTIERT | unverändert (200) | 409 |

## Statusmodell der Tour

`planned` → `in_progress` (erster Übergang eines Halts) → `completed` (Tour
abschließen). Daneben `discarded` (Plan verworfen) und `aborted` (TourAbbrechen).
`completed`, `discarded` und `aborted` sind endgültig.

| Ziel | aus planned / in_progress | aus completed | aus discarded | aus aborted |
| --- | --- | --- | --- | --- |
| `PATCH` `completed` / `discarded` | ja | nur gleicher Status (200), sonst 409 | nur gleicher Status (200), sonst 409 | 409 |
| `POST /abort` | `aborted` (Grund optional) | 409 | 409 | 200; anderer Grund 409 |
| `PATCH` `aborted` | 400, nur über `POST /abort` | | | |

`GET /tours/{tourId}/status` zeigt dazu `beendet`, `abgebrochen` und
`abbruchGrund`; bei beendeter Tour ist `naechsterHalt` `null`. Die Übersicht hat
`abbruchGrund` und `abgebrochenAm`.

## Geklärt

- **Abbruch:** eigener Status `aborted` über `POST /tours/{tourId}/abort`
  (Migration V4). Nicht mehr `completed`.
- **Doppelte Protokolle:** TerraMax speichert die Protokolle. Der speech-service
  schickt sie an `POST /tours/{tourId}/protocols`; die Engine legt keine an und
  liest sie nur bei Bedarf. Damit kann es keine doppelten Protokolle aus Engine
  und speech-service geben.

## Noch abzustimmen

- ~~**Netzwerk im Stack**~~ **Geklärt (#63):** Die Engine hängt wie der speech-service
  im Netzwerk `nginx-proxy-manager` und erreicht so `terramax-service:8080`.
- **`TourPlan` in der Engine** (jetzt `de.thi.terramax.process.terramax.TourPlan`): Ändert sich die Klasse, muss die Testkopie in
  `terramax-service/src/test/java/de/thi/terramax/tour/TourPlanResourceTest.java` mitgehen.
