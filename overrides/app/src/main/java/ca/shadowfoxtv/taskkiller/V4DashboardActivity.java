package ca.shadowfoxtv.taskkiller;

import android.app.Activity;
import android.app.ActivityManager;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.net.ConnectivityManager;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.view.View;
import android.widget.Button;
import android.widget.ProgressBar;
import android.widget.Switch;
import android.widget.TextView;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.regex.Pattern;

public class V4DashboardActivity extends Activity {
    private static final Pattern PACKAGE_NAME = Pattern.compile("^[A-Za-z0-9_]+(?:\\.[A-Za-z0-9_]+)+$");

    private final ExecutorService worker = Executors.newSingleThreadExecutor();
    private final Set<String> whitelist = new HashSet<>();

    private TextView networkText;
    private TextView speedText;
    private TextView bufferText;
    private TextView resultTitle;
    private TextView resultDetail;
    private TextView versionText;
    private ProgressBar bufferProgress;
    private SpeedGaugeView gauge;
    private LatencyGraphView graph;
    private Button optimizeButton;
    private Button bufferButton;
    private Switch fixPing;

    private volatile boolean busy;
    private volatile boolean rooted;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_v4_dashboard);

        networkText = findViewById(R.id.networkTextV4);
        speedText = findViewById(R.id.speedText);
        bufferText = findViewById(R.id.bufferText);
        bufferProgress = findViewById(R.id.bufferProgress);
        gauge = findViewById(R.id.speedGauge);
        graph = findViewById(R.id.latencyGraph);
        resultTitle = findViewById(R.id.resultTitleV4);
        resultDetail = findViewById(R.id.resultDetailV4);
        versionText = findViewById(R.id.versionTextV4);
        optimizeButton = findViewById(R.id.optimizeButtonV4);
        bufferButton = findViewById(R.id.bufferButtonV4);
        fixPing = findViewById(R.id.fixPingSwitch);

        buildWhitelist();
        versionText.setText("v" + BuildConfig.VERSION_NAME + " • SHADOWFOX OPTIMIZER");

        optimizeButton.setOnClickListener(v -> runFullOptimization());
        bufferButton.setOnClickListener(v -> runBufferOptimization());
        fixPing.setOnCheckedChangeListener((buttonView, enabled) -> {
            if (enabled) runPingTest();
        });

        addFocus(optimizeButton);
        addFocus(bufferButton);
        addFocus(fixPing);
        addFocus(findViewById(R.id.bufferCard));
        addFocus(findViewById(R.id.latencyCard));

        optimizeButton.requestFocus();
        refreshNetwork();
        detectRoot();
        runPingTest();
    }

    private void buildWhitelist() {
        whitelist.clear();
        whitelist.addAll(Arrays.asList(
                getPackageName(),
                "com.android.systemui",
                "com.android.settings",
                "com.android.phone",
                "com.android.bluetooth",
                "com.android.providers.settings",
                "com.google.android.gms",
                "com.google.android.gsf",
                "com.google.android.tvlauncher",
                "com.google.android.apps.tv.launcherx",
                "com.google.android.tv.remote.service",
                "com.google.android.inputmethod.latin",
                "com.amazon.tv.launcher",
                "com.amazon.firehomestarter",
                "com.amazon.device.messaging",
                "com.amazon.device.software.ota",
                "com.amazon.device.software.ota.override"
        ));

        protectCurrentInputMethod();
        protectHomeLauncher();
        protectActiveVpnOwner();
    }

    private void protectCurrentInputMethod() {
        try {
            String ime = Settings.Secure.getString(getContentResolver(), Settings.Secure.DEFAULT_INPUT_METHOD);
            if (ime != null && ime.contains("/")) {
                whitelist.add(ime.substring(0, ime.indexOf('/')));
            }
        } catch (Exception ignored) {
        }
    }

    private void protectHomeLauncher() {
        try {
            Intent home = new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME);
            if (getPackageManager().resolveActivity(home, PackageManager.MATCH_DEFAULT_ONLY) != null) {
                whitelist.add(getPackageManager().resolveActivity(home, PackageManager.MATCH_DEFAULT_ONLY).activityInfo.packageName);
            }
        } catch (Exception ignored) {
        }
    }

    private void protectActiveVpnOwner() {
        try {
            ConnectivityManager cm = (ConnectivityManager) getSystemService(CONNECTIVITY_SERVICE);
            Network network = cm == null ? null : cm.getActiveNetwork();
            NetworkCapabilities caps = network == null || cm == null ? null : cm.getNetworkCapabilities(network);
            if (caps != null && caps.hasTransport(NetworkCapabilities.TRANSPORT_VPN) && Build.VERSION.SDK_INT >= 29) {
                String[] packages = getPackageManager().getPackagesForUid(caps.getOwnerUid());
                if (packages != null) whitelist.addAll(Arrays.asList(packages));
            }
        } catch (Exception ignored) {
        }
    }

    private void detectRoot() {
        worker.execute(() -> {
            rooted = runRootProbe();
            runOnUiThread(() -> {
                resultTitle.setText(rooted ? "ROOT ENGINE READY" : "STANDARD ENGINE READY");
                resultDetail.setText(rooted
                        ? "Privileged background cleanup enabled • whitelist protection active"
                        : "Android-safe optimization mode • root not detected");
                refreshNetwork();
            });
        });
    }

    private boolean runRootProbe() {
        Process process = null;
        try {
            process = Runtime.getRuntime().exec(new String[]{"su", "-c", "id"});
            BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()));
            String line = reader.readLine();
            return process.waitFor() == 0 && line != null && line.contains("uid=0");
        } catch (Exception ignored) {
            return false;
        } finally {
            if (process != null) process.destroy();
        }
    }

    public void runFullOptimization() {
        if (busy) return;

        busy = true;
        optimizeButton.setEnabled(false);
        optimizeButton.setText("OPTIMIZING…");
        resultTitle.setText(rooted ? "ROOT OPTIMIZATION RUNNING" : "OPTIMIZATION RUNNING");
        resultDetail.setText("Protecting launcher, VPN, keyboard and critical Android services…");

        worker.execute(() -> {
            buildWhitelist();
            long beforeRam = availableRam();
            OptimizationResult result = rooted ? runRootOptimization() : runStandardOptimization();
            long ping = measurePing();

            try {
                Thread.sleep(650);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }

            long recovered = Math.max(0L, availableRam() - beforeRam);
            runOnUiThread(() -> {
                busy = false;
                optimizeButton.setEnabled(true);
                optimizeButton.setText("RUN FULL OPTIMIZATION");
                graph.addSample(ping);
                refreshNetworkWithPing(ping);
                resultTitle.setText("OPTIMIZATION COMPLETE");

                if (result.rootUsed) {
                    resultDetail.setText("ROOT ENGINE • " + result.successful + " background packages cleaned • "
                            + result.protectedCount + " protected • " + formatMb(recovered)
                            + " RAM recovered • Ping " + fmtPing(ping));
                } else if (Build.VERSION.SDK_INT >= 34) {
                    resultDetail.setText("STANDARD ENGINE • Android 14+ limits third-party cleanup • "
                            + result.attempted + " packages evaluated • Ping " + fmtPing(ping));
                } else {
                    resultDetail.setText(result.successful + " background packages requested • "
                            + formatMb(recovered) + " RAM recovered • Ping " + fmtPing(ping));
                }
            });
        });
    }

    private OptimizationResult runRootOptimization() {
        OptimizationResult result = new OptimizationResult(true);
        Set<String> candidates = getRootRunningPackages();

        if (candidates.isEmpty()) {
            candidates.addAll(getVisibleRunningPackages());
        }

        for (String pkg : candidates) {
            result.attempted++;
            if (isWhitelisted(pkg) || isSystemPackage(pkg)) {
                result.protectedCount++;
                continue;
            }

            if (runRootCommand("am kill " + pkg)) {
                result.successful++;
            }
        }
        return result;
    }

    private OptimizationResult runStandardOptimization() {
        OptimizationResult result = new OptimizationResult(false);
        ActivityManager am = (ActivityManager) getSystemService(Context.ACTIVITY_SERVICE);
        if (am == null) return result;

        for (String pkg : getVisibleRunningPackages()) {
            result.attempted++;
            if (isWhitelisted(pkg) || isSystemPackage(pkg)) {
                result.protectedCount++;
                continue;
            }

            try {
                am.killBackgroundProcesses(pkg);
                result.successful++;
            } catch (Exception ignored) {
            }
        }
        return result;
    }

    private Set<String> getRootRunningPackages() {
        Set<String> packages = new LinkedHashSet<>();
        List<String> lines = runRootCommandForLines("ps -A -o NAME");
        if (lines.isEmpty()) lines = runRootCommandForLines("ps -A");

        for (String line : lines) {
            if (line == null) continue;
            String trimmed = line.trim();
            if (trimmed.isEmpty() || trimmed.equalsIgnoreCase("NAME")) continue;

            String[] columns = trimmed.split("\\s+");
            String processName = columns[columns.length - 1];
            int colon = processName.indexOf(':');
            if (colon > 0) processName = processName.substring(0, colon);

            if (PACKAGE_NAME.matcher(processName).matches()) {
                packages.add(processName);
            }
        }
        return packages;
    }

    private Set<String> getVisibleRunningPackages() {
        Set<String> packages = new LinkedHashSet<>();
        ActivityManager am = (ActivityManager) getSystemService(Context.ACTIVITY_SERVICE);
        if (am == null) return packages;

        try {
            List<ActivityManager.RunningAppProcessInfo> running = am.getRunningAppProcesses();
            if (running == null) return packages;
            for (ActivityManager.RunningAppProcessInfo process : running) {
                if (process.pkgList == null) continue;
                packages.addAll(Arrays.asList(process.pkgList));
            }
        } catch (Exception ignored) {
        }
        return packages;
    }

    private boolean runRootCommand(String command) {
        Process process = null;
        try {
            process = Runtime.getRuntime().exec(new String[]{"su", "-c", command});
            return process.waitFor() == 0;
        } catch (Exception ignored) {
            return false;
        } finally {
            if (process != null) process.destroy();
        }
    }

    private List<String> runRootCommandForLines(String command) {
        List<String> lines = new ArrayList<>();
        Process process = null;
        try {
            process = Runtime.getRuntime().exec(new String[]{"su", "-c", command});
            BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()));
            String line;
            while ((line = reader.readLine()) != null) lines.add(line);
            process.waitFor();
        } catch (Exception ignored) {
            lines.clear();
        } finally {
            if (process != null) process.destroy();
        }
        return lines;
    }

    private boolean isSystemPackage(String pkg) {
        if (pkg == null || pkg.startsWith("android") || pkg.startsWith("com.android.")) return true;
        try {
            ApplicationInfo info = getPackageManager().getApplicationInfo(pkg, 0);
            boolean system = (info.flags & ApplicationInfo.FLAG_SYSTEM) != 0;
            boolean updatedSystem = (info.flags & ApplicationInfo.FLAG_UPDATED_SYSTEM_APP) != 0;
            return system && !updatedSystem;
        } catch (Exception ignored) {
            return true;
        }
    }

    private boolean isWhitelisted(String pkg) {
        return pkg == null || whitelist.contains(pkg);
    }

    private void runBufferOptimization() {
        bufferProgress.setProgress(0);
        bufferText.setText("REFRESHING NETWORK");
        runPingTest();
        worker.execute(() -> {
            try {
                Thread.sleep(350);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            runOnUiThread(() -> {
                bufferProgress.setProgress(memoryPressure());
                bufferText.setText(memoryPressure() + "% MEMORY LOAD");
            });
        });
    }

    private void runPingTest() {
        worker.execute(() -> {
            long ping = measurePing();
            runOnUiThread(() -> {
                graph.addSample(ping);
                refreshNetworkWithPing(ping);
            });
        });
    }

    private long measurePing() {
        long start = System.nanoTime();
        try (Socket socket = new Socket()) {
            socket.connect(new InetSocketAddress("1.1.1.1", 443), 1800);
            return (System.nanoTime() - start) / 1_000_000L;
        } catch (Exception ignored) {
            return -1L;
        }
    }

    private void refreshNetwork() {
        refreshNetworkWithPing(-1L);
    }

    private void refreshNetworkWithPing(long ping) {
        ConnectivityManager cm = (ConnectivityManager) getSystemService(CONNECTIVITY_SERVICE);
        Network network = cm == null ? null : cm.getActiveNetwork();
        NetworkCapabilities caps = network == null || cm == null ? null : cm.getNetworkCapabilities(network);
        boolean online = caps != null && caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET);

        String engine = rooted ? "ROOT ENGINE" : "STANDARD ENGINE";
        networkText.setText(engine + " • " + (online ? "NETWORK ONLINE" : "NETWORK OFFLINE"));

        int mbps = caps == null ? 0 : Math.max(0, caps.getLinkDownstreamBandwidthKbps() / 1000);
        gauge.setMbps(mbps);
        speedText.setText("NETWORK LINK ESTIMATE\n"
                + (mbps > 0 ? mbps + " Mbps" : "— Mbps")
                + "\nDOWNLOAD CAPACITY\nPING: " + fmtPing(ping));

        int pressure = memoryPressure();
        bufferProgress.setProgress(pressure);
        bufferText.setText(pressure + "% MEMORY LOAD");
    }

    private int memoryPressure() {
        ActivityManager am = (ActivityManager) getSystemService(ACTIVITY_SERVICE);
        if (am == null) return 0;
        ActivityManager.MemoryInfo info = new ActivityManager.MemoryInfo();
        am.getMemoryInfo(info);
        if (info.totalMem == 0) return 0;
        return (int) (((info.totalMem - info.availMem) * 100L) / info.totalMem);
    }

    private long availableRam() {
        ActivityManager am = (ActivityManager) getSystemService(ACTIVITY_SERVICE);
        if (am == null) return 0L;
        ActivityManager.MemoryInfo info = new ActivityManager.MemoryInfo();
        am.getMemoryInfo(info);
        return info.availMem;
    }

    private String formatMb(long bytes) {
        return String.format(Locale.US, "%.0f MB", bytes / (1024.0 * 1024.0));
    }

    private String fmtPing(long ping) {
        return ping >= 0 ? ping + " ms" : "— ms";
    }

    private void addFocus(View view) {
        if (view == null) return;
        view.setOnFocusChangeListener((v, focused) -> {
            v.animate().scaleX(focused ? 1.04f : 1f).scaleY(focused ? 1.04f : 1f).setDuration(100).start();
            v.setElevation(focused ? 18f : 2f);
        });
    }

    @Override
    protected void onDestroy() {
        worker.shutdownNow();
        super.onDestroy();
    }

    private static final class OptimizationResult {
        final boolean rootUsed;
        int attempted;
        int successful;
        int protectedCount;

        OptimizationResult(boolean rootUsed) {
            this.rootUsed = rootUsed;
        }
    }
}
