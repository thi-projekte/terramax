# TerraMax – Interaktive H3-Agrarmarktkarte

## Projektbeschreibung

Im Rahmen von GitHub-Ticket #17 wurde eine interaktive Agrarmarktkarte für Österreich entwickelt.

Die Anwendung visualisiert Weizen- und Sojabohnenflächen auf Basis offener Agrardaten in einem H3-Raster mit Auflösung 6.

## Funktionen

- Interaktive Karte mit Mapbox GL JS
- 3D-Globus mit automatischem Kameraflug nach Österreich
- H3-Raster für Österreich mit Auflösung 6
- Fünf Farbklassen zur Darstellung der Anbauflächen in Hektar
- Legende zur Erklärung der Farbklassen
- Anklickbare H3-Zellen mit Flächenwert, H3-ID und Datenquelle
- Kennzeichnung fehlender Flächenwerte als „unbekannt“

## Verwendete Technologien

- React und TypeScript
- Vite
- Mapbox GL JS
- H3
- QGIS zur Geodatenverarbeitung

## Installation und Start

Voraussetzung: Node.js und npm sind installiert.

**1. Repository klonen und Frontend-Ordner öffnen**

```bash
git clone https://github.com/thi-projekte/terramax.git
cd terramax/frontend
```

Hinweis: Solange der Pull Request nicht in `main` übernommen wurde, muss der Branch `feature/17-h3-market-map` ausgewählt werden.

**2. Abhängigkeiten installieren**

```bash
npm install
```

**3. Mapbox-Token konfigurieren**

Im Ordner `frontend` eine Datei namens `.env` erstellen:

```env
VITE_MAPBOX_TOKEN=DEIN_MAPBOX_ACCESS_TOKEN
```

Ein gültiger Mapbox-Access-Token wird benötigt. Die `.env`-Datei wird nicht auf GitHub hochgeladen.

**Optional: Adresse des location-service**

Die Händlerstandorte (Wettbewerber, Mehrmarken/unbekannt, John Deere) lädt die Karte vom
`location-service` über `GET /locations` mit dem Keycloak-Token des angemeldeten Nutzers.

```env
VITE_LOCATION_SERVICE_URL=http://localhost:8082
```

Ohne die Variable wird `http://localhost:8082` verwendet. Lokale Werte gehören in `.env.local`
(wird nicht eingecheckt). Ist der Service nicht erreichbar oder lehnt er den Token ab (401),
lädt die Karte trotzdem; in der Legende erscheint dann „Standorte konnten nicht geladen werden“,
die Ursache steht in der Browser-Konsole. Der `location-service` muss den Frontend-Origin in
`CORS_ORIGINS` erlauben (Standard `http://localhost:5173`).

**4. Entwicklungsserver starten**

```bash
npm run dev
```

Anschließend die im Terminal angezeigte lokale Adresse öffnen, normalerweise `http://localhost:5173/`.

## Datengrundlage und Berechnung

- **Quelle:** INVEKOS Österreich 2024
- **Kulturarten:** Winter- und Sommerweizen einschließlich Durumweizen sowie Sojabohnen
- **Geografisches Gebiet:** Österreich
- **H3-Auflösung:** 6
- **Verarbeitung:** QGIS

Die landwirtschaftlichen Polygone wurden nach Kulturarten gefiltert, geometrisch verarbeitet und mit dem H3-Raster verschnitten. Anschließend wurden die Flächen in Hektar berechnet und nach H3-Zelle aggregiert.

Die aufbereiteten Daten liegen im Verzeichnis `frontend/public/` als GeoJSON- und CSV-Dateien vor.

## Einschränkungen

Die Karte stellt derzeit ausschließlich Weizen- und Sojabohnenflächen dar, nicht die gesamte landwirtschaftliche Ackerfläche Österreichs.

Zellen ohne zugeordneten Flächenwert werden als „unbekannt“ angezeigt. Diese Darstellung darf nicht automatisch als 0 ha interpretiert werden.

Die Auswertung ist vorläufig und dient als MVP für die geografische Visualisierung landwirtschaftlicher Marktpotenziale.

## GitHub

- **Ticket:** #17
- **Pull Request:** #30
- **Branch:** `feature/17-h3-market-map`

## Deployment

Das Image `ghcr.io/thi-projekte/terramax-frontend` wird bei jedem Push auf `main` gebaut
(`.github/workflows/frontend.yml`). Da Vite `VITE_*` beim Build ins JS schreibt, werden die
URLs als Build-Args übergeben. Sie kommen aus den GitHub-Repository-Variablen
(Settings → Secrets and variables → Actions → Variables):

| Variable | Standard, wenn nicht gesetzt |
|---|---|
| `VITE_MAPBOX_TOKEN` | – (Pflicht) |
| `VITE_KEYCLOAK_URL` | `https://terramax-keycloak.winfprojekt.de` |
| `VITE_API_URL` | `http://localhost:8080` |
| `VITE_LOCATION_SERVICE_URL` | `http://localhost:8082` |
| `VITE_ENGINE_URL` | `http://localhost:8086/engine-rest` |

Betrieb: `docker-compose.yml` in diesem Ordner als Repository-Stack in Portainer
(ohne `ports:`, Netzwerk `nginx-proxy-manager`); der Nginx Proxy Manager leitet die Subdomain auf
den Service `frontend`, Port 80.

### Neuen Backend-Service anbinden

Die Service-URLs stehen nicht im Code, sondern werden beim Build ins Frontend geschrieben.
Wenn ein Service auf dem Server läuft, reicht der Merge nach `main` deshalb nicht. Zusätzlich:

1. **Repository-Variable setzen** (Settings → Secrets and variables → Actions → Variables,
   Admin-Rechte nötig) mit der öffentlichen URL des Service:
   `VITE_API_URL` (hello-service), `VITE_LOCATION_SERVICE_URL` (location-service) oder
   `VITE_ENGINE_URL` (Engine, inkl. `/engine-rest`).
2. **Frontend neu bauen.** Der Workflow läuft automatisch nur bei Änderungen unter `frontend/`.
   Also manuell starten: Actions → „TerraMax Frontend CI“ → „Run workflow“ auf `main`
   (oder `gh workflow run frontend.yml --ref main`).
3. **Stack in Portainer neu deployen**, damit das neue Image gezogen wird.
4. **CORS freigeben:** Der Service muss die Frontend-Domain in `CORS_ORIGINS` erlauben,
   nicht nur `http://localhost:5173`. Sonst blockiert der Browser die Anfragen.
