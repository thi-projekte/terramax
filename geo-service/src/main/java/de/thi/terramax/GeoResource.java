
package de.thi.terramax;

import jakarta.annotation.security.RolesAllowed;
import jakarta.inject.Inject;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;

@Path("/api/geo")
public class GeoResource {

    @Inject
    DataSource dataSource;

    @GET
    @Path("/h3")
    @Produces(MediaType.APPLICATION_JSON)
    @RolesAllowed({ Roles.SALES_PROFESSIONAL, Roles.DEALER_PRINCIPAL })
    public Response getH3Cells() throws Exception {

        String sql = """
                SELECT json_build_object(
                    'type', 'FeatureCollection',
                    'features', COALESCE(
                        json_agg(
                            json_build_object(
                                'type', 'Feature',
                                'geometry', ST_AsGeoJSON(h.geometry)::json,
                                'properties', json_build_object(
                                    'h3Index', h.h3_id,
                                    'cropType', c.crop_type,
                                    'areaHa', c.area_ha,
                                    'source', c.source,
                                    'dataDate', c.data_date,
                                    'confidence', c.confidence
                                )
                            )
                        ) FILTER (WHERE h.h3_id IS NOT NULL),
                        '[]'::json
                    )
                )::text AS geojson
                FROM h3_cells h
                LEFT JOIN h3_crop_areas c ON h.h3_id = c.h3_id
                """;

        try (Connection connection = dataSource.getConnection();
                PreparedStatement statement = connection.prepareStatement(sql);
                ResultSet result = statement.executeQuery()) {

            result.next();

            return Response.ok(result.getString("geojson")).build();
        }
    }
}
