package de.thi.terramax.process.security;

/**
 * Fachrollen von TerraMax. Die Namen müssen exakt den Realm-Rollen in Keycloak entsprechen und zu Roles.java in den
 * anderen Services passen.
 */
public final class Roles {

    public static final String SALES_PROFESSIONAL = "sales_professional";

    private Roles() {
    }
}
