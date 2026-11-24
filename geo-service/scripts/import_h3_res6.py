
import json
import os
from pathlib import Path

import psycopg2

# Projektverzeichnis ermitteln
ROOT = Path(__file__).resolve().parents[2]
GEOJSON_PATH = ROOT / "frontend/public/austria_h3_resolution6.geojson"

def main():
    with GEOJSON_PATH.open(encoding="utf-8") as file:
        data = json.load(file)

    features = data["features"]
    print(f"GeoJSON geladen: {len(features)} H3-Zellen")

    if len(features) != 2375:
        raise ValueError("Unerwartete Anzahl an H3-Zellen")

    password = os.environ.get("PGPASSWORD")
    if not password:
        raise RuntimeError("PGPASSWORD ist nicht gesetzt")

    with psycopg2.connect(
        host="127.0.0.1",
        port=5433,
        dbname="terramax_geo",
        user="terramax_geo",
        password=password,
    ) as connection:
        with connection.cursor() as cursor:
            for feature in features:
                h3_id = str(feature["properties"]["h3Index"])
                geometry = json.dumps(feature["geometry"])

                cursor.execute(
                    """
                    INSERT INTO h3_cells (h3_id, geometry, source)
                    VALUES (
                        %s,
                        ST_Multi(ST_SetSRID(ST_GeomFromGeoJSON(%s), 4326)),
                        %s
                    )
                    ON CONFLICT (h3_id)
                    DO UPDATE SET
                        geometry = EXCLUDED.geometry,
                        source = EXCLUDED.source
                    """,
                    (h3_id, geometry, "TerraMax H3 Resolution 6"),
                )

    print(f"Import erfolgreich: {len(features)} H3-Zellen")

if __name__ == "__main__":
    main()
