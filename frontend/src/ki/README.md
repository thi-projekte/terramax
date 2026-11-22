# KI-Kennzeichnung (`KiHinweis`)

Issue: #27 – „Als Nutzer erkenne ich KI-generierte Inhalte im UI“

## Wofür

User Story: Als Nutzer (Dealer Principal oder Sales Professional) möchte ich auf einen Blick erkennen, welche
Inhalte KI-generiert sind, damit ich sie richtig einordnen kann.

Alles, was ein Sprachmodell **zusammenfasst, vorschlägt oder entwirft**, wird im UI mit derselben Marke
gekennzeichnet: Stern-Icon ✦ und Text „KI-generiert“ mit Tooltip. Hintergrund ist die Transparenzpflicht aus
dem EU AI Act und der Wunsch des Stakeholders, generierte Inhalte auf einen Blick zu erkennen.

**Nicht** dafür gedacht sind ML-Vorhersagen auf Datenbasis (z. B. „Ackerbau vs. Milchvieh“). Die zeigen eine
Konfidenzstufe hoch/mittel/niedrig mit Datenquelle, siehe #20.

| | KI-Kennzeichnung (#27) | ML-Konfidenz (#20) |
|---|---|---|
| Inhalt | generierter Text (Protokoll, Zusammenfassung, Vorschlag) | Vorhersage aus Daten |
| Darstellung | ✦ + „KI-generiert“, violett, gestrichelter Rand | Stufe hoch/mittel/niedrig |
| Farben | Akzentfarbe (`--accent`), keine Ampelfarben | eigene Darstellung von #20, ohne ✦ |

## Dateien

| Datei | Inhalt |
|---|---|
| `KiHinweis.tsx` | React-Komponente |
| `KiHinweis.css` | Aussehen, nutzt die Farben aus `index.css` (hell/dunkel) |
| `ki.ts` | `KI_ICON`, `KI_TEXT`, `KI_ERKLAERUNG` für Stellen ohne React |

## Verwendung

```tsx
import { KiHinweis } from '../ki/KiHinweis'

<KiHinweis />                                         // ✦ KI-generiert
<KiHinweis quelle="der Importstatistik" />            // ✦ KI-generiert  basierend auf der Importstatistik
<KiHinweis text="KI-Entwurf" quelle="deiner Sprachnotiz" />
```

| Prop | Pflicht | Bedeutung |
|---|---|---|
| `quelle` | nein | Worauf sich der Inhalt stützt; wird zu „basierend auf …“. **Immer angeben, wenn der Inhalt auf Daten beruht.** |
| `text` | nein | Sichtbarer Text neben dem Stern, Standard „KI-generiert“ |
| `erklaerung` | nein | Text im Tooltip, Standard: „Dieser Inhalt wurde von einer KI erstellt und kann Fehler enthalten. Bitte prüfen.“ |

Außerhalb von React (z. B. Mapbox-Popups, die per DOM gebaut werden) die Konstanten aus `ki.ts` verwenden,
damit Icon und Wortlaut überall gleich bleiben:

```ts
import { KI_ICON } from './ki/ki'
const label = `${KI_ICON} Web-Recherche (KI-gestützt)`
element.title = 'Dieser Standort wurde von einer KI im Web recherchiert …' // Tooltip
```

Ohne React gibt es dort den einfachen Browser-Tooltip (`title`), der beim Darüberfahren mit der Maus erscheint.
Der Hinweis „(KI-gestützt)“ steht zusätzlich sichtbar im Text, damit er auch auf dem Tablet erkennbar ist.

## Bedienung und Barrierefreiheit

- Die Marke ist ein echter Button (`type="button"`, schickt also kein Formular ab).
- Der Tooltip erscheint beim Darüberfahren mit der Maus und beim Fokus per Tab-Taste. Auf Tablet und Handy
  öffnet und schließt ihn ein Antippen; Antippen woanders schließt ihn ebenfalls. Die Trefferfläche ist größer
  als die gezeichnete Marke.
- Die Maus kann auf den Tooltip wechseln, ohne dass er verschwindet, und **Escape** schließt ihn immer, auch
  wenn nur die Maus darüber steht (WCAG 1.4.13).
- Screenreader lesen den Tooltip als Beschreibung vor (`aria-describedby`), das Stern-Icon selbst wird
  übersprungen.
- Allgemeine Button-Stile der Seiten (`.tour-card button`, `.role-sections button`) greifen nicht auf die Marke.
- Die Quelle steht sichtbar neben der Marke, damit sie auch ohne Tooltip lesbar ist.
- Auf schmalen Bildschirmen wird der Tooltip nicht breiter als der Bildschirm.

## Regeln

- Jeder generierte Inhalt bekommt die Marke, direkt an der Stelle, wo er angezeigt wird.
- Hat der Nutzer den Inhalt geprüft und bestätigt (z. B. Besuchsprotokoll), darf die Marke im bestätigten
  Ergebnis entfallen. Im Entwurf bleibt sie immer stehen.
- Leads oder Kundeneigenschaften werden nicht per LLM erfunden; die Marke ist kein Freifahrtschein dafür.
- Kein Profiling zum Nachteil des Kunden (EU AI Act): KI-Inhalte dienen der Vorbereitung und Dokumentation des
  Vertriebs, nicht der Bewertung von Kunden.

## Wo eingesetzt

| Stelle | Datei | Was |
|---|---|---|
| Besuchstour, Schritt „Protokoll prüfen“ | `src/tour/Schritte.tsx` | KI-Entwurf aus der Sprachnotiz |
| Protokollformular des Speech-Service | `src/speech/VisitProtocolForm.tsx` | KI-Entwurf aus der Sprachnotiz, ersetzt „Von der AI vorausgefüllt“ |
| Standort-Popup der Karte | `src/locationLayer.ts` | Herkunft „✦ Web-Recherche (KI-gestützt)“ mit Tooltip: Standort von einer KI recherchiert, Quelle und Prüfstatus stehen schon im Popup |

Bewusst nicht gekennzeichnet:

- Weekly Alerts (`src/alerts.ts`): regelbasiert, nicht KI-generiert.
- Transkript der Sprachnotiz: wörtliche Umwandlung von Sprache in Text, kein zusammengefasster oder entworfener
  Inhalt. Es wird direkt beim KI-Entwurf angezeigt, den es begründet.
- Bestätigte Protokolle (Tourübersicht, Protokoll-Zusammenfassung): vom Nutzer geprüft, siehe Regeln.
