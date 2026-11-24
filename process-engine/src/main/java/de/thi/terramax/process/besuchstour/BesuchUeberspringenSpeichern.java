package de.thi.terramax.process.besuchstour;

import de.thi.terramax.process.terramax.TerraMaxClient;
import de.thi.terramax.process.terramax.TerraMaxFachlicherFehler;
import org.cibseven.bpm.engine.delegate.DelegateExecution;
import org.cibseven.bpm.engine.delegate.JavaDelegate;
import org.springframework.stereotype.Component;

import static de.thi.terramax.process.besuchstour.BesuchstourFehler.UEBERSPRINGEN_ABGELEHNT;
import static de.thi.terramax.process.besuchstour.BesuchstourVariablen.UEBERSPRINGEN_GRUND;

/**
 * Service Task "Grund für Überspringen speichern" ({@code ${besuchUeberspringenSpeichern}}): {@code POST
 * /tours/{tourId}/stops/{kundeId}/skip}. Der Grund ist Pflicht (Akzeptanzkriterium #29); fehlt er, wird die Aufgabe
 * "Besuch vorbereiten" nicht abgeschlossen. Fachlicher Fehler von TerraMax (z. B. Halt schon erledigt oder Tour
 * abgebrochen): BPMN-Fehler {@code UEBERSPRINGEN_ABGELEHNT}.
 */
@Component("besuchUeberspringenSpeichern")
public class BesuchUeberspringenSpeichern implements JavaDelegate {

    private final TerraMaxClient terraMax;

    public BesuchUeberspringenSpeichern(TerraMaxClient terraMax) {
        this.terraMax = terraMax;
    }

    @Override
    public void execute(DelegateExecution execution) {
        String grund = BesuchstourVariablen.text(execution, UEBERSPRINGEN_GRUND);
        if (grund == null) {
            throw new IllegalArgumentException("Zum Überspringen eines Besuchs ist ein Grund nötig ("
                    + UEBERSPRINGEN_GRUND + ")");
        }
        try {
            terraMax.besuchUeberspringen(BesuchstourVariablen.tourId(execution),
                    BesuchstourVariablen.kundeId(execution), grund);
        } catch (TerraMaxFachlicherFehler e) {
            throw BesuchstourFehler.bpmnFehler(UEBERSPRINGEN_ABGELEHNT, e);
        }
    }
}
