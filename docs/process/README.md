# BPMN-Prozess: Besuchstour

Ergebnis zu Issue #29. Das Modell [besuchstour.bpmn](besuchstour.bpmn) beschreibt die geführte Besuchstour des Sales Professional mit Sprachprotokoll. Es ist für die Engine CIB seven ausführbar (Prozess-Key `Besuchstour`).

Das Modell läuft auf CIB seven 2.2.0 (Deployment und Durchläufe mit Test-Delegates): Tour mit und ohne Sprachnotiz, Überspringen, beide Fehlerpfade, Abbruch und Plan mit weniger als zwei Kunden.

## Schnittstellen für die Umsetzung

| Art | Namen |
|---|---|
| Rolle für den Start | `sales_professional` |
| Java-Delegates | `besuchsplanErzeugen`, `besuchUeberspringenSpeichern`, `protokollZuordnen`, `touruebersichtErstellen` |
| External-Task-Topics | `sprachnotiz-transkribieren`, `protokollentwurf-erstellen` |
| BPMN-Fehler | `TRANSKRIPTION_FEHLGESCHLAGEN`, `PROTOKOLLENTWURF_FEHLGESCHLAGEN` |
| Message | `TourAbbrechen` |
| Formulare | `terramax:tour-starten`, `terramax:besuchsplan-pruefen`, `terramax:besuch-vorbereiten`, `terramax:besuch-durchfuehren`, `terramax:sprachnotiz`, `terramax:protokoll-pruefen`, `terramax:tour-uebersicht` |

## Offene Punkte

- ~~Daten laufen in den nächsten Besuch über~~ **Behoben:** Die Variablen eines Besuchs (`ueberspringenGrund`, `besuchBegonnenAm`, `besuchAbgeschlossenAm`, `sprachnotizId`, `transkript`, `protokollEntwurf`, `protokoll`) sind jetzt lokale Eingangsvariablen des Unterprozesses.
- **Abweichung zum `speech-service`:** Das Modell erwartet eine gespeicherte Audiodatei (`sprachnotizId`) und transkribiert per External Task. Der `speech-service` auf `feature/speech-to-text` arbeitet synchron und speichert kein Audio.
