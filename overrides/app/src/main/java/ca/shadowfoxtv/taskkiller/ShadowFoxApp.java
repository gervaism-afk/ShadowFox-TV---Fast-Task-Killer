package ca.shadowfoxtv.taskkiller;

import android.Manifest;
import android.app.Activity;
import android.app.Application;
import android.os.Build;
import android.os.Bundle;
import android.content.pm.PackageManager;
import android.content.SharedPreferences;

public class ShadowFoxApp extends Application implements Application.ActivityLifecycleCallbacks {
    private static final String PREFS = "shadowfox_update";
    private static final String ASKED_NOTIFICATIONS = "asked_notifications";

    @Override
    public void onCreate() {
        super.onCreate();
        UpdateScheduler.schedule(this);
        registerActivityLifecycleCallbacks(this);
    }

    @Override public void onActivityResumed(Activity activity) {
        if (Build.VERSION.SDK_INT >= 33) {
            SharedPreferences prefs = getSharedPreferences(PREFS, MODE_PRIVATE);
            boolean asked = prefs.getBoolean(ASKED_NOTIFICATIONS, false);
            if (!asked && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                prefs.edit().putBoolean(ASKED_NOTIFICATIONS, true).apply();
                activity.requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS}, 9031);
            }
        }
    }

    @Override public void onActivityCreated(Activity a, Bundle b) {}
    @Override public void onActivityStarted(Activity a) {}
    @Override public void onActivityPaused(Activity a) {}
    @Override public void onActivityStopped(Activity a) {}
    @Override public void onActivitySaveInstanceState(Activity a, Bundle b) {}
    @Override public void onActivityDestroyed(Activity a) {}
}