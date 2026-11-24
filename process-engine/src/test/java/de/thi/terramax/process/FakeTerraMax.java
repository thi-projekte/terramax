package de.thi.terramax.process;

import de.thi.terramax.process.terramax.TerraMaxClient;
import de.thi.terramax.process.terramax.TerraMaxFachlicherFehler;
import de.thi.terramax.process.terramax.TerraMaxTechnischerFehler;
import de.thi.terramax.process.terramax.TourPlan;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicLong;

/**
 * terramax-service im Speicher für die Tests. Bildet die Statusregeln aus docs/process/terramax-engine-schnittstelle.md
 * nach (409 bei ungültigen Übergängen, Idempotenz), damit die Tests auch die Fehlerpfade des Modells prüfen.
 * <p>
 * Einbinden mit {@code @Import(FakeTerraMax.Config.class)}; ersetzt den {@code RestTerraMaxClient}.
 */
public class FakeTerraMax implements TerraMaxClient {

    @TestConfiguration
    public static class Config {
        @Bean
        @Primary
        FakeTerraMax fakeTerraMax() {
            return new FakeTerraMax();
        }
    }

    private static final Set<String> ENDGUELTIG = Set.of("completed", "discarded", "aborted");

    private final Map<Long, FakeTour> touren = new LinkedHashMap<>();
    private final Map<Long, String> letzteBesuche = new HashMap<>();
    private final List<String> aufrufe = new ArrayList<>();
    private final AtomicLong naechsteId = new AtomicLong(100);
    private TourPlan plan = planMitKunden(3);
    private String fachlicherFehlerBei;
    private boolean nichtErreichbar;

    /** Plan mit {@code anzahl} Kunden 1 … n für den 10.11.2026, wie {@code GET /tours/plan} ihn liefert. */
    public static TourPlan planMitKunden(int anzahl) {
        List<TourPlan.Visit> visits = new ArrayList<>();
        for (int i = 1; i <= anzahl; i++) {
            visits.add(new TourPlan.Visit(i, new TourPlan.Customer(String.valueOf(i), "Betrieb " + i, "Ort " + i,
                    new TourPlan.Position(48.0 + i / 10.0, 14.0), null), 5.0 * i));
        }
        return new TourPlan("2026-11-10", visits, 5.0 * anzahl);
    }

    // --- Steuerung aus den Tests ---

    public void reset() {
        touren.clear();
        letzteBesuche.clear();
        aufrufe.clear();
        plan = planMitKunden(3);
        fachlicherFehlerBei = null;
        nichtErreichbar = false;
    }

    public void planSetzen(TourPlan plan) {
        this.plan = plan;
    }

    /** Der nächste Aufruf dieser Methode antwortet mit 409. */
    public void fachlicherFehlerBei(String methode) {
        this.fachlicherFehlerBei = methode;
    }

    public void nichtErreichbar(boolean nichtErreichbar) {
        this.nichtErreichbar = nichtErreichbar;
    }

    /** Datum des letzten Besuchs, das {@code GET /tours/{id}} für diesen Kunden liefert. */
    public void letzterBesuch(long kundeId, String datum) {
        letzteBesuche.put(kundeId, datum);
    }

    public List<String> aufrufe() {
        return aufrufe;
    }

    public FakeTour einzigeTour() {
        if (touren.size() != 1) {
            throw new IllegalStateException("Erwartet genau eine Tour, gefunden " + touren.size());
        }
        return touren.values().iterator().next();
    }

    public int anzahlTouren() {
        return touren.size();
    }

    // --- TerraMaxClient ---

    @Override
    public TourPlan planHolen(String tourDatum) {
        pruefen("planHolen");
        return plan;
    }

    @Override
    public Tour tourAnlegen(String tourDatum, List<Long> kundenIds, String processInstanceId) {
        pruefen("tourAnlegen");
        for (FakeTour tour : touren.values()) {
            if (processInstanceId.equals(tour.processInstanceId)) {
                return tour.alsTour(letzteBesuche);
            }
        }
        FakeTour tour = new FakeTour(naechsteId.getAndIncrement(), tourDatum, processInstanceId);
        kundenIds.forEach(id -> tour.halte.add(new FakeHalt(id)));
        touren.put(tour.id, tour);
        return tour.alsTour(letzteBesuche);
    }

    @Override
    public Tour tour(long tourId) {
        pruefen("tour");
        return tour404(tourId).alsTour(letzteBesuche);
    }

    @Override
    public Touruebersicht uebersicht(long tourId) {
        pruefen("uebersicht");
        FakeTour tour = tour404(tourId);
        List<Besuch> besuche = new ArrayList<>();
        int position = 1;
        for (FakeHalt halt : tour.halte) {
            besuche.add(new Besuch(position++, halt.kundeId, "Betrieb " + halt.kundeId, "Ort " + halt.kundeId,
                    halt.status, halt.grund, halt.begonnenAm, halt.abgeschlossenAm, List.of()));
        }
        return new Touruebersicht(tour.id, tour.datum, tour.status, tour.abbruchGrund, besuche, anzahl(tour,
                "DOKUMENTIERT"), anzahl(tour, "UEBERSPRUNGEN"), anzahl(tour, "OFFEN"));
    }

    @Override
    public void haltEntfernen(long tourId, long kundeId) {
        pruefen("haltEntfernen");
        FakeTour tour = tour404(tourId);
        FakeHalt halt = halt404(tour, kundeId);
        if (!"planned".equals(tour.status) || !"OFFEN".equals(halt.status)) {
            throw konflikt("Halt kann nicht mehr gestrichen werden");
        }
        tour.halte.remove(halt);
    }

    @Override
    public void reihenfolgeAendern(long tourId, List<Long> kundenIds) {
        pruefen("reihenfolgeAendern");
        FakeTour tour = tour404(tourId);
        if (kundenIds.size() != tour.halte.size() || !tour.kundenIds().containsAll(kundenIds)) {
            throw new TerraMaxFachlicherFehler(400, "customerOrder muss genau die Kunden der Tour enthalten");
        }
        List<FakeHalt> neu = kundenIds.stream().map(id -> halt404(tour, id)).toList();
        tour.halte.clear();
        tour.halte.addAll(neu);
    }

    @Override
    public void tourStatusSetzen(long tourId, String status) {
        pruefen("tourStatusSetzen:" + status);
        FakeTour tour = tour404(tourId);
        if (ENDGUELTIG.contains(tour.status) && !tour.status.equals(status)) {
            throw konflikt("Tour ist schon " + tour.status);
        }
        tour.status = status;
    }

    @Override
    public void tourAbbrechen(long tourId, String grund) {
        pruefen("tourAbbrechen");
        FakeTour tour = tour404(tourId);
        if ("completed".equals(tour.status) || "discarded".equals(tour.status)) {
            throw konflikt("Tour ist schon " + tour.status);
        }
        tour.status = "aborted";
        tour.abbruchGrund = grund;
    }

    @Override
    public void besuchBeginnen(long tourId, long kundeId, String zeitpunkt) {
        pruefen("besuchBeginnen:" + kundeId);
        FakeHalt halt = offenerHalt(tourId, kundeId);
        if (!"OFFEN".equals(halt.status)) {
            throw konflikt("Halt ist schon " + halt.status);
        }
        if (halt.begonnenAm == null) {
            halt.begonnenAm = zeitpunkt;
        }
    }

    @Override
    public void besuchUeberspringen(long tourId, long kundeId, String grund) {
        pruefen("besuchUeberspringen:" + kundeId);
        FakeHalt halt = offenerHalt(tourId, kundeId);
        if ("UEBERSPRUNGEN".equals(halt.status) && grund.equals(halt.grund)) {
            return;
        }
        if (!"OFFEN".equals(halt.status)) {
            throw konflikt("Halt ist schon " + halt.status);
        }
        halt.status = "UEBERSPRUNGEN";
        halt.grund = grund;
    }

    @Override
    public void besuchAbschliessen(long tourId, long kundeId, String zeitpunkt) {
        pruefen("besuchAbschliessen:" + kundeId);
        FakeHalt halt = offenerHalt(tourId, kundeId);
        if ("DOKUMENTIERT".equals(halt.status)) {
            return;
        }
        if (!"OFFEN".equals(halt.status)) {
            throw konflikt("Halt ist schon " + halt.status);
        }
        halt.status = "DOKUMENTIERT";
        halt.abgeschlossenAm = zeitpunkt;
    }

    // --- intern ---

    private void pruefen(String aufruf) {
        aufrufe.add(aufruf);
        if (nichtErreichbar) {
            throw new TerraMaxTechnischerFehler(0, "terramax-service nicht erreichbar (Test)", null);
        }
        String methode = aufruf.contains(":") ? aufruf.substring(0, aufruf.indexOf(':')) : aufruf;
        if (methode.equals(fachlicherFehlerBei)) {
            fachlicherFehlerBei = null;
            throw konflikt("Test: " + methode + " abgelehnt");
        }
    }

    private FakeHalt offenerHalt(long tourId, long kundeId) {
        FakeTour tour = tour404(tourId);
        if (ENDGUELTIG.contains(tour.status)) {
            throw konflikt("Tour ist schon " + tour.status);
        }
        FakeHalt halt = halt404(tour, kundeId);
        if ("planned".equals(tour.status)) {
            tour.status = "in_progress";
        }
        return halt;
    }

    private FakeTour tour404(long tourId) {
        FakeTour tour = touren.get(tourId);
        if (tour == null) {
            throw new TerraMaxFachlicherFehler(404, "Tour " + tourId + " unbekannt");
        }
        return tour;
    }

    private static FakeHalt halt404(FakeTour tour, long kundeId) {
        return tour.halte.stream().filter(h -> h.kundeId == kundeId).findFirst()
                .orElseThrow(() -> new TerraMaxFachlicherFehler(404, "Halt " + kundeId + " unbekannt"));
    }

    private static TerraMaxFachlicherFehler konflikt(String message) {
        return new TerraMaxFachlicherFehler(409, message);
    }

    private static int anzahl(FakeTour tour, String status) {
        return (int) tour.halte.stream().filter(h -> h.status.equals(status)).count();
    }

    /** Tour im Speicher; Felder sind für Prüfungen in den Tests lesbar. */
    public static final class FakeTour {
        public final long id;
        public final String datum;
        public final String processInstanceId;
        public final List<FakeHalt> halte = new ArrayList<>();
        public String status = "planned";
        public String abbruchGrund;

        FakeTour(long id, String datum, String processInstanceId) {
            this.id = id;
            this.datum = datum;
            this.processInstanceId = processInstanceId;
        }

        public List<Long> kundenIds() {
            return halte.stream().map(h -> h.kundeId).toList();
        }

        public List<String> statusJeHalt() {
            return halte.stream().map(h -> h.status).toList();
        }

        Tour alsTour(Map<Long, String> letzteBesuche) {
            List<Halt> customers = new ArrayList<>();
            int position = 1;
            for (FakeHalt halt : halte) {
                customers.add(new Halt(halt.kundeId, "Betrieb " + halt.kundeId, position++, halt.status, halt.grund,
                        48.0 + halt.kundeId / 10.0, 14.0, "Ort " + halt.kundeId, letzteBesuche.get(halt.kundeId)));
            }
            return new Tour(id, datum, status, customers);
        }
    }

    /** Halt im Speicher. */
    public static final class FakeHalt {
        public final long kundeId;
        public String status = "OFFEN";
        public String grund;
        public String begonnenAm;
        public String abgeschlossenAm;

        FakeHalt(long kundeId) {
            this.kundeId = kundeId;
        }
    }
}
