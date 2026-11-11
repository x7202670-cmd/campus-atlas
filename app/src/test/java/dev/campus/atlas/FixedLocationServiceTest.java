package dev.campus.atlas;

import android.app.*;
import android.content.*;
import android.os.Handler;
import android.os.Looper;
import dev.campus.atlas.core.*;
import org.junit.*;
import org.junit.runner.RunWith;
import org.robolectric.*;
import org.robolectric.android.controller.ServiceController;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.LooperMode;
import java.lang.reflect.Field;
import java.time.Duration;
import java.util.Arrays;
import static org.junit.Assert.*;
import static org.robolectric.Shadows.shadowOf;

@RunWith(RobolectricTestRunner.class)
@Config(sdk=35,application=Application.class)
@LooperMode(LooperMode.Mode.PAUSED)
public class FixedLocationServiceTest {
    Application app;PlaybackRuntime runtime;SystemLocationOutputTest.FakeBackend backend;ServiceController<FixedLocationService> controller;FixedLocationService service;Handler worker;
    @Before public void setup() throws Exception{app=RuntimeEnvironment.getApplication();app.getSharedPreferences("playback_recovery",0).edit().clear().commit();app.getSharedPreferences("atlas",0).edit().clear().commit();backend=new SystemLocationOutputTest.FakeBackend();runtime=new PlaybackRuntime(app,new SystemLocationOutput(app,backend));runtime.output.select(runtime.systemLocation);Field singleton=PlaybackRuntime.class.getDeclaredField("instance");singleton.setAccessible(true);singleton.set(null,runtime);controller=Robolectric.buildService(FixedLocationService.class).create();service=controller.get();Field field=FixedLocationService.class.getDeclaredField("worker");field.setAccessible(true);worker=(Handler)field.get(service);}
    @After public void teardown(){if(controller!=null)controller.destroy();}
    private void start(){service.onStartCommand(new Intent(service,FixedLocationService.class).putExtra("lat",30d).putExtra("lon",120d),0,1);shadowOf(worker.getLooper()).idle();}
    @Test public void fixedPointKeepsUpdatingOffTheMainThreadWithAVisibleStopAction(){start();assertNotSame(Looper.getMainLooper(),worker.getLooper());int before=backend.writes;shadowOf(worker.getLooper()).idleFor(Duration.ofSeconds(3));assertTrue(backend.writes>=before+20);Notification notification=shadowOf(service.getSystemService(NotificationManager.class)).getNotification(4202);assertNotNull(notification);assertEquals("停止并还原",notification.actions[0].title);}
    @Test public void destroyingFixedServiceRestoresProviders(){start();controller.destroy();controller=null;assertNull(runtime.output.last());assertTrue(backend.locations.isEmpty());assertFalse(app.getSharedPreferences("playback_recovery",0).contains("pending_target"));}
    @Test public void handingOffToRouteStopsFixedWritesWithoutResettingTheNewOwner(){start();FixedLocationService.stopForHandoff(app);runtime.player.start(new RoutePlan(Arrays.asList(new Point(31,121),new Point(31.01,121.01)),6,1,1000,true));int before=backend.writes;controller.destroy();controller=null;shadowOf(Looper.getMainLooper()).idleFor(Duration.ofSeconds(2));assertEquals(before,backend.writes);assertEquals(RoutePlayer.State.RUNNING,runtime.player.snapshot().state);assertEquals(31,runtime.output.last().lat,0);runtime.player.stop(true);}
    @Test public void changingFixedPointReusesProvidersAndDoesNotReportTeleportSpeed(){start();service.onStartCommand(new Intent(service,FixedLocationService.class).putExtra("lat",31d).putExtra("lon",121d),0,2);shadowOf(worker.getLooper()).idle();assertEquals(31,runtime.output.last().lat,0);assertEquals(2,backend.prepares);assertTrue(backend.removed.isEmpty());assertEquals(0,backend.locations.get("gps").getSpeed(),0);}
    @Test public void stoppedRequestCannotPublishAfterTheStop(){long request=runtime.newRequest();service.onStartCommand(new Intent(service,FixedLocationService.class).putExtra("lat",30d).putExtra("lon",120d).putExtra("request",request),0,1);runtime.newRequest();shadowOf(worker.getLooper()).idle();assertEquals(0,backend.writes);}
    @Test public void fixedPointRefreshesWithinReferenceCadence(){start();int writes=backend.writes;shadowOf(worker.getLooper()).idleFor(Duration.ofMillis(100));assertEquals(writes+2,backend.writes);shadowOf(worker.getLooper()).idleFor(Duration.ofMillis(400));assertEquals(writes+10,backend.writes);}
    @Test public void rejectedForegroundRequestPreservesExistingOutput(){start();Point before=runtime.output.last();Context rejecting=new ContextWrapper(app){@Override public ComponentName startForegroundService(Intent intent){throw new IllegalStateException("launch denied");}};try{FixedLocationService.start(rejecting,new Point(31,121));fail();}catch(IllegalStateException expected){}assertSame(before,runtime.output.last());int writes=backend.writes;shadowOf(worker.getLooper()).idleFor(Duration.ofSeconds(1));assertTrue(backend.writes>writes);assertTrue(backend.removed.isEmpty());}
}
