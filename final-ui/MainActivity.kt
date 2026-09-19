package ca.shadowfoxtv.taskkiller
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
// SHADOWFOX_V622_UI
class MainActivity : ComponentActivity() {
 override fun onCreate(savedInstanceState: Bundle?) {
  super.onCreate(savedInstanceState)
  startActivity(Intent(this, UltimateCenterActivity::class.java))
  finish()
 }
}