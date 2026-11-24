package de.thi.terramax.process.besuchstour;

import de.thi.terramax.process.terramax.TerraMaxClient;
import de.thi.terramax.process.terramax.TerraMaxClient.Besuch;
import de.thi.terramax.process.terramax.TerraMaxClient.Halt;
import de.thi.terramax.process.terramax.TerraMaxClient.Tour;
import de.thi.terramax.process.terramax.TerraMaxClient.Touruebersicht;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Lädt die Anzeigedaten einer Tour per {@code tourId} aus TerraMax und bringt sie in die Form, die das Frontend
 * erwartet ({@code Kunde} und {@code Touruebersicht} in frontend/src/tour/engineApi.ts). Wird nur von den
 * ExecutionListenern benutzt; nichts davon bleibt dauerhaft im Prozess.
 */
@Component
public class Tourdaten {

    private static final double ERDRADIUS_KM = 6371.0;

    private final TerraMaxClient terraMax;

    public Tourdaten(TerraMaxClient terraMax) {
        this.terraMax = terraMax;
    }

    /** Kunden der Tour in Besuchsreihenfolge, aus einem Aufruf {@code GET /tours/{id}}. */
    public List<Map<String, Object>> besuchsplan(long tourId) {
        Tour tour = terraMax.tour(tourId);
        List<Map<String, Object>> plan = new ArrayList<>();
        Halt vorheriger = null;
        for (Halt halt : tour.customers()) {
            plan.add(kunde(halt, vorheriger));
            vorheriger = halt;
        }
        return plan;
    }

    /** Ein Kunde aus dem Besuchsplan. */
    public Map<String, Object> kunde(List<Map<String, Object>> besuchsplan, long kundeId) {
        String id = String.valueOf(kundeId);
        return besuchsplan.stream().filter(k -> id.equals(k.get("kundeId"))).findFirst()
                .<Map<String, Object>> map(LinkedHashMap::new)
                .orElseThrow(() -> new IllegalStateException("Kunde " + kundeId + " ist nicht (mehr) auf der Tour"));
    }

    /** Tourübersicht mit denselben Feldnamen wie bisher; statt des Protokollinhalts {@code protokollIds}. */
    public Map<String, Object> touruebersicht(long tourId) {
        Touruebersicht uebersicht = terraMax.uebersicht(tourId);
        List<Map<String, Object>> besuche = new ArrayList<>();
        for (Besuch besuch : uebersicht.besuche()) {
            Map<String, Object> eintrag = new LinkedHashMap<>();
            eintrag.put("position", besuch.position());
            eintrag.put("kundeId", String.valueOf(besuch.kundeId()));
            eintrag.put("name", besuch.name());
            eintrag.put("ort", besuch.ort());
            eintrag.put("status", besuch.status());
            eintrag.put("grund", besuch.grund());
            eintrag.put("protokollIds", besuch.protokollIds() == null ? List.of() : besuch.protokollIds());
            besuche.add(eintrag);
        }
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("tourId", uebersicht.tourId());
        result.put("tourDatum", uebersicht.tourDatum());
        result.put("tourStatus", uebersicht.tourStatus());
        result.put("abbruchGrund", uebersicht.abbruchGrund());
        result.put("besuche", besuche);
        result.put("anzahlDokumentiert", uebersicht.anzahlDokumentiert());
        result.put("anzahlUebersprungen", uebersicht.anzahlUebersprungen());
        result.put("anzahlOffen", uebersicht.anzahlOffen());
        return result;
    }

    private static Map<String, Object> kunde(Halt halt, Halt vorheriger) {
        Map<String, Object> kunde = new LinkedHashMap<>();
        kunde.put("position", halt.position());
        kunde.put("kundeId", String.valueOf(halt.customerId()));
        kunde.put("name", halt.name());
        kunde.put("ort", halt.region());
        kunde.put("latitude", halt.latitude());
        kunde.put("longitude", halt.longitude());
        kunde.put("letzterBesuch", halt.lastVisit());
        kunde.put("entfernungKm", entfernungKm(vorheriger, halt));
        kunde.put("status", halt.status());
        return kunde;
    }

    /** Luftlinie zum vorherigen Halt in km (eine Nachkommastelle), {@code null} beim ersten Halt. */
    static Double entfernungKm(Halt von, Halt nach) {
        if (von == null || von.latitude() == null || von.longitude() == null || nach.latitude() == null
                || nach.longitude() == null) {
            return null;
        }
        double dLat = Math.toRadians(nach.latitude() - von.latitude());
        double dLon = Math.toRadians(nach.longitude() - von.longitude());
        double h = Math.sin(dLat / 2) * Math.sin(dLat / 2) + Math.cos(Math.toRadians(von.latitude()))
                * Math.cos(Math.toRadians(nach.latitude())) * Math.sin(dLon / 2) * Math.sin(dLon / 2);
        double km = 2 * ERDRADIUS_KM * Math.asin(Math.sqrt(h));
        return Math.round(km * 10) / 10.0;
    }
}
