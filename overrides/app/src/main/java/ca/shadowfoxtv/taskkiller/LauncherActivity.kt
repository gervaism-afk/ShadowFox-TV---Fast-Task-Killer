package ca.shadowfoxtv.taskkiller

import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import androidx.activity.ComponentActivity

class LauncherActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val pm = packageManager
        val isTv = pm.hasSystemFeature(PackageManager.FEATURE_LEANBACK) ||
            pm.hasSystemFeature("android.hardware.type.television")
        startActivity(Intent(this, if (isTv) MainActivity::class.java else MobileActivity::class.java))
        finish()
    }
}
