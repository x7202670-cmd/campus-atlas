package dev.campus.atlas;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import com.amap.api.maps.model.LatLng;
import dev.campus.atlas.core.Point;
import org.junit.Test;

public final class CoordinateTransformTest {
    @Test public void mainlandCoordinatesRoundTripWithinOneMeter() {
        Point original = new Point(30.263, 120.122);
        LatLng mapCoordinate = CoordinateTransform.toGcj02(original);
        Point restored = CoordinateTransform.toWgs84(mapCoordinate);
        assertTrue("GCJ-02 point should be offset from WGS84 in mainland China",
                Math.abs(mapCoordinate.latitude - original.lat) > 0.001);
        assertTrue("round-trip error was " + original.distanceTo(restored) + " m",
                original.distanceTo(restored) < 1.0);
    }

    @Test public void coordinatesOutsideChinaRemainUnchanged() {
        Point original = new Point(48.8584, 2.2945);
        LatLng mapCoordinate = CoordinateTransform.toGcj02(original);
        Point restored = CoordinateTransform.toWgs84(mapCoordinate);
        assertEquals(original.lat, mapCoordinate.latitude, 0.0);
        assertEquals(original.lon, mapCoordinate.longitude, 0.0);
        assertEquals(original.lat, restored.lat, 0.0);
        assertEquals(original.lon, restored.lon, 0.0);
    }
}
