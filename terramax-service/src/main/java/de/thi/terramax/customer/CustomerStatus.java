package de.thi.terramax.customer;

/**
 * Status eines Kunden. Die Namen stehen so in der Datenbank ({@code customer.status}) und in der API.
 * <ul>
 * <li>{@link #CUSTOMER}: Bestandskunde; erscheint in der Kundenliste, auf der Karte und in der Tourplanung</li>
 * <li>{@link #LEAD}: schnell erfasster Interessent (z. B. Visitenkarte auf einer Messe), vorerst nur unter
 * {@code /leads}</li>
 * </ul>
 */
public enum CustomerStatus {

    CUSTOMER, LEAD
}
