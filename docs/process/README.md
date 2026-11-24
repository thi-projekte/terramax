# BPMN-Prozess: Besuchstour

Ergebnis zu Issue #29. Das Modell [besuchstour.bpmn](besuchstour.bpmn) beschreibt die geführte Besuchstour des Sales Professional mit Sprachprotokoll. Es ist für die Engine CIB seven ausführbar (Prozess-Key `Besuchstour`).

Das Modell läuft auf CIB seven 2.2.0 (Deployment und Durchläufe mit Test-Delegates): Tour mit und ohne Sprachnotiz, Überspringen, beide Fehlerpfade, Abbruch und Plan mit weniger als zwei Kunden.

## Schnittstellen für die Umsetzung

| Art | Namen |
|---|---|
| Rolle für den Start | `sales_professional` |
| Java-Delegates | `besuchsplanErzeugen`, `besuchUeberspringenSpeichern`, `protokollZuordnen` |
| ExecutionListener | `besuchsplanLaden`, `besuchsplanUebernehmen`, `kundeLaden`, `touruebersichtLaden`, `geladeneDatenEntfernen`, `tourVerwerfen`, `tourAbbrechen`, `tourAbschliessen` |
| External-Task-Topics | `sprachnotiz-transkribieren`, `protokollentwurf-erstellen` |
| BPMN-Fehler | `TRANSKRIPTION_FEHLGESCHLAGEN`, `PROTOKOLLENTWURF_FEHLGESCHLAGEN`, `BESUCHSPLAN_FEHLGESCHLAGEN`, `UEBERSPRINGEN_ABGELEHNT`, `BESUCHSABSCHLUSS_ABGELEHNT` |
| Message | `TourAbbrechen` |
| Formulare | `terramax:tour-starten`, `terramax:besuchsplan-pruefen`, `terramax:besuch-vorbereiten`, `terramax:besuch-durchfuehren`, `terramax:sprachnotiz`, `terramax:protokoll-pruefen`, `terramax:tour-uebersicht` |

## Daten im Prozess (#63)

Die Tour liegt im terramax-service, die Engine speichert nur die Ausführung. Im Prozess stehen Referenzen (`tourId`, `kundenIds`, `kundeId`) und Flags für die Gateways (`anzahlHalte`, `planBestaetigt`, …). Die Schleife „Kunden nacheinander besuchen“ läuft über `kundenIds`. Daten für die Anzeige (`besuchsplan`, `kunde`, `touruebersicht`) lädt ein ExecutionListener am Start der User Task per ID und entfernt sie am Ende wieder. Der frühere Schritt „Tourübersicht erstellen“ entfällt, weil „Tour abschließen“ die Übersicht direkt aus TerraMax lädt. Jeder Service Task hat ein Error-Boundary-Event für fachliche Fehler von TerraMax. Details: [process-engine/README.md](../../process-engine/README.md).

## Offene Punkte

- ~~Daten laufen in den nächsten Besuch über~~ **Behoben:** Die Variablen eines Besuchs (`ueberspringenGrund`, `besuchBegonnenAm`, `besuchAbgeschlossenAm`, `sprachnotizId`, `transkript`, `protokollEntwurf`, `protokoll`) sind jetzt lokale Eingangsvariablen des Unterprozesses.
- **Abweichung zum `speech-service`:** Das Modell erwartet eine gespeicherte Audiodatei (`sprachnotizId`) und transkribiert per External Task. Der `speech-service` auf `feature/speech-to-text` arbeitet synchron und speichert kein Audio.
