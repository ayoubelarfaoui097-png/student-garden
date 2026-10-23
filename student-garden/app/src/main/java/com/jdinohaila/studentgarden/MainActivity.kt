package com.jdinohaila.studentgarden

import android.app.KeyguardManager
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.lifecycle.ViewModel
import androidx.lifecycle.lifecycleScope
import com.jdinohaila.studentgarden.ui.*
import com.jdinohaila.studentgarden.ui.theme.GardenTheme
import kotlinx.coroutines.launch

class AppGate : ViewModel() {
    var unlocked by mutableStateOf(false)
}

class MainActivity : AppCompatActivity() {
    private val gate by viewModels<AppGate>()
    internal var lessonRequest by mutableIntStateOf(0)
        private set
    internal var reminderRequest by mutableIntStateOf(0)
        private set
    internal var reminderDestination by mutableStateOf("")
        private set
    private var reminderToken = ""
    private fun acceptReminderIntent(intent: android.content.Intent) {
        acceptLessonIntent(intent)
        val destination = intent.getStringExtra("reminder_destination") ?: return
        if (destination !in setOf("tasks", "agenda", "cards", "habits", "focus", "settings")) return
        val token = intent.getLongExtra("reminder_request_token", Long.MIN_VALUE)
        val key = "$destination:$token"
        if (token != Long.MIN_VALUE && key != reminderToken) {
            reminderToken = key
            reminderDestination = destination
            reminderRequest++
        }
    }
    private var lessonToken: Long = Long.MIN_VALUE

    private fun acceptLessonIntent(intent: android.content.Intent) {
        val token = intent.getLongExtra("lesson_request_token", Long.MIN_VALUE)
        if (token != Long.MIN_VALUE && token != lessonToken) {
            lessonToken = token
            lessonRequest++
        }
    }
    private val unlockLauncher =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) {
            gate.unlocked = it.resultCode == RESULT_OK
        }

    private fun requestUnlock() {
        val manager = getSystemService(KeyguardManager::class.java)
        @Suppress("DEPRECATION")
        val intent =
            manager.createConfirmDeviceCredentialIntent(
                getString(R.string.unlock),
                getString(R.string.lock_hint),
            )
        if (intent != null) unlockLauncher.launch(intent)
        else
            startActivity(
                android.content.Intent(android.provider.Settings.ACTION_SECURITY_SETTINGS)
            )
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        lessonRequest = savedInstanceState?.getInt("lessonRequest") ?: 0
        lessonToken = savedInstanceState?.getLong("lessonToken", Long.MIN_VALUE) ?: Long.MIN_VALUE
        reminderRequest = savedInstanceState?.getInt("reminderRequest") ?: 0
        reminderToken = savedInstanceState?.getString("reminderToken").orEmpty()
        reminderDestination = savedInstanceState?.getString("reminderDestination").orEmpty()
        acceptReminderIntent(intent)
        enableEdgeToEdge()
        if (android.os.Build.VERSION.SDK_INT >= 29) window.isNavigationBarContrastEnforced = false
        val app = application as GardenApp
        if (!getSharedPreferences("settings", 0).getBoolean("lock", false)) gate.unlocked = true
        lifecycleScope.launch { app.repository.load() }
        setContent {
            var appearance by remember {
                mutableStateOf(getSharedPreferences("settings", 0).getString("theme", "system")!!)
            }
            GardenTheme(appearance) {
                Box {
                    Box(if (gate.unlocked) Modifier else Modifier.clearAndSetSemantics {}) {
                        GardenRoot(app.repository, app.media, onTheme = { appearance = it },
                            lessonRequest = lessonRequest, reminderRequest = reminderRequest, reminderDestination = reminderDestination)
                    }
                    if (!gate.unlocked) UnlockScreen { requestUnlock() }
                    BackHandler(enabled = !gate.unlocked) {}
                }
            }
        }
    }

    override fun onNewIntent(intent: android.content.Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        acceptReminderIntent(intent)
    }

    override fun onSaveInstanceState(outState: Bundle) {
        outState.putInt("reminderRequest", reminderRequest)
        outState.putString("reminderToken", reminderToken)
        outState.putString("reminderDestination", reminderDestination)
        outState.putInt("lessonRequest", lessonRequest)
        outState.putLong("lessonToken", lessonToken)
        super.onSaveInstanceState(outState)
    }

    override fun onStop() {
        super.onStop()
        if (
            getSharedPreferences("settings", 0).getBoolean("lock", false) &&
                !isChangingConfigurations
        )
            gate.unlocked = false
    }

    override fun onResume() {
        super.onResume()
        if (getSharedPreferences("settings", 0).getBoolean("lock", false))
            window.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
        else window.clearFlags(WindowManager.LayoutParams.FLAG_SECURE)
        lifecycleScope.launch {
            val app = application as GardenApp
            app.repository.load()
            app.repository.state.value.timer?.let {
                com.jdinohaila.studentgarden.platform.FocusAlarm.schedule(this@MainActivity, it)
            }
        }
    }
}
