package ca.shadowfoxtv.taskkiller;

import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageInfo;
import android.os.Build;

import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;

public class UpdateCheckReceiver extends BroadcastReceiver {
    private static final String API = "https://api.github.com/repos/gervaism-afk/ShadowFox-TV---Fast-Task-Killer/releases/latest";
    private static final String CHANNEL_ID = "shadowfox_updates";

    @Override
    public void onReceive(Context context, Intent intent) {
        PendingResult pendingResult = goAsync();
        new Thread(() -> {
            try {
                String current = getCurrentVersion(context);
                JSONObject release = fetchLatest();
                if (release == null) return;
                String latest = release.optString("tag_name", "").replaceFirst("^[vV]", "");
                if (latest.isEmpty() || compareVersions(latest, current) <= 0) return;
                showNotification(context, latest);
            } finally {
                pendingResult.finish();
            }
        }, "ShadowFoxUpdateCheck").start();
    }

    private static JSONObject fetchLatest() {
        HttpURLConnection connection = null;
        try {
            connection = (HttpURLConnection) new URL(API).openConnection();
            connection.setConnectTimeout(5000);
            connection.setReadTimeout(7000);
            connection.setRequestProperty("Accept", "application/vnd.github+json");
            connection.setRequestProperty("User-Agent", "ShadowFox-TV-Task-Killer-Updater");
            if (connection.getResponseCode() != 200) return null;
            BufferedReader reader = new BufferedReader(new InputStreamReader(connection.getInputStream()));
            StringBuilder json = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) json.append(line);
            reader.close();
            return new JSONObject(json.toString());
        } catch (Exception ignored) {
            return null;
        } finally {
            if (connection != null) connection.disconnect();
        }
    }

    private static String getCurrentVersion(Context context) {
        try {
            PackageInfo info = context.getPackageManager().getPackageInfo(context.getPackageName(), 0);
            return info.versionName == null ? "0" : info.versionName;
        } catch (Exception ignored) {
            return "0";
        }
    }

    private static void showNotification(Context context, String latest) {
        NotificationManager manager = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
        if (manager == null) return;
        if (Build.VERSION.SDK_INT >= 26) {
            NotificationChannel channel = new NotificationChannel(CHANNEL_ID, "ShadowFox Updates", NotificationManager.IMPORTANCE_HIGH);
            channel.setDescription("New ShadowFox TV - Task Killer versions");
            manager.createNotificationChannel(channel);
        }

        Intent open = new Intent(context, MainActivity.class);
        open.putExtra("shadowfox_update_center", true);
        open.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        PendingIntent contentIntent = PendingIntent.getActivity(context, 8801, open,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

        android.app.Notification.Builder builder = Build.VERSION.SDK_INT >= 26
                ? new android.app.Notification.Builder(context, CHANNEL_ID)
                : new android.app.Notification.Builder(context);
        builder.setSmallIcon(android.R.drawable.stat_sys_download_done)
                .setContentTitle("ShadowFox update available")
                .setContentText("Version " + latest + " is ready. Select to update.")
                .setContentIntent(contentIntent)
                .setAutoCancel(true)
                .setPriority(android.app.Notification.PRIORITY_HIGH);
        manager.notify(8801, builder.build());
    }

    private static int compareVersions(String a, String b) {
        String[] aa = a.split("\\.");
        String[] bb = b.split("\\.");
        int max = Math.max(aa.length, bb.length);
        for (int i = 0; i < max; i++) {
            int av = i < aa.length ? parsePart(aa[i]) : 0;
            int bv = i < bb.length ? parsePart(bb[i]) : 0;
            if (av != bv) return Integer.compare(av, bv);
        }
        return 0;
    }

    private static int parsePart(String value) {
        try {
            return Integer.parseInt(value.replaceAll("[^0-9].*$", ""));
        } catch (Exception ignored) {
            return 0;
        }
    }
}
