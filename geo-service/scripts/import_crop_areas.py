
import csv
import os
from pathlib import Path

import psycopg2

ROOT = Path(__file__).resolve().parents[2]
CSV_PATH = ROOT / "frontend/public/TerraMax_H3_Weizen_Soja_Res6.csv"

def main():
    password = os.environ.get("PGPASSWORD")
    if not password:
        raise RuntimeError("PGPASSWORD ist nicht gesetzt")

    with CSV_PATH.open(encoding="utf-8-sig", newline="") as file:
        rows = list(csv.DictReader(file))

    print(f"CSV geladen: {len(rows)} Zeilen")

    if len(rows) != 1230:
        raise ValueError("Unerwartete Anzahl an CSV-Zeilen")

    with psycopg2.connect(
        host="127.0.0.1",
        port=5433,
        dbname="terramax_geo",
        user="terramax_geo",
        password=password,
    ) as connection:
        with connection.cursor() as cursor:
            for row in rows:
                h3_id = row["h3Index"].strip()
                area_ha = float(row["sum"])

                if area_ha < 0:
                    raise ValueError(f"Negative Fläche für {h3_id}")

                cursor.execute(
                    """
                    INSERT INTO h3_crop_areas
                        (h3_id, crop_type, area_ha, source, data_date, confidence)
                    VALUES (%s, %s, %s, %s, %s, %s)
                    ON CONFLICT (h3_id, crop_type)
                    DO UPDATE SET
                        area_ha = EXCLUDED.area_ha,
                        source = EXCLUDED.source,
                        data_date = EXCLUDED.data_date,
                        confidence = EXCLUDED.confidence
                    """,
                    (
                        h3_id,
                        "wheat_soy_combined",
                        area_ha,
                        "INVEKOS 2024 / QGIS",
                        "2024-01-01",
                        "aggregated",
                    ),
                )

    print(f"Import erfolgreich: {len(rows)} Flächenwerte")

if __name__ == "__main__":
    main()
