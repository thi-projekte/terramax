package de.thi.terramax.dealer;

import io.quarkus.narayana.jta.QuarkusTransaction;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.persistence.PersistenceException;
import java.util.Optional;
import org.jboss.logging.Logger;

/**
 * Legt den Mitarbeiter-Eintrag eines Nutzers beim ersten Aufruf an. Ein bestehender Eintrag wird nie geändert, auch
 * wenn sich Name, Rolle oder Händler im Token inzwischen geändert haben.
 */
@ApplicationScoped
public class EmployeeProvisioner {

    static final int MAX_NAME_LENGTH = 255;

    private static final Logger LOG = Logger.getLogger(EmployeeProvisioner.class);

    /**
     * @param keycloakUserId
     *            Claim {@code sub}
     * @param role
     *            Fachrolle aus {@link de.thi.terramax.security.Roles}
     *
     * @return der bestehende oder neu angelegte Eintrag; leer, wenn der Händler nicht in der Datenbank steht
     */
    public Optional<DealerEmployee> ensure(String keycloakUserId, String dealerId, String name, String role) {
        try {
            return QuarkusTransaction.requiringNew().call(() -> findOrCreate(keycloakUserId, dealerId, name, role));
        } catch (PersistenceException e) {
            // Zwei gleichzeitige erste Aufrufe: Der andere hat den Eintrag schon angelegt (Unique-Constraint)
            LOG.debugf("Mitarbeiter %s wurde parallel angelegt: %s", keycloakUserId, e.getMessage());
            return QuarkusTransaction.requiringNew()
                    .call(() -> DealerEmployee.findByKeycloakUserId(keycloakUserId));
        }
    }

    private static Optional<DealerEmployee> findOrCreate(String keycloakUserId, String dealerId, String name,
            String role) {
        Optional<DealerEmployee> existing = DealerEmployee.findByKeycloakUserId(keycloakUserId);
        if (existing.isPresent()) {
            return existing;
        }
        Dealer dealer = Dealer.findById(dealerId);
        if (dealer == null) {
            return Optional.empty();
        }
        DealerEmployee employee = new DealerEmployee();
        employee.dealer = dealer;
        employee.keycloakUserId = keycloakUserId;
        employee.name = name.length() > MAX_NAME_LENGTH ? name.substring(0, MAX_NAME_LENGTH) : name;
        employee.role = role;
        employee.persist();
        employee.flush();
        LOG.infof("Mitarbeiter %d für Händler %s angelegt (%s)", employee.id, dealerId, role);
        return Optional.of(employee);
    }
}
