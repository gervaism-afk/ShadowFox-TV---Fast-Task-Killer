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
 private static final String TARGET="eu.rivilab.ud1g4v";
 private final StringBuilder log=new StringBuilder(); private TextView status,output; private Process logcat; private volatile boolean capturing; private long startedAt; private final Handler ui=new Handler(Looper.getMainLooper());
 @Override public void onCreate(Bundle b){super.onCreate(b);buildUi();status.setText("READY — Select START CRASH TEST");}
 private void buildUi(){LinearLayout r=new LinearLayout(this);r.setOrientation(LinearLayout.VERTICAL);r.setPadding(40,28,40,28);r.setBackgroundColor(Color.rgb(3,17,29));TextView t=new TextView(this);t.setText("SHADOWFOX H96 CRASH LOGGER v1.3");t.setTextColor(Color.CYAN);t.setTextSize(26);t.setGravity(Gravity.CENTER);r.addView(t);status=new TextView(this);status.setTextColor(Color.WHITE);status.setTextSize(17);status.setPadding(0,18,0,18);status.setGravity(Gravity.CENTER);r.addView(status);Button s=new Button(this);s.setText("START CRASH TEST");s.setTextSize(18);s.setFocusable(true);s.setOnClickListener(v->startTest());r.addView(s);Button x=new Button(this);x.setText("STOP / SHOW CAPTURE");x.setTextSize(16);x.setFocusable(true);x.setOnClickListener(v->stopAndShow());r.addView(x);output=new TextView(this);output.setTextColor(Color.WHITE);output.setTextSize(12);output.setTextIsSelectable(true);output.setPadding(0,18,0,0);ScrollView sc=new ScrollView(this);sc.addView(output);r.addView(sc,new LinearLayout.LayoutParams(-1,0,1));setContentView(r);s.requestFocus();}
 private void startTest(){if(capturing)return;log.setLength(0);startedAt=System.currentTimeMillis();status.setText("REQUESTING ROOT + VERIFYING TARGET…");new Thread(()->{try{String check=root("pm path "+TARGET+"; dumpsys package "+TARGET+" | grep -m 1 versionName");append("ShadowFox Crash Logger 1.3\nDevice: "+android.os.Build.MANUFACTURER+" "+android.os.Build.MODEL+"\nAndroid: "+android.os.Build.VERSION.RELEASE+" SDK "+android.os.Build.VERSION.SDK_INT+"\nStarted: "+now()+"\nTarget: "+TARGET+"\nVerification: "+check+"\n");if(check==null||!check.contains("package:")){ui.post(()->{status.setText("TARGET NOT INSTALLED — photograph report");output.setText(log.toString());});return;}root("logcat -c");logcat=new ProcessBuilder("su","-c","logcat -v threadtime").redirectErrorStream(true).start();capturing=true;Thread rd=new Thread(()->readLog(logcat));rd.setDaemon(true);rd.start();ui.postDelayed(this::launchTarget,900);}catch(Throwable e){append("ROOT/VERIFY FAILED: "+e+"\n");ui.post(()->{status.setText("FAILED — photograph report below");output.setText(log.toString());});}}).start();}
 private String root(String cmd)throws Exception{Process p=new ProcessBuilder("su","-c",cmd).redirectErrorStream(true).start();StringBuilder b=new StringBuilder();try(BufferedReader r=new BufferedReader(new InputStreamReader(p.getInputStream()))){String l;while((l=r.readLine())!=null)b.append(l).append('\n');}p.waitFor();return b.toString();}
 private void launchTarget(){new Thread(()->{try{append("Launching "+TARGET+" via root at "+now()+"\n");ui.post(()->status.setText("CAPTURING — ShadowFox will open now"));String res=root("monkey -p "+TARGET+" -c android.intent.category.LAUNCHER 1");append("Launch result: "+res+"\n");}catch(Throwable e){append("ROOT LAUNCH FAILED: "+e+"\n");ui.post(this::stopAndShow);}}).start();}
 @Override protected void onResume(){super.onResume();if(capturing&&System.currentTimeMillis()-startedAt>2500){status.setText("SHADOWFOX RETURNED/CLOSED — collecting final system lines…");ui.postDelayed(this::stopAndShow,2200);}}
 private void readLog(Process p){try(BufferedReader r=new BufferedReader(new InputStreamReader(p.getInputStream()))){String l;while(capturing&&(l=r.readLine())!=null)if(relevant(l))append(l+"\n");}catch(Throwable e){append("LOG READER: "+e+"\n");}}
 private boolean relevant(String s){String x=s.toLowerCase(Locale.US);return x.contains(TARGET)||x.contains("shadowfox")||x.contains("taskkiller")||x.contains("fatal")||x.contains("androidruntime")||x.contains("activitymanager")||x.contains("lowmemory")||x.contains("lmkd")||x.contains("killing")||x.contains("signal")||x.contains("crash")||x.contains("selinux")||x.contains("avc:")||x.contains("zygote")||x.contains("package manager")||x.contains("packagemanager");}
 private synchronized void append(String s){log.append(s);if(log.length()>60000)log.delete(0,log.length()-50000);}
 private void stopAndShow(){capturing=false;if(logcat!=null){try{logcat.destroy();}catch(Throwable ignored){}logcat=null;}append("\nCapture stopped: "+now()+"\n");status.setText("CAPTURE COMPLETE — photograph/scroll through report");output.setText(log.toString());}
 private String now(){return new SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS",Locale.US).format(new Date());}
 @Override protected void onDestroy(){capturing=false;if(logcat!=null)logcat.destroy();super.onDestroy();}
}
