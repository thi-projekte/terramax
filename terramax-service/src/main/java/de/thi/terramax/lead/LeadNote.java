package de.thi.terramax.lead;

import de.thi.terramax.customer.Customer;
import de.thi.terramax.customer.CustomerSource;
import io.quarkus.hibernate.orm.panache.PanacheEntityBase;
import io.quarkus.panache.common.Sort;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import java.time.OffsetDateTime;
import java.util.List;

/**
 * Kurzer Kommentar zu einer Schnellerfassung (getippt oder diktiert), ohne Tour. Gehört zu genau einem Kunden, optional
 * zu dem Ansprechpartner aus derselben Erfassung.
 */
@Entity
@Table(name = "lead_note")
public class LeadNote extends PanacheEntityBase {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    public Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "customer_id", nullable = false)
    public Customer customer;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "contact_id")
    public CustomerContact contact;

    @Column(name = "comment")
    public String comment;

    @Column(name = "transcript")
    public String transcript;

    @Column(name = "event_name")
    public String eventName;

    @Enumerated(EnumType.STRING)
    @Column(name = "source")
    public CustomerSource source;

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

    /** Alle Notizen der Kunden, neueste zuerst. */
    public static List<LeadNote> listForCustomers(List<Long> customerIds) {
        if (customerIds.isEmpty()) {
            return List.of();
        }
        return list("customer.id in ?1", Sort.descending("createdAt").and("id", Sort.Direction.Descending),
                customerIds);
    }
}
