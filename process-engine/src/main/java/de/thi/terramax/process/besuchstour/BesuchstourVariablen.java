package de.thi.terramax.process.besuchstour;

import org.cibseven.bpm.engine.delegate.DelegateExecution;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Namen und Zugriff auf die Prozessvariablen der Besuchstour. Die Namen kommen aus dem BPMN-Modell (Dokumentation der
 * einzelnen Schritte in docs/process/besuchstour.bpmn). Listen und Maps werden als JSON gespeichert und sind so auch
 * über die REST-API lesbar.
 */
public final class BesuchstourVariablen {

    // --- aus dem Modell ---
    public static final String TOUR_DATUM = "tourDatum";
    public static final String BESUCHSPLAN = "besuchsplan";
    public static final String KUNDE = "kunde";
    public static final String UEBERSPRINGEN_GRUND = "ueberspringenGrund";
    public static final String PROTOKOLL = "protokoll";
    public static final String PROTOKOLL_ENTWURF = "protokollEntwurf";
    public static final String TRANSKRIPT = "transkript";
    public static final String SPRACHNOTIZ_ID = "sprachnotizId";
    public static final String BESUCH_BEGONNEN_AM = "besuchBegonnenAm";
    public static final String BESUCH_ABGESCHLOSSEN_AM = "besuchAbgeschlossenAm";

    // --- von der Engine ergänzt ---
    /** Ergebnis je Kunde (dokumentiert oder übersprungen), Grundlage für die Tourübersicht. */
    public static final String BESUCHSERGEBNISSE = "besuchsergebnisse";
    /** Zusammenfassung für den Schritt "Tour abschließen". */
    public static final String TOURUEBERSICHT = "touruebersicht";

    public static final String STATUS_DOKUMENTIERT = "DOKUMENTIERT";
    public static final String STATUS_UEBERSPRUNGEN = "UEBERSPRUNGEN";
    public static final String STATUS_OFFEN = "OFFEN";

    private BesuchstourVariablen() {
    }

    /** Aktueller Kunde im Schleifendurchlauf (Element aus {@code besuchsplan}). */
    @SuppressWarnings("unchecked")
    public static Map<String, Object> kunde(DelegateExecution execution) {
        Object kunde = execution.getVariable(KUNDE);
        if (!(kunde instanceof Map<?, ?>)) {
            throw new IllegalStateException("Prozessvariable 'kunde' fehlt");
        }
        return (Map<String, Object>) kunde;
    }

    public static List<Map<String, Object>> besuchsplan(DelegateExecution execution) {
        return listOfMaps(execution.getVariable(BESUCHSPLAN));
    }

    public static List<Map<String, Object>> besuchsergebnisse(DelegateExecution execution) {
        return listOfMaps(execution.getVariable(BESUCHSERGEBNISSE));
    }

    /**
     * Hängt ein Ergebnis an {@code besuchsergebnisse} an. Die Variable liegt auf Prozessebene (angelegt in "Besuchsplan
     * erzeugen"), daher landet das Ergebnis dort und nicht nur im einzelnen Schleifendurchlauf.
     */
    public static void ergebnisHinzufuegen(DelegateExecution execution, Map<String, Object> ergebnis) {
        List<Map<String, Object>> ergebnisse = besuchsergebnisse(execution);
        ergebnisse.add(ergebnis);
        execution.setVariable(BESUCHSERGEBNISSE, ergebnisse);
    }

    /**
     * Entfernt Variablen eines abgeschlossenen Besuchs, damit sie nicht im nächsten Besuch auftauchen. Behebt in der
     * Engine den offenen Punkt aus docs/process/README.md ("Daten laufen in den nächsten Besuch über").
     */
    public static void besuchsdatenEntfernen(DelegateExecution execution) {
        for (String name : List.of(UEBERSPRINGEN_GRUND, PROTOKOLL, PROTOKOLL_ENTWURF, TRANSKRIPT, SPRACHNOTIZ_ID,
                BESUCH_BEGONNEN_AM, BESUCH_ABGESCHLOSSEN_AM)) {
            execution.removeVariable(name);
        }
    }

    /** Grunddaten eines Kunden für Ergebnis und Übersicht. */
    public static Map<String, Object> kundeKurz(Map<String, Object> kunde) {
        Map<String, Object> kurz = new LinkedHashMap<>();
        kurz.put("kundeId", kunde.get("kundeId"));
        kurz.put("name", kunde.get("name"));
        kurz.put("ort", kunde.get("ort"));
        return kurz;
    }

    @SuppressWarnings("unchecked")
    private static List<Map<String, Object>> listOfMaps(Object value) {
        List<Map<String, Object>> list = new ArrayList<>();
        if (value instanceof List<?> items) {
            for (Object item : items) {
                list.add(new LinkedHashMap<>((Map<String, Object>) item));
            }
        }
        return list;
    }
}
