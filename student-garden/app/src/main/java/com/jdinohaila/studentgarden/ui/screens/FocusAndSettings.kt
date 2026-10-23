@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.jdinohaila.studentgarden.ui.screens

import android.Manifest
import android.app.*
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.*
import android.provider.Settings
import androidx.activity.compose.LocalActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatDelegate
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.Image
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.lazy.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.*
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.*
import androidx.core.content.ContextCompat
import androidx.core.os.LocaleListCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import com.jdinohaila.studentgarden.R
import com.jdinohaila.studentgarden.backup.*
import com.jdinohaila.studentgarden.data.GardenRepository
import com.jdinohaila.studentgarden.domain.*
import com.jdinohaila.studentgarden.platform.*
import com.jdinohaila.studentgarden.ui.*
import com.jdinohaila.studentgarden.ui.theme.*
import java.time.LocalDate
import kotlinx.coroutines.delay

@Composable
fun FocusScreen(
    s: GardenState,
    repo: GardenRepository,
    onBack: () -> Unit,
    onAction: (suspend () -> Unit) -> Unit,
) {
    val context = LocalContext.current
    val activity = LocalActivity.current
    val prefs = remember { context.getSharedPreferences("settings", 0) }
    var minutes by rememberSaveable { mutableStateOf("25") }
    var course by rememberSaveable { mutableStateOf("") }
    var sound by rememberSaveable { mutableStateOf(prefs.getBoolean("sound", true)) }
    var bellPlayer by remember { mutableStateOf<android.media.MediaPlayer?>(null) }
    DisposableEffect(Unit) { onDispose { bellPlayer?.release() } }

    var error by remember { mutableStateOf(false) }
    var tick by remember { mutableLongStateOf(SystemClock.elapsedRealtime()) }
    var precise by remember {
        mutableStateOf(
            Build.VERSION.SDK_INT < 31 ||
                context.getSystemService(AlarmManager::class.java).canScheduleExactAlarms()
        )
    }
    val timer = s.timer
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    LaunchedEffect(timer?.id, timer?.endsAt, timer?.finished, lifecycle) {
        lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
            while (timer != null && timer.endsAt != null && !timer.finished) {
                tick = SystemClock.elapsedRealtime()
                if (
                    StudyRules.remaining(
                        timer,
                        System.currentTimeMillis(),
                        tick,
                        FocusAlarm.boot(context),
                    ) <= 0
                ) {
                    if (repo.finishTimer(timer.id)) {
                        FocusAlarm.notify(context)
                    }
                    break
                }
                delay(250)
            }
        }
    }
    var permissionDialog by remember { mutableStateOf(false) }
    var permissionSettings by remember { mutableStateOf(false) }
    var notificationGranted by remember {
        mutableStateOf(
            Build.VERSION.SDK_INT < 33 ||
                ContextCompat.checkSelfPermission(
                    context,
                    Manifest.permission.POST_NOTIFICATIONS,
                ) == PackageManager.PERMISSION_GRANTED
        )
    }
    val permission =
        rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            notificationGranted = granted
            if (
                !granted &&
                    activity?.shouldShowRequestPermissionRationale(
                        Manifest.permission.POST_NOTIFICATIONS
                    ) != true
            ) {
                permissionSettings = true
                permissionDialog = true
            }
        }
    val exact =
        rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) {
            precise =
                Build.VERSION.SDK_INT < 31 ||
                    context.getSystemService(AlarmManager::class.java).canScheduleExactAlarms()
        }
    if (permissionDialog)
        AlertDialog(
            onDismissRequest = { permissionDialog = false },
            text = { Text(stringResource(R.string.notification_hint)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        permissionDialog = false
                        if (permissionSettings)
                            context.startActivity(
                                Intent(
                                    Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                                    Uri.parse("package:${context.packageName}"),
                                )
                            )
                        else if (Build.VERSION.SDK_INT >= 33)
                            permission.launch(Manifest.permission.POST_NOTIFICATIONS)
                    }
                ) {
                    Text(
                        stringResource(
                            if (permissionSettings) R.string.open_settings
                            else R.string.notifications
                        )
                    )
                }
            },
            dismissButton = {
                TextButton(onClick = { permissionDialog = false }) {
                    Text(stringResource(R.string.cancel))
                }
            },
        )

    val remaining =
        timer?.let {
            StudyRules.remaining(it, System.currentTimeMillis(), tick, FocusAlarm.boot(context))
        } ?: ((minutes.toLongOrNull() ?: 25) * 60_000)
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.focus)) },
                navigationIcon = { BackButton(onBack) },
            )
        }
    ) { p ->
        LazyColumn(contentPadding = pagePadding(p), modifier = Modifier.fillMaxSize()) {
            item {
                Column(
                    Modifier.fillMaxWidth().padding(vertical = 24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Box(Modifier.size(240.dp), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(
                            progress = {
                                if (timer == null) 1f
                                else
                                    (remaining.toFloat() / (timer.minutes * 60_000)).coerceIn(
                                        0f,
                                        1f,
                                    )
                            },
                            modifier = Modifier.fillMaxSize(),
                            strokeWidth = 8.dp,
                            color = accent("teal"),
                            trackColor = MaterialTheme.colorScheme.outlineVariant,
                        )
                        val seconds = (remaining + 999) / 1000
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text("%02d:%02d".format(seconds / 60, seconds % 60), style = MaterialTheme.typography.displaySmall, fontSize = 52.sp, fontWeight = FontWeight.SemiBold)
                            if (timer != null && timer.endsAt == null && !timer.finished) Text(stringResource(R.string.paused), color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                    if (timer?.finished == true)
                        Text(stringResource(R.string.focus_finished), Modifier.padding(top = 16.dp))
                }
                if (timer == null || timer.finished) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf(25, 50, 5).forEach { duration ->
                            FilterChip(minutes == duration.toString(), { minutes = duration.toString() }, { Text("$duration ${stringResource(R.string.minutes_unit)}") })
                        }
                    }
                    Field(minutes, { minutes = it.filter(Char::isDigit).take(3) }, R.string.minutes)
                    Spacer(Modifier.height(16.dp))
                    Choice(
                        R.string.course,
                        course,
                        listOf("" to stringResource(R.string.none)) +
                            s.courses.map { it.id to it.title },
                    ) {
                        course = it
                    }
                    Spacer(Modifier.height(16.dp))
                    AccentButton(
                        stringResource(R.string.start),
                        "teal",
                        {
                            val duration = minutes.toIntOrNull()
                            if (duration == null || duration !in 1..180) error = true
                            else
                                onAction {
                                    val now = System.currentTimeMillis()
                                    val t =
                                        FocusTimer(
                                            minutes = duration,
                                            courseId = course.ifBlank { null },
                                            endsAt = now + duration * 60_000,
                                            elapsedEnd =
                                                SystemClock.elapsedRealtime() + duration * 60_000,
                                            boot = FocusAlarm.boot(context),
                                        )
                                    repo.setTimer(t)
                                    FocusAlarm.schedule(context, t)
                                }
                        },
                        Modifier.fillMaxWidth(),
                    )
                } else {
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        AccentButton(
                            stringResource(
                                if (timer.endsAt == null) R.string.resume else R.string.pause
                            ),
                            "teal",
                            {
                                onAction {
                                    val left =
                                        StudyRules.remaining(
                                            timer,
                                            System.currentTimeMillis(),
                                            SystemClock.elapsedRealtime(),
                                            FocusAlarm.boot(context),
                                        )
                                    if (left == 0L) {
                                        if (repo.finishTimer(timer.id)) FocusAlarm.notify(context)
                                    } else {
                                        val t =
                                            if (timer.endsAt == null)
                                                timer.copy(
                                                    endsAt = System.currentTimeMillis() + left,
                                                    elapsedEnd =
                                                        SystemClock.elapsedRealtime() + left,
                                                    boot = FocusAlarm.boot(context),
                                                )
                                            else
                                                timer.copy(
                                                    endsAt = null,
                                                    elapsedEnd = null,
                                                    remaining = left,
                                                )
                                        FocusAlarm.cancel(context)
                                        repo.setTimer(t)
                                        FocusAlarm.schedule(context, t)
                                    }
                                }
                            },
                            Modifier.weight(1f),
                        )
                        OutlinedButton(
                            onClick = {
                                onAction {
                                    FocusAlarm.cancel(context)
                                    repo.setTimer(null)
                                }
                            },
                            modifier = Modifier.weight(1f),
                        ) {
                            Text(stringResource(R.string.reset))
                        }
                    }
                }
                if (error)
                    Text(
                        stringResource(R.string.invalid_minutes),
                        color = MaterialTheme.colorScheme.error,
                    )
                SettingSwitch(R.string.sound, sound) {
                    sound = it
                    prefs.edit().putBoolean("sound", it).apply()
                }
                TextButton(onClick = {
                    bellPlayer?.release()
                    bellPlayer = android.media.MediaPlayer.create(context, R.raw.focus_bell)?.apply {
                        setOnCompletionListener { player -> player.release(); if (bellPlayer == player) bellPlayer = null }
                        start()
                    }
                }) { Text(stringResource(R.string.test_sound)) }
                if (
                    !notificationGranted &&
                        Build.VERSION.SDK_INT >= 33 &&
                        ContextCompat.checkSelfPermission(
                            context,
                            Manifest.permission.POST_NOTIFICATIONS,
                        ) != PackageManager.PERMISSION_GRANTED
                )
                    OutlinedButton(
                        onClick = {
                            if (
                                ContextCompat.checkSelfPermission(
                                    context,
                                    Manifest.permission.POST_NOTIFICATIONS,
                                ) == PackageManager.PERMISSION_GRANTED
                            )
                                notificationGranted = true
                            else if (
                                activity?.shouldShowRequestPermissionRationale(
                                    Manifest.permission.POST_NOTIFICATIONS
                                ) == true
                            ) {
                                permissionSettings = false
                                permissionDialog = true
                            } else if (prefs.getBoolean("notificationAsked", false)) {
                                permissionSettings = true
                                permissionDialog = true
                            } else {
                                prefs.edit().putBoolean("notificationAsked", true).apply()
                                permission.launch(Manifest.permission.POST_NOTIFICATIONS)
                            }
                        }
                    ) {
                        Text(stringResource(R.string.notifications))
                    }
                if (!precise && Build.VERSION.SDK_INT >= 31) {
                    Text(
                        stringResource(R.string.alarm_hint),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    TextButton(
                        onClick = {
                            exact.launch(
                                Intent(
                                    Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM,
                                    Uri.parse("package:${context.packageName}"),
                                )
                            )
                        }
                    ) {
                        Text(stringResource(R.string.exact_alarm))
                    }
                }
                SectionTitle(stringResource(R.string.history))
            }
            items(
                s.blocks.filter { it.source == "focus" }.take(10),
                key = { it.id },
                contentType = { "session" },
            ) { b ->
                EntryRow(
                    s.courses.find { it.id == b.courseId }?.title ?: stringResource(R.string.focus),
                    "${dateLabel(b.startsAt)} • ${b.minutes} ${stringResource(R.string.minutes_unit)}",
                    "teal",
                    {},
                )
            }
        }
    }
}

@Composable
fun SettingSwitch(label: Int, checked: Boolean, onChange: (Boolean) -> Unit) {
    val contextLabel = stringResource(label)
    Row(
        Modifier.fillMaxWidth().heightIn(min = 64.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(stringResource(label), Modifier.weight(1f))
        Switch(checked, onChange, modifier = Modifier.semantics { contentDescription = contextLabel })
    }
}

@Composable
private fun ReminderSwitch(label: Int, key: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    val title = stringResource(label)
    val image = remember(key) { ReminderPresentation.artwork(key).asImageBitmap() }
    Row(Modifier.fillMaxWidth().heightIn(min = 64.dp), verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Image(image, null, Modifier.size(36.dp))
        Text(title, Modifier.weight(1f))
        Switch(checked, onChange, Modifier.semantics { contentDescription = title },
            colors = SwitchDefaults.colors(checkedTrackColor = androidx.compose.ui.graphics.Color(ReminderPresentation.color(key))))
    }
}

@Composable
fun LessonReminderSettings(repo: GardenRepository) {
    val context = LocalContext.current
    val activity = LocalActivity.current
    val prefs = remember { context.getSharedPreferences("settings", 0) }
    var lessonEnabled by remember { mutableStateOf(prefs.getBoolean("lessonReminders", false)) }
    val categoryEnabled = remember { mutableStateMapOf<String, Boolean>().apply { StudyReminders.categories.forEach { put(it.key, StudyReminders.enabled(context, it.key)) } } }
    var minute by remember { mutableIntStateOf(prefs.getInt("dailyReminderMinute", 540)) }
    var revision by remember { mutableIntStateOf(0) }
    var rationale by remember { mutableStateOf(false) }
    fun refresh() {
        revision++
        LessonReminder.schedule(context, repo.state.value.lessons)
        StudyReminders.schedule(context, repo.state.value)
    }
    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { refresh() }
    val owner = LocalLifecycleOwner.current
    DisposableEffect(owner) {
        val observer = androidx.lifecycle.LifecycleEventObserver { _, event -> if (event == Lifecycle.Event.ON_RESUME) refresh() }
        owner.lifecycle.addObserver(observer)
        onDispose { owner.lifecycle.removeObserver(observer) }
    }
    // Re-evaluate OS and channel permissions on every return from system settings.
    val allowed = remember(revision) { StudyReminders.allowed(context) }
    val blocked = remember(revision, lessonEnabled, categoryEnabled.toMap()) {
        (lessonEnabled && !LessonReminder.allowed(context)) || StudyReminders.categories.any { categoryEnabled[it.key] == true && !StudyReminders.allowed(context, it.key) }
    }
    SectionTitle(stringResource(R.string.reminders))
    if (!allowed || blocked) Text(stringResource(R.string.reminders_blocked), style = MaterialTheme.typography.bodySmall)
    Text(stringResource(R.string.reminder_scheduled), style = MaterialTheme.typography.labelLarge)
    StudyReminders.categories.filter { it.key in setOf("tasks", "agenda") }.forEach { category ->
        ReminderSwitch(category.title, category.key, categoryEnabled[category.key] == true) {
            categoryEnabled[category.key] = it; prefs.edit().putBoolean("${category.key}Reminders", it).apply(); refresh()
        }
    }
    Spacer(Modifier.height(12.dp))
    Text(stringResource(R.string.reminder_study_time), style = MaterialTheme.typography.labelLarge)
    ReminderSwitch(R.string.lesson_reminders, "lessons", lessonEnabled) {
        lessonEnabled = it; prefs.edit().putBoolean("lessonReminders", it).apply(); refresh()
    }
    StudyReminders.categories.filter { it.key in setOf("cards", "habits") }.forEach { category ->
        ReminderSwitch(category.title, category.key, categoryEnabled[category.key] == true) {
            categoryEnabled[category.key] = it; prefs.edit().putBoolean("${category.key}Reminders", it).apply(); refresh()
        }
    }
    OutlinedButton(onClick = {
        TimePickerDialog(context, { _, h, m ->
            minute = h * 60 + m; prefs.edit().putInt("dailyReminderMinute", minute).apply(); refresh()
        }, minute / 60, minute % 60, android.text.format.DateFormat.is24HourFormat(context)).show()
    }) { Text(stringResource(R.string.daily_reminder_time, java.time.LocalTime.of(minute / 60, minute % 60).format(java.time.format.DateTimeFormatter.ofLocalizedTime(java.time.format.FormatStyle.SHORT)))) }
    OutlinedButton(onClick = {
        if (Build.VERSION.SDK_INT >= 33 && ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED && activity?.shouldShowRequestPermissionRationale(Manifest.permission.POST_NOTIFICATIONS) == true) rationale = true
        else if (Build.VERSION.SDK_INT >= 33 && !prefs.getBoolean("lessonNotificationAsked", false) && ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            prefs.edit().putBoolean("lessonNotificationAsked", true).apply(); permission.launch(Manifest.permission.POST_NOTIFICATIONS)
        } else if (Build.VERSION.SDK_INT >= 33 && ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED)
            context.startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:${context.packageName}")))
        else context.startActivity(Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName))
    }) { Text(stringResource(if (allowed) R.string.notification_settings else R.string.allow_notifications)) }
    if (Build.VERSION.SDK_INT >= 31 && !context.getSystemService(AlarmManager::class.java).canScheduleExactAlarms()) {
        Text(stringResource(R.string.precise_reminder_hint), style = MaterialTheme.typography.bodySmall)
        OutlinedButton(onClick = { context.startActivity(Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM, Uri.parse("package:${context.packageName}"))) }) { Text(stringResource(R.string.allow_precise_reminders)) }
    }
    if (allowed) OutlinedButton(onClick = { StudyReminders.test(context) }) { Text(stringResource(R.string.test_notification)) }
    if (rationale) AlertDialog(onDismissRequest = { rationale = false }, text = { Text(stringResource(R.string.study_reminder_hint)) },
        confirmButton = { TextButton(onClick = { rationale = false; prefs.edit().putBoolean("lessonNotificationAsked", true).apply(); if (Build.VERSION.SDK_INT >= 33) permission.launch(Manifest.permission.POST_NOTIFICATIONS) }) { Text(stringResource(R.string.allow_notifications)) } },
        dismissButton = { TextButton(onClick = { rationale = false }) { Text(stringResource(R.string.cancel)) } })
}

@Composable
fun SettingsScreen(
    repo: GardenRepository,
    media: PrivateMedia,
    onBack: () -> Unit,
    onTheme: (String) -> Unit,
    onTrash: () -> Unit,
    onAction: (suspend () -> Unit) -> Unit,
) {
    val context = LocalContext.current
    val activity = LocalActivity.current
    val prefs = remember { context.getSharedPreferences("settings", 0) }
    val configuration = LocalConfiguration.current
    val backup = remember { GardenBackup(context, repo, media) }
    var appearance by remember { mutableStateOf(prefs.getString("theme", "system")!!) }
    var locked by remember { mutableStateOf(prefs.getBoolean("lock", false)) }
    var lockError by remember { mutableStateOf(false) }
    var preview by remember { mutableStateOf<RestorePreview?>(null) }
    var result by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    val exported = stringResource(R.string.backup_saved)
    val imported = stringResource(R.string.import_done)
    val export =
        rememberLauncherForActivityResult(
            ActivityResultContracts.CreateDocument("application/zip")
        ) { uri ->
            if (uri != null)
                onAction {
                    busy = true
                    try {
                        backup.export(uri)
                        result = exported
                    } finally {
                        busy = false
                    }
                }
        }
    val import =
        rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
            if (uri != null)
                onAction {
                    busy = true
                    try {
                        preview = backup.preview(uri)
                    } finally {
                        busy = false
                    }
                }
        }
    DisposableEffect(Unit) { onDispose { preview?.staged?.deleteRecursively() } }
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.settings)) },
                navigationIcon = { BackButton(onBack) },
            )
        }
    ) { p ->
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(pagePadding(p))) {
                Choice(
                    R.string.language,
                    AppCompatDelegate.getApplicationLocales().toLanguageTags().ifBlank {
                        configuration.locales[0].language
                    },
                    listOf(
                        "en" to "English",
                        "ar" to "العربية",
                        "fr" to "Français",
                        "es" to "Español",
                    ),
                ) {
                    AppCompatDelegate.setApplicationLocales(LocaleListCompat.forLanguageTags(it))
                }
                Spacer(Modifier.height(24.dp))
                Choice(
                    R.string.theme,
                    appearance,
                    listOf(
                        "system" to stringResource(R.string.system),
                        "light" to stringResource(R.string.light),
                        "dark" to stringResource(R.string.dark),
                    ),
                ) {
                    appearance = it
                    prefs.edit().putString("theme", it).apply()
                    onTheme(it)
                }
                LessonReminderSettings(repo)
                SectionTitle(stringResource(R.string.backup))
                AccentButton(
                    stringResource(R.string.export_backup),
                    "blue",
                    { export.launch("student-garden-${LocalDate.now()}.zip") },
                    Modifier.fillMaxWidth(),
                    !busy,
                )
                OutlinedButton(
                    onClick = {
                        import.launch(arrayOf("application/zip", "application/octet-stream"))
                    },
                    modifier = Modifier.fillMaxWidth(),
                    enabled = !busy,
                ) {
                    Text(stringResource(R.string.import_backup))
                }
                if (busy) {
                    LinearProgressIndicator(Modifier.fillMaxWidth())
                }
                if (result.isNotBlank()) Text(result)
                EntryRow(stringResource(R.string.trash), onClick = onTrash)
                Spacer(Modifier.height(16.dp))
                SettingSwitch(R.string.app_lock, locked) { value ->
                    if (
                        value &&
                            !context.getSystemService(KeyguardManager::class.java).isDeviceSecure
                    )
                        lockError = true
                    else {
                        locked = value
                        prefs.edit().putBoolean("lock", value).apply()
                        if (value)
                            activity
                                ?.window
                                ?.addFlags(android.view.WindowManager.LayoutParams.FLAG_SECURE)
                        else
                            activity
                                ?.window
                                ?.clearFlags(android.view.WindowManager.LayoutParams.FLAG_SECURE)
                    }
                }
                Text(
                    stringResource(R.string.lock_hint),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                if (lockError)
                    Text(
                        stringResource(R.string.lock_required),
                        color = MaterialTheme.colorScheme.error,
                    )
                Spacer(Modifier.height(24.dp))
                Text(
                    "Student Garden 0.11.0 · GPL-3.0-or-later",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
        }
    }
    if (preview != null)
        AlertDialog(
            onDismissRequest = {
                preview?.staged?.deleteRecursively()
                preview = null
            },
            title = { Text(stringResource(R.string.import_confirm)) },
            text = {
                Text(
                    "${preview!!.manifest.records.size} ${stringResource(R.string.all)}\n${stringResource(R.string.backup_hint)}"
                )
            },
            confirmButton = {
                TextButton(
                    enabled = !busy,
                    onClick = {
                        val staged = preview!!
                        onAction {
                            busy = true
                            try {
                                backup.restore(staged)
                                preview = null
                                result = imported
                            } finally {
                                busy = false
                            }
                        }
                    },
                ) {
                    Text(stringResource(R.string.restore))
                }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        preview?.staged?.deleteRecursively()
                        preview = null
                    }
                ) {
                    Text(stringResource(R.string.cancel))
                }
            },
        )
}
