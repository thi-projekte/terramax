package de.thi.terramax.process.besuchstour;

import org.cibseven.bpm.engine.delegate.DelegateExecution;
import org.cibseven.bpm.engine.delegate.JavaDelegate;
import org.springframework.stereotype.Component;

import java.util.Map;

import static de.thi.terramax.process.besuchstour.BesuchstourVariablen.BESUCH_ABGESCHLOSSEN_AM;
import static de.thi.terramax.process.besuchstour.BesuchstourVariablen.BESUCH_BEGONNEN_AM;
import static de.thi.terramax.process.besuchstour.BesuchstourVariablen.PROTOKOLL;
import static de.thi.terramax.process.besuchstour.BesuchstourVariablen.STATUS_DOKUMENTIERT;

/**
 * Service Task "Protokoll dem Kunden zuordnen" ({@code ${protokollZuordnen}}). Läuft erst nach der Bestätigung durch
 * den Sales Professional (Akzeptanzkriterium #29).
 * <p>
 * MVP: Gespeichert wird in der Tour ({@code besuchsergebnisse}), weil es noch keine Kundendatenbank gibt.
 */
@Component("protokollZuordnen")
public class ProtokollZuordnen implements JavaDelegate {

    @Override
    public void execute(DelegateExecution execution) {
        Map<String, Object> ergebnis = BesuchstourVariablen.kundeKurz(BesuchstourVariablen.kunde(execution));
        ergebnis.put("status", STATUS_DOKUMENTIERT);
        ergebnis.put("protokoll", execution.getVariable(PROTOKOLL));
        ergebnis.put("besuchBegonnenAm", execution.getVariable(BESUCH_BEGONNEN_AM));
        ergebnis.put("besuchAbgeschlossenAm", execution.getVariable(BESUCH_ABGESCHLOSSEN_AM));
        BesuchstourVariablen.ergebnisHinzufuegen(execution, ergebnis);

        BesuchstourVariablen.besuchsdatenEntfernen(execution);
    }
}
