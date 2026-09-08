package ca.shadowfoxtv.taskkiller;

import android.app.Activity;
import android.app.ActivityManager;
import android.app.AlertDialog;
import android.app.UiModeManager;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.content.res.Configuration;
import android.net.ConnectivityManager;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.os.StatFs;
import android.os.SystemClock;
import android.provider.Settings;
import android.view.View;
import android.view.animation.AnimationUtils;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class V4DashboardActivity extends Activity {
    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private final List<String> userPackages = new ArrayList<>();
    private final Set<String> protectedPackages = new HashSet<>();

    private TextView healthScoreText, healthStateText, deviceText, rootText, networkText;
    private TextView ramText, storageText, appsText, uptimeText, versionText;
    private TextView resultTitle, resultDetail;
    private Button optimizeButton, appsButton, storageButton, networkButton, rootButton;
    private Button reportButton, updateButton, settingsButton, aboutButton;
    private boolean rooted;
    private boolean optimizing;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_v4_dashboard);

        healthScoreText = findViewById(R.id.healthScoreText);
        healthStateText = findViewById(R.id.healthStateText);
        deviceText = findViewById(R.id.deviceTextV4);
        rootText = findViewById(R.id.rootTextV4);
        networkText = findViewById(R.id.networkTextV4);
        ramText = findViewById(R.id.ramTextV4);
        storageText = findViewById(R.id.storageTextV4);
        appsText = findViewById(R.id.appsTextV4);
        uptimeText = findViewById(R.id.uptimeTextV4);
        versionText = findViewById(R.id.versionTextV4);
        resultTitle = findViewById(R.id.resultTitleV4);
        resultDetail = findViewById(R.id.resultDetailV4);

        optimizeButton = findViewById(R.id.optimizeButtonV4);
        appsButton = findViewById(R.id.appsButtonV4);
        storageButton = findViewById(R.id.storageButtonV4);
        networkButton = findViewById(R.id.networkButtonV4);
        rootButton = findViewById(R.id.rootButtonV4);
        reportButton = findViewById(R.id.reportButtonV4);
        updateButton = findViewById(R.id.updateButtonV4);
        settingsButton = findViewById(R.id.settingsButtonV4);
        aboutButton = findViewById(R.id.aboutButtonV4);

        Collections.addAll(protectedPackages,
                getPackageName(),
                "com.android.systemui",
                "com.android.settings",
                "com.google.android.tvlauncher",
                "com.google.android.apps.tv.launcherx",
                "com.google.android.tv.remote.service",
                "com.amazon.tv.launcher");

        optimizeButton.setOnClickListener(v -> optimizeNow());
        appsButton.setOnClickListener(v -> showAppManager());
        storageButton.setOnClickListener(v -> openStorageTools());
        networkButton.setOnClickListener(v -> openNetworkTools());
        rootButton.setOnClickListener(v -> showRootTools());
        reportButton.setOnClickListener(v -> showDeviceReport());
        updateButton.setOnClickListener(v -> openUpdateCenter());
        settingsButton.setOnClickListener(v -> openSystemSettings());
        aboutButton.setOnClickListener(v -> showAbout());

        addFocus(optimizeButton);
        addFocus(appsButton);
        addFocus(storageButton);
        addFocus(networkButton);
        addFocus(rootButton);
        addFocus(reportButton);
        addFocus(updateButton);
        addFocus(settingsButton);
        addFocus(aboutButton);

        versionText.setText("v" + BuildConfig.VERSION_NAME + "  •  SYSTEM OPTIMIZER EDITION");
        scanApps();
        detectRoot();
        refreshDashboard();
        optimizeButton.requestFocus();

        View commandCenter = findViewById(R.id.commandCenterPanel);
        if (commandCenter != null) commandCenter.startAnimation(AnimationUtils.loadAnimation(this, R.anim.fade_scale_in));
    }

    @Override
    protected void onResume() {
        super.onResume();
        scanApps();
        refreshDashboard();
    }

    private void addFocus(View view) {
        if (view == null) return;
        view.setOnFocusChangeListener((v, focused) -> {
            float scale = focused ? 1.025f : 1f;
            v.animate().scaleX(scale).scaleY(scale).setDuration(100).start();
            v.setElevation(focused ? 14f : 2f);
        });
    }

    private void refreshDashboard() {
        deviceText.setText(getDeviceLabel());
        networkText.setText(getNetworkLabel());
        rootText.setText(rooted ? "ROOT MODE READY" : "STANDARD MODE");
        uptimeText.setText(formatUptime());
        appsText.setText(String.valueOf(userPackages.size()));

        ActivityManager.MemoryInfo mem = getMemoryInfo();
        long usedMem = Math.max(0, mem.totalMem - mem.availMem);
        int ramPct = mem.totalMem > 0 ? (int) ((usedMem * 100L) / mem.totalMem) : 0;
        ramText.setText(ramPct + "%  •  " + formatGb(usedMem) + " / " + formatGb(mem.totalMem));

        long[] storage = getStorageStats();
        int storagePct = storage[0] > 0 ? (int) ((storage[1] * 100L) / storage[0]) : 0;
        storageText.setText(storagePct + "%  •  " + formatGb(storage[1]) + " / " + formatGb(storage[0]));

        int score = calculateHealthScore(ramPct, storagePct, isNetworkConnected());
        healthScoreText.setText(String.valueOf(score));
        if (score >= 90) healthStateText.setText("EXCELLENT");
        else if (score >= 75) healthStateText.setText("GOOD");
        else if (score >= 60) healthStateText.setText("FAIR");
        else healthStateText.setText("NEEDS ATTENTION");
    }

    private int calculateHealthScore(int ramPct, int storagePct, boolean online) {
        int score = 100;
        if (ramPct > 90) score -= 20;
        else if (ramPct > 80) score -= 10;
        else if (ramPct > 70) score -= 5;
        if (storagePct > 95) score -= 25;
        else if (storagePct > 90) score -= 15;
        else if (storagePct > 80) score -= 8;
        if (!online) score -= 15;
        if (SystemClock.elapsedRealtime() > 14L * 24L * 60L * 60L * 1000L) score -= 5;
        return Math.max(0, Math.min(100, score));
    }

    private void scanApps() {
        executor.execute(() -> {
            PackageManager pm = getPackageManager();
            List<String> found = new ArrayList<>();
            for (ApplicationInfo info : pm.getInstalledApplications(0)) {
                boolean system = (info.flags & ApplicationInfo.FLAG_SYSTEM) != 0;
                boolean updatedSystem = (info.flags & ApplicationInfo.FLAG_UPDATED_SYSTEM_APP) != 0;
                if (system && !updatedSystem) continue;
                if (protectedPackages.contains(info.packageName)) continue;
                found.add(info.packageName);
            }
            runOnUiThread(() -> {
                userPackages.clear();
                userPackages.addAll(found);
                appsText.setText(String.valueOf(userPackages.size()));
            });
        });
    }

    private void detectRoot() {
        executor.execute(() -> {
            boolean found = false;
            Process p = null;
            try {
                p = Runtime.getRuntime().exec(new String[]{"su", "-c", "id"});
                BufferedReader r = new BufferedReader(new InputStreamReader(p.getInputStream()));
                String line = r.readLine();
                found = line != null && line.contains("uid=0");
                p.waitFor();
            } catch (Exception ignored) {
            } finally {
                if (p != null) p.destroy();
            }
            boolean finalFound = found;
            runOnUiThread(() -> {
                rooted = finalFound;
                rootText.setText(rooted ? "ROOT MODE READY" : "STANDARD MODE");
            });
        });
    }

    private void optimizeNow() {
        if (optimizing) return;
        optimizing = true;
        optimizeButton.setEnabled(false);
        optimizeButton.setText("OPTIMIZING…");
        resultTitle.setText("SYSTEM ANALYSIS RUNNING");
        resultDetail.setText("Checking memory, storage, apps and device state…");

        final long beforeRam = getMemoryInfo().availMem;
        executor.execute(() -> {
            int processed = 0;
            boolean rootAction = false;
            if (rooted) {
                rootAction = runRootCommand("am kill-all");
                processed = userPackages.size();
            } else if (Build.VERSION.SDK_INT < 34) {
                ActivityManager am = (ActivityManager) getSystemService(ACTIVITY_SERVICE);
                if (am != null) {
                    for (String pkg : new ArrayList<>(userPackages)) {
                        if (protectedPackages.contains(pkg)) continue;
                        try {
                            am.killBackgroundProcesses(pkg);
                            processed++;
                        } catch (Exception ignored) {}
                    }
                }
            }

            try { Thread.sleep(900); } catch (InterruptedException ignored) { Thread.currentThread().interrupt(); }
            long recovered = Math.max(0, getMemoryInfo().availMem - beforeRam);
            int finalProcessed = processed;
            boolean finalRootAction = rootAction;
            runOnUiThread(() -> {
                optimizing = false;
                optimizeButton.setEnabled(true);
                optimizeButton.setText("OPTIMIZE NOW");
                resultTitle.setText("OPTIMIZATION COMPLETE");
                if (rooted && finalRootAction) {
                    resultDetail.setText("ROOT ENGINE • " + finalProcessed + " apps evaluated • " + formatMb(recovered) + " RAM recovered");
                } else if (Build.VERSION.SDK_INT >= 34) {
                    resultDetail.setText("STANDARD ENGINE • Android protects third-party app termination on this version • health scan completed");
                } else {
                    resultDetail.setText(finalProcessed + " apps processed • " + formatMb(recovered) + " RAM recovered");
                }
                optimizeButton.startAnimation(AnimationUtils.loadAnimation(this, R.anim.button_pop));
                refreshDashboard();
                scanApps();
            });
        });
    }

    private void showAppManager() {
        executor.execute(() -> {
            PackageManager pm = getPackageManager();
            List<AppEntry> entries = new ArrayList<>();
            for (String pkg : userPackages) {
                try {
                    ApplicationInfo info = pm.getApplicationInfo(pkg, 0);
                    entries.add(new AppEntry(pm.getApplicationLabel(info).toString(), pkg));
                } catch (Exception ignored) {}
            }
            Collections.sort(entries, (a, b) -> a.label.compareToIgnoreCase(b.label));
            String[] labels = new String[entries.size()];
            for (int i = 0; i < entries.size(); i++) labels[i] = entries.get(i).label;
            runOnUiThread(() -> new AlertDialog.Builder(this)
                    .setTitle("SHADOWFOX APP MANAGER  •  " + entries.size() + " apps")
                    .setItems(labels, (d, which) -> openAppDetails(entries.get(which).pkg))
                    .setPositiveButton("CLOSE", null)
                    .show());
        });
    }

    private void openAppDetails(String pkg) {
        try {
            startActivity(new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:" + pkg)));
        } catch (Exception e) {
            Toast.makeText(this, pkg, Toast.LENGTH_SHORT).show();
        }
    }

    private void openStorageTools() {
        String[] items = {"Storage dashboard", "Manage apps", "Downloads"};
        new AlertDialog.Builder(this).setTitle("STORAGE TOOLS").setItems(items, (d, which) -> {
            try {
                if (which == 0) startActivity(new Intent(Settings.ACTION_INTERNAL_STORAGE_SETTINGS));
                else if (which == 1) startActivity(new Intent(Settings.ACTION_MANAGE_APPLICATIONS_SETTINGS));
                else startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse("content://com.android.providers.downloads.documents/root/downloads")));
            } catch (Exception e) {
                Toast.makeText(this, "This shortcut is not available on this device.", Toast.LENGTH_SHORT).show();
            }
        }).setPositiveButton("CLOSE", null).show();
    }

    private void openNetworkTools() {
        String[] items = {"Wi-Fi / Internet settings", "Wireless settings", "Refresh network status"};
        new AlertDialog.Builder(this).setTitle("NETWORK TOOLS  •  " + getNetworkLabel()).setItems(items, (d, which) -> {
            try {
                if (which == 0) startActivity(new Intent(Settings.ACTION_WIFI_SETTINGS));
                else if (which == 1) startActivity(new Intent(Settings.ACTION_WIRELESS_SETTINGS));
                else {
                    refreshDashboard();
                    Toast.makeText(this, getNetworkLabel(), Toast.LENGTH_SHORT).show();
                }
            } catch (Exception e) {
                Toast.makeText(this, "Network settings unavailable.", Toast.LENGTH_SHORT).show();
            }
        }).setPositiveButton("CLOSE", null).show();
    }

    private void showRootTools() {
        if (!rooted) {
            new AlertDialog.Builder(this)
                    .setTitle("SHADOWFOX ROOT MODE")
                    .setMessage("Root was not detected. Standard Mode stays fully functional and uses Android-safe maintenance tools.")
                    .setPositiveButton("OK", null).show();
            return;
        }
        String[] items = {"Kill background processes", "Restart launcher", "Reboot device"};
        new AlertDialog.Builder(this).setTitle("SHADOWFOX ROOT MODE  •  ACTIVE").setItems(items, (d, which) -> {
            if (which == 0) runRootAction("am kill-all", "Background processes cleaned.");
            else if (which == 1) runRootAction("am force-stop com.google.android.tvlauncher; am force-stop com.google.android.apps.tv.launcherx", "Launcher restart requested.");
            else confirmReboot();
        }).setPositiveButton("CLOSE", null).show();
    }

    private void confirmReboot() {
        new AlertDialog.Builder(this).setTitle("REBOOT DEVICE?")
                .setMessage("This will restart the device now.")
                .setNegativeButton("CANCEL", null)
                .setPositiveButton("REBOOT", (d, w) -> executor.execute(() -> runRootCommand("reboot")))
                .show();
    }

    private void runRootAction(String command, String success) {
        executor.execute(() -> {
            boolean ok = runRootCommand(command);
            runOnUiThread(() -> Toast.makeText(this, ok ? success : "Root command failed.", Toast.LENGTH_LONG).show());
        });
    }

    private boolean runRootCommand(String command) {
        Process p = null;
        try {
            p = Runtime.getRuntime().exec(new String[]{"su", "-c", command});
            return p.waitFor() == 0;
        } catch (Exception ignored) {
            return false;
        } finally {
            if (p != null) p.destroy();
        }
    }

    private void showDeviceReport() {
        ActivityManager.MemoryInfo mem = getMemoryInfo();
        long[] storage = getStorageStats();
        String report = "Device: " + Build.MANUFACTURER + " " + Build.MODEL +
                "\nPlatform: " + getDeviceLabel() +
                "\nAndroid: " + Build.VERSION.RELEASE + " (API " + Build.VERSION.SDK_INT + ")" +
                "\nMode: " + (rooted ? "ShadowFox Root Mode" : "Standard Mode") +
                "\nNetwork: " + getNetworkLabel() +
                "\nRAM: " + formatGb(mem.totalMem - mem.availMem) + " / " + formatGb(mem.totalMem) +
                "\nStorage: " + formatGb(storage[1]) + " / " + formatGb(storage[0]) +
                "\nUser apps detected: " + userPackages.size() +
                "\nUptime: " + formatUptime() +
                "\nShadowFox: v" + BuildConfig.VERSION_NAME;
        new AlertDialog.Builder(this).setTitle("DEVICE HEALTH REPORT").setMessage(report).setPositiveButton("CLOSE", null).show();
    }

    private void openUpdateCenter() {
        try {
            Intent i = new Intent(this, MainActivity.class);
            i.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
            startActivity(i);
            Toast.makeText(this, "Checking for ShadowFox updates…", Toast.LENGTH_SHORT).show();
        } catch (Exception e) {
            sendBroadcast(new Intent(this, UpdateCheckReceiver.class));
            Toast.makeText(this, "Background update check requested.", Toast.LENGTH_SHORT).show();
        }
    }

    private void openSystemSettings() {
        try { startActivity(new Intent(Settings.ACTION_SETTINGS)); }
        catch (Exception e) { Toast.makeText(this, "Settings unavailable.", Toast.LENGTH_SHORT).show(); }
    }

    private void showAbout() {
        new AlertDialog.Builder(this)
                .setTitle("ShadowFox TV - Task Killer v" + BuildConfig.VERSION_NAME)
                .setMessage("SYSTEM OPTIMIZER EDITION\n\nFree premium device tools for Android TV, Fire TV, Android phones and tablets. Root Mode unlocks deeper device controls when root is available.\n\nwww.shadowfoxtv.ca")
                .setNegativeButton("CLOSE", null)
                .setPositiveButton("WEBSITE", (d, w) -> {
                    try { startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse("https://www.shadowfoxtv.ca"))); }
                    catch (Exception ignored) {}
                }).show();
    }

    private ActivityManager.MemoryInfo getMemoryInfo() {
        ActivityManager.MemoryInfo info = new ActivityManager.MemoryInfo();
        ActivityManager am = (ActivityManager) getSystemService(ACTIVITY_SERVICE);
        if (am != null) am.getMemoryInfo(info);
        return info;
    }

    private long[] getStorageStats() {
        try {
            StatFs stat = new StatFs(Environment.getDataDirectory().getAbsolutePath());
            long total = stat.getTotalBytes();
            long used = Math.max(0, total - stat.getAvailableBytes());
            return new long[]{total, used};
        } catch (Exception e) {
            return new long[]{0, 0};
        }
    }

    private boolean isNetworkConnected() {
        try {
            ConnectivityManager cm = (ConnectivityManager) getSystemService(CONNECTIVITY_SERVICE);
            if (cm == null) return false;
            Network network = cm.getActiveNetwork();
            if (network == null) return false;
            NetworkCapabilities caps = cm.getNetworkCapabilities(network);
            return caps != null && caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET);
        } catch (Exception e) {
            return false;
        }
    }

    private String getNetworkLabel() {
        try {
            ConnectivityManager cm = (ConnectivityManager) getSystemService(CONNECTIVITY_SERVICE);
            if (cm == null) return "OFFLINE";
            Network network = cm.getActiveNetwork();
            if (network == null) return "OFFLINE";
            NetworkCapabilities caps = cm.getNetworkCapabilities(network);
            if (caps == null || !caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)) return "OFFLINE";
            if (caps.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET)) return "ETHERNET • ONLINE";
            if (caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)) return "WI-FI • ONLINE";
            if (caps.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR)) return "MOBILE • ONLINE";
            return "ONLINE";
        } catch (Exception e) {
            return "UNKNOWN";
        }
    }

    private String getDeviceLabel() {
        String manufacturer = Build.MANUFACTURER == null ? "" : Build.MANUFACTURER.toLowerCase(Locale.US);
        if (manufacturer.contains("amazon")) return "FIRE TV";
        UiModeManager ui = (UiModeManager) getSystemService(UI_MODE_SERVICE);
        if (ui != null && ui.getCurrentModeType() == Configuration.UI_MODE_TYPE_TELEVISION) return "ANDROID TV";
        return "ANDROID";
    }

    private String formatUptime() {
        long seconds = SystemClock.elapsedRealtime() / 1000L;
        long days = seconds / 86400L;
        long hours = (seconds % 86400L) / 3600L;
        long minutes = (seconds % 3600L) / 60L;
        return days > 0 ? days + "d " + hours + "h" : hours + "h " + minutes + "m";
    }

    private String formatGb(long bytes) {
        double gb = bytes / (1024.0 * 1024.0 * 1024.0);
        return String.format(Locale.US, gb < 10 ? "%.1f GB" : "%.0f GB", gb);
    }

    private String formatMb(long bytes) {
        return String.format(Locale.US, "%.0f MB", bytes / (1024.0 * 1024.0));
    }

    private static class AppEntry {
        final String label;
        final String pkg;
        AppEntry(String label, String pkg) { this.label = label; this.pkg = pkg; }
    }
}
