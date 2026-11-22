// Datenquelle und Konfidenzstufe zu Empfehlungen (#20).
// Die Konfidenz ist eine einfache Stufe, kein Rohwert. Die Regeln dazu stehen in README.md.

export type Konfidenzstufe = 'hoch' | 'mittel' | 'niedrig' | 'unbekannt'

export type Datenquelle = Readonly<{
  /** Name, wie ihn der Dealer Principal gegenüber John Deere nennen kann */
  name: string
  stufe: Konfidenzstufe
  /** Kurze Begründung der Stufe */
  grund: string
}>

/** Bekannte Datenquellen mit ihrer Stufe. Neue Quellen hier eintragen, damit sie überall gleich heißen. */
export const DATENQUELLEN = {
  invekosWeizenSoja: {
    name: 'INVEKOS Österreich 2024 (Weizen und Sojabohnen)',
    stufe: 'mittel',
    grund: 'Amtliche Agrardaten, aber vorläufige Auswertung und nur zwei Kulturarten',
  },
  ackerflaeche: {
    name: 'Ackerfläche je H3-Zelle (QGIS-Auswertung)',
    stufe: 'mittel',
    grund: 'Eigene Auswertung, Rohdatenquelle noch nicht dokumentiert',
  },
  mapboxFahrzeit: {
    name: 'Mapbox Isochrone API (Fahrzeit mit dem Auto)',
    stufe: 'hoch',
    grund: 'Routing auf dem aktuellen Straßennetz bei typischem Verkehr',
  },
  johnDeereStandorte: {
    name: 'John-Deere-Standorte (Beispieldaten)',
    stufe: 'niedrig',
    grund: 'Aus öffentlichen Quellen zusammengestellt, nicht geprüft',
  },
  crmTestkunden: {
    name: 'CRM-Export (fiktive Testkunden)',
    stufe: 'niedrig',
    grund: 'Testdaten, kein echter CRM-Export',
  },
} as const satisfies Record<string, Datenquelle>

export const OHNE_DATEN = 'Keine belastbaren Daten vorhanden'

/** Eine bekannte Quelle, die für diese Stelle keinen Wert hat, z. B. eine H3-Zelle ohne Flächenwert. */
export function ohneWert(quelle: Datenquelle, grund: string): Datenquelle {
  return { ...quelle, stufe: 'unbekannt', grund }
}

const RANG: Record<Konfidenzstufe, number> = { unbekannt: 0, niedrig: 1, mittel: 2, hoch: 3 }

/**
 * Stufe einer Empfehlung, die sich auf mehrere Quellen stützt: Die schwächste Quelle bestimmt die Stufe.
 * Ohne Quelle oder mit einer Quelle ohne belastbare Daten ist sie „unbekannt“.
 */
export function gesamtstufe(quellen: readonly Datenquelle[]): Konfidenzstufe {
  if (quellen.length === 0) return 'unbekannt'
  return quellen.reduce<Konfidenzstufe>(
    (schwaechste, quelle) => (RANG[quelle.stufe] < RANG[schwaechste] ? quelle.stufe : schwaechste),
    'hoch',
  )
}

export type KonfidenzAnzeige = Readonly<{
  stufe: Konfidenzstufe
  /** gefüllte Balken von 3; „unbekannt“ hat keinen */
  balken: number
  /** Datenquellen, bei mehreren je mit eigener Stufe */
  quellen: readonly Datenquelle[]
  /** Erklärung unter der Stufe */
  grund: string
}>

/** Was angezeigt wird. Die React-Komponente und die DOM-Variante für Kartenpopups nutzen beide diese Funktion. */
export function konfidenzAnzeige(quellen: readonly Datenquelle[], ohneDaten = OHNE_DATEN): KonfidenzAnzeige {
  const stufe = gesamtstufe(quellen)
  let grund: string
  if (quellen.length === 0) grund = ohneDaten
  else if (quellen.length === 1) grund = quellen[0].grund
  else if (stufe === 'unbekannt') grund = 'Für mindestens eine Quelle fehlen belastbare Daten'
  else grund = 'Die schwächste Quelle bestimmt die Stufe'
  return { stufe, balken: RANG[stufe], quellen, grund }
}
