package ca.shadowfoxtv.crashlogger;

import android.app.Activity;
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
import java.util.Locale;

public class MainActivity extends Activity {
 private final StringBuilder log=new StringBuilder(); private TextView status,output; private Process logcat; private volatile boolean capturing; private long startedAt; private String target; private final Handler ui=new Handler(Looper.getMainLooper());
 @Override public void onCreate(Bundle b){super.onCreate(b);buildUi();status.setText("READY — Select START CRASH TEST");}
 private void buildUi(){LinearLayout r=new LinearLayout(this);r.setOrientation(LinearLayout.VERTICAL);r.setPadding(40,28,40,28);r.setBackgroundColor(Color.rgb(3,17,29));TextView t=new TextView(this);t.setText("SHADOWFOX H96 CRASH LOGGER v1.2");t.setTextColor(Color.CYAN);t.setTextSize(26);t.setGravity(Gravity.CENTER);r.addView(t);status=new TextView(this);status.setTextColor(Color.WHITE);status.setTextSize(17);status.setPadding(0,18,0,18);status.setGravity(Gravity.CENTER);r.addView(status);Button s=new Button(this);s.setText("START CRASH TEST");s.setTextSize(18);s.setFocusable(true);s.setOnClickListener(v->startTest());r.addView(s);Button x=new Button(this);x.setText("STOP / SHOW CAPTURE");x.setTextSize(16);x.setFocusable(true);x.setOnClickListener(v->stopAndShow());r.addView(x);output=new TextView(this);output.setTextColor(Color.WHITE);output.setTextSize(12);output.setTextIsSelectable(true);output.setPadding(0,18,0,0);ScrollView sc=new ScrollView(this);sc.addView(output);r.addView(sc,new LinearLayout.LayoutParams(-1,0,1));setContentView(r);s.requestFocus();}
 private void startTest(){if(capturing)return;log.setLength(0);startedAt=System.currentTimeMillis();status.setText("REQUESTING ROOT + SCANNING PACKAGES…");new Thread(()->{try{String packages=root("pm list packages -3");target=findTarget(packages);append("ShadowFox Crash Logger 1.2\nDevice: "+android.os.Build.MANUFACTURER+" "+android.os.Build.MODEL+"\nAndroid: "+android.os.Build.VERSION.RELEASE+" SDK "+android.os.Build.VERSION.SDK_INT+"\nStarted: "+now()+"\nDetected target: "+(target==null?"NONE":target)+"\n\n");if(target==null){append("ROOT PACKAGE SCAN — matching candidates:\n"+candidates(packages));ui.post(()->{status.setText("NO TARGET FOUND — photograph report below");output.setText(log.toString());});return;}root("logcat -c");logcat=new ProcessBuilder("su","-c","logcat -v threadtime").redirectErrorStream(true).start();capturing=true;Thread rd=new Thread(()->readLog(logcat));rd.setDaemon(true);rd.start();ui.postDelayed(this::launchTarget,900);}catch(Throwable e){append("ROOT/SCAN FAILED: "+e+"\n");ui.post(()->{status.setText("FAILED — photograph report below");output.setText(log.toString());});}}).start();}
 private String findTarget(String p){if(p==null)return null;String[] lines=p.split("\\r?\\n");String[] keys={"shadowfox","taskkiller","fasttask","fast.task","task.killer"};for(String line:lines){String pkg=line.replace("package:","").trim();String low=pkg.toLowerCase(Locale.US);if(pkg.equals(getPackageName()))continue;for(String k:keys)if(low.contains(k))return pkg;}return null;}
 private String candidates(String p){if(p==null||p.trim().isEmpty())return "No package output returned by root.\n";StringBuilder b=new StringBuilder();for(String line:p.split("\\r?\\n")){String low=line.toLowerCase(Locale.US);if(low.contains("shadow")||low.contains("task")||low.contains("killer")||low.contains("fast"))b.append(line).append('\n');}if(b.length()==0){b.append("No name match. First user packages from root scan:\n");int n=0;for(String line:p.split("\\r?\\n")){if(!line.trim().isEmpty()){b.append(line).append('\n');if(++n>=30)break;}}}return b.toString();}
 private String root(String cmd)throws Exception{Process p=new ProcessBuilder("su","-c",cmd).redirectErrorStream(true).start();StringBuilder b=new StringBuilder();try(BufferedReader r=new BufferedReader(new InputStreamReader(p.getInputStream()))){String l;while((l=r.readLine())!=null)b.append(l).append('\n');}p.waitFor();return b.toString();}
 private void launchTarget(){new Thread(()->{try{append("Launching "+target+" via root at "+now()+"\n");ui.post(()->status.setText("CAPTURING — ShadowFox will open now"));String res=root("monkey -p "+target+" -c android.intent.category.LAUNCHER 1");append("Launch result: "+res+"\n");}catch(Throwable e){append("ROOT LAUNCH FAILED: "+e+"\n");ui.post(this::stopAndShow);}}).start();}
 @Override protected void onResume(){super.onResume();if(capturing&&System.currentTimeMillis()-startedAt>2500){status.setText("SHADOWFOX RETURNED/CLOSED — collecting final system lines…");ui.postDelayed(this::stopAndShow,1800);}}
 private void readLog(Process p){try(BufferedReader r=new BufferedReader(new InputStreamReader(p.getInputStream()))){String l;while(capturing&&(l=r.readLine())!=null)if(relevant(l))append(l+"\n");}catch(Throwable e){append("LOG READER: "+e+"\n");}}
 private boolean relevant(String s){String x=s.toLowerCase(Locale.US);return x.contains("shadowfox")||x.contains("taskkiller")||(target!=null&&x.contains(target.toLowerCase(Locale.US)))||x.contains("fatal")||x.contains("androidruntime")||x.contains("activitymanager")||x.contains("lowmemory")||x.contains("lmkd")||x.contains("killing")||x.contains("signal")||x.contains("crash")||x.contains("selinux")||x.contains("avc:")||x.contains("zygote")||x.contains("package manager")||x.contains("packagemanager");}
 private synchronized void append(String s){log.append(s);if(log.length()>60000)log.delete(0,log.length()-50000);}
 private void stopAndShow(){capturing=false;if(logcat!=null){try{logcat.destroy();}catch(Throwable ignored){}logcat=null;}append("\nCapture stopped: "+now()+"\n");status.setText("CAPTURE COMPLETE — photograph/scroll through report");output.setText(log.toString());}
 private String now(){return new SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS",Locale.US).format(new Date());}
 @Override protected void onDestroy(){capturing=false;if(logcat!=null)logcat.destroy();super.onDestroy();}
}
