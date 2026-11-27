-- Schnellerfassung von Leads (#134), Grundlage für den geschlossenen Kreislauf (#126).
-- Ein Lead ist ein Kunde mit status LEAD. Kundenliste und Tourplanung zeigen vorerst nur status CUSTOMER.

ALTER TABLE customer
    -- Bestehende Kunden bleiben CUSTOMER
    ADD COLUMN status      VARCHAR(16) NOT NULL DEFAULT 'CUSTOMER',
    -- Woher der Kunde kommt (MESSE, BESUCH, MANUELL), optional; Altdaten bleiben leer
    ADD COLUMN source      VARCHAR(32),
    ADD COLUMN street      VARCHAR(255),
    ADD COLUMN postal_code VARCHAR(16),
    ADD COLUMN city        VARCHAR(255);

ALTER TABLE customer
    ADD CONSTRAINT ck_customer_status CHECK (status IN ('CUSTOMER', 'LEAD')),
    ADD CONSTRAINT ck_customer_source CHECK (source IS NULL OR source IN ('MESSE', 'BESUCH', 'MANUELL'));

CREATE INDEX idx_customer_dealer_status ON customer (dealer_id, status);

-- Ansprechpartner eines Kunden, z. B. von einer Visitenkarte. Eine Firma kann mehrere haben
CREATE TABLE customer_contact (
    id          BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    customer_id BIGINT       NOT NULL,
    name        VARCHAR(255) NOT NULL,
    role        VARCHAR(255),
    phone       VARCHAR(64),
    email       VARCHAR(255),
    created_at  TIMESTAMPTZ  NOT NULL DEFAULT now(),
    CONSTRAINT fk_customer_contact_customer FOREIGN KEY (customer_id) REFERENCES customer (id)
);

CREATE INDEX idx_customer_contact_customer ON customer_contact (customer_id);

-- Kurzer Kommentar zu einer Erfassung (getippt oder diktiert), ohne Tour
CREATE TABLE lead_note (
    id          BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    customer_id BIGINT      NOT NULL,
    -- Ansprechpartner aus derselben Erfassung, optional
    contact_id  BIGINT,
    comment     TEXT,
    -- Transkript der Sprachnotiz, optional
    transcript  TEXT,
    -- z. B. Name der Messe
    event_name  VARCHAR(255),
    source      VARCHAR(32),
    -- Claim sub des Erstellers
    created_by  VARCHAR(64) NOT NULL,
    created_at  TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT fk_lead_note_customer FOREIGN KEY (customer_id) REFERENCES customer (id),
    CONSTRAINT fk_lead_note_contact FOREIGN KEY (contact_id) REFERENCES customer_contact (id),
    CONSTRAINT ck_lead_note_source CHECK (source IS NULL OR source IN ('MESSE', 'BESUCH', 'MANUELL'))
);

CREATE INDEX idx_lead_note_customer ON lead_note (customer_id);
