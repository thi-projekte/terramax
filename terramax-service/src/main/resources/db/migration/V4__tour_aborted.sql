-- Eigener Tour-Status für abgebrochene Touren (BPMN-Message TourAbbrechen), getrennt von completed (regulär
-- abgeschlossen) und discarded (Plan verworfen). Offene Halte bleiben beim Abbruch OFFEN.

ALTER TABLE tour DROP CONSTRAINT ck_tour_status;

ALTER TABLE tour
    ADD CONSTRAINT ck_tour_status CHECK (status IN ('planned', 'in_progress', 'completed', 'discarded', 'aborted')),
    -- Optionaler Grund, nur bei aborted
    ADD COLUMN abort_reason VARCHAR(1000),
    -- Zeitpunkt des Abbruchs, nur bei aborted
    ADD COLUMN aborted_at   TIMESTAMPTZ;
