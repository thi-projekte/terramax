package de.thi.terramax.dealer;

import io.quarkus.hibernate.orm.panache.PanacheEntityBase;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.util.Optional;

/**
 * Mitarbeiter eines Händlers. {@code keycloakUserId} entspricht dem Claim {@code sub} im Token, {@code role} einer
 * Rolle aus {@link de.thi.terramax.security.Roles}.
 */
@Entity
@Table(name = "dealer_employee")
public class DealerEmployee extends PanacheEntityBase {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    public Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "dealer_id", nullable = false)
    public Dealer dealer;

    @Column(name = "keycloak_user_id", unique = true)
    public String keycloakUserId;

    @Column(name = "name", nullable = false)
    public String name;

    @Column(name = "role", nullable = false)
    public String role;

    /** Mitarbeiter zum Claim {@code sub}. */
    public static Optional<DealerEmployee> findByKeycloakUserId(String keycloakUserId) {
        return find("keycloakUserId", keycloakUserId).firstResultOptional();
    }
}
