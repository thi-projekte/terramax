package de.thi.terramax.process.besuchstour;

import org.cibseven.bpm.engine.delegate.DelegateExecution;
import org.cibseven.bpm.engine.delegate.JavaDelegate;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import static de.thi.terramax.process.besuchstour.BesuchstourVariablen.STATUS_DOKUMENTIERT;
import static de.thi.terramax.process.besuchstour.BesuchstourVariablen.STATUS_OFFEN;
import static de.thi.terramax.process.besuchstour.BesuchstourVariablen.STATUS_UEBERSPRUNGEN;
import static de.thi.terramax.process.besuchstour.BesuchstourVariablen.TOURUEBERSICHT;
import static de.thi.terramax.process.besuchstour.BesuchstourVariablen.TOUR_DATUM;

/**
 * Service Task "Tourübersicht erstellen" ({@code ${touruebersichtErstellen}}): fasst alle Besuche zusammen –
 * dokumentiert, übersprungen (mit Grund) oder wegen Abbruch offen. Ergebnis in {@code touruebersicht}.
 */
@Component("touruebersichtErstellen")
public class TouruebersichtErstellen implements JavaDelegate {

    @Override
    public void execute(DelegateExecution execution) {
        List<Map<String, Object>> ergebnisse = BesuchstourVariablen.besuchsergebnisse(execution);

        List<Map<String, Object>> besuche = new ArrayList<>();
        for (Map<String, Object> kunde : BesuchstourVariablen.besuchsplan(execution)) {
            Map<String, Object> besuch = ergebnisse.stream()
                    .filter(e -> Objects.equals(e.get("kundeId"), kunde.get("kundeId")))
                    .findFirst()
                    .<Map<String, Object>> map(LinkedHashMap::new)
                    .orElseGet(() -> {
                        Map<String, Object> offen = BesuchstourVariablen.kundeKurz(kunde);
                        offen.put("status", STATUS_OFFEN);
                        return offen;
                    });
            // Neu durchnummeriert, weil der Plan in "Besuchsplan prüfen" umsortiert worden sein kann
            besuch.put("position", besuche.size() + 1);
            besuche.add(besuch);
        }

        Map<String, Object> uebersicht = new LinkedHashMap<>();
        uebersicht.put("tourDatum", execution.getVariable(TOUR_DATUM));
        uebersicht.put("besuche", besuche);
        uebersicht.put("anzahlDokumentiert", anzahl(besuche, STATUS_DOKUMENTIERT));
        uebersicht.put("anzahlUebersprungen", anzahl(besuche, STATUS_UEBERSPRUNGEN));
        uebersicht.put("anzahlOffen", anzahl(besuche, STATUS_OFFEN));
        execution.setVariable(TOURUEBERSICHT, uebersicht);
    }

    private static long anzahl(List<Map<String, Object>> besuche, String status) {
        return besuche.stream().filter(b -> status.equals(b.get("status"))).count();
    }
}
