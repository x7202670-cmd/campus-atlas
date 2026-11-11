package dev.campus.atlas.probe;

import android.app.Application;
import android.util.Log;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;

/** Tests only a temporary marker owned by this probe; never opens third-party app data. */
public final class IoProbeApplication extends Application {
    private static final String TAG = "AtlasIoProbe";
    private static native int readMarker(String path, boolean directSyscall);
    private static native int createMarker(String path, String directory, int kind);

    @Override public void onCreate() {
        super.onCreate();
        File marker = null;
        try {
            System.loadLibrary("atlas_io_probe");
            marker = File.createTempFile("atlas-io-", ".tmp", getFilesDir());
            try (FileOutputStream out = new FileOutputStream(marker)) { out.write(0x5a); }
            String actual = marker.getAbsolutePath();
            String logical = "/data/user/0/" + getPackageName() + "/files/" + marker.getName();
            int javaLogical;
            try (FileInputStream in = new FileInputStream(logical)) {
                javaLogical = in.read() == 0x5a ? 0 : -1;
            } catch (Exception error) {
                javaLogical = -2;
            }
            Log.i(TAG, "paths: actual=" + actual + ", logical=" + logical);
            Log.i(TAG, "result: javaLogical=" + javaLogical
                    + ", libcActual=" + readMarker(actual, false)
                    + ", syscallActual=" + readMarker(actual, true)
                    + ", libcLogical=" + readMarker(logical, false)
                    + ", syscallLogical=" + readMarker(logical, true)
                    + "; 0=marker matched, positive=errno, -1=wrong bytes, -2=Java failure");
            for (int kind = 0; kind < 4; kind++) {
                File created = File.createTempFile("atlas-create-", ".tmp", getFilesDir());
                try {
                    if (!created.delete()) throw new IllegalStateException("Cannot prepare creation test");
                    String logicalCreated = "/data/user/0/" + getPackageName() + "/files/" + created.getName();
                    int result = createMarker(logicalCreated, getFilesDir().getAbsolutePath(), kind);
                    boolean matches = false;
                    if (result == 0) {
                        try (FileInputStream in = new FileInputStream(created)) { matches = in.read() == 0x5a; }
                    }
                    Log.i(TAG, "create: kind=" + kind + ", result=" + result + ", markerMatched=" + matches);
                } finally {
                    if (created.exists() && !created.delete()) Log.w(TAG, "Creation marker cleanup failed");
                }
            }
        } catch (Throwable error) {
            Log.e(TAG, "Probe unavailable", error);
        } finally {
            if (marker != null && !marker.delete()) Log.w(TAG, "Temporary marker cleanup failed");
        }
    }
}
