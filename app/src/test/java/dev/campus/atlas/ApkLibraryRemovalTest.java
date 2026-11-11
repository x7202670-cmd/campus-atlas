package dev.campus.atlas;

import android.app.Application;
import java.io.File;
import org.json.*;
import org.junit.*;
import org.junit.runner.RunWith;
import org.robolectric.*;
import org.robolectric.annotation.Config;
import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class)
@Config(sdk=35,application=Application.class)
public class ApkLibraryRemovalTest {
    private Application app;
    private File dir;
    private final String a="aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa",b="bbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb";
    private JSONObject item(String pkg,String sha)throws Exception{return new JSONObject().put("package",pkg).put("sha256",sha).put("file",sha+".apk");}
    @Before public void setup()throws Exception{app=RuntimeEnvironment.getApplication();app.getSharedPreferences("atlas",0).edit().clear().commit();dir=new File(app.getFilesDir(),"original-apks");dir.mkdirs();new File(dir,a+".apk").createNewFile();new File(dir,b+".apk").createNewFile();}
    @Test public void deletesOnlySelectedPackageAfterConfirmedUninstall()throws Exception{JSONArray items=new JSONArray().put(item("example.remove",a)).put(item("example.keep",b));app.getSharedPreferences("atlas",0).edit().putString("apks",items.toString()).commit();boolean[] called={false};ApkLibrary.removePackage(app,"example.remove",pkg->{called[0]=true;assertEquals("example.remove",pkg);});assertTrue(called[0]);assertFalse(new File(dir,a+".apk").exists());assertTrue(new File(dir,b+".apk").exists());JSONArray left=new JSONArray(app.getSharedPreferences("atlas",0).getString("apks","[]"));assertEquals(1,left.length());assertEquals("example.keep",left.getJSONObject(0).getString("package"));}
    @Test public void uninstallFailureKeepsFilesAndRecords()throws Exception{String records=new JSONArray().put(item("example.remove",a)).toString();app.getSharedPreferences("atlas",0).edit().putString("apks",records).commit();try{ApkLibrary.removePackage(app,"example.remove",pkg->{throw new IllegalStateException("uninstall failed");});fail();}catch(IllegalStateException expected){}assertTrue(new File(dir,a+".apk").exists());assertEquals(records,app.getSharedPreferences("atlas",0).getString("apks",""));}
}
