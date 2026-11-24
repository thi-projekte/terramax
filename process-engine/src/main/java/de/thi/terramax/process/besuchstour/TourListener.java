package de.thi.terramax.process.besuchstour;

import de.thi.terramax.process.terramax.TerraMaxClient;
import org.cibseven.bpm.engine.delegate.DelegateExecution;
import org.cibseven.bpm.engine.delegate.ExecutionListener;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static de.thi.terramax.process.besuchstour.BesuchstourVariablen.ABBRUCH_GRUND;
import static de.thi.terramax.process.besuchstour.BesuchstourVariablen.BESUCHSPLAN;
import static de.thi.terramax.process.besuchstour.BesuchstourVariablen.GELADENE_DATEN;
import static de.thi.terramax.process.besuchstour.BesuchstourVariablen.KUNDE;
import static de.thi.terramax.process.besuchstour.BesuchstourVariablen.PLAN_BESTAETIGT;
import static de.thi.terramax.process.besuchstour.BesuchstourVariablen.TOURUEBERSICHT;

/**
 * ExecutionListener der Besuchstour, im Modell eingebunden per {@code delegateExpression} (Spring-Beans, damit der
 * TerraMax-Client injiziert wird).
 * <p>
 * <b>Laden per ID (Lazy Loading):</b> Am {@code start} einer User Task lädt ein Listener nur die Daten, die genau diese
 * Aufgabe anzeigt, per {@code tourId} aus TerraMax und setzt sie als lokale Variable. Am {@code end} entfernt
 * {@code geladeneDatenEntfernen} sie wieder. So steht in der Engine nie eine veraltete Kopie der Tour.
 * <p>
 * <b>Fehler:</b> Schlägt ein Aufruf fehl, läuft die Exception durch und die Transaktion wird zurückgerollt. Der Nutzer
 * bekommt beim Abschließen der vorigen Aufgabe (bzw. beim Abbrechen) eine Fehlermeldung, der Prozess bleibt im letzten
 * Wartezustand und der Schritt kann wiederholt werden. Alle Schreibaufrufe an TerraMax sind idempotent.
 */
@Configuration
public class TourListener {

    private static final Set<String> ENDGUELTIGE_STATUS = Set.of("completed", "discarded", "aborted");

    /** {@code start} an "Besuchsplan prüfen": Kunden der Tour. */
    @Bean
    ExecutionListener besuchsplanLaden(Tourdaten tourdaten) {
        return execution -> execution.setVariableLocal(BESUCHSPLAN,
                tourdaten.besuchsplan(BesuchstourVariablen.tourId(execution)));
    }

    /**
     * {@code end} an "Besuchsplan prüfen": übernimmt die Änderungen des Sales Professional (gestrichene Kunden,
     * Reihenfolge) nach TerraMax und setzt {@code kundenIds} und {@code anzahlHalte} neu. Verwirft er die Tour, ändert
     * sich nichts (der Status folgt am Ende "Tour verworfen").
     */
    @Bean
    ExecutionListener besuchsplanUebernehmen(TerraMaxClient terraMax) {
        return execution -> {
            if (!Boolean.TRUE.equals(execution.getVariable(PLAN_BESTAETIGT))) {
                return;
            }
            long tourId = BesuchstourVariablen.tourId(execution);
            List<Long> bisher = terraMax.tour(tourId).kundenIds();
            List<Long> gewuenscht = gewuenschteReihenfolge(execution, bisher);

            for (Long kundeId : bisher) {
                if (!gewuenscht.contains(kundeId)) {
                    terraMax.haltEntfernen(tourId, kundeId);
                }
            }
            if (!gewuenscht.isEmpty() && !gewuenscht.equals(bleibende(bisher, gewuenscht))) {
                terraMax.reihenfolgeAendern(tourId, gewuenscht);
            }
            BesuchstourVariablen.halteSetzen(execution, terraMax.tour(tourId).kundenIds());
        };
    }

    /** {@code start} an den User Tasks eines Besuchs: Besuchsplan (für "Kunde x von n") und aktueller Kunde. */
    @Bean
    ExecutionListener kundeLaden(Tourdaten tourdaten) {
        return execution -> {
            List<Map<String, Object>> plan = tourdaten.besuchsplan(BesuchstourVariablen.tourId(execution));
            execution.setVariableLocal(BESUCHSPLAN, plan);
            execution.setVariableLocal(KUNDE, tourdaten.kunde(plan, BesuchstourVariablen.kundeId(execution)));
        };
    }

    /** {@code start} an "Tour abschließen": Tourübersicht aus TerraMax. */
    @Bean
    ExecutionListener touruebersichtLaden(Tourdaten tourdaten) {
        return execution -> execution.setVariableLocal(TOURUEBERSICHT,
                tourdaten.touruebersicht(BesuchstourVariablen.tourId(execution)));
    }

    /** {@code end} an jeder User Task mit geladenen Daten: entfernt sie wieder aus dem Prozess. */
    @Bean
    ExecutionListener geladeneDatenEntfernen() {
        return execution -> GELADENE_DATEN.forEach(execution::removeVariable);
    }

    /** {@code end} an "Tour abschließen": Tour in TerraMax auf {@code completed}, außer sie ist schon beendet. */
    @Bean
    ExecutionListener tourAbschliessen(TerraMaxClient terraMax) {
        return execution -> {
            long tourId = BesuchstourVariablen.tourId(execution);
            if (!ENDGUELTIGE_STATUS.contains(terraMax.tour(tourId).status())) {
                terraMax.tourStatusSetzen(tourId, "completed");
            }
        };
    }

    /** {@code start} am Ende "Tour verworfen": Tour in TerraMax auf {@code discarded}. */
    @Bean
    ExecutionListener tourVerwerfen(TerraMaxClient terraMax) {
        return execution -> {
            Long tourId = BesuchstourVariablen.tourIdOderNull(execution);
            if (tourId != null) {
                terraMax.tourStatusSetzen(tourId, "discarded");
            }
        };
    }

    /** {@code take} am Fluss nach "Tour abgebrochen": Tour in TerraMax abbrechen, optional mit Grund. */
    @Bean
    ExecutionListener tourAbbrechen(TerraMaxClient terraMax) {
        return execution -> terraMax.tourAbbrechen(BesuchstourVariablen.tourId(execution),
                BesuchstourVariablen.text(execution, ABBRUCH_GRUND));
    }

    /**
     * Reihenfolge aus dem vom Frontend zurückgeschickten {@code besuchsplan}. Nur Kunden, die in TerraMax auf der Tour
     * stehen; fehlt der Plan, bleibt alles wie es ist.
     */
    static List<Long> gewuenschteReihenfolge(DelegateExecution execution, List<Long> bisher) {
        if (!(execution.getVariable(BESUCHSPLAN) instanceof List<?> eintraege)) {
            return bisher;
        }
        List<Long> reihenfolge = new ArrayList<>();
        Set<Long> gesehen = new HashSet<>();
        for (Object eintrag : eintraege) {
            if (eintrag instanceof Map<?, ?> kunde) {
                Long kundeId = BesuchstourVariablen.alsLong(kunde.get("kundeId"), "besuchsplan[].kundeId");
                if (kundeId != null && bisher.contains(kundeId) && gesehen.add(kundeId)) {
                    reihenfolge.add(kundeId);
                }
            }
        }
        return reihenfolge;
    }

    private static List<Long> bleibende(List<Long> bisher, List<Long> gewuenscht) {
        return bisher.stream().filter(gewuenscht::contains).toList();
    }
}
