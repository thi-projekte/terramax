package de.thi.terramax.process.terramax;

/**
 * TerraMax lehnt den Aufruf fachlich ab: 400 (Angaben ungültig), 404 (Tour oder Halt unbekannt) oder 409 (ungültiger
 * Statusübergang, z. B. Halt schon dokumentiert oder Tour abgebrochen). Ein Wiederholen ändert nichts; die Delegates
 * machen daraus einen BPMN-Fehler, den das Modell mit einem Error-Boundary-Event abfängt.
 */
public final class TerraMaxFachlicherFehler extends TerraMaxException {

    public TerraMaxFachlicherFehler(int status, String message) {
        super(status, message, null);
    }
}
