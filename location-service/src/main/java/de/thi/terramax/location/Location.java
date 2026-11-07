package de.thi.terramax.location;

import io.quarkus.hibernate.orm.panache.PanacheEntityBase;
import io.quarkus.panache.common.Sort;
import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

/**
 * Standort eines Händlers (Wettbewerber, gemischter Wettbewerber oder John Deere). Das Schema wird über die
 * Flyway-Migration {@code V1__create_location.sql} angelegt.
 */
@Entity
@Table(name = "location")
public class Location extends PanacheEntityBase {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    public Long id;

    @Column(name = "external_id", nullable = false, unique = true)
    public String externalId;

    @Column(name = "name", nullable = false)
    public String name;

    @Convert(converter = LocationCategoryConverter.class)
    @Column(name = "category", nullable = false)
    public LocationCategory category;

    @Column(name = "brands")
    public String brands;

    @Column(name = "brand_known")
    public Boolean brandKnown;

    @Column(name = "street")
    public String street;

    @Column(name = "postcode")
    public String postcode;

    @Column(name = "city")
    public String city;

    @Column(name = "latitude", nullable = false)
    public double latitude;

    @Column(name = "longitude", nullable = false)
    public double longitude;

    @Column(name = "source_type")
    public String sourceType;

    @Column(name = "source_url")
    public String sourceUrl;

    @Column(name = "more_sources")
    public String moreSources;

    @Column(name = "retrieved_at")
    public LocalDate retrievedAt;

    @Column(name = "verified")
    public Boolean verified;

    @Column(name = "note")
    public String note;

    @Column(name = "geocoded_by")
    public String geocodedBy;

    /** Sortierung für Listen: nach Name, bei Gleichstand nach externer ID (stabile Reihenfolge). */
    private static Sort byName() {
        return Sort.by("name").and("externalId");
    }

    /** Alle Standorte, sortiert nach Name. */
    public static List<Location> listAllSortedByName() {
        return listAll(byName());
    }

    /** Standorte der angegebenen Kategorien, sortiert nach Name. Gefiltert wird in der Datenbank. */
    public static List<Location> listByCategories(Collection<LocationCategory> categories) {
        return list("category in ?1", byName(), categories);
    }

    /** Sucht einen Standort über seine externe ID (z. B. {@code res-21}). */
    public static Optional<Location> findByExternalId(String externalId) {
        return find("externalId", externalId).firstResultOptional();
    }
}
