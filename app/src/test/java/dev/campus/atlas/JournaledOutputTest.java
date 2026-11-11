package dev.campus.atlas;
import android.app.Application;
import android.content.SharedPreferences;
import dev.campus.atlas.core.*;
import org.junit.*;
import org.junit.runner.RunWith;
import org.robolectric.*;
import org.robolectric.annotation.Config;
import java.lang.reflect.Field;
import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class)
@Config(sdk=35,application=Application.class)
public class JournaledOutputTest {
    private SharedPreferences prefs;
    private final Sink sink=new Sink();
    private JournaledOutput output;
    private static class Sink implements LocationOutput {
        boolean failWrite,failReset;
        public String description(){return "test";}
        public void publish(Point p){if(failWrite)throw new IllegalStateException("write");}
        public void reset(){if(failReset)throw new IllegalStateException("reset");}
    }
    @Before public void setup(){Application app=RuntimeEnvironment.getApplication();prefs=app.getSharedPreferences("playback_recovery",0);prefs.edit().clear().commit();output=new JournaledOutput(app,"test.guest",sink);}
    @Test public void failedPublishStillLeavesCleanupJournal(){sink.failWrite=true;try{output.publish(new Point(1,2));fail();}catch(IllegalStateException expected){}assertEquals("test.guest",prefs.getString("pending_target",""));}
    @Test public void failedResetKeepsJournalForRetry(){output.publish(new Point(1,2));sink.failReset=true;try{output.reset();fail();}catch(IllegalStateException expected){}assertEquals("test.guest",prefs.getString("pending_target",""));}
    @Test public void successfulResetClearsJournal(){output.publish(new Point(1,2));output.reset();assertFalse(prefs.contains("pending_target"));}
    @Test public void newProcessRestoresTargetWithoutPublishingOrAutoPlaying() throws Exception {
        prefs.edit().putString("pending_target","test.guest").commit();
        Field singleton=PlaybackRuntime.class.getDeclaredField("instance");singleton.setAccessible(true);singleton.set(null,null);
        PlaybackRuntime recovered=PlaybackRuntime.get(RuntimeEnvironment.getApplication());
        assertEquals(RoutePlayer.State.ERROR,recovered.player.snapshot().state);assertFalse(recovered.player.snapshot().restored);
        assertNull(recovered.output.last());assertTrue(recovered.output.description().contains("test.guest"));
    }
}
