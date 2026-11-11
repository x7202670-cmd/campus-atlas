package dev.campus.atlas.core;

import java.util.Objects;

/** All coordinates in this prototype are WGS84. */
public final class Point {
    public final double lat, lon;
    public Point(double lat, double lon) {
        if (!Double.isFinite(lat) || !Double.isFinite(lon) || Math.abs(lat)>90 || Math.abs(lon)>180)
            throw new IllegalArgumentException("经纬度超出范围");
        this.lat=lat; this.lon=lon;
    }
    public double distanceTo(Point p) {
        double a=Math.toRadians(p.lat-lat), b=Math.toRadians(p.lon-lon);
        double h=Math.pow(Math.sin(a/2),2)+Math.cos(Math.toRadians(lat))*Math.cos(Math.toRadians(p.lat))*Math.pow(Math.sin(b/2),2);
        return 6371008.8*2*Math.asin(Math.sqrt(Math.min(1,h)));
    }
    public Point interpolate(Point p, double fraction) {
        double d=p.lon-lon;
        if(d>180) d-=360; if(d< -180) d+=360;
        double x=lon+d*fraction; if(x>180)x-=360; if(x< -180)x+=360;
        return new Point(lat+(p.lat-lat)*fraction,x);
    }
    @Override public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof Point)) return false;
        Point p = (Point) o;
        return Double.compare(p.lat, lat) == 0 && Double.compare(p.lon, lon) == 0;
    }
    @Override public int hashCode() {
        return Objects.hash(lat, lon);
    }
    @Override public String toString() {
        return "Point(" + lat + ", " + lon + ")";
    }
}
