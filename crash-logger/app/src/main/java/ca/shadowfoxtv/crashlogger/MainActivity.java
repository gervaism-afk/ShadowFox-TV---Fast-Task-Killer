package ca.shadowfoxtv.crashlogger;

import android.app.Activity;
import android.content.Intent;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageInfo;
import android.graphics.Color;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class MainActivity extends Activity {
    private static final String[] KNOWN = {"ca.shadowfoxtv.taskkiller","com.aaronhalbert.fasttaskkiller","com.fasttaskkiller"};
    private final StringBuilder log = new StringBuilder();
    private TextView status, output;
    private Process logcat;
    private volatile boolean capturing;
    private long startedAt;
    private String target;
    private final Handler ui = new Handler(Looper.getMainLooper());

    @Override public void onCreate(Bundle b) { super.onCreate(b); buildUi(); status.setText("READY — Select START CRASH TEST"); }

    private void buildUi() {
        LinearLayout root=new LinearLayout(this); root.setOrientation(LinearLayout.VERTICAL); root.setPadding(40,28,40,28); root.setBackgroundColor(Color.rgb(3,17,29));
        TextView title=new TextView(this); title.setText("SHADOWFOX H96 CRASH LOGGER v1.1"); title.setTextColor(Color.CYAN); title.setTextSize(26); title.setGravity(Gravity.CENTER); root.addView(title);
        status=new TextView(this); status.setTextColor(Color.WHITE); status.setTextSize(17); status.setPadding(0,18,0,18); status.setGravity(Gravity.CENTER); root.addView(status);
        Button start=new Button(this); start.setText("START CRASH TEST"); start.setTextSize(18); start.setFocusable(true); start.setOnClickListener(v->startTest()); root.addView(start);
        Button stop=new Button(this); stop.setText("STOP / SHOW CAPTURE"); stop.setTextSize(16); stop.setFocusable(true); stop.setOnClickListener(v->stopAndShow()); root.addView(stop);
        output=new TextView(this); output.setTextColor(Color.WHITE); output.setTextSize(12); output.setTextIsSelectable(true); output.setPadding(0,18,0,0);
        ScrollView scroll=new ScrollView(this); scroll.addView(output); root.addView(scroll,new LinearLayout.LayoutParams(-1,0,1)); setContentView(root); start.requestFocus();
    }

    private void startTest() {
        if(capturing)return; log.setLength(0); startedAt=System.currentTimeMillis(); target=findTarget();
        append("ShadowFox Crash Logger 1.1\nDevice: "+android.os.Build.MANUFACTURER+" "+android.os.Build.MODEL+"\nAndroid: "+android.os.Build.VERSION.RELEASE+" SDK "+android.os.Build.VERSION.SDK_INT+"\nStarted: "+now()+"\nDetected target: "+(target==null?"NONE":target)+"\n\n");
        if(target==null){ append("ERROR: Could not automatically identify the installed ShadowFox/Task Killer package.\n\nInstalled candidates:\n"+candidateList()); status.setText("NO TARGET FOUND — photograph report below"); output.setText(log.toString()); return; }
        status.setText("REQUESTING ROOT + CAPTURING…");
        new Thread(()->{ try { new ProcessBuilder("su","-c","logcat -c").start().waitFor(); logcat=new ProcessBuilder("su","-c","logcat -v threadtime").redirectErrorStream(true).start(); capturing=true; Thread reader=new Thread(()->readLog(logcat)); reader.setDaemon(true); reader.start(); ui.postDelayed(this::launchTarget,900); } catch(Throwable t){ append("ROOT/LOGCAT START FAILED: "+t+"\n"); ui.post(()->{status.setText("FAILED — photograph report below");output.setText(log.toString());}); } }).start();
    }

    private String findTarget(){
        for(String p:KNOWN) if(launchable(p)) return p;
        try { List<PackageInfo> ps=getPackageManager().getInstalledPackages(0); for(PackageInfo pi:ps){ String p=pi.packageName; if(p.equals(getPackageName()))continue; ApplicationInfo ai=pi.applicationInfo; String label=String.valueOf(getPackageManager().getApplicationLabel(ai)); String hay=(p+" "+label).toLowerCase(Locale.US); if((hay.contains("shadowfox")||hay.contains("task killer")||hay.contains("taskkiller")||hay.contains("fast task"))&&launchable(p)) return p; } } catch(Throwable ignored){}
        return null;
    }
    private boolean launchable(String p){ try{return getPackageManager().getLaunchIntentForPackage(p)!=null;}catch(Throwable t){return false;} }
    private String candidateList(){ StringBuilder b=new StringBuilder(); try{for(PackageInfo pi:getPackageManager().getInstalledPackages(0)){String p=pi.packageName;String label=String.valueOf(getPackageManager().getApplicationLabel(pi.applicationInfo));String h=(p+" "+label).toLowerCase(Locale.US);if(h.contains("shadow")||h.contains("task")||h.contains("killer")||h.contains("fast"))b.append(label).append(" = ").append(p).append('\n');}}catch(Throwable t){b.append("Package scan failed: ").append(t);}return b.length()==0?"No matching packages visible to logger.\n":b.toString();}

    private void launchTarget(){ Intent i=getPackageManager().getLaunchIntentForPackage(target); if(i==null){append("ERROR: Detected target is not launchable: "+target+"\n");stopAndShow();return;} append("Launching "+target+" at "+now()+"\n"); status.setText("CAPTURING — ShadowFox will open now"); startActivity(i); }
    @Override protected void onResume(){super.onResume();if(capturing&&System.currentTimeMillis()-startedAt>2500){status.setText("SHADOWFOX RETURNED/CLOSED — collecting final system lines…");ui.postDelayed(this::stopAndShow,1800);}}
    private void readLog(Process p){try(BufferedReader r=new BufferedReader(new InputStreamReader(p.getInputStream()))){String line;while(capturing&&(line=r.readLine())!=null)if(relevant(line))append(line+"\n");}catch(Throwable t){append("LOG READER: "+t+"\n");}}
    private boolean relevant(String s){String x=s.toLowerCase(Locale.US);return x.contains("shadowfox")||x.contains("taskkiller")||(target!=null&&x.contains(target.toLowerCase(Locale.US)))||x.contains("fatal")||x.contains("androidruntime")||x.contains("activitymanager")||x.contains("lowmemory")||x.contains("lmkd")||x.contains("killing")||x.contains("signal")||x.contains("crash")||x.contains("selinux")||x.contains("avc:")||x.contains("zygote")||x.contains("package manager")||x.contains("packagemanager");}
    private synchronized void append(String s){log.append(s);if(log.length()>60000)log.delete(0,log.length()-50000);}
    private void stopAndShow(){capturing=false;if(logcat!=null){try{logcat.destroy();}catch(Throwable ignored){}logcat=null;}append("\nCapture stopped: "+now()+"\n");status.setText("CAPTURE COMPLETE — photograph/scroll through report");output.setText(log.toString());}
    private String now(){return new SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS",Locale.US).format(new Date());}
    @Override protected void onDestroy(){capturing=false;if(logcat!=null)logcat.destroy();super.onDestroy();}
}
