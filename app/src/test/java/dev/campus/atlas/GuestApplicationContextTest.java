package dev.campus.atlas;

import android.app.Application;
import android.app.Instrumentation;
import android.content.Context;
import android.content.ContextWrapper;
import java.lang.reflect.Field;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;
import top.niunaijun.blackbox.fake.delegate.AppInstrumentation;
import top.niunaijun.blackbox.fake.delegate.BaseInstrumentationDelegate;
import static org.junit.Assert.*;

/** Covers the pre-attach lifecycle contract used by guest application loaders. */
@RunWith(RobolectricTestRunner.class)
@Config(sdk=35, application=Application.class)
public class GuestApplicationContextTest {
    private static final class GuestContext extends ContextWrapper {
        private final Context application;
        GuestContext(Context application) { super(null); this.application=application; }
        @Override public String getPackageName() { return "test.guest.context"; }
        @Override public Context getApplicationContext() { return application; }
    }
    private static final class AttachedApplication extends Application {
        AttachedApplication(Context context) { attachBaseContext(context); }
    }
    private static class RecordingInstrumentation extends Instrumentation {
        Context supplied;
        @Override public Application newApplication(ClassLoader loader,String name,Context context)
                throws ClassNotFoundException {
            supplied=context;
            return new AttachedApplication(context);
        }
    }
    private AppInstrumentation delegate(Instrumentation base) throws Exception {
        AppInstrumentation delegate=new AppInstrumentation();
        Field field=BaseInstrumentationDelegate.class.getDeclaredField("mBaseInstrumentation");
        field.setAccessible(true);field.set(delegate,base);return delegate;
    }
    @Test public void preAttachNullApplicationContextDoesNotReplaceFrameworkContext() throws Exception {
        GuestContext original=new GuestContext(null);
        RecordingInstrumentation base=new RecordingInstrumentation();
        Application created=delegate(base).newApplication(getClass().getClassLoader(),AttachedApplication.class.getName(),original);
        assertSame(original,base.supplied);
        assertSame(original,created.getBaseContext());
        assertNull(original.getApplicationContext());
    }
    @Test public void establishedApplicationContextAlsoKeepsItsOriginalIdentity() throws Exception {
        GuestContext original=new GuestContext(new Application());
        RecordingInstrumentation base=new RecordingInstrumentation();
        Application created=delegate(base).newApplication(getClass().getClassLoader(),AttachedApplication.class.getName(),original);
        assertSame(original,created.getBaseContext());
        assertSame(original,base.supplied);
    }
    @Test public void missingGuestApplicationIsReportedWithoutReplacingItWithPlainApplication() throws Exception {
        ClassNotFoundException missing=new ClassNotFoundException("missing.guest.Application");
        Instrumentation base=new RecordingInstrumentation(){
            @Override public Application newApplication(ClassLoader loader,String name,Context context)
                    throws ClassNotFoundException { throw missing; }
        };
        try {
            delegate(base).newApplication(getClass().getClassLoader(),"missing.guest.Application",new GuestContext(null));
            fail("Expected the missing business class to be reported");
        } catch(ClassNotFoundException actual) { assertSame(missing,actual); }
    }
}
