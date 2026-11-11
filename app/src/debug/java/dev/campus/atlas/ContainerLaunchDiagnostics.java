package dev.campus.atlas;

import android.app.Activity;
import android.app.Instrumentation;
import android.app.Application;
import android.os.Bundle;
import android.os.SystemClock;

/** Debug-only driver for the same launch operation exposed by the space page. */
public final class ContainerLaunchDiagnostics extends Instrumentation {
    private String packageName;
    private volatile boolean hostForeground;
    private boolean importInstalledProbe;
    private final Application.ActivityLifecycleCallbacks lifecycle = new Application.ActivityLifecycleCallbacks() {
        @Override public void onActivityResumed(Activity activity) {
            if (activity instanceof MainActivity) hostForeground = true;
        }
        @Override public void onActivityPaused(Activity activity) {
            if (activity instanceof MainActivity) hostForeground = false;
        }
        @Override public void onActivityCreated(Activity activity, Bundle state) {}
        @Override public void onActivityStarted(Activity activity) {}
        @Override public void onActivityStopped(Activity activity) {}
        @Override public void onActivitySaveInstanceState(Activity activity, Bundle state) {}
        @Override public void onActivityDestroyed(Activity activity) {}
    };

    @Override public void onCreate(Bundle arguments) {
        super.onCreate(arguments);
        packageName = arguments == null ? null : arguments.getString("packageName");
        importInstalledProbe = arguments != null && "true".equals(arguments.getString("importInstalledProbe"));
        start();
    }

    @Override public void onStart() {
        Bundle result = new Bundle();
        int exitCode = Activity.RESULT_CANCELED;
        Application application = null;
        try {
            if (packageName == null || !packageName.matches("[A-Za-z0-9_]+(\\.[A-Za-z0-9_]+)+")) {
                throw new IllegalArgumentException("An explicit installed container packageName is required");
            }
            application = (Application) getTargetContext().getApplicationContext();
            application.registerActivityLifecycleCallbacks(lifecycle);
            result.putString("stage", "await_host_foreground");
            sendStatus(0, result);
            long foregroundDeadline = SystemClock.elapsedRealtime() + 30000;
            while (!hostForeground && SystemClock.elapsedRealtime() < foregroundDeadline) {
                SystemClock.sleep(100);
            }
            if (!hostForeground) {
                throw new IllegalStateException("Open MainActivity from the host before launching the guest");
            }
            BlackBoxEngine engine = new BlackBoxEngine(getTargetContext());
            long deadline = SystemClock.elapsedRealtime() + 10000;
            while (!engine.isAvailable() && SystemClock.elapsedRealtime() < deadline) {
                SystemClock.sleep(100);
            }
            if (importInstalledProbe) {
                if (!"dev.campus.atlas.probe".equals(packageName)) {
                    throw new IllegalArgumentException("Probe import is restricted to our diagnostic APK");
                }
                top.niunaijun.blackbox.entity.pm.InstallResult install =
                        top.niunaijun.blackbox.BlackBoxCore.get().installPackageAsUser(packageName, 0);
                if (!install.success) throw new IllegalStateException("Probe import failed: " + install.msg);
            }
            engine.launch(packageName);
            result.putString("packageName", packageName);
            result.putString("stage", "launch_requested");
            sendStatus(0, result);
            SystemClock.sleep(8000);
            // A submitted launch does not prove that the guest initialized successfully.
            result.putString("stage", "observation_window_finished");
            result.putString("result", "Inspect the guest runtime logs for startup outcome");
            exitCode = Activity.RESULT_OK;
        } catch (Throwable error) {
            result.putString("stage", "launch_request_failed");
            result.putString("error", error.getClass().getSimpleName() + ": " + error.getMessage());
        } finally {
            if (application != null) application.unregisterActivityLifecycleCallbacks(lifecycle);
        }
        finish(exitCode, result);
    }
}
