package ca.shadowfoxtv.taskkiller;

import android.app.Activity;
import android.app.Application;
import android.os.Bundle;

public class ShadowFoxApp extends Application implements Application.ActivityLifecycleCallbacks {
    @Override public void onCreate() {
        super.onCreate();
        StartupDiagnostics.INSTANCE.begin(this);
        StartupDiagnostics.INSTANCE.installExceptionHandler(this);
        registerActivityLifecycleCallbacks(this);
    }
    @Override public void onActivityCreated(Activity a, Bundle b) { StartupDiagnostics.INSTANCE.stage(this, "Activity created"); }
    @Override public void onActivityStarted(Activity a) { StartupDiagnostics.INSTANCE.stage(this, "Activity started"); }
    @Override public void onActivityResumed(Activity a) { StartupDiagnostics.INSTANCE.stage(this, "Activity resumed"); }
    @Override public void onActivityPaused(Activity a) { StartupDiagnostics.INSTANCE.stage(this, "Activity paused"); }
    @Override public void onActivityStopped(Activity a) { StartupDiagnostics.INSTANCE.stage(this, "Activity stopped"); }
    @Override public void onActivitySaveInstanceState(Activity a, Bundle b) { StartupDiagnostics.INSTANCE.stage(this, "Activity state saved"); }
    @Override public void onActivityDestroyed(Activity a) { StartupDiagnostics.INSTANCE.stage(this, "Activity destroyed"); }
}
