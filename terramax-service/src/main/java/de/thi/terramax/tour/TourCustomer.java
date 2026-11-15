package de.thi.terramax.tour;

import de.thi.terramax.customer.Customer;
import io.quarkus.hibernate.orm.panache.PanacheEntityBase;
import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.MapsId;
import jakarta.persistence.Table;
import java.io.Serializable;
import java.util.Objects;

/**
 * Ein Kunde auf einer Tour: Position in der Reihenfolge und ob er schon besucht wurde.
 */
@Entity
@Table(name = "tour_customer")
public class TourCustomer extends PanacheEntityBase {

    @EmbeddedId
    public Key key = new Key();

    @MapsId("tourId")
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "tour_id")
    public Tour tour;

    @MapsId("customerId")
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "customer_id")
    public Customer customer;

    @Column(name = "position", nullable = false)
    public int position;

    @Column(name = "visited", nullable = false)
    public boolean visited;

    public static TourCustomer of(Tour tour, Customer customer, int position) {
        TourCustomer stop = new TourCustomer();
        stop.tour = tour;
        stop.customer = customer;
        stop.position = position;
        return stop;
    }

    /** Zusammengesetzter Schlüssel (tour_id, customer_id). */
    @Embeddable
    public static class Key implements Serializable {

        @Column(name = "tour_id")
        public Long tourId;

        @Column(name = "customer_id")
        public Long customerId;

        @Override
        public boolean equals(Object o) {
            return o instanceof Key other && Objects.equals(tourId, other.tourId)
                    && Objects.equals(customerId, other.customerId);
        }

        @Override
        public int hashCode() {
            return Objects.hash(tourId, customerId);
        }
    }
}
