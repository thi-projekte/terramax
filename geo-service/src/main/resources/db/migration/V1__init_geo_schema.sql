CREATE EXTENSION IF NOT EXISTS postgis;

CREATE TABLE h3_cells (
    h3_id VARCHAR(20) PRIMARY KEY,
    geometry GEOMETRY(MultiPolygon, 4326) NOT NULL,
    wheat_ha DOUBLE PRECISION,
    soy_ha DOUBLE PRECISION,
    source TEXT NOT NULL,
    data_date DATE,
    confidence VARCHAR(50)
);

CREATE INDEX idx_h3_cells_geometry
    ON h3_cells USING GIST (geometry);