package de.thi.terramax.process.besuchstour;

import de.thi.terramax.process.tour.TourPlan;
import de.thi.terramax.process.tour.TourPlanClient;
import org.cibseven.bpm.engine.delegate.DelegateExecution;
import org.cibseven.bpm.engine.delegate.JavaDelegate;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static de.thi.terramax.process.besuchstour.BesuchstourVariablen.BESUCHSERGEBNISSE;
import static de.thi.terramax.process.besuchstour.BesuchstourVariablen.BESUCHSPLAN;
import static de.thi.terramax.process.besuchstour.BesuchstourVariablen.TOUR_DATUM;

/**
 * Service Task "Besuchsplan erzeugen" ({@code ${besuchsplanErzeugen}}): holt den Plan vom tour-service (#29, Teil 2)
 * und legt ihn als {@code besuchsplan} ab. Über diese Liste läuft danach die Schleife "Kunden nacheinander besuchen".
 */
@Component("besuchsplanErzeugen")
public class BesuchsplanErzeugen implements JavaDelegate {

    private final TourPlanClient tourPlanClient;

    public BesuchsplanErzeugen(TourPlanClient tourPlanClient) {
        this.tourPlanClient = tourPlanClient;
    }

    @Override
    public void execute(DelegateExecution execution) {
        TourPlan plan = tourPlanClient.fetchPlan((String) execution.getVariable(TOUR_DATUM));

        List<Map<String, Object>> besuchsplan = new ArrayList<>();
        for (TourPlan.Visit visit : plan.visits()) {
            besuchsplan.add(eintrag(visit));
        }

        execution.setVariable(TOUR_DATUM, plan.date());
        execution.setVariable(BESUCHSPLAN, besuchsplan);
        execution.setVariable(BESUCHSERGEBNISSE, new ArrayList<Map<String, Object>>());
    }

    private static Map<String, Object> eintrag(TourPlan.Visit visit) {
        TourPlan.Customer customer = visit.customer();
        Map<String, Object> eintrag = new LinkedHashMap<>();
        eintrag.put("position", visit.position());
        eintrag.put("kundeId", customer.id());
        eintrag.put("name", customer.name());
        eintrag.put("ort", customer.location());
        eintrag.put("latitude", customer.position() == null ? null : customer.position().latitude());
        eintrag.put("longitude", customer.position() == null ? null : customer.position().longitude());
        eintrag.put("letzterBesuch", customer.lastVisit());
        eintrag.put("entfernungKm", visit.distanceFromPreviousKm());
        return eintrag;
    }
}
