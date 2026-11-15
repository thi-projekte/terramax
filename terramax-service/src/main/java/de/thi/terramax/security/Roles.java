package de.thi.terramax.security;

import java.util.Set;

/**
 * Fachrollen von TerraMax (wie im hello-service). Die Namen müssen exakt den Realm-Rollen in Keycloak entsprechen, sie
 * kommen aus dem Token unter realm_access.roles. Im Realm ist dealer_principal eine Composite-Rolle, die
 * sales_professional enthält.
 */
public final class Roles {

    public static final String SALES_PROFESSIONAL = "sales_professional";
    public static final String DEALER_PRINCIPAL = "dealer_principal";

    public static final Set<String> ALL = Set.of(SALES_PROFESSIONAL, DEALER_PRINCIPAL);

    private Roles() {
    }
}
