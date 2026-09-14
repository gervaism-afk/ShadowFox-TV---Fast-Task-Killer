from pathlib import Path

# Add Advanced Tools launcher to the existing Ultimate Center without redesigning it.
p = Path('extracted/app/src/main/java/ca/shadowfoxtv/taskkiller/UltimateCenterActivity.kt')
s = p.read_text()

if 'import android.content.Intent\n' not in s:
    s = s.replace('import android.content.res.Configuration\n', 'import android.content.Intent\nimport android.content.res.Configuration\n', 1)
if 'import androidx.compose.ui.platform.LocalContext\n' not in s:
    s = s.replace('import androidx.compose.ui.platform.LocalConfiguration\n', 'import androidx.compose.ui.platform.LocalConfiguration\nimport androidx.compose.ui.platform.LocalContext\n', 1)

old = '''        UltimatePanel("ADVANCED APP CONTROL", "Rooted devices unlock deeper controls. Standard devices keep Android-safe actions.") {
            Text("Unsupported actions are never reported as completed.", color = UMUTED, fontSize = 10.sp)
        }'''
new = '''        UltimatePanel("ADVANCED APP CONTROL", "Running Apps • Startup • System Apps • Gaming • Safety • Update Center") {
            val context = LocalContext.current
            Text("Rooted devices unlock deeper controls. Standard devices keep Android-safe actions. Unsupported actions are never reported as completed.", color = UMUTED, fontSize = 10.sp)
            Spacer(Modifier.height(8.dp))
            UltimateButton("OPEN ADVANCED TOOLS") {
                context.startActivity(Intent(context, AdvancedToolsActivity::class.java))
            }
        }'''
if old not in s:
    raise SystemExit('Expected Advanced App Control panel not found')
s = s.replace(old, new, 1)

# TV/Fire TV focus visibility: keep the locked UI, but make D-pad focus unmistakable.
for imp, anchor in [
    ('import androidx.compose.foundation.border\n', 'import androidx.compose.foundation.background\n'),
    ('import androidx.compose.ui.draw.scale\n', 'import androidx.compose.ui.draw.shadow\n'),
    ('import androidx.compose.ui.focus.onFocusChanged\n', 'import androidx.compose.ui.graphics.Brush\n'),
]:
    if imp not in s:
        s = s.replace(anchor, anchor + imp, 1)

old_button = '''@Composable
private fun UltimateButton(text: String, enabled: Boolean = true, onClick: () -> Unit) {
    Button(
        onClick = onClick, enabled = enabled,
        colors = ButtonDefaults.buttonColors(containerColor = UCYAN, contentColor = Color(0xFF05202A), disabledContainerColor = Color(0xFF31505A)),
        modifier = Modifier.height(36.dp).focusable()
    ) { Text(text, fontSize = 9.sp, fontWeight = FontWeight.Black, maxLines = 1) }
}'''
new_button = '''@Composable
private fun UltimateButton(text: String, enabled: Boolean = true, onClick: () -> Unit) {
    var focused by remember { mutableStateOf(false) }
    val shape = RoundedCornerShape(50)
    Button(
        onClick = onClick,
        enabled = enabled,
        colors = ButtonDefaults.buttonColors(
            containerColor = if (focused) UWHITE else UCYAN,
            contentColor = Color(0xFF05202A),
            disabledContainerColor = Color(0xFF31505A)
        ),
        modifier = Modifier
            .height(36.dp)
            .scale(if (focused) 1.08f else 1f)
            .shadow(if (focused) 22.dp else 0.dp, shape, clip = false, ambientColor = UWHITE, spotColor = UCYAN)
            .border(if (focused) 3.dp else 0.dp, if (focused) UWHITE else Color.Transparent, shape)
            .onFocusChanged { focused = it.isFocused }
            .focusable(enabled),
        shape = shape
    ) { Text(text, fontSize = 9.sp, fontWeight = FontWeight.Black, maxLines = 1) }
}'''
if old_button not in s:
    raise SystemExit('Expected UltimateButton not found')
s = s.replace(old_button, new_button, 1)

old_compact = '''@Composable
private fun CompactAction(text: String, modifier: Modifier, onClick: () -> Unit) {
    Box(
        modifier.height(38.dp).background(UCYAN, RoundedCornerShape(50)).clickable(onClick = onClick).focusable(),
        contentAlignment = Alignment.Center
    ) { Text(text, color = Color(0xFF05202A), fontSize = 9.sp, fontWeight = FontWeight.Black, maxLines = 1, overflow = TextOverflow.Ellipsis) }
}'''
new_compact = '''@Composable
private fun CompactAction(text: String, modifier: Modifier, onClick: () -> Unit) {
    var focused by remember { mutableStateOf(false) }
    val shape = RoundedCornerShape(50)
    Box(
        modifier
            .height(38.dp)
            .scale(if (focused) 1.06f else 1f)
            .shadow(if (focused) 20.dp else 0.dp, shape, clip = false, ambientColor = UWHITE, spotColor = UCYAN)
            .background(if (focused) UWHITE else UCYAN, shape)
            .border(if (focused) 3.dp else 0.dp, if (focused) UWHITE else Color.Transparent, shape)
            .onFocusChanged { focused = it.isFocused }
            .clickable(onClick = onClick)
            .focusable(),
        contentAlignment = Alignment.Center
    ) { Text(text, color = Color(0xFF05202A), fontSize = 9.sp, fontWeight = FontWeight.Black, maxLines = 1, overflow = TextOverflow.Ellipsis) }
}'''
if old_compact not in s:
    raise SystemExit('Expected CompactAction not found')
s = s.replace(old_compact, new_compact, 1)

old_tab = '''@Composable
private fun UltimateTabButton(text: String, selected: Boolean, modifier: Modifier, onClick: () -> Unit) {
    Box(
        modifier.height(42.dp).background(if (selected) UCYAN else Color(0xFF0A2637), RoundedCornerShape(12.dp)).clickable(onClick = onClick).focusable(),
        contentAlignment = Alignment.Center
    ) { Text(text, color = if (selected) Color(0xFF05202A) else UWHITE, fontSize = 10.sp, fontWeight = FontWeight.Black, maxLines = 1) }
}'''
new_tab = '''@Composable
private fun UltimateTabButton(text: String, selected: Boolean, modifier: Modifier, onClick: () -> Unit) {
    var focused by remember { mutableStateOf(false) }
    val shape = RoundedCornerShape(12.dp)
    Box(
        modifier
            .height(42.dp)
            .scale(if (focused) 1.05f else 1f)
            .shadow(if (focused) 20.dp else 0.dp, shape, clip = false, ambientColor = UWHITE, spotColor = UCYAN)
            .background(if (focused) UWHITE else if (selected) UCYAN else Color(0xFF0A2637), shape)
            .border(if (focused) 3.dp else 0.dp, if (focused) UWHITE else Color.Transparent, shape)
            .onFocusChanged { focused = it.isFocused }
            .clickable(onClick = onClick)
            .focusable(),
        contentAlignment = Alignment.Center
    ) { Text(text, color = if (focused || selected) Color(0xFF05202A) else UWHITE, fontSize = 10.sp, fontWeight = FontWeight.Black, maxLines = 1) }
}'''
if old_tab not in s:
    raise SystemExit('Expected UltimateTabButton not found')
s = s.replace(old_tab, new_tab, 1)
p.write_text(s)

# Apply the same unmistakable D-pad focus to every button and tab inside Advanced Tools.
a = Path('extracted/app/src/main/java/ca/shadowfoxtv/taskkiller/AdvancedToolsActivity.kt')
asrc = a.read_text()
for imp, anchor in [
    ('import androidx.compose.foundation.border\n', 'import androidx.compose.foundation.background\n'),
    ('import androidx.compose.ui.draw.scale\n', 'import androidx.compose.ui.graphics.Color\n'),
    ('import androidx.compose.ui.draw.shadow\n', 'import androidx.compose.ui.draw.scale\n'),
    ('import androidx.compose.ui.focus.onFocusChanged\n', 'import androidx.compose.ui.graphics.Color\n'),
]:
    if imp not in asrc:
        asrc = asrc.replace(anchor, anchor + imp, 1)

old_action = '@Composable private fun Action(text:String,onClick:()->Unit) { Box(Modifier.height(38.dp).background(ACYAN, RoundedCornerShape(20.dp)).clickable(onClick=onClick).focusable().padding(horizontal=13.dp), contentAlignment=Alignment.Center) { Text(text,color=ABG,fontSize=9.sp,fontWeight=FontWeight.Black,maxLines=1,overflow=TextOverflow.Ellipsis) } }'
new_action = '''@Composable private fun Action(text:String,onClick:()->Unit) {
    var focused by remember { mutableStateOf(false) }
    val shape = RoundedCornerShape(20.dp)
    Box(
        Modifier.height(38.dp)
            .scale(if (focused) 1.08f else 1f)
            .shadow(if (focused) 22.dp else 0.dp, shape, clip=false, ambientColor=AWHITE, spotColor=ACYAN)
            .background(if (focused) AWHITE else ACYAN, shape)
            .border(if (focused) 3.dp else 0.dp, if (focused) AWHITE else Color.Transparent, shape)
            .onFocusChanged { focused = it.isFocused }
            .clickable(onClick=onClick)
            .focusable()
            .padding(horizontal=13.dp),
        contentAlignment=Alignment.Center
    ) { Text(text,color=ABG,fontSize=9.sp,fontWeight=FontWeight.Black,maxLines=1,overflow=TextOverflow.Ellipsis) }
}'''
if old_action not in asrc:
    raise SystemExit('Expected Advanced Action not found')
asrc = asrc.replace(old_action, new_action, 1)

old_atab = '@Composable private fun TabButton(text:String, selected:Boolean, modifier:Modifier,onClick:()->Unit) { Box(modifier.height(40.dp).background(if(selected) ACYAN else APANEL,RoundedCornerShape(10.dp)).clickable(onClick=onClick).focusable(),contentAlignment=Alignment.Center) { Text(text,color=if(selected) ABG else AWHITE,fontSize=8.sp,fontWeight=FontWeight.Black,maxLines=1) } }'
new_atab = '''@Composable private fun TabButton(text:String, selected:Boolean, modifier:Modifier,onClick:()->Unit) {
    var focused by remember { mutableStateOf(false) }
    val shape = RoundedCornerShape(10.dp)
    Box(
        modifier.height(40.dp)
            .scale(if (focused) 1.06f else 1f)
            .shadow(if (focused) 22.dp else 0.dp, shape, clip=false, ambientColor=AWHITE, spotColor=ACYAN)
            .background(if (focused) AWHITE else if(selected) ACYAN else APANEL, shape)
            .border(if (focused) 3.dp else 0.dp, if (focused) AWHITE else Color.Transparent, shape)
            .onFocusChanged { focused = it.isFocused }
            .clickable(onClick=onClick)
            .focusable(),
        contentAlignment=Alignment.Center
    ) { Text(text,color=if(focused || selected) ABG else AWHITE,fontSize=8.sp,fontWeight=FontWeight.Black,maxLines=1) }
}'''
if old_atab not in asrc:
    raise SystemExit('Expected Advanced TabButton not found')
asrc = asrc.replace(old_atab, new_atab, 1)
a.write_text(asrc)

# Make scheduled self-heal health-aware while preserving ordinary scheduled maintenance behavior.
u = Path('extracted/app/src/main/java/ca/shadowfoxtv/taskkiller/UltimateCore.kt')
us = u.read_text()
old_receiver = '''                val manager = UltimateManager(context)
                if (manager.maintenanceEnabled()) kotlinx.coroutines.runBlocking { manager.smartOptimize() }'''
new_receiver = '''                val manager = UltimateManager(context)
                if (manager.maintenanceEnabled()) {
                    val advanced = context.getSharedPreferences("shadowfox_advanced", Context.MODE_PRIVATE)
                    val selfHeal = advanced.getBoolean("self_heal", false)
                    kotlinx.coroutines.runBlocking {
                        if (selfHeal) {
                            val snap = manager.snapshot()
                            if (snap.health < 85 || snap.ramUsedPercent > 75) manager.smartOptimize()
                        } else {
                            manager.smartOptimize()
                        }
                    }
                }'''
if old_receiver not in us:
    raise SystemExit('Expected MaintenanceReceiver block not found')
us = us.replace(old_receiver, new_receiver, 1)
u.write_text(us)

print('Integrated ShadowFox v6.1 Advanced Tools, visible TV focus, and health-aware self-heal')
