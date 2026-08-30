package ca.shadowfoxtv.taskkiller;

import android.app.Activity;
import android.app.ActivityManager;
import android.content.Context;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.view.View;
import android.view.animation.Animation;
import android.view.animation.AnimationUtils;
import android.widget.Button;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class MainActivity extends Activity {
    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private final List<String> userPackages = new ArrayList<>();
    private final Set<String> protectedPackages = new HashSet<>();

    private TextView summaryText;
    private TextView appsCleanedText;
    private TextView ramRecoveredText;
    private TextView storageRecoveredText;
    private View resultCard;
    private Button cleanButton;
    private boolean cleaning = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        summaryText = findViewById(R.id.summaryText);
        appsCleanedText = findViewById(R.id.appsCleanedText);
        ramRecoveredText = findViewById(R.id.ramRecoveredText);
        storageRecoveredText = findViewById(R.id.storageRecoveredText);
        resultCard = findViewById(R.id.resultCard);
        cleanButton = findViewById(R.id.cleanButton);
        Button rescanButton = findViewById(R.id.rescanButton);

        protectedPackages.add(getPackageName());
        protectedPackages.add("com.android.systemui");
        protectedPackages.add("com.google.android.tvlauncher");
        protectedPackages.add("com.google.android.apps.tv.launcherx");
        protectedPackages.add("com.google.android.tv.remote.service");
        protectedPackages.add("com.android.settings");

        cleanButton.setOnClickListener(v -> cleanNow());
        rescanButton.setOnClickListener(v -> {
            resultCard.setVisibility(View.GONE);
            scanApps();
        });

        scanApps();
        cleanButton.requestFocus();

        View root = findViewById(R.id.rootLayout);
        View header = findViewById(R.id.headerCard);
        View status = findViewById(R.id.statusCard);
        Animation intro = AnimationUtils.loadAnimation(this, R.anim.fade_scale_in);
        root.startAnimation(intro);
        header.startAnimation(AnimationUtils.loadAnimation(this, R.anim.fade_scale_in));
        status.startAnimation(AnimationUtils.loadAnimation(this, R.anim.fade_scale_in));
    }

    private void scanApps() {
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
                    summaryText.setText("READY TO CLEAN  •  " + userPackages.size() + " APPS FOUND");
                }
            });
        });
    }

    private void cleanNow() {
        if (cleaning) return;

        cleaning = true;
        cleanButton.setEnabled(false);
        cleanButton.setText("CLEANING…");
        resultCard.setVisibility(View.GONE);
        summaryText.setText("CLEANING BACKGROUND APPS…");

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
                cleanButton.setText(R.string.clean_now);
                cleanButton.startAnimation(AnimationUtils.loadAnimation(this, R.anim.button_pop));

                summaryText.setText(R.string.cleanup_complete);
                appsCleanedText.setText(String.valueOf(cleanedCount));
                ramRecoveredText.setText(formatMb(ramRecovered));
                storageRecoveredText.setText(R.string.storage_restricted);

                resultCard.setVisibility(View.VISIBLE);
                resultCard.startAnimation(AnimationUtils.loadAnimation(this, R.anim.fade_scale_in));

                scanApps();
                cleanButton.requestFocus();
            });
        });
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
        executor.shutdown();
        super.onDestroy();
    }
}
