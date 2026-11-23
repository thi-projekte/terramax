package de.thi.terramax.security;

import de.thi.terramax.dealer.EmployeeProvisioner;
import io.quarkus.security.identity.SecurityIdentity;
import jakarta.inject.Inject;
import java.util.Optional;
import org.jboss.logging.Logger;
import org.jboss.resteasy.reactive.server.ServerRequestFilter;

/**
 * Legt bei jedem Aufruf eines Nutzers mit {@code dealer_id} und Fachrolle dessen Mitarbeiter-Eintrag an, falls es ihn
 * noch nicht gibt ({@code keycloak_user_id} = {@code sub}, Name aus {@code name} bzw. {@code preferred_username}, Rolle
 * aus dem Token). Steht der Händler nicht in der Datenbank, passiert nichts.
 * <p>
 * Läuft vor der Resource-Methode auf demselben (Worker-)Thread. Fehler werden nur geloggt, damit ein Problem beim
 * Anlegen keine Leseanfrage verhindert.
 */
public class EmployeeProvisioningFilter {

    private static final Logger LOG = Logger.getLogger(EmployeeProvisioningFilter.class);

    @Inject
    SecurityIdentity identity;

    @Inject
    CurrentUser user;

    @Inject
    EmployeeProvisioner provisioner;

    @ServerRequestFilter
    public void provisionEmployee() {
        if (identity.isAnonymous()) {
            return;
        }
        Optional<String> role = user.businessRole();
        Optional<String> dealerId = user.dealerId();
        Optional<String> subject = user.subject();
        if (role.isEmpty() || dealerId.isEmpty() || subject.isEmpty()) {
            return;
        }
        try {
            provisioner.ensure(subject.get(), dealerId.get(), user.displayName(), role.get());
        } catch (RuntimeException e) {
            LOG.warnf("Mitarbeiter-Eintrag konnte nicht angelegt werden (%s)", e.getClass().getSimpleName());
        }
    }
}
