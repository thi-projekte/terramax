import mapboxgl from 'mapbox-gl'

export function addCustomerPopups(map: mapboxgl.Map) {
  map.on('click', 'customers-circles', (event) => {
    const customer = event.features?.[0]
    if (!customer || customer.geometry.type !== 'Point') return

    const props = customer.properties ?? {}
    const coordinates = customer.geometry.coordinates as [number, number]
    const box = document.createElement('div')
    box.style.cssText = 'color:#222;padding:6px;text-align:center;min-width:160px'

    const title = document.createElement('strong')
    title.textContent = String(props.name ?? 'Kunde')

    const id = document.createElement('p')
    id.textContent = 'Kunden-ID: ' + String(props.id ?? '')

    const cell = document.createElement('p')
    cell.style.fontSize = '11px'
    cell.textContent = 'H3-Zelle: ' + String(props.h3Index ?? '')

    box.append(title, id, cell)

    const url = String(props.crmUrl ?? '')
    if (/^https?:\/\//.test(url)) {
      const link = document.createElement('a')
      link.href = url
      link.target = '_blank'
      link.rel = 'noopener noreferrer'
      link.textContent = 'Im CRM \u00f6ffnen'
      box.append(link)
    } else {
      const none = document.createElement('p')
      none.textContent = 'Kein CRM-Link verf\u00fcgbar'
      box.append(none)
    }

    // Kurz warten, damit dieses Popup nach dem Hexagon-Popup oeffnet und oben liegt
    setTimeout(() => {
      new mapboxgl.Popup({ closeButton: true })
        .setLngLat(coordinates)
        .setDOMContent(box)
        .addTo(map)
    }, 0)
  })

  map.on('mouseenter', 'customers-circles', () => {
    map.getCanvas().style.cursor = 'pointer'
  })
  map.on('mouseleave', 'customers-circles', () => {
    map.getCanvas().style.cursor = ''
  })
}