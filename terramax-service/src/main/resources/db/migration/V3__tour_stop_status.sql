-- TerraMax ist die Single Source of Truth für den Ablauf einer Tour (#64). Die process-engine hält nur noch die
-- tourId im Prozesskontext; Status, Grund fürs Überspringen und Zeiten je Halt stehen hier.

-- Status je Halt statt visited BOOLEAN. Namen wie in der process-engine (BesuchstourVariablen.STATUS_*)
ALTER TABLE tour_customer
    ADD COLUMN status      VARCHAR(16) NOT NULL DEFAULT 'OFFEN',
    -- Pflicht bei UEBERSPRUNGEN, sonst leer
    ADD COLUMN skip_reason VARCHAR(1000),
    -- Besuch begonnen (POST .../start), optional
    ADD COLUMN started_at  TIMESTAMPTZ,
    -- Halt erledigt: dokumentiert (POST .../complete) oder übersprungen (POST .../skip)
    ADD COLUMN finished_at TIMESTAMPTZ;

-- Bestehende Daten: besuchte Kunden gelten als dokumentiert. Zeiten sind für Altdaten unbekannt
UPDATE tour_customer SET status = 'DOKUMENTIERT' WHERE visited;

ALTER TABLE tour_customer DROP COLUMN visited;

ALTER TABLE tour_customer
    ADD CONSTRAINT ck_tour_customer_status CHECK (status IN ('OFFEN', 'DOKUMENTIERT', 'UEBERSPRUNGEN')),
    ADD CONSTRAINT ck_tour_customer_skip_reason CHECK (status <> 'UEBERSPRUNGEN' OR skip_reason IS NOT NULL);

-- Eine Prozessinstanz gehört zu höchstens einer Tour. Grundlage für das idempotente POST /tours und
-- GET /tours/by-process/{processInstanceId}. NULL (Tour ohne Prozess) ist mehrfach erlaubt
CREATE UNIQUE INDEX uk_tour_process_instance ON tour (process_instance_id);
