package de.thi.terramax.process.besuchstour;

import de.thi.terramax.process.terramax.TerraMaxClient;
import de.thi.terramax.process.terramax.TerraMaxFachlicherFehler;
import org.cibseven.bpm.engine.delegate.DelegateExecution;
import org.cibseven.bpm.engine.delegate.JavaDelegate;
import org.springframework.stereotype.Component;

import static de.thi.terramax.process.besuchstour.BesuchstourFehler.BESUCHSABSCHLUSS_ABGELEHNT;
import static de.thi.terramax.process.besuchstour.BesuchstourVariablen.BESUCH_ABGESCHLOSSEN_AM;
import static de.thi.terramax.process.besuchstour.BesuchstourVariablen.BESUCH_BEGONNEN_AM;

/**
 * Service Task "Protokoll dem Kunden zuordnen" ({@code ${protokollZuordnen}}). Läuft erst nach der Bestätigung durch
 * den Sales Professional (Akzeptanzkriterium #29).
 * <p>
 * Das Protokoll selbst speichert der speech-service in TerraMax ({@code POST /tours/{tourId}/protocols}); die Engine
 * legt keines an. Sie schließt nur den Halt ab: {@code start} mit {@code besuchBegonnenAm}, dann {@code complete} mit
 * {@code besuchAbgeschlossenAm}. Beide Aufrufe sind idempotent. Fachlicher Fehler von TerraMax: BPMN-Fehler
 * {@code BESUCHSABSCHLUSS_ABGELEHNT}.
 */
@Component("protokollZuordnen")
public class ProtokollZuordnen implements JavaDelegate {

    private final TerraMaxClient terraMax;

    public ProtokollZuordnen(TerraMaxClient terraMax) {
        this.terraMax = terraMax;
    }

    @Override
    public void execute(DelegateExecution execution) {
        long tourId = BesuchstourVariablen.tourId(execution);
        long kundeId = BesuchstourVariablen.kundeId(execution);
        try {
            terraMax.besuchBeginnen(tourId, kundeId, BesuchstourVariablen.text(execution, BESUCH_BEGONNEN_AM));
            terraMax.besuchAbschliessen(tourId, kundeId,
                    BesuchstourVariablen.text(execution, BESUCH_ABGESCHLOSSEN_AM));
        } catch (TerraMaxFachlicherFehler e) {
            throw BesuchstourFehler.bpmnFehler(BESUCHSABSCHLUSS_ABGELEHNT, e);
        }
    }
}
