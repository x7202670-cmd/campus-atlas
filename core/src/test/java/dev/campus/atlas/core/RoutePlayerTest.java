package dev.campus.atlas.core;
import org.junit.Test;
import static org.junit.Assert.*;
import java.util.Arrays;

public class RoutePlayerTest {
    private final long[] now={0};
    private final Sink sink=new Sink();
    private final RoutePlayer player=new RoutePlayer(()->now[0],sink);
    private RoutePlan plan(){return new RoutePlan(Arrays.asList(new Point(0,0),new Point(0,0.01)),5,2,1000,true);}
    private static class Sink implements LocationOutput {
        Point last;int writes,resets;boolean failWrite,failReset;
        public String description(){return "test";}
        public void publish(Point p){if(failWrite)throw new IllegalStateException("write");last=p;writes++;}
        public void reset(){if(failReset)throw new IllegalStateException("reset");last=null;resets++;}
    }
    @Test public void delayedTickUsesClockNotCallbackCount(){player.start(plan());now[0]=15000;player.tick();assertEquals(15000,player.snapshot().elapsedMillis);assertEquals(50,player.snapshot().distanceMeters,0.001);}
    @Test public void pauseDoesNotCountTimeInBackground(){player.start(plan());now[0]=10000;player.tick();player.pause();now[0]=90000;player.tick();assertEquals(10000,player.snapshot().elapsedMillis);player.resume();now[0]=95000;player.tick();assertEquals(15000,player.snapshot().elapsedMillis);}
    @Test public void pauseBetweenUpdatesKeepsProgressAtLastPublishedPosition(){player.start(plan());now[0]=50000;Point held=sink.last;player.pause();assertEquals(0,player.snapshot().elapsedMillis);assertEquals(0,player.snapshot().distanceMeters,0);assertSame(held,sink.last);now[0]=90000;player.resume();assertEquals(0,held.distanceTo(sink.last),0.001);now[0]=91000;player.tick();assertEquals(1000,player.snapshot().elapsedMillis);}
    @Test public void completionRestoresAndStopsWriting(){player.start(plan());now[0]=200000;player.tick();assertEquals(RoutePlayer.State.COMPLETED,player.snapshot().state);assertTrue(player.snapshot().restored);assertNull(sink.last);int writes=sink.writes;player.tick();assertEquals(writes,sink.writes);}
    @Test public void duplicateStartCannotRestartRunningSession(){player.start(plan());try{player.start(plan());fail();}catch(IllegalStateException expected){}assertEquals(1,sink.writes);}
    @Test public void writeFailureRestoresAndReportsError(){player.start(plan());sink.failWrite=true;now[0]=1000;player.tick();assertEquals(RoutePlayer.State.ERROR,player.snapshot().state);assertTrue(player.snapshot().restored);assertNull(sink.last);}
    @Test public void failedRestoreCanBeRetried(){player.start(plan());sink.failReset=true;try{player.stop(true);fail();}catch(IllegalStateException expected){}assertFalse(player.snapshot().restored);sink.failReset=false;player.stop(true);assertTrue(player.snapshot().restored);assertEquals(RoutePlayer.State.IDLE,player.snapshot().state);}
    @Test public void interruptedSessionNeverAutoResumes(){player.requireRecovery("interrupted");try{player.start(plan());fail();}catch(IllegalStateException expected){}assertEquals(0,sink.writes);player.stop(true);player.start(plan());assertEquals(RoutePlayer.State.RUNNING,player.snapshot().state);}
    @Test public void stoppedPlayerIgnoresOldScheduledCallbacks(){player.start(plan());player.stop(true);now[0]=60000;player.tick();assertEquals(1,sink.writes);assertFalse(player.snapshot().hasPlan);}
    @Test public void systemFailureCleansUpOutput(){player.start(plan());player.systemFailure("wake lock");assertEquals(RoutePlayer.State.ERROR,player.snapshot().state);assertTrue(player.snapshot().restored);assertNull(sink.last);}
    @Test public void pausedHeartbeatRefreshesWithoutAdvancingDistanceOrTime(){player.start(plan());now[0]=3000;player.tick();player.pause();Point held=sink.last;int before=sink.writes;now[0]=23000;player.hold();assertEquals(before+1,sink.writes);assertSame(held,sink.last);assertEquals(3000,player.snapshot().elapsedMillis);assertEquals(10,player.snapshot().distanceMeters,0.001);player.stop(true);player.hold();assertNull(sink.last);}
}
