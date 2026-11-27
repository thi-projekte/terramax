# TerraMax – Database Schema

Entity-relationship model of the TerraMax service, transcribed from the whiteboard
sketch ([`terramax-db-schema-whiteboard.jpg`](terramax-db-schema-whiteboard.jpg)).
A browser-viewable version is in [`terramax-db-schema.html`](terramax-db-schema.html).

```mermaid
erDiagram
    Dealer          ||--|{ DealerEmployee  : employs
    Dealer          ||--o{ Customer        : serves
    DealerEmployee  }|--o{ Tour            : drives
    Customer        }|--o{ Tour            : "visited on"
    Customer        ||--o{ VisitorProtocol : "documented in"
    Tour            ||--o{ VisitorProtocol : produces
    Customer        ||--o{ CustomerContact : "has contact"
    Customer        ||--o{ LeadNote        : "noted in"
    CustomerContact |o--o{ LeadNote        : "captured with"
```

## Relationships

| From           | Cardinality | To              | Meaning                                                   |
|----------------|-------------|-----------------|-----------------------------------------------------------|
| Dealer         | 1 : 1..n    | DealerEmployee  | A dealer has at least one employee.                       |
| Dealer         | 1 : 0..n    | Customer        | A customer belongs to exactly one dealer.                 |
| DealerEmployee | 1..n : 0..n | Tour            | A tour is run by one or more employees (many-to-many).    |
| Customer       | 1..n : 0..n | Tour            | A tour visits one or more customers (many-to-many).       |
| Customer       | 1 : 0..n    | VisitorProtocol | Each protocol is about exactly one customer.              |
| Tour           | 1 : 0..n    | VisitorProtocol | Each protocol is created during exactly one tour.         |
| Customer       | 1 : 0..n    | CustomerContact | Contact persons of a customer, e.g. from a business card. |
| Customer       | 1 : 0..n    | LeadNote        | Short comment of a quick capture, without a tour.         |
| CustomerContact| 0..1 : 0..n | LeadNote        | The contact captured together with the note (optional).   |

## Notes

- The whiteboard shows entities and cardinalities only; attributes are not yet defined.
- The two many-to-many relationships (`DealerEmployee`–`Tour`, `Customer`–`Tour`) need
  join tables in a relational implementation, e.g. `tour_employee` and `tour_customer`.
- Leads (#134) are customers with `status = 'LEAD'` (migration `V5__lead_capture.sql`).
  `customer` also gets `source` (`MESSE`, `BESUCH`, `MANUELL`) and an address
  (`street`, `postal_code`, `city`). The customer list, the map and tour planning only
  use `status = 'CUSTOMER'` for now; bringing leads in is part of #126.
