package de.thi.terramax.process.besuchstour;

import de.thi.terramax.process.terramax.TerraMaxClient;
import de.thi.terramax.process.terramax.TerraMaxFachlicherFehler;
import de.thi.terramax.process.terramax.TourPlan;
import org.cibseven.bpm.engine.delegate.DelegateExecution;
import org.cibseven.bpm.engine.delegate.JavaDelegate;
import org.springframework.stereotype.Component;

import java.util.List;

import static de.thi.terramax.process.besuchstour.BesuchstourFehler.BESUCHSPLAN_FEHLGESCHLAGEN;
import static de.thi.terramax.process.besuchstour.BesuchstourVariablen.ANZAHL_HALTE;
import static de.thi.terramax.process.besuchstour.BesuchstourVariablen.TOUR_DATUM;
import static de.thi.terramax.process.besuchstour.BesuchstourVariablen.TOUR_ID;

/**
 * Service Task "Besuchsplan erzeugen" ({@code ${besuchsplanErzeugen}}).
 * <ol>
 * <li>{@code GET /tours/plan} beim terramax-service (Token des Sales Professional).</li>
 * <li>Bei mindestens zwei Kunden: {@code POST /tours} mit den Kunden-IDs und der Prozessinstanz (idempotent).</li>
 * </ol>
 * Im Prozess stehen danach nur {@code tourId}, {@code kundenIds} und {@code anzahlHalte}, nicht der Plan selbst.
 * Fachlicher Fehler von TerraMax: BPMN-Fehler {@code BESUCHSPLAN_FEHLGESCHLAGEN} (Ende "Keine Tour möglich").
 */
@Component("besuchsplanErzeugen")
public class BesuchsplanErzeugen implements JavaDelegate {

    /** Unter dieser Zahl gibt es keine Tour (Gateway "Mindestens zwei Kunden?"). */
    static final int MINDESTANZAHL_KUNDEN = 2;

    private final TerraMaxClient terraMax;

    public BesuchsplanErzeugen(TerraMaxClient terraMax) {
        this.terraMax = terraMax;
    }

    @Override
    public void execute(DelegateExecution execution) {
        try {
            TourPlan plan = terraMax.planHolen(BesuchstourVariablen.text(execution, TOUR_DATUM));
            List<Long> kundenIds = plan.visits().stream()
                    .map(visit -> BesuchstourVariablen.alsLong(visit.customer().id(), "customer.id")).toList();
            execution.setVariable(TOUR_DATUM, plan.date());

            if (kundenIds.size() < MINDESTANZAHL_KUNDEN) {
                // Keine Tour in TerraMax anlegen, die sofort wieder verworfen würde
                execution.setVariable(ANZAHL_HALTE, kundenIds.size());
                return;
            }

            TerraMaxClient.Tour tour = terraMax.tourAnlegen(plan.date(), kundenIds, execution.getProcessInstanceId());
            execution.setVariable(TOUR_ID, tour.id());
            BesuchstourVariablen.halteSetzen(execution, tour.kundenIds());
        } catch (TerraMaxFachlicherFehler e) {
            throw BesuchstourFehler.bpmnFehler(BESUCHSPLAN_FEHLGESCHLAGEN, e);
        }
    }
}
