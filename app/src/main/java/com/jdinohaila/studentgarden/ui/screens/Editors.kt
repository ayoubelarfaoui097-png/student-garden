@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.jdinohaila.studentgarden.ui.screens

import android.Manifest
import android.app.DatePickerDialog
import android.app.TimePickerDialog
import android.content.Intent
import android.content.pm.PackageManager
import android.media.MediaRecorder
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.BackHandler
import androidx.activity.compose.LocalActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.*
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.ui.text.*
import androidx.compose.ui.text.font.*
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.jdinohaila.studentgarden.R
import com.jdinohaila.studentgarden.data.GardenRepository
import com.jdinohaila.studentgarden.domain.*
import com.jdinohaila.studentgarden.platform.PrivateMedia
import com.jdinohaila.studentgarden.ui.*
import com.jdinohaila.studentgarden.ui.theme.*
import com.mohamedrejeb.richeditor.model.RichTextState
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.testTag
import java.time.*
import java.util.Locale
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay
import coil3.compose.AsyncImage
import androidx.compose.ui.layout.ContentScale
import com.jdinohaila.studentgarden.GardenApp

@Composable
fun Field(value: String, onChange: (String) -> Unit, label: Int, single: Boolean = true) {
    OutlinedTextField(
        value,
        onChange,
        Modifier.fillMaxWidth(),
        label = { Text(stringResource(label)) },
        keyboardOptions =
            KeyboardOptions(
                keyboardType =
                    if (label == R.string.minutes) KeyboardType.Number else KeyboardType.Text
            ),
        singleLine = single,
        minLines = if (single) 1 else 3,
        maxLines = if (single) 1 else 8,
    )
}

@Composable
fun Choice(
    label: Int,
    value: String,
    options: List<Pair<String, String>>,
    onChange: (String) -> Unit,
) {
    var open by remember { mutableStateOf(false) }
    Column {
        Text(stringResource(label), style = MaterialTheme.typography.labelLarge)
        OutlinedButton(onClick = { open = true }, modifier = Modifier.fillMaxWidth()) {
            Text(options.find { it.first == value }?.second ?: stringResource(R.string.none))
            Spacer(Modifier.weight(1f))
            Icon(Icons.Outlined.ExpandMore, null)
        }
        DropdownMenu(open, { open = false }) {
            options.forEach { (key, title) ->
                DropdownMenuItem(
                    text = { Text(title) },
                    onClick = {
                        onChange(key)
                        open = false
                    },
                )
            }
        }
    }
}

@Composable
fun ColorChoice(value: String, onChange: (String) -> Unit) {
    Text(stringResource(R.string.color), style = MaterialTheme.typography.labelLarge)
    FlowRow(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        featureColors.forEach { color ->
            IconButton(
                onClick = { onChange(color) },
                modifier = Modifier.size(48.dp).background(accent(color), CircleShape),
            ) {
                if (value == color)
                    Icon(
                        Icons.Outlined.Check,
                        stringResource(colorLabel(color)),
                        tint = Color(0xFF202A3B),
                    )
                else
                    Icon(
                        Icons.Outlined.Circle,
                        stringResource(colorLabel(color)),
                        tint = Color.Transparent,
                    )
            }
        }
    }
}

@Composable
fun AgendaColorChoice(value: String, onChange: (String) -> Unit) {
    val custom = value !in featureColors
    val invalid = custom && normalizedHexColor(value) == null
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        ColorChoice(value, onChange)
        OutlinedTextField(
            value = if (custom) value else "",
            onValueChange = onChange,
            modifier = Modifier.fillMaxWidth(),
            label = { Text(stringResource(R.string.custom_color)) },
            placeholder = { Text("#0052CC") },
            singleLine = true,
            textStyle = LocalTextStyle.current.copy(textDirection = TextDirection.Ltr),
            isError = invalid,
            supportingText = {
                Text(stringResource(if (invalid) R.string.invalid_color else R.string.custom_color_hint))
            },
            leadingIcon = {
                val preview = if (invalid) accent("blue") else accent(value)
                Box(Modifier.size(24.dp).background(preview, CircleShape)
                    .border(1.dp, MaterialTheme.colorScheme.outline, CircleShape))
            },
        )
    }
}

@Composable
fun RecordEditor(
    kind: String,
    id: String,
    s: GardenState,
    repo: GardenRepository,
    media: PrivateMedia,
    onBack: () -> Unit,
    onSave: suspend (GardenRecord) -> Unit,
    onDelete: (String) -> Unit,
) {
    val existing: GardenRecord? =
        when (kind) {
            "task" -> s.tasks.find { it.id == id }
            "deck" -> s.decks.find { it.id == id }
            "card" -> s.cards.find { it.id == id }
            "lesson" -> s.lessons.find { it.id == id }
            "habit" -> s.habits.find { it.id == id }
            "course" -> s.courses.find { it.id == id }
            "block" -> s.blocks.find { it.id == id }
            else -> null
        }
    val initialTitle =
        when (existing) {
            is Task -> existing.title
            is Deck -> existing.title
            is Flashcard -> existing.answer
            is Lesson -> existing.title
            is Habit -> existing.title
            is Course -> existing.title
            is StudyBlock -> existing.title
            else -> ""
        }
    val newRecordId = rememberSaveable { newId() }
    var title by rememberSaveable { mutableStateOf(initialTitle) }
    var detail by rememberSaveable {
        mutableStateOf(
            when (existing) {
                is Task -> existing.detail
                is Flashcard -> existing.prompt
                is Lesson -> existing.detail
                is Habit -> existing.cue
                is Course -> existing.code
                else -> ""
            }
        )
    }
    var subject by rememberSaveable {
        mutableStateOf(
            when (existing) {
                is Flashcard -> existing.subject
                is Lesson -> existing.subject
                is Course -> existing.term
                else -> ""
            }
        )
    }
    var course by rememberSaveable {
        mutableStateOf(
            when (existing) {
                is Task -> existing.courseId
                is StudyBlock -> existing.courseId
                else -> null
            } ?: ""
        )
    }
    var deck by rememberSaveable {
        mutableStateOf(
            (existing as? Flashcard)?.deckId
                ?: if (kind == "card" && s.decks.any { it.id == id }) id else ""
        )
    }
    var color by rememberSaveable {
        mutableStateOf(
            when (existing) {
                is Habit -> existing.color
                is Course -> existing.color
                is Deck -> existing.color
                is StudyBlock -> existing.color
                is Lesson -> existing.color
                else -> when (kind) {
                    "habit" -> "green"
                    "block" -> "blue"
                    "lesson" -> "cyan"
                    else -> "orange"
                }
            }
        )
    }
    var priority by rememberSaveable { mutableStateOf((existing as? Task)?.priority ?: "normal") }
    var taskStatus by rememberSaveable { mutableStateOf((existing as? Task)?.status ?: "todo") }
    var linkedTask by rememberSaveable { mutableStateOf((existing as? StudyBlock)?.taskId.orEmpty()) }
    var minutes by rememberSaveable {
        mutableStateOf(
            when (existing) {
                is Task -> existing.minutes
                is StudyBlock -> existing.minutes
                else -> 30
            }.toString()
        )
    }
    var date by rememberSaveable {
        mutableStateOf(
            when (existing) {
                is Task -> existing.dueAt
                is StudyBlock -> existing.startsAt
                else -> if (kind == "block") runCatching {
                    LocalDate.parse(id).atTime(LocalTime.now().withSecond(0).withNano(0))
                        .atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
                }.getOrElse { System.currentTimeMillis() } else null
            }
        )
    }
    var type by rememberSaveable { mutableStateOf((existing as? Flashcard)?.type ?: "text") }
    var image by rememberSaveable { mutableStateOf((existing as? Flashcard)?.imageId) }
    var audio by rememberSaveable { mutableStateOf((existing as? Flashcard)?.audioId) }
    var recording by remember { mutableStateOf(false) }
    var recordingStartedAt by remember { mutableLongStateOf(0L) }
    var recordingPeaks by remember { mutableStateOf<List<Float>>(emptyList()) }
    var stopRecording by remember { mutableStateOf<(() -> Unit)?>(null) }
    var days by rememberSaveable { mutableIntStateOf((existing as? Habit)?.days ?: 127) }
    var error by remember { mutableStateOf("") }
    var saving by remember { mutableStateOf(false) }
    var discard by remember { mutableStateOf(false) }
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val initialSignature = rememberSaveable {
        listOf(
                title,
                detail,
                subject,
                course,
                deck,
                color,
                priority,
                minutes,
                date,
                type,
                image,
                audio,
                days,
                taskStatus,
                linkedTask,
            )
            .toString()
    }
    val dirty =
        initialSignature !=
            listOf(
                    title,
                    detail,
                    subject,
                    course,
                    deck,
                    color,
                    priority,
                    minutes,
                    date,
                    type,
                    image,
                    audio,
                    days,
                    taskStatus,
                    linkedTask,
                )
                .toString()
    fun leave() {
        if (recording) {
            stopRecording?.invoke()
            discard = true
        } else if (dirty) discard = true else onBack()
    }
    BackHandler { leave() }
    val chooseImage =
        rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
            if (uri != null)
                scope.launch {
                    try {
                        image = media.import(uri, "image")
                        type = "image"
                    } catch (e: Exception) {
                        error = e.message.orEmpty()
                    }
                }
        }
    val chooseAudio =
        rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
            if (uri != null)
                scope.launch {
                    try {
                        audio = media.import(uri, "audio")
                    } catch (e: Exception) {
                        error = e.message.orEmpty()
                    }
                }
        }
    val colorName =
        when (kind) {
            "task" -> "coral"
            "card",
            "deck" -> "orange"
            "lesson" -> "cyan"
            "habit" -> "green"
            else -> "blue"
        }
    val label =
        when (kind) {
            "task" -> R.string.new_task
            "card" -> R.string.new_card
            "deck" -> R.string.new_deck
            "lesson" -> R.string.new_lesson
            "habit" -> R.string.new_habit
            "course" -> R.string.new_course
            else -> R.string.new_block
        }
    val required = stringResource(R.string.required)
    val attachMedia = stringResource(R.string.attach_media)
    val addPrompt = stringResource(R.string.add_prompt)
    val invalidColor = stringResource(R.string.invalid_color)
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(if (existing == null) label else R.string.edit)) },
                navigationIcon = { BackButton { leave() } },
                actions = {
                    if (existing != null)
                        IconButton(onClick = { onDelete(existing.id) }) {
                            Icon(Icons.Outlined.DeleteOutline, stringResource(R.string.delete))
                        }
                },
            )
        },
        bottomBar = {
            Surface {
                Row(Modifier.fillMaxWidth().navigationBarsPadding().imePadding().padding(16.dp)) {
                    if (recording) {
                        LiveRecordingStatus(recordingStartedAt, recordingPeaks) { stopRecording?.invoke() }
                    } else {
                        AccentButton(
                            stringResource(R.string.save),
                            colorName,
                            {
                                if (title.isBlank()) {
                                    error = required
                                    return@AccentButton
                                }
                                if (
                                    kind == "card" &&
                                        ((type == "image" && image == null) ||
                                            (type == "audio" && audio == null))
                                ) {
                                    error = attachMedia
                                    return@AccentButton
                                }
                                if (kind == "card" && type == "text" && detail.isBlank()) {
                                    error = addPrompt
                                    return@AccentButton
                                }
                                if (kind in listOf("block", "lesson") && color !in featureColors && normalizedHexColor(color) == null) {
                                    error = invalidColor
                                    return@AccentButton
                                }
                                try {
                                    val duration = minutes.toIntOrNull() ?: 0
                                    require(duration in 1..1440)
                                    val key = existing?.id ?: newRecordId
                                    val created = existing?.createdAt ?: System.currentTimeMillis()
                                    val record: GardenRecord =
                                        when (kind) {
                                            "task" ->
                                                (existing as? Task
                                                        ?: Task(
                                                            id = key,
                                                            title = title,
                                                            createdAt = created,
                                                        ))
                                                    .copy(
                                                        title = title.trim(),
                                                        detail = detail,
                                                        courseId = course.ifBlank { null },
                                                        dueAt = date,
                                                        priority = priority,
                                                        status = taskStatus,
                                                        minutes = duration,
                                                    )
                                            "block" ->
                                                (existing as? StudyBlock
                                                        ?: StudyBlock(
                                                            id = key,
                                                            title = title,
                                                            startsAt =
                                                                date ?: System.currentTimeMillis(),
                                                            createdAt = created,
                                                        ))
                                                    .copy(
                                                        title = title.trim(),
                                                        courseId = course.ifBlank { null },
                                                        taskId = linkedTask.ifBlank { null },
                                                        startsAt = date ?: System.currentTimeMillis(),
                                                        minutes = duration,
                                                        color = normalizedHexColor(color) ?: color,
                                                    )
                                            "deck" ->
                                                (existing as? Deck
                                                        ?: Deck(
                                                            id = key,
                                                            title = title,
                                                            createdAt = created,
                                                        ))
                                                    .copy(title = title.trim(), color = color)
                                            "card" ->
                                                (existing as? Flashcard
                                                        ?: Flashcard(
                                                            id = key,
                                                            answer = title,
                                                            createdAt = created,
                                                        ))
                                                    .copy(
                                                        answer = title.trim(),
                                                        prompt = detail,
                                                        subject = subject,
                                                        deckId = deck.ifBlank { null },
                                                        type = type,
                                                        imageId = image,
                                                        audioId = audio,
                                                    )
                                            "lesson" ->
                                                (existing as? Lesson
                                                        ?: Lesson(
                                                            id = key,
                                                            title = title,
                                                            createdAt = created,
                                                        ))
                                                    .copy(
                                                        title = title.trim(),
                                                        subject = subject,
                                                        detail = detail,
                                                        color = normalizedHexColor(color) ?: color,
                                                    )
                                            "habit" ->
                                                (existing as? Habit
                                                        ?: Habit(
                                                            id = key,
                                                            title = title,
                                                            createdAt = created,
                                                        ))
                                                    .copy(
                                                        title = title.trim(),
                                                        cue = detail,
                                                        color = color,
                                                        days = days,
                                                    )
                                            else ->
                                                (existing as? Course
                                                        ?: Course(
                                                            id = key,
                                                            title = title,
                                                            createdAt = created,
                                                        ))
                                                    .copy(
                                                        title = title.trim(),
                                                        code = detail,
                                                        term = subject,
                                                        color = color,
                                                    )
                                        }
                                    repo.validate(record)
                                    saving = true
                                    scope.launch {
                                        try {
                                            onSave(record)
                                        } catch (e: Exception) {
                                            error = e.message ?: required
                                        } finally {
                                            saving = false
                                        }
                                    }
                                } catch (e: Exception) {
                                    error = e.message ?: required
                                }
                            },
                            Modifier.fillMaxWidth(),
                            !saving && !recording,
                        )
                    }
                }
            }
        },
    ) { p ->
        Column(
            Modifier.fillMaxSize()
                .padding(p)
                .consumeWindowInsets(p)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Field(title, { title = it }, if (kind == "card") R.string.answer else R.string.title)
            if (kind in listOf("task", "lesson", "habit", "card", "course"))
                Field(
                    detail,
                    { detail = it },
                    when (kind) {
                        "card" -> R.string.prompt
                        "habit" -> R.string.cue
                        "course" -> R.string.code
                        else -> R.string.details
                    },
                    kind in listOf("habit", "course"),
                )
            if (kind in listOf("card", "lesson", "course"))
                Field(
                    subject,
                    { subject = it },
                    if (kind == "course") R.string.term else R.string.subject,
                )
            if (kind in listOf("task", "block")) {
                Choice(
                    R.string.course,
                    course,
                    listOf("" to stringResource(R.string.none)) +
                        s.courses.map { it.id to it.title },
                ) {
                    course = it
                }
                Field(minutes, { minutes = it.filter(Char::isDigit) }, R.string.minutes)
                if (kind == "block") {
                    Row(Modifier.fillMaxWidth().testTag("agenda-selected-day"), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Icon(Icons.Outlined.CalendarMonth, null)
                        Column {
                            Text(dateLabel(date!!), style = MaterialTheme.typography.titleMedium)
                            Text(stringResource(R.string.agenda_day_hint), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                } else OutlinedButton(
                    onClick = {
                        val d =
                            Instant.ofEpochMilli(date ?: System.currentTimeMillis())
                                .atZone(ZoneId.systemDefault())
                        DatePickerDialog(
                                context,
                                { _, y, m, day ->
                                    date =
                                        LocalDate.of(y, m + 1, day)
                                            .atTime(d.toLocalTime())
                                            .atZone(ZoneId.systemDefault())
                                            .toInstant()
                                            .toEpochMilli()
                                },
                                d.year,
                                d.monthValue - 1,
                                d.dayOfMonth,
                            )
                            .show()
                    }
                ) {
                    Icon(Icons.Outlined.CalendarMonth, null)
                    Spacer(Modifier.width(8.dp))
                    Text(date?.let(::dateLabel) ?: stringResource(R.string.choose_date))
                }
                if (date != null)
                    Row {
                        OutlinedButton(
                            onClick = {
                                val d = Instant.ofEpochMilli(date!!).atZone(ZoneId.systemDefault())
                                TimePickerDialog(
                                        context,
                                        { _, h, m ->
                                            date =
                                                d.toLocalDate()
                                                    .atTime(h, m)
                                                    .atZone(ZoneId.systemDefault())
                                                    .toInstant()
                                                    .toEpochMilli()
                                        },
                                        d.hour,
                                        d.minute,
                                        android.text.format.DateFormat.is24HourFormat(context),
                                    )
                                    .show()
                            },
                            modifier = Modifier.testTag("record-time"),
                        ) {
                            Icon(Icons.Outlined.Schedule, null)
                            Spacer(Modifier.width(8.dp))
                            Text(timeLabel(date!!))
                        }
                        if (kind == "task")
                            TextButton(onClick = { date = null }) {
                                Text(stringResource(R.string.none))
                            }
                    }
                if (kind == "task") {
                    Choice(R.string.status, taskStatus, listOf(
                        "todo" to stringResource(R.string.to_do),
                        "progress" to stringResource(R.string.task_in_progress),
                        "done" to stringResource(R.string.done),
                    )) { taskStatus = it }
                    Choice(
                        R.string.priority,
                        priority,
                        listOf(
                            "normal" to stringResource(R.string.normal),
                            "high" to stringResource(R.string.high),
                        ),
                    ) {
                        priority = it
                    }
                }
                if (kind == "block") Choice(R.string.linked_task, linkedTask,
                    listOf("" to stringResource(R.string.none)) + s.tasks.map { it.id to it.title }
                ) { linkedTask = it }
            }
            if (kind == "card") {
                Choice(
                    R.string.decks,
                    deck,
                    listOf("" to stringResource(R.string.none)) + s.decks.map { it.id to it.title },
                ) {
                    deck = it
                }
                Choice(
                    R.string.card_type,
                    type,
                    listOf(
                        "text" to stringResource(R.string.text),
                        "image" to stringResource(R.string.image),
                        "audio" to stringResource(R.string.audio),
                    ),
                ) {
                    type = it
                }
                if (type == "image") {
                    OutlinedButton(
                        onClick = {
                            chooseImage.launch(
                                arrayOf("image/jpeg", "image/png", "image/webp", "image/gif")
                            )
                        }
                    ) {
                        Text(stringResource(R.string.choose_image))
                    }
                    if (image != null) Text(stringResource(R.string.media_ready))
                }
                // Audio can accompany a picture or written question as well.
                if (kind == "card") {
                    OutlinedButton(onClick = { chooseAudio.launch(arrayOf("audio/*")) }) {
                        Text(stringResource(R.string.choose_audio))
                    }
                    AudioRecorder(
                        media,
                        onRecorded = { audio = it },
                        onRecording = {
                            recording = it
                            if (it) { recordingStartedAt = android.os.SystemClock.elapsedRealtime(); recordingPeaks = emptyList() }
                        },
                        onStopAvailable = { stopRecording = it },
                        onAmplitude = { recordingPeaks = (recordingPeaks + it).takeLast(48) },
                        onError = {
                            error = it
                        },
                    )
                    if (audio != null && !recording) AudioPlayer(media.file(audio!!))
                }
            }
            if (kind in listOf("habit", "deck", "course")) ColorChoice(color) { color = it }
            if (kind in listOf("block", "lesson")) AgendaColorChoice(color) {
                color = it
                if (error == invalidColor) error = ""
            }
            if (kind == "habit") {
                Text(stringResource(R.string.schedule))
                FlowRow {
                    (0..6).forEach { n ->
                        FilterChip(
                            selected = days and (1 shl n) != 0,
                            onClick = {
                                val next = days xor (1 shl n)
                                if (next != 0) days = next
                            },
                            label = {
                                Text(
                                    DayOfWeek.of(n + 1)
                                        .getDisplayName(
                                            java.time.format.TextStyle.SHORT,
                                            Locale.getDefault(),
                                        )
                                )
                            },
                            modifier = Modifier.padding(end = 6.dp),
                        )
                    }
                }
            }
            if (kind == "lesson" && existing is Lesson && existing.stage < 3) {
                Text(existing.dueAt?.let(::dateLabel).orEmpty())
                AccentButton(
                    stringResource(R.string.reviewed),
                    "cyan",
                    {
                        scope.launch {
                            try {
                                repo.review(existing)
                                onBack()
                            } catch (e: Exception) {
                                error = e.message.orEmpty()
                            }
                        }
                    },
                )
            }
            if (error.isNotBlank()) Text(error, color = MaterialTheme.colorScheme.error)
        }
    }
    if (discard)
        AlertDialog(
            onDismissRequest = { discard = false },
            title = { Text(stringResource(R.string.discard)) },
            confirmButton = {
                TextButton(onClick = onBack) { Text(stringResource(R.string.close)) }
            },
            dismissButton = {
                TextButton(onClick = { discard = false }) {
                    Text(stringResource(R.string.keep_editing))
                }
            },
        )
}

@Composable
fun AudioRecorder(
    media: PrivateMedia,
    onRecorded: (String) -> Unit,
    onRecording: (Boolean) -> Unit,
    onError: (String) -> Unit,
    onStopAvailable: ((() -> Unit)?) -> Unit = {},
    onAmplitude: (Float) -> Unit = {},
) {
    val context = LocalContext.current
    val activity = LocalActivity.current
    val recordError = stringResource(R.string.error)
    var recorder by remember { mutableStateOf<MediaRecorder?>(null) }
    var id by remember { mutableStateOf<String?>(null) }
    var rationale by remember { mutableStateOf(false) }
    var settings by remember { mutableStateOf(false) }
    val recordedCallback by rememberUpdatedState(onRecorded)
    val recordingCallback by rememberUpdatedState(onRecording)
    val stopCallback by rememberUpdatedState(onStopAvailable)
    val amplitudeCallback by rememberUpdatedState(onAmplitude)
    val errorCallback by rememberUpdatedState(onError)
    LaunchedEffect(recorder) {
        val active = recorder ?: return@LaunchedEffect
        while (true) {
            amplitudeCallback(recordingLevel(runCatching { active.maxAmplitude }.getOrDefault(0)))
            delay(80)
        }
    }
    fun stop() {
        val r = recorder ?: return
        recorder = null
        stopCallback(null)
        recordingCallback(false)
        try {
            r.stop()
            id?.let(recordedCallback)
        } catch (e: Exception) {
            id?.let { media.file(it).delete() }
            errorCallback(recordError)
        } finally {
            r.release()
        }
    }
    fun start() {
        if (
            ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) !=
                PackageManager.PERMISSION_GRANTED
        )
            return
        val key = "${newId()}.m4a"
        @Suppress("DEPRECATION")
        val r = if (Build.VERSION.SDK_INT >= 31) MediaRecorder(context) else MediaRecorder()
        try {
            r.setAudioSource(MediaRecorder.AudioSource.MIC)
            r.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
            r.setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
            r.setAudioEncodingBitRate(96000)
            r.setAudioSamplingRate(44100)
            r.setOutputFile(media.file(key).absolutePath)
            r.setMaxDuration(180000)
            r.setMaxFileSize(10_000_000)
            r.setOnInfoListener { _, what, _ ->
                if (
                    what == MediaRecorder.MEDIA_RECORDER_INFO_MAX_DURATION_REACHED ||
                        what == MediaRecorder.MEDIA_RECORDER_INFO_MAX_FILESIZE_REACHED
                )
                    stop()
            }
            r.prepare()
            r.start()
            id = key
            recorder = r
            stopCallback(::stop)
            recordingCallback(true)
        } catch (e: Exception) {
            r.release()
            media.file(key).delete()
            errorCallback(recordError)
        }
    }
    val permission =
        rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            if (granted) start()
            else if (
                activity != null &&
                    !ActivityCompat.shouldShowRequestPermissionRationale(
                        activity,
                        Manifest.permission.RECORD_AUDIO,
                    )
            )
                settings = true
        }
    val owner = LocalLifecycleOwner.current
    DisposableEffect(owner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_STOP) stop()
        }
        owner.lifecycle.addObserver(observer)
        onDispose {
            owner.lifecycle.removeObserver(observer)
            stop()
        }
    }
    OutlinedButton(
        onClick = {
            if (recorder != null) stop()
            else if (
                ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) ==
                    PackageManager.PERMISSION_GRANTED
            )
                start()
            else if (
                activity != null &&
                    ActivityCompat.shouldShowRequestPermissionRationale(
                        activity,
                        Manifest.permission.RECORD_AUDIO,
                    )
            )
                rationale = true
            else permission.launch(Manifest.permission.RECORD_AUDIO)
        }
    ) {
        Icon(if (recorder == null) Icons.Outlined.Mic else Icons.Outlined.Stop, null)
        Spacer(Modifier.width(8.dp))
        Text(stringResource(if (recorder == null) R.string.record else R.string.stop_recording))
    }
    if (rationale || settings)
        AlertDialog(
            onDismissRequest = {
                rationale = false
                settings = false
            },
            text = { Text(stringResource(R.string.mic_hint)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        if (settings)
                            context.startActivity(
                                Intent(
                                    Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                                    Uri.parse("package:${context.packageName}"),
                                )
                            )
                        else permission.launch(Manifest.permission.RECORD_AUDIO)
                        rationale = false
                        settings = false
                    }
                ) {
                    Text(stringResource(if (settings) R.string.open_settings else R.string.record))
                }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        rationale = false
                        settings = false
                    }
                ) {
                    Text(stringResource(R.string.cancel))
                }
            },
        )
}

@Composable
fun NoteEditor(
    id: String,
    note: Note?,
    repo: GardenRepository,
    onBack: () -> Unit,
    onSave: suspend (Note, String) -> Unit,
    onDelete: (String) -> Unit,
    draft: Note? = null,
    onVideo: (String, Long) -> Unit = { _, _ -> },
) {
    val initial = remember(id) { note ?: draft }
    val media = (LocalContext.current.applicationContext as GardenApp).media
    val workspace by repo.state.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()
    var error by remember { mutableStateOf("") }
    var title by rememberSaveable { mutableStateOf(initial?.title ?: "") }
    var courseId by rememberSaveable { mutableStateOf(initial?.courseId.orEmpty()) }
    var audioIds by rememberSaveable { mutableStateOf(initial?.media.orEmpty()) }
    var imageIds by rememberSaveable { mutableStateOf(initial?.images.orEmpty()) }
    var showAudio by rememberSaveable { mutableStateOf(audioIds.isNotEmpty()) }
    var recording by remember { mutableStateOf(false) }
    var recordingStartedAt by remember { mutableLongStateOf(0L) }
    var recordingPeaks by remember { mutableStateOf<List<Float>>(emptyList()) }
    var stopRecording by remember { mutableStateOf<(() -> Unit)?>(null) }
    var importing by remember { mutableStateOf(false) }
    val audioLimit = stringResource(R.string.note_audio_limit)
    fun addAudio(id: String) {
        if (audioIds.size < 20) audioIds = audioIds + id else error = audioLimit
    }
    val chooseAudio = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) scope.launch {
            importing = true
            try { addAudio(media.import(uri, "audio")) }
            catch (e: Exception) { error = e.message.orEmpty() }
            finally { importing = false }
        }
    }
    val document = rememberSaveable(saver = NoteDocument.Saver) { NoteDocument() }
    val editor = document.active.editor
    val keyboard = LocalSoftwareKeyboardController.current
    var insertingTable by rememberSaveable { mutableStateOf(false) }
    val noteLimitError = stringResource(R.string.note_limit)
    fun format(action: (RichTextState) -> Unit) {
        val active = document.active
        val current = active.editor
        val selection = current.selection
        active.focus.requestFocus()
        if (current.selection != selection) current.selection = selection
        document.change { action(current) }
        keyboard?.show()
    }
    var loaded by rememberSaveable { mutableStateOf(false) }
    var original by rememberSaveable { mutableStateOf("") }
    var discard by remember { mutableStateOf(false) }
    var saving by remember { mutableStateOf(false) }
    LaunchedEffect(id) {
        if (!loaded) {
            val body = repo.body(id)
            document.setHtml(body)
            original = document.toHtml()
            loaded = true
        }
    }
    fun leave() {
        if (recording) { stopRecording?.invoke(); discard = true }
        else if (courseId != note?.courseId.orEmpty() || title != (note?.title ?: "") || audioIds != note?.media.orEmpty() || imageIds != note?.images.orEmpty() || (loaded && document.toHtml() != original)) discard = true
        else onBack()
    }
    BackHandler { leave() }
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.notes)) },
                navigationIcon = { BackButton { leave() } },
                actions = {
                    if (note != null)
                        IconButton(onClick = { onDelete(id) }) {
                            Icon(Icons.Outlined.DeleteOutline, stringResource(R.string.delete))
                        }
                    NoteExportAction(media, loaded && title.isNotBlank() && !recording && !importing) {
                        com.jdinohaila.studentgarden.export.ExportNote.from(
                            (initial ?: Note(id = id, title = title)).copy(title = title, media = audioIds, images = imageIds), document.toHtml())
                    }
                    TextButton(
                        enabled = loaded && title.isNotBlank() && !saving && !recording && !importing,
                        onClick = {
                            if (document.plainText.length > 50000) {
                                error = noteLimitError
                                return@TextButton
                            }
                            saving = true
                            val html = document.toHtml()
                            val preview = document.plainText.take(180)
                            scope.launch {
                                try {
                                    onSave(
                                        (initial ?: Note(id = id, title = title)).copy(
                                            title = title.trim(),
                                            courseId = courseId.ifBlank { null },
                                            preview = preview,
                                            media = audioIds,
                                            images = imageIds,
                                            videoMoments = initial?.videoMoments.orEmpty().map { moment ->
                                                moment.copy(media = moment.media.filter { it in audioIds }, images = moment.images.filter { it in imageIds })
                                            } + if (initial?.videoId != null && (audioIds.any { id -> initial.videoMoments.none { id in it.media } } || imageIds.any { id -> initial.videoMoments.none { id in it.images } })) {
                                                listOf(com.jdinohaila.studentgarden.domain.VideoMoment(positionMillis = initial.videoPositionMillis,
                                                    media = audioIds.filter { id -> initial.videoMoments.none { id in it.media } },
                                                    images = imageIds.filter { id -> initial.videoMoments.none { id in it.images } }))
                                            } else emptyList(),
                                        ),
                                        html,
                                    )
                                } catch (e: Exception) {
                                    error = e.message.orEmpty()
                                } finally {
                                    saving = false
                                }
                            }
                        },
                    ) {
                        Text(stringResource(R.string.save))
                    }
                },
            )
        },
        bottomBar = {
            if (recording) Surface {
                Box(Modifier.fillMaxWidth().navigationBarsPadding().imePadding().padding(16.dp)) {
                    LiveRecordingStatus(recordingStartedAt, recordingPeaks) { stopRecording?.invoke() }
                }
            }
        },
        contentWindowInsets = WindowInsets.safeDrawing,
    ) { p ->
        Column(Modifier.fillMaxSize().padding(p).consumeWindowInsets(p)) {
            TextField(
                title,
                { title = it },
                Modifier.fillMaxWidth(),
                placeholder = { Text(stringResource(R.string.title)) },
                textStyle = MaterialTheme.typography.headlineMedium,
                singleLine = true,
                colors =
                    TextFieldDefaults.colors(
                        focusedContainerColor = MaterialTheme.colorScheme.surface,
                        unfocusedContainerColor = MaterialTheme.colorScheme.surface,
                        focusedIndicatorColor = accent("gold"),
                    ),
            )
            if (workspace.courses.isNotEmpty()) {
                Box(Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
                    Choice(R.string.course, courseId, listOf("" to stringResource(R.string.none)) + workspace.courses.map { it.id to it.title }) { courseId = it }
                }
            }
            FlowRow(
                Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surfaceContainer).padding(horizontal = 8.dp),
            ) {
                IconToggleButton(checked = showAudio, onCheckedChange = { showAudio = it }, enabled = !recording) {
                    Icon(Icons.Outlined.Mic, stringResource(R.string.audio_notes))
                }
                IconButton(onClick = { document.undo() }, enabled = loaded && document.canUndo) {
                    Icon(Icons.AutoMirrored.Outlined.Undo, stringResource(R.string.undo))
                }
                IconButton(onClick = { document.redo() }, enabled = loaded && document.canRedo) {
                    Icon(Icons.AutoMirrored.Outlined.Redo, stringResource(R.string.redo))
                }
                IconToggleButton(checked = editor.currentSpanStyle.fontWeight == FontWeight.Bold, enabled = loaded,
                    onCheckedChange = { bold -> format { it.addSpanStyle(SpanStyle(fontWeight = if (bold) FontWeight.Bold else FontWeight.Normal)) } }) {
                    Icon(Icons.Outlined.FormatBold, stringResource(R.string.bold))
                }
                IconToggleButton(checked = editor.currentSpanStyle.fontStyle == FontStyle.Italic, enabled = loaded,
                    onCheckedChange = { format { it.toggleSpanStyle(SpanStyle(fontStyle = FontStyle.Italic)) } }) {
                    Icon(Icons.Outlined.FormatItalic, stringResource(R.string.italic))
                }
                IconToggleButton(checked = editor.currentSpanStyle.textDecoration?.contains(TextDecoration.Underline) == true, enabled = loaded,
                    onCheckedChange = { format { it.toggleSpanStyle(SpanStyle(textDecoration = TextDecoration.Underline)) } }) {
                    Icon(Icons.Outlined.FormatUnderlined, stringResource(R.string.underline))
                }
                IconToggleButton(checked = editor.currentSpanStyle.fontSize == 24.sp, enabled = loaded,
                    onCheckedChange = { format { it.toggleSpanStyle(SpanStyle(fontSize = 24.sp)) } }) {
                    Icon(Icons.Outlined.Title, stringResource(R.string.heading))
                }
                IconToggleButton(checked = editor.isUnorderedList, enabled = loaded,
                    onCheckedChange = { format { it.toggleUnorderedList() } }) {
                    Icon(Icons.AutoMirrored.Outlined.FormatListBulleted, stringResource(R.string.bulleted_list))
                }
                IconToggleButton(checked = editor.isOrderedList, enabled = loaded,
                    onCheckedChange = { format { it.toggleOrderedList() } }) {
                    Icon(Icons.Outlined.FormatListNumbered, stringResource(R.string.numbered_list))
                }
                IconButton(onClick = { insertingTable = true }, enabled = loaded) {
                    Icon(Icons.Outlined.TableChart, stringResource(R.string.insert_table))
                }
                IconButton(onClick = { format { it.clearSpanStyles(); it.removeOrderedList(); it.removeUnorderedList() } }, enabled = loaded) {
                    Icon(Icons.Outlined.FormatClear, stringResource(R.string.clear_formatting))
                }
            }
            if (error.isNotBlank()) Text(error, color = MaterialTheme.colorScheme.error)
            if (loaded) NoteBody(document, Modifier.fillMaxWidth().weight(1f).imePadding()) {
                initial?.videoId?.let { source ->
                    TextButton(onClick = { onVideo(source, initial.videoPositionMillis) }, enabled = !recording) {
                        Icon(Icons.Outlined.PlayCircle, null)
                        Spacer(Modifier.width(8.dp))
                        Text(stringResource(R.string.video_note_source, videoTime(initial.videoPositionMillis / 1000)))
                    }
                }
                if (initial?.videoId != null && initial.videoMoments.isNotEmpty()) {
                    val positions = remember(initial.videoMoments) { initial.videoMoments.map { it.positionMillis }.distinctBy { it / 1000 } }
                    androidx.compose.foundation.lazy.LazyRow(contentPadding = PaddingValues(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        items(positions.size, key = { positions[it] }, contentType = { "timestamp" }) { index ->
                            AssistChip(onClick = { onVideo(initial.videoId, positions[index]) }, enabled = !recording,
                                label = { Text(videoTime(positions[index] / 1000)) }, leadingIcon = { Icon(Icons.Outlined.PlayCircle, null) })
                        }
                    }
                }
                if (imageIds.isNotEmpty()) Column(Modifier.fillMaxWidth().heightIn(max = 240.dp).verticalScroll(rememberScrollState()).padding(horizontal = 16.dp)) {
                    imageIds.forEachIndexed { index, imageId -> key(imageId) {
                        Box(Modifier.fillMaxWidth().testTag("note-image-$imageId")) {
                            AsyncImage(media.file(imageId), stringResource(R.string.video_frame), Modifier.fillMaxWidth().aspectRatio(16f / 9f), contentScale = ContentScale.Fit)
                            initial?.videoMoments?.firstOrNull { imageId in it.images }?.let { moment ->
                                TextButton(onClick = { onVideo(initial.videoId!!, moment.positionMillis) }, enabled = !recording,
                                    modifier = Modifier.align(Alignment.BottomStart)) {
                                    Text(videoTime(moment.positionMillis / 1000))
                                }
                            }
                            IconButton(onClick = { imageIds = imageIds - imageId }, modifier = Modifier.align(Alignment.TopEnd)) {
                                Icon(Icons.Outlined.Close, stringResource(R.string.remove_frame, index + 1))
                            }
                        }
                    } }
                }
                if (showAudio) Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
                    Text(stringResource(R.string.audio_notes), style = MaterialTheme.typography.titleSmall)
                    if (audioIds.size < 20) FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        AudioRecorder(media, ::addAudio, {
                            recording = it
                            if (it) { recordingStartedAt = android.os.SystemClock.elapsedRealtime(); recordingPeaks = emptyList() }
                        }, { error = it }, { stopRecording = it }, { recordingPeaks = (recordingPeaks + it).takeLast(48) })
                        OutlinedButton(onClick = { chooseAudio.launch(arrayOf("audio/*")) }, enabled = !recording && !importing) {
                            Text(stringResource(R.string.choose_audio))
                        }
                    } else Text(audioLimit)
                    if (importing) LinearProgressIndicator(Modifier.fillMaxWidth())
                    Column(Modifier.heightIn(max = 180.dp).verticalScroll(rememberScrollState())) {
                        audioIds.forEachIndexed { index, audioId -> key(audioId) {
                            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().testTag("note-audio-$audioId")) {
                                Text(stringResource(R.string.audio_clip, index + 1), Modifier.weight(1f))
                                initial?.videoMoments?.firstOrNull { audioId in it.media }?.let { moment ->
                                    TextButton(onClick = { onVideo(initial.videoId!!, moment.positionMillis) }, enabled = !recording) {
                                        Text(videoTime(moment.positionMillis / 1000))
                                    }
                                }
                                if (!recording) AudioPlayer(media.file(audioId))
                                IconButton(onClick = { audioIds = audioIds - audioId }, enabled = !recording) {
                                    Icon(Icons.Outlined.Close, stringResource(R.string.remove_audio, index + 1))
                                }
                            }
                        } }
                    }
                    HorizontalDivider()
                }
            }
            else CircularProgressIndicator(Modifier.padding(24.dp))
        }
    }
    if (insertingTable) NoteTableDialog(onDismiss = { insertingTable = false }) {
        document.saveTable(null, it)
        insertingTable = false
    }

    if (discard)
        AlertDialog(
            onDismissRequest = { discard = false },
            title = { Text(stringResource(R.string.discard)) },
            confirmButton = {
                TextButton(onClick = onBack) { Text(stringResource(R.string.close)) }
            },
            dismissButton = {
                TextButton(onClick = { discard = false }) {
                    Text(stringResource(R.string.keep_editing))
                }
            },
        )
}
