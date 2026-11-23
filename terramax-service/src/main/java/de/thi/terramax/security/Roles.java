package de.thi.terramax.security;

import java.util.Set;

/**
 * Rollen von TerraMax. Die Namen müssen exakt den Realm-Rollen in Keycloak entsprechen, sie kommen aus dem Token unter
 * realm_access.roles.
 * <ul>
 * <li>{@link #SALES_PROFESSIONAL} und {@link #DEALER_PRINCIPAL}: Fachrollen der Nutzer (wie im hello-service). Im Realm
 * ist dealer_principal eine Composite-Rolle, die sales_professional enthält.</li>
 * <li>{@link #PROCESS_ENGINE}: Service-Konto der process-engine (Client-Credentials-Token, ohne {@code dealer_id}).
 * Darf die Tour- und Protokoll-Endpunkte für Engine-Schritte ohne angemeldeten Nutzer aufrufen; der Händler kommt dann
 * aus der Tour.</li>
 * </ul>
 */
public final class Roles {

    public static final String SALES_PROFESSIONAL = "sales_professional";
    public static final String DEALER_PRINCIPAL = "dealer_principal";
    public static final String PROCESS_ENGINE = "process_engine";

    public static final Set<String> ALL = Set.of(SALES_PROFESSIONAL, DEALER_PRINCIPAL, PROCESS_ENGINE);

    private Roles() {
    }
}
