import { konfidenzAnzeige, type Datenquelle } from './datenquellen'
import './Konfidenz.css'

// Datenquelle und Konfidenzstufe einer Empfehlung (#20), für React-Oberflächen.
// Für Kartenpopups, die per DOM gebaut werden, gibt es konfidenzElement() mit demselben Aufbau.

type Props = Readonly<{
  /** Quellen, auf die sich die Empfehlung stützt. Leer bedeutet: keine belastbaren Daten. */
  quellen: readonly Datenquelle[]
  /** Erklärung, wenn keine Quelle vorliegt */
  ohneDaten?: string
}>

function KonfidenzBalken({ anzahl }: Readonly<{ anzahl: number }>) {
  return (
    <span className="konfidenz-balken" aria-hidden="true">
      {[1, 2, 3].map((nr) => (
        <span key={nr} className={nr <= anzahl ? 'an' : undefined} />
      ))}
    </span>
  )
}

export function Konfidenz({ quellen, ohneDaten }: Props) {
  const anzeige = konfidenzAnzeige(quellen, ohneDaten)
  const mehrere = anzeige.quellen.length > 1
  return (
    <div className="konfidenz">
      {!mehrere && (
        <p>
          <span className="konfidenz-label">Datenquelle:</span> {anzeige.quellen[0]?.name ?? 'keine'}
        </p>
      )}
      <p className="konfidenz-stufe">
        <span className="konfidenz-label">Konfidenz:</span> <KonfidenzBalken anzahl={anzeige.balken} />{' '}
        <strong>{anzeige.stufe}</strong>
      </p>
      <p className="konfidenz-grund">{anzeige.grund}</p>
      {/* Mehrere Quellen zum Aufklappen, damit kleine Kartenboxen nicht zu hoch werden */}
      {mehrere && (
        <details>
          <summary>Datenquellen ({anzeige.quellen.length})</summary>
          <ul className="konfidenz-quellen">
            {anzeige.quellen.map((quelle) => (
              <li key={quelle.name}>
                {quelle.name}: <strong>{quelle.stufe}</strong>
                <span className="konfidenz-grund">{quelle.grund}</span>
              </li>
            ))}
          </ul>
        </details>
      )}
    </div>
  )
}
