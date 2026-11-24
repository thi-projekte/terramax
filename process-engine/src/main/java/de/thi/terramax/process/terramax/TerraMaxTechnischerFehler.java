package de.thi.terramax.process.terramax;

/**
 * Technischer Fehler: terramax-service nicht erreichbar, 5xx, oder Token fehlt bzw. reicht nicht (401/403). Die
 * Exception läuft bis zur Engine durch; die Transaktion wird zurückgerollt. Der Aufruf des Nutzers (z. B. Aufgabe
 * abschließen) schlägt fehl und kann wiederholt werden, der Prozess bleibt im letzten Wartezustand.
 */
public final class TerraMaxTechnischerFehler extends TerraMaxException {

    public TerraMaxTechnischerFehler(int status, String message, Throwable cause) {
        super(status, message, cause);
    }
}
