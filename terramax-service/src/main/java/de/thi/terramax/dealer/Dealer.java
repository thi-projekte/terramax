package de.thi.terramax.dealer;

import de.thi.terramax.location.Location;
import io.quarkus.hibernate.orm.panache.PanacheEntityBase;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

/**
 * Händler. Die ID entspricht dem Claim {@code dealer_id} im Keycloak-Token. Schema:
 * {@code V2__create_terramax_core.sql}.
 */
@Entity
@Table(name = "dealer")
public class Dealer extends PanacheEntityBase {

    @Id
    @Column(name = "id", nullable = false)
    public String id;

    @Column(name = "name", nullable = false)
    public String name;

    @Column(name = "street")
    public String street;

    @Column(name = "postcode")
    public String postcode;

    @Column(name = "city")
    public String city;

    @Column(name = "latitude")
    public Double latitude;

    @Column(name = "longitude")
    public Double longitude;

    /** Punkt auf der Marktkarte, falls der Händler dort eingetragen ist. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "location_id")
    public Location location;
}
