package de.thi.terramax.process.besuchstour;

import org.cibseven.bpm.engine.delegate.DelegateExecution;
import org.cibseven.bpm.engine.delegate.JavaDelegate;
import org.springframework.stereotype.Component;

import java.util.Map;

import static de.thi.terramax.process.besuchstour.BesuchstourVariablen.STATUS_UEBERSPRUNGEN;
import static de.thi.terramax.process.besuchstour.BesuchstourVariablen.UEBERSPRINGEN_GRUND;

/**
 * Service Task "Grund für Überspringen speichern" ({@code ${besuchUeberspringenSpeichern}}). Der Grund ist Pflicht
 * (Akzeptanzkriterium #29).
 * <p>
 * MVP: Gespeichert wird in der Tour ({@code besuchsergebnisse}), weil es noch keine Kundendatenbank gibt. Später kann
 * der Eintrag zusätzlich an einen Kunden-Service gehen.
 */
@Component("besuchUeberspringenSpeichern")
public class BesuchUeberspringenSpeichern implements JavaDelegate {

    @Override
    public void execute(DelegateExecution execution) {
        Object grund = execution.getVariable(UEBERSPRINGEN_GRUND);
        if (!(grund instanceof String text) || text.isBlank()) {
            throw new IllegalArgumentException("Zum Überspringen eines Besuchs ist ein Grund nötig ("
                    + UEBERSPRINGEN_GRUND + ")");
        }

        Map<String, Object> ergebnis = BesuchstourVariablen.kundeKurz(BesuchstourVariablen.kunde(execution));
        ergebnis.put("status", STATUS_UEBERSPRUNGEN);
        ergebnis.put("grund", text.strip());
        BesuchstourVariablen.ergebnisHinzufuegen(execution, ergebnis);

        BesuchstourVariablen.besuchsdatenEntfernen(execution);
    }
}
