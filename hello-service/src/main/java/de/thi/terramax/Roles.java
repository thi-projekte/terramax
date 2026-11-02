package de.thi.terramax;

import java.util.Set;

/**
 * Fachrollen von TerraMax. Die Namen müssen exakt den Realm-Rollen in Keycloak
 * (bzw. später Okta) entsprechen – sie kommen aus dem Token unter realm_access.roles.
 */
public final class Roles {

    public static final String SALES_PROFESSIONAL = "sales_professional";
    public static final String DEALER_PRINCIPAL = "dealer_principal";

    public static final Set<String> ALL = Set.of(SALES_PROFESSIONAL, DEALER_PRINCIPAL);

    private Roles() {
    }
}
