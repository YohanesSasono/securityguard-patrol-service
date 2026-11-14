package com.securityguard.patrol.domain;

import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.GeometryFactory;
import org.locationtech.jts.geom.Point;
import org.locationtech.jts.geom.PrecisionModel;

/**
 * Reusable JTS/PostGIS point construction (SRID 4326 = WGS84 lat/lng), used
 * by the `site` and (from Phase 5) `patrol` modules. A stable technical
 * primitive per ARCHITECTURE_V2.txt's "Shared" section.
 */
public final class GeoPointFactory {

    private static final int WGS84_SRID = 4326;
    private static final GeometryFactory FACTORY = new GeometryFactory(new PrecisionModel(), WGS84_SRID);

    private GeoPointFactory() {
    }

    /** JTS Coordinate order is (x, y) = (longitude, latitude). */
    public static Point point(double latitude, double longitude) {
        Point point = FACTORY.createPoint(new Coordinate(longitude, latitude));
        point.setSRID(WGS84_SRID);
        return point;
    }
}
