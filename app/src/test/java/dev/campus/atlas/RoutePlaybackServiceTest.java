package dev.campus.atlas;
import android.app.*;
import android.content.*;
import android.os.*;
import dev.campus.atlas.core.*;
import org.junit.*;
import org.junit.runner.RunWith;
import org.robolectric.*;
import org.robolectric.android.controller.ServiceController;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.LooperMode;
import org.robolectric.shadows.ShadowSystemClock;
import java.lang.reflect.Field;
import java.time.Duration;
import static org.junit.Assert.*;
import static org.robolectric.Shadows.shadowOf;

/** Test the Android lifecycle with a plain Application and preview sink, never native hooks. */
@RunWith(RobolectricTestRunner.class)
@Config(sdk=35,application=Application.class)
@LooperMode(LooperMode.Mode.PAUSED)
public class RoutePlaybackServiceTest {
    private ServiceController<RoutePlaybackService> controller;
    private RoutePlaybackService service;
    private PlaybackRuntime runtime;
    private Handler worker;
    private SystemLocationOutputTest.FakeBackend backend;
    private int id;
    @Before public void setup() throws Exception {
        Application app=RuntimeEnvironment.getApplication();
        app.getSharedPreferences("playback_recovery",0).edit().clear().commit();
        Field singleton=PlaybackRuntime.class.getDeclaredField("instance");singleton.setAccessible(true);singleton.set(null,null);
        backend=new SystemLocationOutputTest.FakeBackend();runtime=new PlaybackRuntime(app,new SystemLocationOutput(app,backend));singleton.set(null,runtime);
        controller=Robolectric.buildService(RoutePlaybackService.class).create();service=controller.get();
        Field field=RoutePlaybackService.class.getDeclaredField("worker");field.setAccessible(true);worker=(Handler)field.get(service);
    }
    @After public void teardown(){if(controller!=null)controller.destroy();}
    private void command(String action){service.onStartCommand(new Intent(service,RoutePlaybackService.class).setAction(action),0,++id);shadowOf(worker.getLooper()).idle();}
    private RoutePlan plan(long interval){return new RoutePlan(java.util.Arrays.asList(new Point(0,0),new Point(0,0.01)),5,2,interval,true);}
    private void start(){
        Intent intent=new Intent(service,RoutePlaybackService.class).setAction(RoutePlaybackService.START)
            .putExtra("request",runtime.newRouteRequest(plan(1000)));
        service.onStartCommand(intent,0,++id);shadowOf(worker.getLooper()).idle();
    }
    @Test public void importAndRouteStartsAreMutuallyExclusive(){
        assertTrue(runtime.beginRouteImport());
        try{runtime.newRouteRequest(plan(1000));fail("import must block route start");}catch(IllegalStateException expected){}
        runtime.finishRouteImport();long pending=runtime.newRouteRequest(plan(1000));
        assertFalse(runtime.beginRouteImport());runtime.cancelRouteRequest(pending);
        start();assertFalse(runtime.beginRouteImport());command(RoutePlaybackService.PAUSE);assertFalse(runtime.beginRouteImport());
    }
    private PowerManager.WakeLock wakeLock() throws Exception{Field field=RoutePlaybackService.class.getDeclaredField("wakeLock");field.setAccessible(true);return (PowerManager.WakeLock)field.get(service);}
    private Notification notification(){return shadowOf(service.getSystemService(NotificationManager.class)).getNotification(4201);}
    @Test public void playbackRunsWithoutAnActivityAndHasNotificationControls() throws Exception {
        start();assertEquals(RoutePlayer.State.RUNNING,runtime.player.snapshot().state);
        assertTrue(wakeLock().isHeld());assertEquals(2,notification().actions.length);assertEquals("暂停",notification().actions[0].title);
        ShadowSystemClock.advanceBy(Duration.ofSeconds(3));shadowOf(worker.getLooper()).idle();
        assertEquals(3000,runtime.player.snapshot().elapsedMillis);assertNotNull(runtime.output.last());
    }
    @Test public void pauseResumeReleasesAndReacquiresWakeLock() throws Exception {
        start();ShadowSystemClock.advanceBy(Duration.ofSeconds(3));shadowOf(worker.getLooper()).idle();command(RoutePlaybackService.PAUSE);
        assertFalse(wakeLock().isHeld());assertEquals("继续",notification().actions[0].title);
        ShadowSystemClock.advanceBy(Duration.ofSeconds(20));assertEquals(3000,runtime.player.snapshot().elapsedMillis);
        command(RoutePlaybackService.RESUME);assertTrue(wakeLock().isHeld());
        ShadowSystemClock.advanceBy(Duration.ofSeconds(2));shadowOf(worker.getLooper()).idle();assertEquals(5000,runtime.player.snapshot().elapsedMillis);
    }
    @Test public void stopClearsOutputAndWakeLock() throws Exception {
        start();command(RoutePlaybackService.STOP);assertEquals(RoutePlayer.State.IDLE,runtime.player.snapshot().state);
        assertNull(runtime.output.last());assertFalse(wakeLock().isHeld());
    }
    @Test public void destroyWhileRunningAttemptsRestore() throws Exception {
        start();controller.destroy();controller=null;
        assertEquals(RoutePlayer.State.IDLE,runtime.player.snapshot().state);assertNull(runtime.output.last());assertFalse(RoutePlaybackService.alive);assertFalse(wakeLock().isHeld());
    }
    @Test public void duplicateStartDoesNotStopTheActiveRoute(){start();start();assertEquals(RoutePlayer.State.RUNNING,runtime.player.snapshot().state);}
    @Test public void completionAutomaticallyRestores() throws Exception {
        start();ShadowSystemClock.advanceBy(Duration.ofMinutes(3));shadowOf(worker.getLooper()).idle();
        assertEquals(RoutePlayer.State.COMPLETED,runtime.player.snapshot().state);assertTrue(runtime.player.snapshot().restored);
        assertNull(runtime.output.last());assertFalse(wakeLock().isHeld());
    }
    @Test public void systemPauseMaintainsFreshStationaryLocations() throws Exception {
        runtime.output.select(runtime.systemLocation);start();
        ShadowSystemClock.advanceBy(Duration.ofSeconds(3));shadowOf(worker.getLooper()).idle();command(RoutePlaybackService.PAUSE);
        Point held=runtime.output.last();long time=backend.locations.get("gps").getElapsedRealtimeNanos();int writes=backend.writes;
        shadowOf(worker.getLooper()).idleFor(Duration.ofSeconds(20));
        assertEquals(RoutePlayer.State.PAUSED,runtime.player.snapshot().state);assertSame(held,runtime.output.last());assertEquals(3000,runtime.player.snapshot().elapsedMillis);
        assertTrue(backend.writes>writes);assertTrue(backend.locations.get("gps").getElapsedRealtimeNanos()>time);assertEquals(0,backend.locations.get("gps").getSpeed(),0);assertTrue(wakeLock().isHeld());
        command(RoutePlaybackService.RESUME);assertEquals(0,backend.locations.get("gps").getSpeed(),0);
    }
    @Test public void destroyingPreviousRouteCannotResetNewFixedOwner(){start();Object fixed=new Object();synchronized(runtime.player){runtime.player.stop(false);runtime.output.publish(new Point(30,120));runtime.owner=fixed;}controller.destroy();controller=null;assertEquals(30,runtime.output.last().lat,0);assertSame(fixed,runtime.owner);}
    @Test public void supersededRouteStartDoesNotTakeOverExistingOutput(){runtime.output.publish(new Point(30,120));long request=runtime.newRouteRequest(plan(1000));runtime.newRequest();Intent intent=new Intent(service,RoutePlaybackService.class).setAction(RoutePlaybackService.START).putExtra("request",request);service.onStartCommand(intent,0,++id);shadowOf(worker.getLooper()).idle();assertEquals(30,runtime.output.last().lat,0);assertEquals(RoutePlayer.State.IDLE,runtime.player.snapshot().state);}
    @Test public void invalidRouteRequestPreservesPreviousFixedOutput(){runtime.output.select(runtime.systemLocation);runtime.output.publish(new Point(30,120));Object fixed=new Object();runtime.owner=fixed;service.onStartCommand(new Intent(service,RoutePlaybackService.class).setAction(RoutePlaybackService.START).putExtra("request",runtime.newRequest()),0,++id);shadowOf(worker.getLooper()).idle();assertEquals(30,runtime.output.last().lat,0);assertSame(fixed,runtime.owner);assertTrue(backend.removed.isEmpty());assertFalse(runtime.serviceError.isEmpty());}
    @Test public void longSystemIntervalStillProducesFreshLocations(){runtime.output.select(runtime.systemLocation);Intent intent=new Intent(service,RoutePlaybackService.class).setAction(RoutePlaybackService.START).putExtra("request",runtime.newRouteRequest(plan(60000)));service.onStartCommand(intent,0,++id);shadowOf(worker.getLooper()).idle();int before=backend.writes;shadowOf(worker.getLooper()).idleFor(Duration.ofSeconds(6));assertTrue(backend.writes>=before+120);assertTrue(runtime.systemLocation.verified());}
    @Test public void veryLongRouteIsTransferredWithoutIntentPayload(){java.util.List<Point> points=new java.util.ArrayList<>();for(int i=0;i<50000;i++)points.add(new Point(0,i*0.000001));RoutePlan longRoute=new RoutePlan(points,5,2,1000,false);Intent intent=new Intent(service,RoutePlaybackService.class).setAction(RoutePlaybackService.START).putExtra("request",runtime.newRouteRequest(longRoute));android.os.Parcel parcel=android.os.Parcel.obtain();intent.writeToParcel(parcel,0);assertTrue(parcel.dataSize()<4096);parcel.recycle();service.onStartCommand(intent,0,++id);shadowOf(worker.getLooper()).idle();assertEquals(RoutePlayer.State.RUNNING,runtime.player.snapshot().state);assertNotNull(runtime.output.last());}
}
