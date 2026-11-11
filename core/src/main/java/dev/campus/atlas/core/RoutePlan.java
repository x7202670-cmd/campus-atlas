package dev.campus.atlas.core;
import java.util.*;

/** Immutable route; elapsed time is supplied by a monotonic clock. */
public final class RoutePlan {
    public final List<Point> points;
    public final double lengthMeters, metersPerSecond;
    public final long durationMillis, intervalMillis;
    public final boolean loop;
    public RoutePlan(List<Point> source, double paceMinutes, double durationMinutes, long intervalMillis, boolean loop) {
        if(source==null || source.size()<2) throw new IllegalArgumentException("至少添加两个路线点");
        if(!Double.isFinite(paceMinutes)||paceMinutes<1||paceMinutes>60) throw new IllegalArgumentException("配速范围为 1–60 分钟/公里");
        if(!Double.isFinite(durationMinutes)||durationMinutes<=0||durationMinutes>240) throw new IllegalArgumentException("时长范围为 0–240 分钟");
        if(intervalMillis<250||intervalMillis>60000) throw new IllegalArgumentException("更新间隔范围为 0.25–60 秒");
        ArrayList<Point> copy=new ArrayList<>(source);
        if(loop && copy.get(copy.size()-1).distanceTo(copy.get(0))>0.01) copy.add(copy.get(0));
        double length=0;
        for(int i=1;i<copy.size();i++) length+=copy.get(i-1).distanceTo(copy.get(i));
        if(length<1) throw new IllegalArgumentException("路线长度不足 1 米");
        points=Collections.unmodifiableList(copy);lengthMeters=length;metersPerSecond=1000/(paceMinutes*60);
        this.loop=loop;this.intervalMillis=intervalMillis;
        long requested=(long)(durationMinutes*60000);
        durationMillis=loop?requested:Math.min(requested,(long)Math.ceil(length/metersPerSecond*1000));
    }
    public double distanceAt(long elapsed) { return Math.min(Math.max(0,elapsed),durationMillis)/1000.0*metersPerSecond; }
    public Point positionAt(long elapsed) {
        double distance=distanceAt(elapsed);
        if(loop)distance%=lengthMeters;
        else if(distance>=lengthMeters)return points.get(points.size()-1);
        for(int i=1;i<points.size();i++) {
            double segment=points.get(i-1).distanceTo(points.get(i));
            if(segment>0 && distance<=segment)return points.get(i-1).interpolate(points.get(i),distance/segment);
            distance-=segment;
        }
        return points.get(points.size()-1);
    }
}
