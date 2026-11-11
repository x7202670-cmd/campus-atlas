package dev.campus.atlas.core;
import org.junit.Test;
import static org.junit.Assert.*;

public class RoutedOutputTest {
    private static class Target implements LocationOutput {
        Point position;boolean failWrite,failReset;int resets;
        public String description(){return "target";}
        public void publish(Point p){if(failWrite)throw new IllegalStateException("write failed");position=p;}
        public void reset(){if(failReset)throw new IllegalStateException("reset failed");position=null;resets++;}
    }
    @Test public void switchResetsPreviousTarget(){RoutedOutput r=new RoutedOutput();Target old=new Target(),next=new Target();r.select(old);r.publish(new Point(1,2));r.select(next);assertNull(old.position);assertEquals(1,old.resets);assertNull(r.last());r.publish(new Point(3,4));assertEquals(3,next.position.lat,0);}
    @Test public void failedWriteDoesNotClaimPositionWasApplied(){RoutedOutput r=new RoutedOutput();Target target=new Target();r.select(target);target.failWrite=true;try{r.publish(new Point(1,2));fail();}catch(IllegalStateException expected){}assertNull(r.last());}
    @Test public void failedResetKeepsOldTargetForRetry(){RoutedOutput r=new RoutedOutput();Target target=new Target();r.select(target);Point p=new Point(1,2);r.publish(p);target.failReset=true;try{r.preview();fail();}catch(IllegalStateException expected){}assertSame(p,r.last());assertEquals("target",r.description());target.failReset=false;r.preview();assertNull(target.position);assertNull(r.last());}
}
