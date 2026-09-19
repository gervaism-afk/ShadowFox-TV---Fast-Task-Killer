package ca.shadowfoxtv.taskkiller

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.*
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.view.*
import kotlinx.coroutines.launch
import java.util.Locale

// SHADOWFOX_V622_UI
private val BG=Color(0xFF07090C); private val RAIL=Color(0xFF0B0E12)
private val PANEL=Color(0xFF11151A); private val PANEL2=Color(0xFF171C22)
private val STEEL=Color(0xFF39434D); private val BLUE=Color(0xFF21A7FF)
private val CYAN=Color(0xFF70D4FF); private val WHITE=Color(0xFFF2F5F7)
private val MUTED=Color(0xFF8E99A4); private val GREEN=Color(0xFF61D17D)
private val ORANGE=Color(0xFFFFA340)

class UltimateCenterActivity:ComponentActivity(){
 override fun onCreate(b:Bundle?){super.onCreate(b);WindowCompat.setDecorFitsSystemWindows(window,false);bars()
  setContent{MaterialTheme(darkColorScheme(background=BG,surface=PANEL)){Dashboard(UltimateManager(applicationContext))}}
 }
 override fun onResume(){super.onResume();bars()}
 private fun bars(){WindowInsetsControllerCompat(window,window.decorView).apply{hide(WindowInsetsCompat.Type.systemBars());systemBarsBehavior=WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE}}
}
private enum class Page{OPTIMIZE,APPS,NETWORK,SYSTEM}

@Composable private fun Dashboard(m:UltimateManager){
 var page by remember{mutableStateOf(Page.OPTIMIZE)};var snap by remember{mutableStateOf<UltimateSnapshot?>(null)}
 val scope=rememberCoroutineScope();fun refresh(){scope.launch{snap=m.snapshot()}};LaunchedEffect(Unit){snap=m.snapshot()}
 Row(Modifier.fillMaxSize().background(BG)){
  Rail(page,snap){page=it}
  Column(Modifier.weight(1f).fillMaxHeight().padding(18.dp,14.dp,20.dp,10.dp)){
   Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically){
    Column(Modifier.weight(1f)){Text("SHADOWFOX TV OPTIMIZER",color=WHITE,fontSize=20.sp,fontWeight=FontWeight.Black);Text(snap?.mode?:"DETECTING DEVICE…",color=if(snap?.root==true)GREEN else MUTED,fontSize=9.sp)}
    Text("ANDROID TV PERFORMANCE CONSOLE",color=MUTED,fontSize=9.sp)
   };Spacer(Modifier.height(12.dp))
   Box(Modifier.weight(1f)){when(page){Page.OPTIMIZE->Optimize(m,snap,::refresh);Page.APPS->Apps(m);Page.NETWORK->Network(m);Page.SYSTEM->System(m,snap,::refresh)}}
   Text("SHADOWFOX TV  |  OPTIMIZED FOR PERFORMANCE",color=MUTED,fontSize=8.sp)
  }
 }
}
@Composable private fun Rail(page:Page,s:UltimateSnapshot?,select:(Page)->Unit){
 Column(Modifier.width(184.dp).fillMaxHeight().background(RAIL).border(1.dp,STEEL.copy(.55f)).padding(14.dp)){
  Image(painterResource(R.drawable.shadowfox_logo),"ShadowFox TV",Modifier.fillMaxWidth().height(94.dp),contentScale=ContentScale.Fit)
  Text("ROOTED PRO MODE",color=if(s?.root==true)GREEN else MUTED,fontSize=9.sp,fontWeight=FontWeight.Bold);Text("v${BuildConfig.VERSION_NAME}",color=CYAN,fontSize=9.sp)
  Spacer(Modifier.height(28.dp));Page.entries.forEach{Nav(it.name,page==it){select(it)};Spacer(Modifier.height(9.dp))}
  Spacer(Modifier.weight(1f));Text("www.shadowfoxtv.ca",color=MUTED,fontSize=8.sp)
 }
}
@Composable private fun Optimize(m:UltimateManager,s:UltimateSnapshot?,refresh:()->Unit){
 val scope=rememberCoroutineScope();var busy by remember{mutableStateOf(false)};var status by remember{mutableStateOf("READY")};var cache by remember{mutableStateOf("ShadowFox cache ready")}
 Column{
  Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(10.dp)){
   Metric("SHADOWFOX SCORE","${s?.health?:0}/100",if((s?.health?:0)>=75)GREEN else ORANGE,Modifier.weight(1f))
   Metric("RAM USED","${s?.ramUsedPercent?:0}%",CYAN,Modifier.weight(1f));Metric("RUNNING APPS","${s?.runningApps?:0}",CYAN,Modifier.weight(1f));Metric("NETWORK",s?.network?:"…",CYAN,Modifier.weight(1f))
  };Spacer(Modifier.height(12.dp))
  Row(Modifier.fillMaxWidth().height(222.dp),horizontalArrangement=Arrangement.spacedBy(12.dp)){
   Card("SMART OPTIMIZE","Safe cleanup selected for this device.",Modifier.weight(1.5f).fillMaxHeight()){
    Text(status,color=if(status=="READY"||status.contains("verified",true))GREEN else MUTED,fontSize=10.sp,maxLines=2);Spacer(Modifier.weight(1f))
    Action(if(busy)"OPTIMIZING…" else "ONE-TAP SMART OPTIMIZE",!busy){scope.launch{busy=true;status="SCANNING RAM • APPS • CACHE…";status=m.smartOptimize().summary;busy=false;refresh()}}
   }
   Card("CACHE CLEANER","Clear temporary cache without deleting app data.",Modifier.weight(1f).fillMaxHeight()){
    Text(cache,color=MUTED,fontSize=10.sp);Spacer(Modifier.weight(1f));Action("CLEAN CACHE"){val n=m.clearOwnCache();cache="${bytes(n)} cleared";refresh()}
   }
   Card("STATUS","Live optimizer state.",Modifier.weight(.8f).fillMaxHeight()){
    Text(if(s?.root==true)"ROOT ACTIVE" else "STANDARD MODE",color=if(s?.root==true)GREEN else CYAN,fontSize=14.sp,fontWeight=FontWeight.Black);Spacer(Modifier.height(8.dp))
    Text("Free RAM  ${bytes(s?.freeRam?:0)}",color=WHITE,fontSize=10.sp);Text("Free Storage  ${bytes(s?.freeStorage?:0)}",color=WHITE,fontSize=10.sp)
   }
  };Spacer(Modifier.height(12.dp))
  Card("THERMAL + PERFORMANCE","Actual Android telemetry from this device.",Modifier.fillMaxWidth().height(92.dp)){
   Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween){Text("THERMAL  ${s?.temperatureStatus?:"…"}",color=if(s?.temperatureStatus=="NORMAL")GREEN else ORANGE,fontSize=12.sp,fontWeight=FontWeight.Bold);Text("RAM FREE  ${bytes(s?.freeRam?:0)}",color=CYAN,fontSize=12.sp);Text("STORAGE FREE  ${bytes(s?.freeStorage?:0)}",color=CYAN,fontSize=12.sp)}
  }
 }
}
@Composable private fun Apps(m:UltimateManager){
 val scope=rememberCoroutineScope();var apps by remember{mutableStateOf<List<ManagedApp>>(emptyList())};var msg by remember{mutableStateOf("Loading apps…")}
 fun load(){scope.launch{apps=m.apps();msg="${apps.size} launchable apps"}};LaunchedEffect(Unit){load()}
 Column(Modifier.fillMaxSize()){Title("APPS",msg);Spacer(Modifier.height(10.dp));Column(Modifier.weight(1f).verticalScroll(rememberScrollState())){
  apps.forEach{a->Card(a.label,"${a.packageName} • ${if(a.running)"RUNNING" else "IDLE"}",Modifier.fillMaxWidth()){
   Row(horizontalArrangement=Arrangement.spacedBy(7.dp)){Small("LAUNCH"){m.launch(a.packageName)};Small(if(a.protected)"UNPROTECT" else "PROTECT"){m.setProtected(a.packageName,!a.protected);load()};Small("STREAM"){scope.launch{msg=m.streamingOptimize(a.packageName).summary;load()}};Small("SETTINGS"){m.openSettings(a.packageName)};if(!a.system)Small("UNINSTALL"){m.uninstall(a.packageName)}}
  };Spacer(Modifier.height(7.dp))}
 }}
}
@Composable private fun Network(m:UltimateManager){
 val scope=rememberCoroutineScope();var r by remember{mutableStateOf<NetworkReport?>(null)};var testing by remember{mutableStateOf(false)}
 fun test(){scope.launch{testing=true;r=m.networkReport();testing=false}};LaunchedEffect(Unit){test()}
 Column{Title("NETWORK","Direct connection diagnostics");Spacer(Modifier.height(12.dp));Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(10.dp)){
  Metric("STATUS",r?.verdict?:"TESTING",if(r?.connected==true)GREEN else ORANGE,Modifier.weight(1f));Metric("TYPE",r?.transport?:"…",CYAN,Modifier.weight(1f));Metric("PING",r?.pingMs?.takeIf{it>=0}?.let{"$it ms"}?:"--",CYAN,Modifier.weight(1f));Metric("DNS",r?.dnsMs?.takeIf{it>=0}?.let{"$it ms"}?:"--",CYAN,Modifier.weight(1f))
 };Spacer(Modifier.height(12.dp));Card("CONNECTION DIAGNOSTICS","Direct socket latency checks using ShadowFox thresholds.",Modifier.fillMaxWidth().height(180.dp)){Text(explain(r?.verdict),color=WHITE,fontSize=11.sp);Spacer(Modifier.weight(1f));Action(if(testing)"TESTING…" else "RUN NETWORK TEST",!testing){test()}}}
}
@Composable private fun System(m:UltimateManager,s:UltimateSnapshot?,refresh:()->Unit){
 val d=remember{m.deviceReport()};var st by remember{mutableStateOf(m.storageReport())};var maint by remember{mutableStateOf(m.maintenanceEnabled())};var msg by remember{mutableStateOf("SYSTEM READY")}
 Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState())){Title("SYSTEM",msg);Spacer(Modifier.height(10.dp));Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(10.dp)){
  Metric("MODE",d.rootMode,if(s?.root==true)GREEN else CYAN,Modifier.weight(1f));Metric("ANDROID","${d.android} / SDK ${d.sdk}",CYAN,Modifier.weight(1f));Metric("WIDEVINE",d.widevine,CYAN,Modifier.weight(1f));Metric("THERMAL",d.thermal,if(d.thermal=="NORMAL")GREEN else ORANGE,Modifier.weight(1f))
 };Spacer(Modifier.height(12.dp));Card("DEVICE CENTER","${d.manufacturer} ${d.model} • ${d.abi}",Modifier.fillMaxWidth()){
  Text("Storage: ${bytes(st.usedBytes)} used • ${bytes(st.freeBytes)} free",color=WHITE,fontSize=10.sp);Spacer(Modifier.height(10.dp));Row(horizontalArrangement=Arrangement.spacedBy(8.dp)){
   Small("CLEAR CACHE"){val n=m.clearOwnCache();st=m.storageReport();msg="${bytes(n)} cleared"};Small(if(maint)"MAINTENANCE: ON" else "MAINTENANCE: OFF"){maint=!maint;m.scheduleMaintenance(maint)};Small("REFRESH"){refresh();msg="System refreshed"};Small("CHECK UPDATE"){msg="Checking for update…";GitHubReleaseUpdater.start(m.appContext()){msg=it}}
  }
 };Spacer(Modifier.height(10.dp));Card("MAINTENANCE HISTORY","Verified activity only.",Modifier.fillMaxWidth()){val h=m.history();if(h.isEmpty())Text("No maintenance history yet.",color=MUTED,fontSize=9.sp);h.take(6).forEach{Text(it,color=WHITE,fontSize=9.sp,maxLines=1,overflow=TextOverflow.Ellipsis)}}}
}
@Composable private fun Title(a:String,b:String){Column{Text(a,color=WHITE,fontSize=20.sp,fontWeight=FontWeight.Black);Text(b,color=MUTED,fontSize=9.sp)}}
@Composable private fun Metric(a:String,b:String,c:Color,mod:Modifier){Column(mod.height(72.dp).background(PANEL,RoundedCornerShape(6.dp)).border(1.dp,STEEL,RoundedCornerShape(6.dp)).padding(12.dp)){Text(a,color=MUTED,fontSize=8.sp,fontWeight=FontWeight.Bold);Spacer(Modifier.height(4.dp));Text(b,color=c,fontSize=15.sp,fontWeight=FontWeight.Black,maxLines=1)}}
@Composable private fun Card(a:String,b:String,mod:Modifier,content:@Composable ColumnScope.()->Unit){Column(mod.background(Brush.verticalGradient(listOf(PANEL2,PANEL)),RoundedCornerShape(7.dp)).border(1.dp,STEEL,RoundedCornerShape(7.dp)).padding(14.dp)){Text(a,color=WHITE,fontSize=14.sp,fontWeight=FontWeight.Black);Text(b,color=MUTED,fontSize=8.sp,maxLines=2);Spacer(Modifier.height(10.dp));content()}}
@Composable private fun Nav(t:String,selected:Boolean,click:()->Unit){var f by remember{mutableStateOf(false)};val sh=RoundedCornerShape(5.dp);Row(Modifier.fillMaxWidth().height(46.dp).scale(if(f)1.025f else 1f).background(if(selected||f)Color(0xFF151D24) else Color.Transparent,sh).border(1.dp,if(selected||f)BLUE else STEEL.copy(.5f),sh).onFocusChanged{f=it.isFocused}.focusable().clickable(onClick=click).padding(horizontal=12.dp),verticalAlignment=Alignment.CenterVertically){Box(Modifier.width(3.dp).height(22.dp).background(if(selected||f)BLUE else Color.Transparent));Spacer(Modifier.width(10.dp));Text(t,color=if(selected||f)WHITE else MUTED,fontSize=11.sp,fontWeight=FontWeight.Bold)}}
@Composable private fun Action(t:String,en:Boolean=true,click:()->Unit){var f by remember{mutableStateOf(false)};val sh=RoundedCornerShape(5.dp);Box(Modifier.height(40.dp).fillMaxWidth().background(if(f)Color(0xFF17344A) else Color(0xFF101D27),sh).border(1.dp,if(f)CYAN else BLUE.copy(.75f),sh).onFocusChanged{f=it.isFocused}.focusable(en).then(if(en)Modifier.clickable(onClick=click) else Modifier),contentAlignment=Alignment.Center){Text(t,color=if(en)WHITE else MUTED,fontSize=10.sp,fontWeight=FontWeight.Black)}}
@Composable private fun Small(t:String,click:()->Unit){var f by remember{mutableStateOf(false)};val sh=RoundedCornerShape(4.dp);Box(Modifier.height(34.dp).widthIn(min=92.dp).background(if(f)Color(0xFF18374E) else Color(0xFF101820),sh).border(1.dp,if(f)CYAN else STEEL,sh).onFocusChanged{f=it.isFocused}.focusable().clickable(onClick=click).padding(horizontal=12.dp),contentAlignment=Alignment.Center){Text(t,color=WHITE,fontSize=8.sp,fontWeight=FontWeight.Bold)}}
private fun explain(v:String?)=when(v){"EXCELLENT"->"Connection looks excellent for streaming.";"GOOD"->"Connection looks healthy.";"FAIR"->"Streaming should work, but latency is elevated.";"HIGH LATENCY"->"High latency detected. Check Wi-Fi signal, router load or ISP.";"NO INTERNET"->"No usable internet connection detected.";else->"Run the test to diagnose the connection."}
private fun bytes(n:Long):String{if(n<=0)return "0 MB";val mb=n/(1024.0*1024.0);return if(mb>=1024)String.format(Locale.US,"%.2f GB",mb/1024.0) else String.format(Locale.US,"%.0f MB",mb)}
