// Holt fehlende Koordinaten fuer competitors_at.geojson ueber Nominatim (OpenStreetMap).
// Aufruf im Ordner frontend:  node geocode-missing.mjs public/competitors_at.geojson
// Nominatim erlaubt hoechstens 1 Anfrage pro Sekunde, deshalb dauert das ca. 1 Minute.
import { readFileSync, writeFileSync } from 'node:fs'

const file = process.argv[2] ?? 'public/competitors_at.geojson'
const USER_AGENT = 'TerraMax-Uniprojekt/0.1 (nis1824@thi.de)' // bitte eigene Mail eintragen
const sleep = (ms) => new Promise((r) => setTimeout(r, ms))
const search = async (params) => {
    const res = await fetch(`https://nominatim.openstreetmap.org/search?${params}`, {
        headers: { 'User-Agent': USER_AGENT }
    })
    return res.ok ? await res.json() : []
}

const data = JSON.parse(readFileSync(file, 'utf-8'))
const missing = data.features.filter((f) => f.geometry === null)
console.log(`${missing.length} Eintraege ohne Koordinaten`)

let found = 0
for (const f of missing) {
    const p = f.properties
    const params = new URLSearchParams({
        street: p.street, postalcode: p.postcode, city: p.city,
        country: 'Austria', format: 'json', limit: '1'
    })
    let hits = await search(params)
    if (hits.length === 0) {
        // 2. Versuch: Strasse vereinfachen ("Starrein 55 - Hahnweg" -> "Starrein 55", "Objekt A" weg)
        // und als freie Suche schicken. Bewusst KEIN Ausweichen auf die Ortsmitte, das waere ungenau.
        const street = p.street.split(/ [-–] |,| Objekt /)[0].trim()
        await sleep(1100)
        hits = await search(new URLSearchParams({
            q: `${street}, ${p.postcode} ${p.city}, Austria`, format: 'json', limit: '1'
        }))
    }
    if (hits.length > 0) {
        f.geometry = { type: 'Point', coordinates: [Number(hits[0].lon), Number(hits[0].lat)] }
        p.needs_geocoding = false
        p.geocoded_by = 'nominatim'
        found++
    } else {
        console.log(`  nicht gefunden: ${p.name} (${p.street}, ${p.postcode} ${p.city})`)
    }
    await sleep(1100)
}

writeFileSync(file, JSON.stringify(data, null, 1), 'utf-8')
console.log(`Fertig: ${found} von ${missing.length} gefunden, Datei gespeichert.`)