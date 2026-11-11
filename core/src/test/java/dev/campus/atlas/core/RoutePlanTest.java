package dev.campus.atlas.core;
import org.junit.Test;
import static org.junit.Assert.*;
import java.util.*;

public class RoutePlanTest {
    private final Point start=new Point(0,0), end=new Point(0,0.01);
    @Test public void distanceMatchesPace(){RoutePlan r=new RoutePlan(Arrays.asList(start,end),5,20,1000,false);assertEquals(200,r.distanceAt(60000),0.001);assertEquals(200,start.distanceTo(r.positionAt(60000)),0.1);}
    @Test public void oneWayStopsAtEndpoint(){RoutePlan r=new RoutePlan(Arrays.asList(start,end),5,20,1000,false);assertEquals(end.lon,r.positionAt(99999999).lon,0);assertTrue(r.durationMillis<1200000);}
    @Test public void loopClosesWithoutTeleport(){RoutePlan r=new RoutePlan(Arrays.asList(start,end),5,20,1000,true);assertEquals(3,r.points.size());assertEquals(2*start.distanceTo(end),r.lengthMeters,0.01);assertTrue(r.positionAt(400000).lon<end.lon);}
    @Test public void durationCapsDistance(){RoutePlan r=new RoutePlan(Arrays.asList(start,end),5,1,1000,true);assertEquals(200,r.distanceAt(999999),0);}
    @Test public void duplicatePointsDoNotBreakInterpolation(){RoutePlan r=new RoutePlan(Arrays.asList(start,start,end),5,20,1000,false);assertTrue(r.positionAt(5000).lon>0);}
    @Test(expected=IllegalArgumentException.class) public void invalidPaceRejected(){new RoutePlan(Arrays.asList(start,end),Double.NaN,20,1000,true);}
    @Test(expected=IllegalArgumentException.class) public void zeroLengthRejected(){new RoutePlan(Arrays.asList(start,start),5,20,1000,true);}
    @Test(expected=IllegalArgumentException.class) public void invalidCoordinatesRejected(){new Point(91,0);}
    @Test public void antimeridianUsesShortPath(){Point p=new Point(0,179.9).interpolate(new Point(0,-179.9),0.5);assertEquals(180,Math.abs(p.lon),0.00001);}
    @Test public void parserKeepsUniqueCandidateLines(){assertEquals(Arrays.asList("东一教学楼 101","朝阳田径场"),TimetableParser.candidates("星期一\n东一教学楼 101\n朝阳田径场\n东一教学楼 101\n高等数学"));}
    @Test public void unavailableContainerNeverPretendsToLaunch(){ContainerEngine e=new ContainerEngine.Unavailable();assertFalse(e.isAvailable());try{e.launch("example.guest.app");fail();}catch(UnsupportedOperationException expected){}}
    @Test public void restoreClearsPreview(){LocationOutput.Preview p=new LocationOutput.Preview();p.publish(start);p.reset();assertNull(p.last());}
}
