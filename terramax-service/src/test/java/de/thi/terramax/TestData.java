package de.thi.terramax;

import de.thi.terramax.customer.Customer;
import de.thi.terramax.dealer.Dealer;
import de.thi.terramax.dealer.DealerEmployee;
import de.thi.terramax.protocol.VisitorProtocol;
import de.thi.terramax.security.Roles;
import de.thi.terramax.tour.Tour;
import de.thi.terramax.tour.TourCustomer;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import jakarta.transaction.Transactional;
import java.time.LocalDate;
import java.util.List;

/**
 * Feste Testdaten in der Dev-Services-Datenbank. Die Standorte (Tabelle location) bleiben unangetastet.
 * <p>
 * Händler D-100 mit Sales Professional {@value #SALES_SUB}, Kollege {@value #COLLEAGUE_SUB} und Dealer Principal
 * {@value #PRINCIPAL_SUB}; Händler D-200 mit {@value #OTHER_SUB}.
 * <ul>
 * <li>Tour own (D-100, fährt sales): Kunden huber, maier</li>
 * <li>Tour colleague (D-100, fährt Kollege): Kunden huber, ohneKoordinaten</li>
 * <li>Tour foreign (D-200): Kunde fremd</li>
 * <li>Protokolle: ownProtocol (Tour own, huber), colleagueProtocol (Tour colleague, huber)</li>
 * </ul>
 */
@ApplicationScoped
public class TestData {

    public static final String DEALER = "D-100";
    public static final String OTHER_DEALER = "D-200";
    public static final String SALES_SUB = "sub-sales";
    public static final String COLLEAGUE_SUB = "sub-colleague";
    public static final String PRINCIPAL_SUB = "sub-principal";
    public static final String OTHER_SUB = "sub-other";

    public record Fixture(long salesId, long colleagueId, long principalId, long otherEmployeeId, long huber,
            long maier, long ohneKoordinaten, long fremd, long ownTour, long colleagueTour, long foreignTour,
            long ownProtocol, long colleagueProtocol) {
    }

    @Inject
    EntityManager em;

    /** Leert alle Tabellen des Kernmodells (auch die beim Start importierten Testdaten). */
    @Transactional
    public void clear() {
        em.createNativeQuery("TRUNCATE visitor_protocol, tour_customer, tour_employee, tour, customer, "
                + "dealer_employee, dealer RESTART IDENTITY CASCADE").executeUpdate();
    }

    @Transactional
    public Fixture reset() {
        clear();

        Dealer dealer = dealer(DEALER, "Landtechnik Wels GmbH", 48.16, 14.03);
        Dealer other = dealer(OTHER_DEALER, "Agrar Graz", 47.07, 15.44);
        DealerEmployee sales = employee(dealer, SALES_SUB, "Sam Sales", Roles.SALES_PROFESSIONAL);
        DealerEmployee colleague = employee(dealer, COLLEAGUE_SUB, "Kim Kollege", Roles.SALES_PROFESSIONAL);
        DealerEmployee principal = employee(dealer, PRINCIPAL_SUB, "Dana Dealer", Roles.DEALER_PRINCIPAL);
        DealerEmployee otherEmployee = employee(other, OTHER_SUB, "Olli Other", Roles.SALES_PROFESSIONAL);

        Customer huber = customer(dealer, "CRM-1", "Hof Huber", 48.20, 14.10);
        Customer maier = customer(dealer, "CRM-2", "Hof Maier", 48.30, 14.30);
        Customer ohneKoordinaten = customer(dealer, "CRM-3", "Hof ohne Koordinaten", null, null);
        Customer fremd = customer(other, "CRM-9", "Fremder Hof", 47.10, 15.40);

        Tour ownTour = tour(dealer, LocalDate.of(2026, 11, 20), List.of(sales), List.of(huber, maier));
        Tour colleagueTour = tour(dealer, LocalDate.of(2026, 11, 21), List.of(colleague),
                List.of(huber, ohneKoordinaten));
        Tour foreignTour = tour(other, LocalDate.of(2026, 11, 22), List.of(otherEmployee), List.of(fremd));

        VisitorProtocol ownProtocol = protocol(ownTour, huber, SALES_SUB, "Interesse an 6R");
        VisitorProtocol colleagueProtocol = protocol(colleagueTour, huber, COLLEAGUE_SUB, "Kein Bedarf");

        return new Fixture(sales.id, colleague.id, principal.id, otherEmployee.id, huber.id, maier.id,
                ohneKoordinaten.id, fremd.id, ownTour.id, colleagueTour.id, foreignTour.id, ownProtocol.id,
                colleagueProtocol.id);
    }

    private static Dealer dealer(String id, String name, double latitude, double longitude) {
        Dealer d = new Dealer();
        d.id = id;
        d.name = name;
        d.city = "Wels";
        d.latitude = latitude;
        d.longitude = longitude;
        d.persist();
        return d;
    }

    private static DealerEmployee employee(Dealer dealer, String sub, String name, String role) {
        DealerEmployee e = new DealerEmployee();
        e.dealer = dealer;
        e.keycloakUserId = sub;
        e.name = name;
        e.role = role;
        e.persist();
        return e;
    }

    private static Customer customer(Dealer dealer, String externalId, String name, Double latitude,
            Double longitude) {
        Customer c = new Customer();
        c.dealer = dealer;
        c.externalId = externalId;
        c.name = name;
        c.region = "Hausruckviertel";
        c.segment = "Ackerbau";
        c.latitude = latitude;
        c.longitude = longitude;
        c.persist();
        return c;
    }

    private static Tour tour(Dealer dealer, LocalDate date, List<DealerEmployee> employees,
            List<Customer> customers) {
        Tour t = new Tour();
        t.dealer = dealer;
        t.tourDate = date;
        t.employees.addAll(employees);
        for (int i = 0; i < customers.size(); i++) {
            t.stops.add(TourCustomer.of(t, customers.get(i), i + 1));
        }
        t.persist();
        return t;
    }

    private static VisitorProtocol protocol(Tour tour, Customer customer, String createdBy, String outcome) {
        VisitorProtocol p = new VisitorProtocol();
        p.tour = tour;
        p.customer = customer;
        p.contactPerson = "Herr Huber";
        p.topics = "Neuer Traktor\nFinanzierung";
        p.outcome = outcome;
        p.nextSteps = "Angebot schicken";
        p.followUpDate = LocalDate.of(2026, 11, 27);
        p.createdBy = createdBy;
        p.persist();
        return p;
    }
}
