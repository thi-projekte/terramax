package de.thi.terramax.protocol;

import de.thi.terramax.customer.Customer;
import de.thi.terramax.tour.Tour;
import io.quarkus.hibernate.orm.panache.PanacheEntityBase;
import io.quarkus.panache.common.Sort;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;

/**
 * Besuchsprotokoll zu einem Kunden, erstellt während einer Tour. Die Felder entsprechen {@code VisitProtocol} im
 * speech-service. {@code topics} und {@code nextSteps} stehen als Zeilen in der Datenbank (ein Stichpunkt pro Zeile).
 */
@Entity
@Table(name = "visitor_protocol")
public class VisitorProtocol extends PanacheEntityBase {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    public Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "tour_id", nullable = false)
    public Tour tour;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "customer_id", nullable = false)
    public Customer customer;

    @Column(name = "contact_person")
    public String contactPerson;

    @Column(name = "topics")
    public String topics;

    @Column(name = "outcome")
    public String outcome;

    @Column(name = "next_steps")
    public String nextSteps;

    @Column(name = "follow_up_date")
    public LocalDate followUpDate;

    @Column(name = "transcript")
    public String transcript;

    /** Claim {@code sub} des Erstellers. */
    @Column(name = "created_by", nullable = false, updatable = false)
    public String createdBy;

    @Column(name = "created_at", nullable = false, updatable = false)
    public OffsetDateTime createdAt;

    @PrePersist
    void onCreate() {
        if (createdAt == null) {
            createdAt = OffsetDateTime.now();
        }
    }

    /** Protokolle einer Tour, neueste zuerst. */
    public static List<VisitorProtocol> listByTour(long tourId) {
        return list("tour.id", newestFirst(), tourId);
    }

    /** Alle Protokolle eines Kunden, neueste zuerst. */
    public static List<VisitorProtocol> listByCustomer(long customerId) {
        return list("customer.id", newestFirst(), customerId);
    }

    /** Protokolle eines Kunden aus Touren, die der Mitarbeiter fährt, neueste zuerst. */
    public static List<VisitorProtocol> listByCustomerAndEmployee(long customerId, long employeeId) {
        return list("select p from VisitorProtocol p join p.tour t join t.employees e "
                + "where p.customer.id = ?1 and e.id = ?2 order by p.createdAt desc, p.id desc", customerId,
                employeeId);
    }

    private static Sort newestFirst() {
        return Sort.descending("createdAt").and("id", Sort.Direction.Descending);
    }
}
