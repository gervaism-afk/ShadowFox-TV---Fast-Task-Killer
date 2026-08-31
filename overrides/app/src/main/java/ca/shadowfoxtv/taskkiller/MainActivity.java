package ca.shadowfoxtv.taskkiller;

import android.app.Activity;
import android.app.ActivityManager;
import android.app.DownloadManager;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.provider.Settings;
import android.view.View;
import android.view.animation.Animation;
import android.view.animation.AnimationUtils;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class MainActivity extends Activity {
    private static final String LATEST_RELEASE_API = "https://api.github.com/repos/gervaism-afk/ShadowFox-TV---Fast-Task-Killer/releases/latest";
    private static final String UPDATE_PREFS = "shadowfox_update";

    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private final ExecutorService updateExecutor = Executors.newSingleThreadExecutor();
    private final List<String> userPackages = new ArrayList<>();
    private final Set<String> protectedPackages = new HashSet<>();

    private TextView summaryText;
    private TextView detectedAppsText;
    private TextView appsCleanedText;
    private TextView ramRecoveredText;
    private TextView storageRecoveredText;
    private View resultCard;
    private Button cleanButton;
    private Button rescanButton;
    private boolean cleaning = false;
    private boolean updateCheckStarted = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        summaryText = findViewById(R.id.summaryText);
        detectedAppsText = findViewById(R.id.detectedAppsText);
        appsCleanedText = findViewById(R.id.appsCleanedText);
        ramRecoveredText = findViewById(R.id.ramRecoveredText);
        storageRecoveredText = findViewById(R.id.storageRecoveredText);
        resultCard = findViewById(R.id.resultCard);
        cleanButton = findViewById(R.id.cleanButton);
        rescanButton = findViewById(R.id.rescanButton);

        protectedPackages.add(getPackageName());
        protectedPackages.add("com.android.systemui");
        protectedPackages.add("com.google.android.tvlauncher");
        protectedPackages.add("com.google.android.apps.tv.launcherx");
        protectedPackages.add("com.google.android.tv.remote.service");
        protectedPackages.add("com.android.settings");

        cleanButton.setOnClickListener(v -> cleanNow());
        rescanButton.setOnClickListener(v -> scanApps());

        addTvFocusEffect(cleanButton);
        addTvFocusEffect(rescanButton);

        scanApps();
        cleanButton.requestFocus();

        View root = findViewById(R.id.rootLayout);
        View header = findViewById(R.id.headerCard);
        View status = findViewById(R.id.statusCard);
        Animation intro = AnimationUtils.loadAnimation(this, R.anim.fade_scale_in);
        root.startAnimation(intro);
        header.startAnimation(AnimationUtils.loadAnimation(this, R.anim.fade_scale_in));
        status.startAnimation(AnimationUtils.loadAnimation(this, R.anim.fade_scale_in));

        resumePendingUpdateOrCheck();
    }

    @Override
    protected void onResume() {
        super.onResume();
        SharedPreferences prefs = getSharedPreferences(UPDATE_PREFS, MODE_PRIVATE);
        String pendingUri = prefs.getString("pending_install_uri", null);
        if (pendingUri != null && canInstallPackages()) {
            prefs.edit().remove("pending_install_uri").apply();
            launchPackageInstaller(Uri.parse(pendingUri));
        }
    }

    private void addTvFocusEffect(View view) {
        view.setOnFocusChangeListener((v, hasFocus) -> {
            float scale = hasFocus ? 1.035f : 1.0f;
            v.animate().scaleX(scale).scaleY(scale).setDuration(120).start();
            v.setElevation(hasFocus ? 18f : 2f);
        });
    }

    private void scanApps() {
        if (!cleaning) {
            summaryText.setText(R.string.scanning);
            if (detectedAppsText != null) detectedAppsText.setText(R.string.scanning_apps);
        }

        executor.execute(() -> {
            PackageManager pm = getPackageManager();
            List<ApplicationInfo> installed = pm.getInstalledApplications(0);
            List<String> found = new ArrayList<>();

            for (ApplicationInfo info : installed) {
                boolean system = (info.flags & ApplicationInfo.FLAG_SYSTEM) != 0;
                boolean updatedSystem = (info.flags & ApplicationInfo.FLAG_UPDATED_SYSTEM_APP) != 0;
                if (system && !updatedSystem) continue;
                if (protectedPackages.contains(info.packageName)) continue;
                found.add(info.packageName);
            }

            runOnUiThread(() -> {
                userPackages.clear();
                userPackages.addAll(found);
                if (!cleaning) {
                    if (detectedAppsText != null) {
                        summaryText.setText(R.string.ready);
                        detectedAppsText.setText(userPackages.size() + " BACKGROUND APPS DETECTED");
                    } else {
                        summaryText.setText("READY  •  " + userPackages.size() + " BACKGROUND APPS");
                    }
                }
            });
        });
    }

    private void cleanNow() {
        if (cleaning) return;

        cleaning = true;
        cleanButton.setEnabled(false);
        rescanButton.setEnabled(false);
        cleanButton.setText(R.string.cleaning_button);
        summaryText.setText(R.string.cleaning_status);
        if (detectedAppsText != null) {
            detectedAppsText.setText(R.string.cleaning_detail);
            appsCleanedText.setText("—");
            ramRecoveredText.setText("—");
        }

        final List<String> targets = new ArrayList<>(userPackages);
        final long ramBefore = getAvailableRamBytes();

        executor.execute(() -> {
            int appsCleaned = 0;
            ActivityManager am = (ActivityManager) getSystemService(Context.ACTIVITY_SERVICE);

            if (am != null) {
                for (String pkg : targets) {
                    if (protectedPackages.contains(pkg)) continue;
                    try {
                        am.killBackgroundProcesses(pkg);
                        appsCleaned++;
                    } catch (Exception ignored) {}
                }
            }

            try {
                Thread.sleep(1200);
            } catch (InterruptedException ignored) {
                Thread.currentThread().interrupt();
            }

            final long ramAfter = getAvailableRamBytes();
            final long ramRecovered = Math.max(0L, ramAfter - ramBefore);
            final int cleanedCount = appsCleaned;

            runOnUiThread(() -> {
                cleaning = false;
                cleanButton.setEnabled(true);
                rescanButton.setEnabled(true);
                cleanButton.setText(R.string.clean_now);
                cleanButton.startAnimation(AnimationUtils.loadAnimation(this, R.anim.button_pop));
                summaryText.setText(R.string.cleanup_complete);
                if (detectedAppsText != null) detectedAppsText.setText(cleanedCount + " APPS PROCESSED");
                appsCleanedText.setText(String.valueOf(cleanedCount));
                ramRecoveredText.setText(formatMb(ramRecovered));
                storageRecoveredText.setText(R.string.storage_restricted);
                resultCard.setVisibility(View.VISIBLE);
                resultCard.startAnimation(AnimationUtils.loadAnimation(this, R.anim.fade_scale_in));
                cleanButton.requestFocus();
            });
        });
    }

    private void resumePendingUpdateOrCheck() {
        SharedPreferences prefs = getSharedPreferences(UPDATE_PREFS, MODE_PRIVATE);
        long pendingId = prefs.getLong("download_id", -1L);
        String pendingVersion = prefs.getString("download_version", null);
        if (pendingId > 0 && pendingVersion != null) {
            monitorDownload(pendingId, pendingVersion);
            return;
        }
        checkForUpdates();
    }

    private void checkForUpdates() {
        if (updateCheckStarted) return;
        updateCheckStarted = true;
        updateExecutor.execute(() -> {
            HttpURLConnection connection = null;
            try {
                connection = (HttpURLConnection) new URL(LATEST_RELEASE_API).openConnection();
                connection.setConnectTimeout(5000);
                connection.setReadTimeout(7000);
                connection.setRequestProperty("Accept", "application/vnd.github+json");
                connection.setRequestProperty("User-Agent", "ShadowFox-TV-Task-Killer/" + BuildConfig.VERSION_NAME);
                if (connection.getResponseCode() != 200) return;

                BufferedReader reader = new BufferedReader(new InputStreamReader(connection.getInputStream()));
                StringBuilder json = new StringBuilder();
                String line;
                while ((line = reader.readLine()) != null) json.append(line);
                reader.close();

                JSONObject release = new JSONObject(json.toString());
                String latestVersion = release.optString("tag_name", "").replaceFirst("^[vV]", "");
                if (latestVersion.isEmpty() || compareVersions(latestVersion, BuildConfig.VERSION_NAME) <= 0) return;

                String apkUrl = null;
                JSONArray assets = release.optJSONArray("assets");
                if (assets != null) {
                    for (int i = 0; i < assets.length(); i++) {
                        JSONObject asset = assets.optJSONObject(i);
                        if (asset == null) continue;
                        String name = asset.optString("name", "");
                        if (name.toLowerCase(java.util.Locale.US).endsWith(".apk")) {
                            apkUrl = asset.optString("browser_download_url", null);
                            if (apkUrl != null) break;
                        }
                    }
                }

                if (apkUrl != null) {
                    final String finalUrl = apkUrl;
                    runOnUiThread(() -> startUpdateDownload(finalUrl, latestVersion));
                }
            } catch (Exception ignored) {
            } finally {
                if (connection != null) connection.disconnect();
            }
        });
    }

    private int compareVersions(String a, String b) {
        String[] aa = a.split("\\.");
        String[] bb = b.split("\\.");
        int max = Math.max(aa.length, bb.length);
        for (int i = 0; i < max; i++) {
            int av = i < aa.length ? parseVersionPart(aa[i]) : 0;
            int bv = i < bb.length ? parseVersionPart(bb[i]) : 0;
            if (av != bv) return Integer.compare(av, bv);
        }
        return 0;
    }

    private int parseVersionPart(String value) {
        try {
            return Integer.parseInt(value.replaceAll("[^0-9].*$", ""));
        } catch (Exception ignored) {
            return 0;
        }
    }

    private void startUpdateDownload(String apkUrl, String version) {
        try {
            DownloadManager manager = (DownloadManager) getSystemService(DOWNLOAD_SERVICE);
            if (manager == null) return;

            SharedPreferences prefs = getSharedPreferences(UPDATE_PREFS, MODE_PRIVATE);
            long existingId = prefs.getLong("download_id", -1L);
            String existingVersion = prefs.getString("download_version", null);
            if (existingId > 0 && version.equals(existingVersion)) {
                monitorDownload(existingId, version);
                return;
            }

            DownloadManager.Request request = new DownloadManager.Request(Uri.parse(apkUrl));
            request.setTitle("ShadowFox TV - Task Killer v" + version);
            request.setDescription("Downloading update");
            request.setMimeType("application/vnd.android.package-archive");
            request.setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED);
            request.setDestinationInExternalFilesDir(this, Environment.DIRECTORY_DOWNLOADS,
                    "ShadowFox-TV-Task-Killer-v" + version + ".apk");

            long id = manager.enqueue(request);
            prefs.edit().putLong("download_id", id).putString("download_version", version).apply();
            Toast.makeText(this, "Downloading ShadowFox update v" + version + "…", Toast.LENGTH_LONG).show();
            monitorDownload(id, version);
        } catch (Exception ignored) {}
    }

    private void monitorDownload(long downloadId, String version) {
        updateExecutor.execute(() -> {
            DownloadManager manager = (DownloadManager) getSystemService(DOWNLOAD_SERVICE);
            if (manager == null) return;
            SharedPreferences prefs = getSharedPreferences(UPDATE_PREFS, MODE_PRIVATE);

            while (!Thread.currentThread().isInterrupted()) {
                Cursor cursor = null;
                try {
                    DownloadManager.Query query = new DownloadManager.Query().setFilterById(downloadId);
                    cursor = manager.query(query);
                    if (cursor != null && cursor.moveToFirst()) {
                        int statusIndex = cursor.getColumnIndex(DownloadManager.COLUMN_STATUS);
                        int status = statusIndex >= 0 ? cursor.getInt(statusIndex) : DownloadManager.STATUS_FAILED;
                        if (status == DownloadManager.STATUS_SUCCESSFUL) {
                            Uri uri = manager.getUriForDownloadedFile(downloadId);
                            prefs.edit().remove("download_id").remove("download_version").apply();
                            if (uri != null) runOnUiThread(() -> requestInstall(uri));
                            return;
                        }
                        if (status == DownloadManager.STATUS_FAILED) {
                            prefs.edit().remove("download_id").remove("download_version").apply();
                            return;
                        }
                    }
                } catch (Exception ignored) {
                    return;
                } finally {
                    if (cursor != null) cursor.close();
                }

                try {
                    Thread.sleep(1000);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    return;
                }
            }
        });
    }

    private void requestInstall(Uri apkUri) {
        SharedPreferences prefs = getSharedPreferences(UPDATE_PREFS, MODE_PRIVATE);
        prefs.edit().putString("pending_install_uri", apkUri.toString()).apply();

        if (!canInstallPackages()) {
            try {
                Intent settingsIntent = new Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                        Uri.parse("package:" + getPackageName()));
                startActivity(settingsIntent);
                Toast.makeText(this, "Allow updates from ShadowFox TV, then return to the app.", Toast.LENGTH_LONG).show();
            } catch (Exception ignored) {}
            return;
        }

        prefs.edit().remove("pending_install_uri").apply();
        launchPackageInstaller(apkUri);
    }

    private boolean canInstallPackages() {
        return Build.VERSION.SDK_INT < Build.VERSION_CODES.O || getPackageManager().canRequestPackageInstalls();
    }

    private void launchPackageInstaller(Uri apkUri) {
        try {
            Intent install = new Intent(Intent.ACTION_VIEW);
            install.setDataAndType(apkUri, "application/vnd.android.package-archive");
            install.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            startActivity(install);
        } catch (Exception e) {
            Toast.makeText(this, "Update downloaded. Open Downloads to install it.", Toast.LENGTH_LONG).show();
        }
    }

    private long getAvailableRamBytes() {
        ActivityManager am = (ActivityManager) getSystemService(Context.ACTIVITY_SERVICE);
        if (am == null) return 0L;
        ActivityManager.MemoryInfo info = new ActivityManager.MemoryInfo();
        am.getMemoryInfo(info);
        return info.availMem;
    }

    private String formatMb(long bytes) {
        double mb = bytes / (1024.0 * 1024.0);
        if (mb < 0.05) return "0 MB";
        if (mb < 10.0) return String.format(java.util.Locale.US, "%.1f MB", mb);
        return String.format(java.util.Locale.US, "%.0f MB", mb);
    }

    @Override
    protected void onDestroy() {
        executor.shutdownNow();
        updateExecutor.shutdownNow();
        super.onDestroy();
    }
}
