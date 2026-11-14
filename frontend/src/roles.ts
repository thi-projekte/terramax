import keycloak from './keycloak'

// Muss exakt zu den Realm-Rollen in Keycloak und zu Roles.java im Backend passen.
export const ROLES = {
  SALES_PROFESSIONAL: 'sales_professional',
  DEALER_PRINCIPAL: 'dealer_principal',
} as const

export type Role = (typeof ROLES)[keyof typeof ROLES]

export const ROLE_LABELS: Record<Role, string> = {
  sales_professional: 'Sales Professional',
  dealer_principal: 'Dealer Principal',
}

// Liest die Rolle aus dem Token (realm_access.roles).
// Achtung: Das steuert nur, was die Oberfläche anzeigt. Geschützt wird im Backend (@RolesAllowed).
export function hasRole(role: Role): boolean {
  return keycloak.hasRealmRole(role)
}

export function currentRoles(): Role[] {
  return Object.values(ROLES).filter(hasRole)
}
