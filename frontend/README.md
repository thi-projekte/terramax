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