package dev.campus.atlas.core;

/** Derives motion from consecutive published points and monotonic time. */
public final class MotionEstimate {
    private static final long MIN_INTERVAL_NANOS = 100_000_000L;
    private static final long MAX_INTERVAL_NANOS = 60_000_000_000L;
    private static final double MAX_SPEED_METERS_PER_SECOND = 100.0;
    private static final double MIN_BEARING_DISTANCE_METERS = 0.01;
    private Point previous;
    private long previousNanos;
    private float bearing;
    private boolean hasBearing;

    public static final class Result {
        public final float speedMetersPerSecond, bearingDegrees;
        public final boolean hasBearing;
        private Result(float speed, float bearing, boolean hasBearing) {
            this.speedMetersPerSecond = speed;
            this.bearingDegrees = bearing;
            this.hasBearing = hasBearing;
        }
    }

    public synchronized Result update(Point point, long nanos) {
        if (point == null) throw new IllegalArgumentException("point is null");
        if (previous == null || nanos <= previousNanos || nanos - previousNanos > MAX_INTERVAL_NANOS) {
            previous = point;
            previousNanos = nanos;
            bearing = 0f;
            hasBearing = false;
            return new Result(0f, 0f, false);
        }
        long deltaNanos = nanos - previousNanos;
        if (deltaNanos < MIN_INTERVAL_NANOS) return new Result(0f, bearing, hasBearing);
        double distance = previous.distanceTo(point);
        double speed = distance / (deltaNanos / 1_000_000_000.0);
        if (!Double.isFinite(speed) || speed > MAX_SPEED_METERS_PER_SECOND) {
            previous = point;
            previousNanos = nanos;
            bearing = 0f;
            hasBearing = false;
            return new Result(0f, 0f, false);
        }
        if (distance > MIN_BEARING_DISTANCE_METERS) {
            double a = Math.toRadians(previous.lat), b = Math.toRadians(point.lat);
            double d = Math.toRadians(point.lon - previous.lon);
            bearing = (float) ((Math.toDegrees(Math.atan2(Math.sin(d) * Math.cos(b),
                    Math.cos(a) * Math.sin(b) - Math.sin(a) * Math.cos(b) * Math.cos(d))) + 360.0) % 360.0);
            if (bearing >= 360f || bearing < 0f) bearing = 0f;
            hasBearing = true;
        }
        previous = point;
        previousNanos = nanos;
        return new Result((float) speed, bearing, hasBearing);
    }

    public synchronized void reset() {
        previous = null;
        previousNanos = 0L;
        bearing = 0f;
        hasBearing = false;
    }
}
