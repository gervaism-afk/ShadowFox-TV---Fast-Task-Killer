package ca.shadowfoxtv.taskkiller;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.os.SystemClock;

public final class UpdateScheduler {
    private static final long INTERVAL_MS = 6L * 60L * 60L * 1000L;
    private static final long FIRST_CHECK_DELAY_MS = 10L * 60L * 1000L;

    private UpdateScheduler() {}

    public static void schedule(Context context) {
        AlarmManager alarm = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
        if (alarm == null) return;
        Intent intent = new Intent(context, UpdateCheckReceiver.class);
        PendingIntent pending = PendingIntent.getBroadcast(
                context, 4401, intent, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        long first = SystemClock.elapsedRealtime() + FIRST_CHECK_DELAY_MS;
        alarm.setInexactRepeating(AlarmManager.ELAPSED_REALTIME_WAKEUP, first, INTERVAL_MS, pending);
    }

    public static void checkNow(Context context) {
        Intent intent = new Intent(context, UpdateCheckReceiver.class);
        context.sendBroadcast(intent);
    }
}