package de.thi.terramax.customer;

/**
 * Woher ein Kunde oder eine Erfassung kommt. Die Namen stehen so in der Datenbank und in der API.
 */
public enum CustomerSource {

    /** Auf einer Messe erfasst, z. B. per Visitenkarte. */
    MESSE,
    /** Bei einem Besuch erfasst. */
    BESUCH,
    /** Von Hand angelegt. */
    MANUELL
}
