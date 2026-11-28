package de.thi.terramax.customer;

import de.thi.terramax.dealer.Dealer;
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

    /** Bestandskunde oder Lead; neue Kunden sind Bestandskunden. */
    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    public CustomerStatus status = CustomerStatus.CUSTOMER;

    @Enumerated(EnumType.STRING)
    @Column(name = "source")
    public CustomerSource source;

    @Column(name = "street")
    public String street;

    @Column(name = "postal_code")
    public String postalCode;

    @Column(name = "city")
    public String city;

    /**
     * Bestandskunden eines Händlers, sortiert nach Name. Leads fehlen: Kundenliste, Karte und Tourplanung beziehen sie
     * erst mit #126 ein.
     */
    public static List<Customer> listByDealer(String dealerId) {
        return list("dealer.id = ?1 and status = ?2", Sort.by("name").and("id"), dealerId, CustomerStatus.CUSTOMER);
    }

    /** Leads eines Händlers, zuletzt angelegte zuerst. */
    public static List<Customer> listLeadsByDealer(String dealerId) {
        return list("dealer.id = ?1 and status = ?2", Sort.descending("id"), dealerId, CustomerStatus.LEAD);
    }

    /** Ein Kunde, aber nur, wenn er zum Händler gehört. */
    public static Optional<Customer> findForDealer(long id, String dealerId) {
        return find("id = ?1 and dealer.id = ?2", id, dealerId).firstResultOptional();
    }

    public static Optional<Customer> findByExternalId(String externalId) {
        return find("externalId", externalId).firstResultOptional();
    }
}
