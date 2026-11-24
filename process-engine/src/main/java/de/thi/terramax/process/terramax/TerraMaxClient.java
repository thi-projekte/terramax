package de.thi.terramax.process.terramax;

import java.util.List;

/**
 * Aufrufe der Engine an den terramax-service. TerraMax ist die Single Source of Truth für alle Tourdaten; die Engine
 * hält im Prozess nur die {@code tourId}, Kunden-IDs und Flags für Gateways (siehe
 * docs/process/terramax-engine-schnittstelle.md).
 * <p>
 * Fehler: {@link TerraMaxFachlicherFehler} bei 400/404/409 (der Aufruf passt nicht zum Stand der Tour, Wiederholen
 * hilft nicht), {@link TerraMaxTechnischerFehler} bei allem anderen (Service nicht erreichbar, 5xx, Token fehlt oder
 * reicht nicht). Eigene Schnittstelle, damit Tests ohne laufenden terramax-service auskommen.
 */
public interface TerraMaxClient {

    /**
     * {@code GET /tours/plan}: Besuchsplan für einen Tag (nur mit Token eines Sales Professional).
     *
     * @param tourDatum
     *            Tag im Format JJJJ-MM-TT; {@code null} bedeutet heute
     */
    TourPlan planHolen(String tourDatum);

    /**
     * {@code POST /tours}: legt die Tour an. Idempotent über die Prozessinstanz.
     */
    Tour tourAnlegen(String tourDatum, List<Long> kundenIds, String processInstanceId);

    /** {@code GET /tours/{id}}: Tour mit Halten in Besuchsreihenfolge. */
    Tour tour(long tourId);

    /** {@code GET /tours/{id}/overview}: Tourübersicht mit Status, Grund und Protokoll-IDs je Halt. */
    Touruebersicht uebersicht(long tourId);

    /** {@code DELETE /tours/{id}/stops/{customerId}}: Kunden aus einer geplanten Tour streichen. */
    void haltEntfernen(long tourId, long kundeId);

    /** {@code PATCH /tours/{id}} mit {@code customerOrder}: neue Reihenfolge der Halte. */
    void reihenfolgeAendern(long tourId, List<Long> kundenIds);

    /** {@code PATCH /tours/{id}} mit {@code status} ({@code completed} oder {@code discarded}). */
    void tourStatusSetzen(long tourId, String status);

    /** {@code POST /tours/{id}/abort}: Tour abbrechen. */
    void tourAbbrechen(long tourId, String grund);

    /** {@code POST /tours/{id}/stops/{customerId}/start}; {@code zeitpunkt} darf {@code null} sein (jetzt). */
    void besuchBeginnen(long tourId, long kundeId, String zeitpunkt);

    /** {@code POST /tours/{id}/stops/{customerId}/skip}; Grund ist Pflicht. */
    void besuchUeberspringen(long tourId, long kundeId, String grund);

    /** {@code POST /tours/{id}/stops/{customerId}/complete}; {@code zeitpunkt} darf {@code null} sein (jetzt). */
    void besuchAbschliessen(long tourId, long kundeId, String zeitpunkt);

    /** Antwort von {@code GET /tours/{id}} (nur die Felder, die die Engine braucht). */
    record Tour(long id, String tourDate, String status, List<Halt> customers) {

        public List<Long> kundenIds() {
            return customers.stream().map(Halt::customerId).toList();
        }
    }

    /** Ein Halt der Tour; {@code region} und {@code lastVisit} (JJJJ-MM-TT, null wenn nie besucht) für die Anzeige. */
    record Halt(long customerId, String name, int position, String status, String skipReason, Double latitude,
            Double longitude, String region, String lastVisit) {
    }

    /** Antwort von {@code GET /tours/{id}/overview}. */
    record Touruebersicht(long tourId, String tourDatum, String tourStatus, String abbruchGrund, List<Besuch> besuche,
            int anzahlDokumentiert, int anzahlUebersprungen, int anzahlOffen) {
    }

    /** Ein Halt in der Tourübersicht. */
    record Besuch(int position, long kundeId, String name, String ort, String status, String grund,
            String besuchBegonnenAm, String besuchAbgeschlossenAm, List<Long> protokollIds) {
    }
}
