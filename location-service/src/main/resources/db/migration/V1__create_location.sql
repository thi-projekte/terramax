CREATE TABLE location (
    id           BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    external_id  VARCHAR(255)     NOT NULL,
    name         VARCHAR(255)     NOT NULL,
    category     VARCHAR(32)      NOT NULL,
    brands       TEXT,
    brand_known  BOOLEAN,
    street       VARCHAR(255),
    postcode     VARCHAR(16),
    city         VARCHAR(255),
    latitude     DOUBLE PRECISION NOT NULL,
    longitude    DOUBLE PRECISION NOT NULL,
    source_type  VARCHAR(64),
    source_url   VARCHAR(1024),
    more_sources TEXT,
    retrieved_at DATE,
    verified     BOOLEAN,
    note         TEXT,
    geocoded_by  VARCHAR(64),
    CONSTRAINT uk_location_external_id UNIQUE (external_id),
    CONSTRAINT ck_location_category CHECK (category IN ('competitor', 'mixed_competitor', 'john_deere'))
);

CREATE INDEX idx_location_category ON location (category);
