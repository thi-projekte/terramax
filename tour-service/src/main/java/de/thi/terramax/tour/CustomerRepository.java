package de.thi.terramax.tour;

import java.util.List;

/**
 * Quelle der Kundendaten. Im MVP liefert {@link MockCustomerRepository} feste Testdaten; später kann hier eine
 * Datenbank oder ein CRM angebunden werden, ohne den {@link VisitPlanner} zu ändern.
 */
public interface CustomerRepository {

    List<Customer> findAll();
}
