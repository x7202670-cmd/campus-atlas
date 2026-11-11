package dev.campus.atlas;

import android.app.Application;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageInfo;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import java.io.File;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.Shadows;
import org.robolectric.annotation.Config;
import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class)
@Config(sdk=35,application=Application.class)
public class ApkPresentationLoaderTest {
    @Test public void readsArchivedLabelAndIconAndRejectsWrongPackage() throws Exception {
        Application context=RuntimeEnvironment.getApplication();File file=File.createTempFile("atlas-archive-",".apk",context.getCacheDir());
        PackageInfo info=new PackageInfo();info.packageName="example.map";info.applicationInfo=new ApplicationInfo();info.applicationInfo.packageName=info.packageName;info.applicationInfo.nonLocalizedLabel="地图应用";
        Shadows.shadowOf(context.getPackageManager()).setPackageArchiveInfo(file.getAbsolutePath(),info);
        Shadows.shadowOf(context.getPackageManager()).setApplicationIcon(info.packageName,new ColorDrawable(Color.GREEN));
        ApkPresentationLoader loader=new ApkPresentationLoader(context);
        try{ApkPresentationLoader.Presentation found=loader.readArchive("example.map",file);assertEquals("地图应用",found.label);assertNotNull(found.icon);assertEquals(file.getAbsolutePath(),info.applicationInfo.publicSourceDir);
            ApkPresentationLoader.Presentation wrong=loader.readArchive("example.other",file);assertEquals("",wrong.label);assertNull(wrong.icon);
        }finally{loader.close();file.delete();}
    }
}
