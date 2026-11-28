# Datenquelle und Konfidenzstufe (#20)

> Als Dealer Principal möchte ich zu jeder Empfehlung die Datenquelle und eine Konfidenzstufe sehen,
> um meine Standortentscheidung gegenüber John Deere begründen zu können.

Jede Empfehlung auf der Marktkarte nennt, worauf sie sich stützt und wie belastbar das ist. Die Belastbarkeit steht als
Stufe da (hoch, mittel, niedrig), nicht als Rohwert oder Prozentzahl. Fehlen belastbare Daten, steht „unbekannt“.

```
Datenquelle: INVEKOS Österreich 2024 (Weizen und Sojabohnen)
Konfidenz: ▮▮▯ mittel
Amtliche Agrardaten, aber vorläufige Auswertung und nur zwei Kulturarten
```

## Abgrenzung zur KI-Kennzeichnung (#27)

| | Konfidenz (#20, hier) | KI-Kennzeichnung (#27, `src/ki/`) |
|---|---|---|
| Wofür | Auswertungen und Vorhersagen aus Daten | Inhalte, die ein Sprachmodell erzeugt (zusammenfassen, vorschlagen, entwerfen) |
| Aussage | Wie belastbar ist das Ergebnis, und woher kommt es? | Das hat eine KI geschrieben, bitte prüfen |
| Darstellung | Datenquelle, drei Balken und die Stufe als Wort, einfarbig | violettes Etikett mit Stern ✦ und Tooltip |

Ist eine Empfehlung von einer KI erzeugt, bekommt sie zusätzlich `<KiHinweis>` aus #27. Das deckt das Kriterium
„Von KI generierte Inhalte sind eindeutig gekennzeichnet“ ab.

Die Darstellung verzichtet bewusst auf Ampelfarben. Laut Stakeholder-Interview soll TerraMax Entscheidungen begründen
und kein KPI-Dashboard mit „grün = gut, rot = schlecht“ sein. Die Stufe steht immer auch als Wort da, die Balken sind
nur zusätzlich (für Screenreader ausgeblendet).

## Regeln für die Stufen

**Vorschlag, mit dem Product Owner und #25 (Klarenc) abzustimmen.**

| Stufe | Wann | Beispiele in TerraMax |
|---|---|---|
| hoch | Amtliche oder etablierte Quelle, aktuell und vollständig für die Frage | Mapbox-Fahrzeit (Routing auf aktuellem Straßennetz) |
| mittel | Belastbare Quelle mit Einschränkung: vorläufig, nur ein Teil der Daten, eigene Auswertung oder Näherung | INVEKOS 2024 (vorläufig, nur Weizen und Soja), Ackerfläche aus QGIS |
| niedrig | Test- oder Beispieldaten, ungeprüfte Recherche, auch KI-gestützte | Beispiel-Standorte, fiktive CRM-Testkunden |
| unbekannt | Kein Wert vorhanden oder keine Quelle | H3-Zelle ohne Flächenwert |

**Mehrere Quellen:** Die schwächste Quelle bestimmt die Stufe. Hat eine der Quellen keinen Wert, ist die Empfehlung
„unbekannt“. Stufe und Grund stehen immer sichtbar da. Darunter lässt sich „Datenquellen (n)“ aufklappen (`<details>`,
auch per Tastatur): Jede Quelle steht dort mit eigener Stufe und Begründung, damit man sieht, woran es liegt. Aufgeklappt
wird, damit kleine Kartenboxen wie die Fahrzeit-Box nicht so hoch werden, dass sie die Legende verdecken.

Sobald echte Daten die Testdaten ersetzen (z. B. echter CRM-Export), wird nur der Eintrag in `DATENQUELLEN` angepasst.
Alle Anzeigen ändern sich dann mit.

## Dateien

| Datei | Inhalt |
|---|---|
| `datenquellen.ts` | Typen, die bekannten Quellen (`DATENQUELLEN`), die Regel `gesamtstufe()` und `konfidenzAnzeige()`. Ohne React und DOM. |
| `Konfidenz.tsx` | React-Komponente `<Konfidenz>` |
| `konfidenzElement.ts` | Dieselbe Anzeige als DOM-Element für Mapbox-Popups |
| `Konfidenz.css` | Aussehen, nutzt `currentColor` und passt sich so dem Popup oder der Box an |

## Verwendung

React:

```tsx
import { Konfidenz } from './konfidenz/Konfidenz'
import { DATENQUELLEN } from './konfidenz/datenquellen'

<Konfidenz quellen={[DATENQUELLEN.mapboxFahrzeit, DATENQUELLEN.crmTestkunden]} />
<Konfidenz quellen={[]} ohneDaten="Fahrzeit konnte nicht berechnet werden" />
```

Mapbox-Popup (DOM):

```ts
import { konfidenzElement } from './konfidenz/konfidenzElement'
import { DATENQUELLEN, ohneWert } from './konfidenz/datenquellen'

popup.append(konfidenzElement([DATENQUELLEN.invekosWeizenSoja]))
// Quelle bekannt, aber für diese Stelle kein Wert:
popup.append(konfidenzElement([ohneWert(DATENQUELLEN.invekosWeizenSoja, 'Für diese Zelle liegt kein Flächenwert vor')]))
```

| Prop / Parameter | Bedeutung |
|---|---|
| `quellen` | Quellen, auf die sich die Empfehlung stützt. Leer heißt: keine belastbaren Daten, Stufe „unbekannt“. |
| `ohneDaten` | Erklärung, wenn `quellen` leer ist. Standard: „Keine belastbaren Daten vorhanden“. |

Neue Quelle: einen Eintrag in `DATENQUELLEN` mit `name`, `stufe` und `grund` ergänzen. Der Name soll so klingen, wie man
ihn gegenüber John Deere nennen würde.

## Wo eingesetzt

| Stelle | Quellen | Stufe |
|---|---|---|
| Popup einer H3-Zelle (#17) | INVEKOS 2024 Weizen und Soja | mittel, ohne Flächenwert „unbekannt“ |
| Box „Fahrzeitabdeckung (60 Min.)“ (#19) | Mapbox-Fahrzeit, Beispiel-Standorte, INVEKOS 2024, CRM-Testkunden (nur wenn geladen), zum Aufklappen | niedrig, wegen der Testdaten |

## Geprüft

Das Frontend hat kein Test-Setup. Geprüft wurde lokal:

- Regeln (`datenquellen.ts`) mit 9 Fällen über `node --test`: leere Liste, eine Quelle, schwächste Quelle gewinnt, Reihenfolge egal, Quelle ohne Wert, keine Rohwerte
- Anzeige mit 25 automatischen Browser-Checks (Chrome): Popup mit und ohne Flächenwert, Fahrzeit-Box zu- und aufgeklappt per Tastatur, ohne Kundendaten, React und DOM mit gleichem Aufbau, Balken für Screenreader ausgeblendet, dunkle Balken im weißen Popup auch im Dark Mode, 375px Handybreite, keine Konsolenfehler
- `npm run lint` und `npm run build` ohne Fehler

## Für #25 (Gebiete mit zu wenig Betreuung)

#25 verlangt zu jeder hervorgehobenen Zelle Kennzahlen und Datenquelle „vgl. #20“. Dafür ist dieser Baustein da,
z. B. im Popup einer weißen Fläche oder in der Liste der Top-Gebiete:

```ts
konfidenzElement([DATENQUELLEN.ackerflaeche, DATENQUELLEN.crmTestkunden])
// → Konfidenz: niedrig · Die schwächste Quelle bestimmt die Stufe
//   ▸ Datenquellen (2): Ackerfläche je H3-Zelle (QGIS-Auswertung): mittel, CRM-Export (fiktive Testkunden): niedrig
```

Die Popups und die Liste selbst baut #25, die Quellen und Regeln kommen von hier.

## Bewusst nicht gekennzeichnet

- **Popup eines Händlerstandorts:** zeigt schon Quelle und Herkunft, KI-recherchierte Einträge haben den Stern aus #27.
  Ein Standort ist ein Datensatz, keine Empfehlung.
- **Kunden-Popup:** zeigt Daten aus dem CRM, keine Auswertung.
- **Wöchentliche Hinweise (Glocke):** richten sich an den Sales Professional und nennen ihre Quelle schon.
