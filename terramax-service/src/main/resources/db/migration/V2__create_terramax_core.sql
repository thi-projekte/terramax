-- Fachliches Kernmodell von TerraMax (ER-Modell: docs/architecture/terramax-db-schema.md)

-- Händler. Die ID entspricht dem Claim dealer_id im Keycloak-Token (z. B. D-100)
CREATE TABLE dealer (
    id          VARCHAR(64)      PRIMARY KEY,
    name        VARCHAR(255)     NOT NULL,
    street      VARCHAR(255),
    postcode    VARCHAR(16),
    city        VARCHAR(255),
    latitude    DOUBLE PRECISION,
    longitude   DOUBLE PRECISION,
    -- Verknüpfung zum Punkt auf der Marktkarte (optional)
    location_id BIGINT,
    CONSTRAINT fk_dealer_location FOREIGN KEY (location_id) REFERENCES location (id)
);

CREATE INDEX idx_dealer_location ON dealer (location_id);

-- Mitarbeiter eines Händlers. keycloak_user_id entspricht dem Claim sub im Token
CREATE TABLE dealer_employee (
    id               BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    dealer_id        VARCHAR(64)  NOT NULL,
    keycloak_user_id VARCHAR(64),
    name             VARCHAR(255) NOT NULL,
    role             VARCHAR(32)  NOT NULL,
    CONSTRAINT fk_dealer_employee_dealer FOREIGN KEY (dealer_id) REFERENCES dealer (id),
    CONSTRAINT uk_dealer_employee_keycloak_user UNIQUE (keycloak_user_id),
    CONSTRAINT ck_dealer_employee_role CHECK (role IN ('sales_professional', 'dealer_principal'))
);

CREATE INDEX idx_dealer_employee_dealer ON dealer_employee (dealer_id);

-- Kunde, gehört zu genau einem Händler. external_id ist die Nummer im CRM
CREATE TABLE customer (
    id          BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    dealer_id   VARCHAR(64)      NOT NULL,
    external_id VARCHAR(64),
    name        VARCHAR(255)     NOT NULL,
    region      VARCHAR(255),
    segment     VARCHAR(255),
    latitude    DOUBLE PRECISION,
    longitude   DOUBLE PRECISION,
    crm_url     VARCHAR(1024),
    CONSTRAINT fk_customer_dealer FOREIGN KEY (dealer_id) REFERENCES dealer (id),
    CONSTRAINT uk_customer_external_id UNIQUE (external_id)
);

CREATE INDEX idx_customer_dealer ON customer (dealer_id);

-- Besuchstour. process_instance_id verknüpft die Tour mit der Prozessinstanz in CIB Seven
CREATE TABLE tour (
    id                  BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    dealer_id           VARCHAR(64) NOT NULL,
    tour_date           DATE        NOT NULL,
    status              VARCHAR(16) NOT NULL DEFAULT 'planned',
    process_instance_id VARCHAR(64),
    created_at          TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT fk_tour_dealer FOREIGN KEY (dealer_id) REFERENCES dealer (id),
    CONSTRAINT ck_tour_status CHECK (status IN ('planned', 'in_progress', 'completed', 'discarded'))
);

CREATE INDEX idx_tour_dealer ON tour (dealer_id);

-- Wer fährt die Tour (n:m)
CREATE TABLE tour_employee (
    tour_id     BIGINT NOT NULL,
    employee_id BIGINT NOT NULL,
    CONSTRAINT pk_tour_employee PRIMARY KEY (tour_id, employee_id),
    CONSTRAINT fk_tour_employee_tour FOREIGN KEY (tour_id) REFERENCES tour (id) ON DELETE CASCADE,
    CONSTRAINT fk_tour_employee_employee FOREIGN KEY (employee_id) REFERENCES dealer_employee (id)
);

CREATE INDEX idx_tour_employee_employee ON tour_employee (employee_id);

-- Welche Kunden die Tour besucht (n:m), mit Reihenfolge und Besuchsstatus
CREATE TABLE tour_customer (
    tour_id     BIGINT  NOT NULL,
    customer_id BIGINT  NOT NULL,
    position    INT     NOT NULL,
    visited     BOOLEAN NOT NULL DEFAULT false,
    CONSTRAINT pk_tour_customer PRIMARY KEY (tour_id, customer_id),
    CONSTRAINT fk_tour_customer_tour FOREIGN KEY (tour_id) REFERENCES tour (id) ON DELETE CASCADE,
    CONSTRAINT fk_tour_customer_customer FOREIGN KEY (customer_id) REFERENCES customer (id)
);

CREATE INDEX idx_tour_customer_customer ON tour_customer (customer_id);

-- Besuchsprotokoll, entsteht während einer Tour und gehört zu einem Kunden.
-- Felder wie VisitProtocol im speech-service; topics und next_steps als Zeilen (ein Stichpunkt pro Zeile)
CREATE TABLE visitor_protocol (
    id             BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    tour_id        BIGINT      NOT NULL,
    customer_id    BIGINT      NOT NULL,
    contact_person VARCHAR(255),
    topics         TEXT,
    outcome        TEXT,
    next_steps     TEXT,
    follow_up_date DATE,
    transcript     TEXT,
    -- Claim sub des Erstellers
    created_by     VARCHAR(64) NOT NULL,
    created_at     TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT fk_visitor_protocol_tour FOREIGN KEY (tour_id) REFERENCES tour (id),
    CONSTRAINT fk_visitor_protocol_customer FOREIGN KEY (customer_id) REFERENCES customer (id)
);

CREATE INDEX idx_visitor_protocol_tour ON visitor_protocol (tour_id);
CREATE INDEX idx_visitor_protocol_customer ON visitor_protocol (customer_id);
