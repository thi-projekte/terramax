package de.thi.terramax.tour;

/**
 * Status eines Halts (Kunde auf einer Tour). Die Namen stehen so in der Datenbank ({@code tour_customer.status}), in
 * der API und in der process-engine ({@code BesuchstourVariablen.STATUS_*}).
 * <ul>
 * <li>{@link #OFFEN}: noch nicht erledigt; ein begonnener Besuch hat zusätzlich {@code startedAt}</li>
 * <li>{@link #DOKUMENTIERT}: Besuch abgeschlossen (Protokoll zugeordnet)</li>
 * <li>{@link #UEBERSPRUNGEN}: nicht besucht, mit Grund</li>
 * </ul>
 * Übergänge nur von {@code OFFEN} aus; {@code DOKUMENTIERT} und {@code UEBERSPRUNGEN} sind endgültig.
 */
public enum StopStatus {

    OFFEN, DOKUMENTIERT, UEBERSPRUNGEN;

    public boolean isDone() {
        return this != OFFEN;
    }
}
