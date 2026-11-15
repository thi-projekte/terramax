package de.thi.terramax.customer;

import de.thi.terramax.dealer.Dealer;
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
import jakarta.persistence.Table;
import java.util.List;
import java.util.Optional;

/**
 * Kunde eines Händlers. {@code externalId} ist die Kundennummer im CRM.
 */
@Entity
@Table(name = "customer")
public class Customer extends PanacheEntityBase {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    public Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "dealer_id", nullable = false)
    public Dealer dealer;

    @Column(name = "external_id", unique = true)
    public String externalId;

    @Column(name = "name", nullable = false)
    public String name;

    @Column(name = "region")
    public String region;

    @Column(name = "segment")
    public String segment;

    @Column(name = "latitude")
    public Double latitude;

    @Column(name = "longitude")
    public Double longitude;

    @Column(name = "crm_url")
    public String crmUrl;

    /** Alle Kunden eines Händlers, sortiert nach Name. */
    public static List<Customer> listByDealer(String dealerId) {
        return list("dealer.id", Sort.by("name").and("id"), dealerId);
    }

    /** Ein Kunde, aber nur, wenn er zum Händler gehört. */
    public static Optional<Customer> findForDealer(long id, String dealerId) {
        return find("id = ?1 and dealer.id = ?2", id, dealerId).firstResultOptional();
    }

    public static Optional<Customer> findByExternalId(String externalId) {
        return find("externalId", externalId).firstResultOptional();
    }
}
