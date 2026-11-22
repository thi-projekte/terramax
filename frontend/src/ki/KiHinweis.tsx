import { useEffect, useId, useRef, useState } from 'react'
import { KI_ERKLAERUNG, KI_ICON, KI_TEXT } from './ki'
import './KiHinweis.css'

// Einheitliche Kennzeichnung für KI-generierte Inhalte (#27).
// Gilt für Inhalte, die ein Sprachmodell zusammenfasst, vorschlägt oder entwirft (z. B. Besuchsprotokoll aus der
// Sprachnotiz). Nicht für ML-Vorhersagen mit Konfidenzstufe (#20), die haben eine eigene Darstellung.

type Props = Readonly<{
  /** Worauf sich der Inhalt stützt, ergänzt zu „basierend auf …“, z. B. "deiner Sprachnotiz". */
  quelle?: string
  /** Sichtbarer Text neben dem Icon. Standard: „KI-generiert“. */
  text?: string
  /** Text im Tooltip. Standard: Hinweis, dass der Inhalt geprüft werden muss. */
  erklaerung?: string
}>

// null: Maus und Fokus entscheiden (CSS) · true: per Klick/Antippen geöffnet · false: per Escape geschlossen
type Erzwungen = boolean | null

export function KiHinweis({ quelle, text = KI_TEXT, erklaerung = KI_ERKLAERUNG }: Props) {
  const tooltipId = useId()
  const anker = useRef<HTMLSpanElement>(null)
  const [erzwungen, setErzwungen] = useState<Erzwungen>(null)

  useEffect(() => {
    // Escape schließt den Tooltip auch, wenn nur die Maus darüber steht (WCAG 1.4.13)
    const beiTaste = (e: KeyboardEvent) => {
      if (e.key === 'Escape') setErzwungen(false)
    }
    // Antippen außerhalb schließt einen per Antippen geöffneten Tooltip wieder
    const beiZeiger = (e: PointerEvent) => {
      if (!anker.current?.contains(e.target as Node)) setErzwungen(null)
    }
    globalThis.addEventListener('keydown', beiTaste)
    globalThis.addEventListener('pointerdown', beiZeiger)
    return () => {
      globalThis.removeEventListener('keydown', beiTaste)
      globalThis.removeEventListener('pointerdown', beiZeiger)
    }
  }, [])

  // Nach Escape zeigt erst eine neue Bewegung oder ein neuer Fokus den Tooltip wieder
  const wiederFreigeben = () => setErzwungen((vorher) => (vorher === false ? null : vorher))

  let ankerKlasse = 'ki-hinweis-anker'
  if (erzwungen === true) ankerKlasse += ' ki-hinweis-offen'
  if (erzwungen === false) ankerKlasse += ' ki-hinweis-zu'

  return (
    <span className="ki-hinweis">
      <span ref={anker} className={ankerKlasse}>
        {/* Ein echter Button: per Tab erreichbar und auf Tablet und Handy antippbar */}
        <button
          type="button"
          className="ki-hinweis-marke"
          aria-describedby={tooltipId}
          onClick={() => setErzwungen((vorher) => vorher !== true)}
          onMouseEnter={wiederFreigeben}
          onFocus={wiederFreigeben}
        >
          <span className="ki-hinweis-icon" aria-hidden="true">
            {KI_ICON}
          </span>
          {text}
        </button>
        <span role="tooltip" id={tooltipId} className="ki-hinweis-tooltip">
          {erklaerung}
        </span>
      </span>
      {quelle && <span className="ki-hinweis-quelle">basierend auf {quelle}</span>}
    </span>
  )
}
