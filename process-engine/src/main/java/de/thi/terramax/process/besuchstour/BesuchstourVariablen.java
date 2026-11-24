package de.thi.terramax.process.besuchstour;

import org.cibseven.bpm.engine.delegate.VariableScope;

import java.util.ArrayList;
import java.util.List;

/**
 * Prozessvariablen der Besuchstour.
 * <p>
 * Grundsatz: <b>Die Tour liegt in TerraMax, die Engine speichert nur die Ausführung.</b> Dauerhaft im Prozess stehen
 * nur Referenzen ({@code tourId}, {@code kundenIds}, {@code kundeId}), Flags für Gateways und Eingaben des Nutzers, bis
 * sie an TerraMax übergeben sind. Wer die Tour wann gestartet hat, hält die Engine selbst fest ({@code initiator},
 * Historie). Fachliche Daten (Kunden, Status je Halt, Gründe, Protokolle) kommen per ID aus dem terramax-service.
 * <p>
 * Die Variablen unter "Nur während einer Aufgabe" lädt ein ExecutionListener am {@code start} der User Task, damit das
 * Frontend sie anzeigen kann, und entfernt sie am {@code end} wieder ({@link GeladeneDatenEntfernen}).
 */
public final class BesuchstourVariablen {

    // --- Referenzen und Ausführung (dauerhaft im Prozess) ---
    /** ID der Tour im terramax-service, gesetzt von "Besuchsplan erzeugen". */
    public static final String TOUR_ID = "tourId";
    /** Gewünschter Tag der Tour (Eingabe beim Start), JJJJ-MM-TT. */
    public static final String TOUR_DATUM = "tourDatum";
    /** Kunden-IDs der Tour in Besuchsreihenfolge; Collection der Schleife "Kunden nacheinander besuchen". */
    public static final String KUNDEN_IDS = "kundenIds";
    /** Element-Variable der Schleife: Kunde des aktuellen Besuchs. */
    public static final String KUNDE_ID = "kundeId";
    /** Anzahl der Halte für die Gateways "Mindestens zwei Kunden?" und "Noch mindestens zwei Kunden?". */
    public static final String ANZAHL_HALTE = "anzahlHalte";

    // --- Eingaben aus den User Tasks (lokal je Besuch, bis sie an TerraMax übergeben sind) ---
    public static final String PLAN_BESTAETIGT = "planBestaetigt";
    public static final String UEBERSPRINGEN_GRUND = "ueberspringenGrund";
    public static final String BESUCH_BEGONNEN_AM = "besuchBegonnenAm";
    public static final String BESUCH_ABGESCHLOSSEN_AM = "besuchAbgeschlossenAm";
    /** Vom Frontend beim Abbrechen mitgeschickter Grund (optional). */
    public static final String ABBRUCH_GRUND = "abbruchGrund";

    // --- Nur während einer Aufgabe (per tourId geladen, danach wieder entfernt) ---
    /** Kunden der Tour für die Anzeige, Felder wie bisher im Frontend ({@code Kunde} in engineApi.ts). */
    public static final String BESUCHSPLAN = "besuchsplan";
    /** Kunde des aktuellen Besuchs für die Anzeige. */
    public static final String KUNDE = "kunde";
    /** Tourübersicht für "Tour abschließen". */
    public static final String TOURUEBERSICHT = "touruebersicht";

    /** Variablen, die nur während einer Aufgabe im Prozess stehen. */
    public static final List<String> GELADENE_DATEN = List.of(BESUCHSPLAN, KUNDE, TOURUEBERSICHT);

    private BesuchstourVariablen() {
    }

    /** {@code tourId}; fehlt sie, ist das ein Fehler im Ablauf. */
    public static long tourId(VariableScope scope) {
        Long tourId = tourIdOderNull(scope);
        if (tourId == null) {
            throw new IllegalStateException("Prozessvariable '" + TOUR_ID + "' fehlt");
        }
        return tourId;
    }

    /** {@code tourId} oder {@code null}, wenn noch keine Tour angelegt ist. */
    public static Long tourIdOderNull(VariableScope scope) {
        return alsLong(scope.getVariable(TOUR_ID), TOUR_ID);
    }

    /** Kunde des aktuellen Schleifendurchlaufs. */
    public static long kundeId(VariableScope scope) {
        Long kundeId = alsLong(scope.getVariable(KUNDE_ID), KUNDE_ID);
        if (kundeId == null) {
            throw new IllegalStateException("Prozessvariable '" + KUNDE_ID + "' fehlt");
        }
        return kundeId;
    }

    /** Text oder {@code null}, wenn die Variable fehlt oder leer ist. */
    public static String text(VariableScope scope, String name) {
        Object value = scope.getVariable(name);
        return value instanceof String s && !s.isBlank() ? s.strip() : null;
    }

    /** Setzt die Referenzen auf die Halte, wie TerraMax sie gerade kennt. */
    public static void halteSetzen(VariableScope scope, List<Long> kundenIds) {
        scope.setVariable(KUNDEN_IDS, new ArrayList<>(kundenIds));
        scope.setVariable(ANZAHL_HALTE, kundenIds.size());
    }

    static Long alsLong(Object value, String name) {
        if (value == null) {
            return null;
        }
        if (value instanceof Number number) {
            return number.longValue();
        }
        try {
            return Long.parseLong(value.toString().strip());
        } catch (NumberFormatException e) {
            throw new IllegalStateException("Prozessvariable '" + name + "' ist keine ID: " + value, e);
        }
    }
}
