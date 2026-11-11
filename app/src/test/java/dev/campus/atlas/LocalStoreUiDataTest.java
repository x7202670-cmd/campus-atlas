package dev.campus.atlas;

import android.app.Application;
import dev.campus.atlas.core.Point;
import org.junit.*;
import org.junit.runner.RunWith;
import org.robolectric.*;
import org.robolectric.annotation.Config;
import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class)
@Config(sdk=35,application=Application.class)
public class LocalStoreUiDataTest {
    private LocalStore store;
    @Before public void setup(){Application app=RuntimeEnvironment.getApplication();app.getSharedPreferences("atlas",0).edit().clear().commit();store=new LocalStore(app);}
    @Test public void renamingAnInactiveRouteDoesNotSwitchTheActiveRoute(){String original=store.activeRoute().id;store.createRoute("第二条");String current=store.activeRoute().id;store.renameRoute(original,"已改名");assertEquals(current,store.activeRoute().id);assertEquals("已改名",store.routes().get(0).name);}
    @Test public void manualPlaceRenameAndRemovalKeepTheCandidateListConsistent() throws Exception{store.addManualPlace("旧教学楼");store.bind("旧教学楼",new Point(30,120));store.renameBinding("旧教学楼","新教学楼",new Point(31,121));assertNull(store.bound("旧教学楼"));assertEquals(31,store.bound("新教学楼").lat,0);assertEquals("新教学楼",store.manualPlaces().get(0));store.removeBinding("新教学楼");assertTrue(store.manualPlaces().isEmpty());assertNull(store.bound("新教学楼"));}
    @Test public void duplicatePlaceNameDoesNotOverwriteAnotherBinding() throws Exception{store.bind("教学楼甲",new Point(30,120));store.bind("教学楼乙",new Point(31,121));try{store.renameBinding("教学楼甲","教学楼乙",new Point(32,122));fail("Duplicate should be rejected");}catch(IllegalArgumentException expected){}assertEquals(30,store.bound("教学楼甲").lat,0);assertEquals(31,store.bound("教学楼乙").lat,0);}
    @Test public void backupRestoresMoreThanOneHundredRoutesAndTwoThousandPoints(){java.util.List<Point> points=new java.util.ArrayList<>();for(int i=0;i<2001;i++)points.add(new Point(30,120+i*0.000001));store.saveRoute(points);for(int i=0;i<100;i++)store.createRoute("路线"+i);String backup=store.exportRoutes();assertEquals(101,store.importRoutes(backup));assertEquals(2001,store.routes().get(101).points.size());}
}
