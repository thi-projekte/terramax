CREATE TABLE h3_crop_areas (
    h3_id VARCHAR(20) NOT NULL
        REFERENCES h3_cells(h3_id),
    crop_type VARCHAR(50) NOT NULL,
    area_ha DOUBLE PRECISION NOT NULL
        CHECK (area_ha >= 0),
    source TEXT NOT NULL,
    data_date DATE,
    confidence VARCHAR(50),

    PRIMARY KEY (h3_id, crop_type)
);