package de.thi.terramax.process.besuchstour;

import de.thi.terramax.process.terramax.TerraMaxFachlicherFehler;
import org.cibseven.bpm.engine.delegate.BpmnError;

/**
 * Fehlercodes der Service Tasks und die einheitliche Fehlerbehandlung.
 * <p>
 * Jede Aktivität, die TerraMax aufruft, unterscheidet zwei Fälle:
 * <ul>
 * <li><b>Fachlich</b> ({@link TerraMaxFachlicherFehler}, HTTP 400/404/409): Der Aufruf passt nicht zum Stand der Tour,
 * Wiederholen hilft nicht. Der Delegate wirft einen {@link BpmnError} mit dem Code seines Schritts; im Modell fängt ihn
 * ein Error-Boundary-Event am Service Task ab und führt auf einen eigenen Pfad.</li>
 * <li><b>Technisch</b> (TerraMax nicht erreichbar, 5xx, Token fehlt): Die Exception läuft durch, die Transaktion wird
 * zurückgerollt. Der Prozess bleibt im letzten Wartezustand, der Nutzer bekommt eine Fehlermeldung und kann es erneut
 * versuchen. Es geht also nichts verloren und nichts wird halb gespeichert.</li>
 * </ul>
 */
public final class BesuchstourFehler {

    /** "Besuchsplan erzeugen": Plan konnte nicht erstellt oder die Tour nicht angelegt werden. */
    public static final String BESUCHSPLAN_FEHLGESCHLAGEN = "BESUCHSPLAN_FEHLGESCHLAGEN";
    /** "Grund für Überspringen speichern": TerraMax nimmt das Überspringen nicht an. */
    public static final String UEBERSPRINGEN_ABGELEHNT = "UEBERSPRINGEN_ABGELEHNT";
    /** "Protokoll dem Kunden zuordnen": TerraMax nimmt den Abschluss des Besuchs nicht an. */
    public static final String BESUCHSABSCHLUSS_ABGELEHNT = "BESUCHSABSCHLUSS_ABGELEHNT";

    private BesuchstourFehler() {
    }

    /** Macht aus einem fachlichen Fehler von TerraMax einen BPMN-Fehler mit dem Code des Schritts. */
    public static BpmnError bpmnFehler(String code, TerraMaxFachlicherFehler fehler) {
        return new BpmnError(code, fehler.getMessage());
    }
}
