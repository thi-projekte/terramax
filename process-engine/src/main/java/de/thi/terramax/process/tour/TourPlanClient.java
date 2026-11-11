package de.thi.terramax.process.tour;

/**
 * Holt den Besuchsplan vom tour-service. Eigene Schnittstelle, damit Tests ohne laufenden tour-service auskommen.
 */
public interface TourPlanClient {

    /**
     * @param tourDatum
     *            Tag im Format JJJJ-MM-TT; {@code null} bedeutet heute
     */
    TourPlan fetchPlan(String tourDatum);
}
