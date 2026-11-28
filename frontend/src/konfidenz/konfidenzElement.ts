import { konfidenzAnzeige, type Datenquelle } from './datenquellen'
import './Konfidenz.css'

// DOM-Variante von <Konfidenz> (#20) für Mapbox-Popups, die ohne React gebaut werden. Gleicher Aufbau, gleiche Klassen.

function element<K extends keyof HTMLElementTagNameMap>(tag: K, className?: string, text?: string) {
  const el = document.createElement(tag)
  if (className) el.className = className
  if (text !== undefined) el.textContent = text
  return el
}

function zeile(label: string, wert: string): HTMLParagraphElement {
  const p = element('p')
  p.append(element('span', 'konfidenz-label', label), ` ${wert}`)
  return p
}

export function konfidenzElement(quellen: readonly Datenquelle[], ohneDaten?: string): HTMLDivElement {
  const anzeige = konfidenzAnzeige(quellen, ohneDaten)
  const mehrere = anzeige.quellen.length > 1
  const box = element('div', 'konfidenz')

  if (!mehrere) box.append(zeile('Datenquelle:', anzeige.quellen[0]?.name ?? 'keine'))

  const balken = element('span', 'konfidenz-balken')
  balken.setAttribute('aria-hidden', 'true')
  for (let nr = 1; nr <= 3; nr++) balken.append(element('span', nr <= anzeige.balken ? 'an' : undefined))

  const stufe = element('p', 'konfidenz-stufe')
  stufe.append(element('span', 'konfidenz-label', 'Konfidenz:'), ' ', balken, ' ', element('strong', undefined, anzeige.stufe))

  box.append(stufe, element('p', 'konfidenz-grund', anzeige.grund))

  if (mehrere) {
    const liste = element('ul', 'konfidenz-quellen')
    for (const quelle of anzeige.quellen) {
      const li = element('li')
      li.append(`${quelle.name}: `, element('strong', undefined, quelle.stufe), element('span', 'konfidenz-grund', quelle.grund))
      liste.append(li)
    }
    const details = element('details')
    details.append(element('summary', undefined, `Datenquellen (${anzeige.quellen.length})`), liste)
    box.append(details)
  }
  return box
}
