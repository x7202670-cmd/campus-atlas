package dev.campus.atlas;

import com.amap.api.maps.model.LatLng;
import dev.campus.atlas.core.Point;

/** Converts between the app's WGS84 coordinates and AMap's GCJ-02 map coordinates. */
final class CoordinateTransform {
    private static final double PI = Math.PI;
    private static final double A = 6378245.0;
    private static final double EE = 0.00669342162296594323;

    private CoordinateTransform() {}

    static LatLng toGcj02(Point wgs84) {
        if (outsideChina(wgs84.lat, wgs84.lon)) return new LatLng(wgs84.lat, wgs84.lon);
        double[] delta = offset(wgs84.lat, wgs84.lon);
        return new LatLng(wgs84.lat + delta[0], wgs84.lon + delta[1]);
    }

    static Point toWgs84(LatLng gcj02) {
        if (outsideChina(gcj02.latitude, gcj02.longitude)) {
            return new Point(gcj02.latitude, gcj02.longitude);
        }
        double lat = gcj02.latitude;
        double lon = gcj02.longitude;
        // Iteratively remove the forward-transform error; this keeps taps aligned with the map.
        for (int i = 0; i < 8; i++) {
            LatLng projected = toGcj02(new Point(lat, lon));
            lat += gcj02.latitude - projected.latitude;
            lon += gcj02.longitude - projected.longitude;
        }
        return new Point(lat, lon);
    }

    private static boolean outsideChina(double lat, double lon) {
        return lon < 72.004 || lon > 137.8347 || lat < 0.8293 || lat > 55.8271;
    }

    private static double[] offset(double lat, double lon) {
        double dLat = transformLat(lon - 105.0, lat - 35.0);
        double dLon = transformLon(lon - 105.0, lat - 35.0);
        double radLat = lat / 180.0 * PI;
        double sin = Math.sin(radLat);
        double magic = 1 - EE * sin * sin;
        double sqrtMagic = Math.sqrt(magic);
        dLat = dLat * 180.0 / ((A * (1 - EE)) / (magic * sqrtMagic) * PI);
        dLon = dLon * 180.0 / (A / sqrtMagic * Math.cos(radLat) * PI);
        return new double[]{dLat, dLon};
    }

    private static double transformLat(double x, double y) {
        double value = -100.0 + 2.0 * x + 3.0 * y + 0.2 * y * y
                + 0.1 * x * y + 0.2 * Math.sqrt(Math.abs(x));
        value += (20.0 * Math.sin(6.0 * x * PI) + 20.0 * Math.sin(2.0 * x * PI)) * 2.0 / 3.0;
        value += (20.0 * Math.sin(y * PI) + 40.0 * Math.sin(y / 3.0 * PI)) * 2.0 / 3.0;
        value += (160.0 * Math.sin(y / 12.0 * PI) + 320 * Math.sin(y * PI / 30.0)) * 2.0 / 3.0;
        return value;
    }

    private static double transformLon(double x, double y) {
        double value = 300.0 + x + 2.0 * y + 0.1 * x * x
                + 0.1 * x * y + 0.1 * Math.sqrt(Math.abs(x));
        value += (20.0 * Math.sin(6.0 * x * PI) + 20.0 * Math.sin(2.0 * x * PI)) * 2.0 / 3.0;
        value += (20.0 * Math.sin(x * PI) + 40.0 * Math.sin(x / 3.0 * PI)) * 2.0 / 3.0;
        value += (150.0 * Math.sin(x / 12.0 * PI) + 300.0 * Math.sin(x / 30.0 * PI)) * 2.0 / 3.0;
        return value;
    }
}
