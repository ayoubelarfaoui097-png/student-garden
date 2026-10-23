@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.jdinohaila.studentgarden.ui

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.*
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation3.runtime.*
import androidx.navigation3.ui.NavDisplay
import com.jdinohaila.studentgarden.R
import com.jdinohaila.studentgarden.data.GardenRepository
import com.jdinohaila.studentgarden.domain.*
import com.jdinohaila.studentgarden.platform.PrivateMedia
import com.jdinohaila.studentgarden.ui.screens.*
import com.jdinohaila.studentgarden.ui.theme.*
import java.time.*
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.decodeFromString
import com.jdinohaila.studentgarden.video.VideoContext

@Serializable
data class Route(val name: String = "home", val id: String = "", val extra: String = "") : NavKey

@Composable
fun UnlockScreen(unlock: () -> Unit) {
    Surface(
        onClick = {},
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background,
    ) {
        Column(
            Modifier.safeDrawingPadding().padding(28.dp),
            verticalArrangement = Arrangement.Center,
        ) {
            Icon(
                Icons.Outlined.Lock,
                null,
                Modifier.size(40.dp),
                tint = MaterialTheme.colorScheme.primary,
            )
            Spacer(Modifier.height(24.dp))
            Text(stringResource(R.string.app_name), style = MaterialTheme.typography.headlineLarge)
            Text(stringResource(R.string.lock_hint), Modifier.padding(vertical = 16.dp))
            Button(onClick = unlock) { Text(stringResource(R.string.unlock)) }
        }
    }
}

@Composable
fun GardenRoot(repo: GardenRepository, media: PrivateMedia, onTheme: (String) -> Unit, lessonRequest: Int = 0, reminderRequest: Int = 0, reminderDestination: String = "") {
    val state by repo.state.collectAsStateWithLifecycle()
    val stack = rememberNavBackStack(Route())
    var handledLessonRequest by rememberSaveable { mutableIntStateOf(0) }
    LaunchedEffect(lessonRequest) {
        if (lessonRequest > handledLessonRequest) {
            if ((stack.lastOrNull() as? Route)?.name != "lessons") stack.add(Route("lessons"))
            handledLessonRequest = lessonRequest
        }
    }
    var handledReminderRequest by rememberSaveable { mutableIntStateOf(0) }
    LaunchedEffect(reminderRequest) {
        if (reminderRequest > handledReminderRequest && reminderDestination in setOf("tasks", "agenda", "cards", "habits", "focus", "settings")) {
            if ((stack.lastOrNull() as? Route)?.name != reminderDestination) stack.add(Route(reminderDestination))
            handledReminderRequest = reminderRequest
        }
    }
    val scope = rememberCoroutineScope()
    val snackbar = remember { SnackbarHostState() }
    val error = stringResource(R.string.error)
    fun action(block: suspend () -> Unit) {
        scope.launch {
            try {
                block()
            } catch (e: Exception) {
                if (e is kotlinx.coroutines.CancellationException) throw e
                snackbar.showSnackbar(e.message?.take(120) ?: error)
            }
        }
    }
    fun navigate(name: String, id: String = "", extra: String = "") {
        snackbar.currentSnackbarData?.dismiss()
        stack.add(Route(name, id, extra))
    }
    fun back() {
        if (stack.size > 1) stack.removeLastOrNull()
    }
    val deleted = stringResource(R.string.removed)
    val undo = stringResource(R.string.undo)
    fun trash(id: String) {
        action {
            repo.trash(id)
            back()
            snackbar.currentSnackbarData?.dismiss()
            if (showDeletionSnackbar(snackbar, deleted, undo))
                repo.undoTrash(id)
        }
    }
    Surface(color = MaterialTheme.colorScheme.background) {
        Box(Modifier.fillMaxSize()) {
            NavDisplay(
                backStack = stack,
                onBack = { back() },
                entryDecorators = listOf(rememberSaveableStateHolderNavEntryDecorator()),
                entryProvider = { key ->
                    val route = key as Route
                    NavEntry(key) {
                        when (route.name) {
                            "home" -> HomeScaffold(state, repo, ::navigate, ::action)
                            "edit" ->
                                RecordEditor(
                                    route.id,
                                    route.extra,
                                    state,
                                    repo,
                                    media,
                                    onBack = ::back,
                                    onSave = { r ->
                                        repo.save(r)
                                        back()
                                    },
                                    onDelete = ::trash,
                                )
                            "note" ->
                                NoteEditor(
                                    route.id,
                                    state.notes.find { it.id == route.id },
                                    repo,
                                    onBack = ::back,
                                    onSave = { note, html ->
                                        repo.save(note, html)
                                        back()
                                    },
                                    onDelete = ::trash,
                                    onVideo = { id, time -> navigate("video", id, repo.json.encodeToString(VideoContext(positionMillis = time))) },
                                )
                            "deck" ->
                                DeckScreen(
                                    route.id,
                                    state,
                                    media,
                                    onBack = ::back,
                                    onNavigate = ::navigate,
                                )
                            "review" ->
                                CardReview(
                                    route.id,
                                    route.extra == "all",
                                    state,
                                    repo,
                                    media,
                                    onBack = ::back,
                                    onAction = ::action,
                                )
                            "habit" ->
                                HabitDetail(
                                    route.id,
                                    state,
                                    repo,
                                    onBack = ::back,
                                    onNavigate = ::navigate,
                                    onAction = ::action,
                                )
                            "video" -> VideoScreen(route.id, repo, ::back,
                                context = runCatching { repo.json.decodeFromString<VideoContext>(route.extra) }.getOrDefault(VideoContext()),
                                onPlay = { id, playlist -> stack[stack.lastIndex] = Route("video", id, repo.json.encodeToString(VideoContext(playlist))) },
                                onNote = { navigate("note", it, "") })
                            "channel" -> ChannelScreen(route.id, ::back) { navigate("video", it, "") }
                            "playlist" -> PlaylistScreen(route.id, ::back) { id, playlist -> navigate("video", id, repo.json.encodeToString(VideoContext(playlist))) }
                            "focus" ->
                                FocusScreen(state, repo, onBack = ::back, onAction = ::action)
                            "settings" ->
                                SettingsScreen(
                                    repo,
                                    media,
                                    onBack = ::back,
                                    onTheme = onTheme,
                                    onTrash = { navigate("trash") },
                                    onAction = ::action,
                                )
                            "trash" -> TrashScreen(state, repo, ::back, ::action)
                            "lessons" -> Scaffold(topBar = { TopAppBar(title = { Text(stringResource(R.string.lessons)) }, navigationIcon = { BackButton(::back) }) }) { padding ->
                                StudyScreen(state, padding, ::navigate, repo, ::action, initialTab = 4)
                            }
                            "tasks", "agenda", "cards", "habits" -> Scaffold(topBar = { TopAppBar(title = { Text(stringResource(when(route.name) { "tasks" -> R.string.tasks; "agenda" -> R.string.agenda; "cards" -> R.string.cards; else -> R.string.habits })) }, navigationIcon = { BackButton(::back) }) }) { padding ->
                                when (route.name) {
                                    "tasks" -> TasksScreen(state, padding, ::navigate, repo, ::action)
                                    "agenda" -> StudyScreen(state, padding, ::navigate, repo, ::action, initialTab = 1)
                                    "cards" -> CardsScreen(state, padding, ::navigate)
                                    else -> HabitsScreen(state, padding, ::navigate, repo, ::action)
                                }
                            }
                            "courses" ->
                                CoursesScreen(state, onBack = ::back, onNavigate = ::navigate)
                            else -> Text("Unknown screen")
                        }
                    }
                },
            )
            SnackbarHost(
                snackbar,
                Modifier.align(Alignment.BottomCenter).navigationBarsPadding().padding(16.dp),
            )
        }
    }
}

private data class Destination(val label: Int, val icon: ImageVector)

private val destinations =
    listOf(
        Destination(R.string.today, Icons.Outlined.Today),
        Destination(R.string.videos, Icons.Outlined.OndemandVideo),
        Destination(R.string.study, Icons.AutoMirrored.Outlined.MenuBook),
        Destination(R.string.cards, Icons.Outlined.Style),
        Destination(R.string.habits, Icons.Outlined.Eco),
    )

@Composable
private fun HomeScaffold(
    state: GardenState,
    repo: GardenRepository,
    navigate: (String, String, String) -> Unit,
    action: (suspend () -> Unit) -> Unit,
) {
    fun navigateVideo(id: String) { navigate("video", id, "") }
    var tab by rememberSaveable { mutableIntStateOf(0) }
    BoxWithConstraints {
        val wide = maxWidth >= 600.dp
        Row(Modifier.fillMaxSize()) {
            if (wide)
                NavigationRail(containerColor = MaterialTheme.colorScheme.surface) {
                    Spacer(Modifier.height(20.dp))
                    destinations.forEachIndexed { i, d ->
                        NavigationRailItem(
                            modifier = Modifier.testTag("nav-$i"),
                            selected = tab == i,
                            onClick = { tab = i },
                            icon = { Icon(d.icon, stringResource(d.label)) },
                            label = { Text(stringResource(d.label)) },
                        )
                    }
                }
            Scaffold(
                containerColor = MaterialTheme.colorScheme.background,
                topBar = {
                    TopAppBar(
                        title = { Row(verticalAlignment = Alignment.CenterVertically) {
                            Image(painterResource(R.drawable.ic_garden), null, Modifier.size(30.dp))
                            Spacer(Modifier.width(10.dp))
                            Text(stringResource(R.string.app_name), style = MaterialTheme.typography.titleLarge)
                        } },
                        colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
                        actions = {
                            IconButton(onClick = { navigate("focus", "", "") }) { Icon(Icons.Outlined.Timer, stringResource(R.string.focus)) }
                            IconButton(onClick = { navigate("settings", "", "") }) { Icon(Icons.Outlined.Settings, stringResource(R.string.settings)) }
                        }
                    )
                },
                bottomBar = {
                    if (!wide)
                        NavigationBar(
                            containerColor = MaterialTheme.colorScheme.surface,
                            tonalElevation = 0.dp,
                        ) {
                            destinations.forEachIndexed { i, d ->
                                NavigationBarItem(
                                    modifier = Modifier.testTag("nav-$i"),
                                    selected = tab == i,
                                    onClick = { tab = i },
                                    icon = { Icon(d.icon, null) },
                                    label = { Text(stringResource(d.label), maxLines = 1) },
                                    colors =
                                        NavigationBarItemDefaults.colors(
                                            indicatorColor =
                                                Color.Transparent,
                                            selectedIconColor = MaterialTheme.colorScheme.primary,
                                            selectedTextColor = MaterialTheme.colorScheme.primary,
                                        ),
                                )
                            }
                        }
                },
            ) { padding ->
                if (!state.loaded)
                    Box(
                        Modifier.fillMaxSize().padding(padding),
                        contentAlignment = Alignment.Center,
                    ) {
                        CircularProgressIndicator()
                    }
                else
                    when (tab) {
                        0 ->
                            TodayScreen(
                                state,
                                padding,
                                { navigate("tasks", "", "") },
                                { tab = 3 },
                                navigate,
                                repo,
                                action,
                            )
                        1 -> VideosScreen(state, padding, ::navigateVideo, { id -> action { repo.trash(id) } },
                            onPlaylist = { navigate("playlist", it, "") },
                            onChannel = { navigate("channel", it, "") },
                            onPlayInPlaylist = { id, playlist -> navigate("video", id, repo.json.encodeToString(VideoContext(playlist))) })
                        2 -> StudyScreen(state, padding, navigate, repo, action)
                        3 -> CardsScreen(state, padding, navigate)
                        4 -> HabitsScreen(state, padding, navigate, repo, action)
                    }
            }
        }
    }
}

fun dateLabel(millis: Long): String =
    Instant.ofEpochMilli(millis)
        .atZone(ZoneId.systemDefault())
        .format(DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM))

fun timeLabel(millis: Long): String =
    Instant.ofEpochMilli(millis)
        .atZone(ZoneId.systemDefault())
        .format(DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT))

fun pagePadding(p: PaddingValues) =
    PaddingValues(
        start = 16.dp,
        end = 16.dp,
        top = p.calculateTopPadding() + 8.dp,
        bottom = p.calculateBottomPadding() + 24.dp,
    )

@Composable
fun PageTitle(
    title: String,
    button: String? = null,
    color: String = "blue",
    onClick: () -> Unit = {},
) {
    Row(
        Modifier.fillMaxWidth().padding(bottom = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(title, Modifier.weight(1f), style = MaterialTheme.typography.headlineLarge)
        if (button != null)
            FilledTonalButton(
                onClick = onClick,
                colors =
                    ButtonDefaults.filledTonalButtonColors(
                        containerColor = accent(color),
                        contentColor = Color(0xFF202A3B),
                    ),
            ) {
                Icon(Icons.Outlined.Add, null, Modifier.size(18.dp))
                Spacer(Modifier.width(4.dp))
                Text(button)
            }
    }
}

@Composable
fun SectionTitle(title: String, onClick: (() -> Unit)? = null) {
    Row(
        Modifier.fillMaxWidth().padding(top = 24.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(title, Modifier.weight(1f), style = MaterialTheme.typography.titleLarge)
        if (onClick != null)
            IconButton(onClick = onClick) {
                Icon(Icons.AutoMirrored.Outlined.ArrowForward, stringResource(R.string.more))
            }
    }
}

@Composable
fun EmptyState(text: Int, action: Int? = null, color: String = "blue", onClick: () -> Unit = {}) {
    Column(
        Modifier.fillMaxWidth().padding(vertical = 24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text(stringResource(text), color = MaterialTheme.colorScheme.onSurfaceVariant)
        if (action != null) AccentButton(stringResource(action), color, onClick)
    }
}

@Composable
fun AccentButton(
    text: String,
    color: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    Button(
        onClick = onClick,
        modifier = modifier,
        enabled = enabled,
        colors =
            ButtonDefaults.buttonColors(
                containerColor = accent(color),
                contentColor = Color(0xFF202A3B),
            ),
        shape = RoundedCornerShape(12.dp),
    ) {
        Text(text)
    }
}

@Composable
fun EntryRow(
    title: String,
    detail: String = "",
    color: String = "blue",
    onClick: () -> Unit,
    trailing: @Composable (() -> Unit)? = null,
) {
    Row(
        Modifier.fillMaxWidth()
            .clickable(onClick = onClick)
            .heightIn(min = 76.dp)
            .padding(vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.width(4.dp).height(36.dp).background(accent(color), RoundedCornerShape(2.dp)))
        Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
            Text(
                title,
                style = MaterialTheme.typography.titleMedium,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            if (detail.isNotBlank())
                Text(
                    detail,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
        }
        trailing?.invoke()
    }
    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = .65f))
}

@Composable
fun SearchBox(value: String, onChange: (String) -> Unit) {
    OutlinedTextField(
        value,
        onChange,
        Modifier.fillMaxWidth().padding(bottom = 16.dp),
        singleLine = true,
        label = { Text(stringResource(R.string.search)) },
        leadingIcon = { Icon(Icons.Outlined.Search, null) },
        shape = RoundedCornerShape(12.dp),
    )
}

@Composable
fun Tabs(labels: List<Int>, selected: Int, onSelect: (Int) -> Unit) {
    ScrollableTabRow(
        selectedTabIndex = selected,
        edgePadding = 0.dp,
        containerColor = Color.Transparent,
        divider = {},
    ) {
        labels.forEachIndexed { i, label ->
            Tab(
                selected = selected == i,
                onClick = { onSelect(i) },
                text = { Text(stringResource(label)) },
            )
        }
    }
}

@Composable
private fun TodayScreen(
    s: GardenState,
    p: PaddingValues,
    openPlan: () -> Unit,
    openStudy: () -> Unit,
    nav: (String, String, String) -> Unit,
    repo: GardenRepository,
    action: (suspend () -> Unit) -> Unit,
) {
    val today = LocalDate.now()
    val next =
        s.tasks.filter { it.status != "done" }.sortedBy { it.dueAt ?: Long.MAX_VALUE }.take(4)
    LazyColumn(contentPadding = pagePadding(p), modifier = Modifier.fillMaxSize()) {
        item {
            Text(stringResource(R.string.today), style = MaterialTheme.typography.headlineLarge)
            Text(today.format(DateTimeFormatter.ofPattern("EEE, d MMM")), color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(16.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                (0L..6L).forEach { n ->
                    val day = StudyRules.week(today).plusDays(n)
                    val selected = day == today
                    Column(
                        Modifier.widthIn(min = 40.dp)
                            .background(
                                if (selected) MaterialTheme.colorScheme.primary
                                else Color.Transparent,
                                RoundedCornerShape(12.dp),
                            )
                            .padding(vertical = 10.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Text(
                            day.dayOfWeek.getDisplayName(
                                java.time.format.TextStyle.NARROW,
                                java.util.Locale.getDefault(),
                            ),
                            color =
                                if (selected) MaterialTheme.colorScheme.onPrimary
                                else MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Text(
                            day.dayOfMonth.toString(),
                            fontWeight = FontWeight.SemiBold,
                            color =
                                if (selected) MaterialTheme.colorScheme.onPrimary
                                else MaterialTheme.colorScheme.onSurface,
                        )
                    }
                }
            }
            Spacer(Modifier.height(24.dp))
            Surface(
                color = accent("teal").copy(alpha = .12f),
                shape = RoundedCornerShape(16.dp),
                onClick = { nav("focus", "", "") },
            ) {
                Row(
                    Modifier.fillMaxWidth().padding(20.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        Icons.Outlined.Timer,
                        null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(30.dp),
                    )
                    Column(Modifier.weight(1f).padding(horizontal = 16.dp)) {
                        Text(
                            stringResource(R.string.focus),
                            style = MaterialTheme.typography.titleLarge,
                        )
                        Text(
                            s.timer?.let {
                                if (it.finished) stringResource(R.string.focus_finished)
                                else "${it.minutes} ${stringResource(R.string.minutes_unit)}"
                            } ?: "25 ${stringResource(R.string.minutes_unit)}",
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Icon(Icons.Outlined.PlayArrow, null)
                }
            }
            SectionTitle(stringResource(R.string.tasks), openPlan)
        }
        if (next.isEmpty())
            item {
                EmptyState(R.string.empty_tasks, R.string.new_task, "coral") {
                    nav("edit", "task", "")
                }
            }
        items(next, key = { it.id }, contentType = { "task" }) {
            TaskRow(it, s, repo, action) { nav("edit", "task", it.id) }
        }
        item {
            SectionTitle(stringResource(R.string.review))
            val due = s.cards.count { StudyRules.due(it, System.currentTimeMillis()) }
            EntryRow(stringResource(R.string.cards), stringResource(R.string.due_count, due), "orange", openStudy)
            val lessonsDue = s.lessons.count { it.stage < 3 && (it.dueAt ?: Long.MAX_VALUE) <= System.currentTimeMillis() }
            EntryRow(stringResource(R.string.lessons), stringResource(R.string.due_count, lessonsDue), "cyan", { nav("lessons", "", "") })

        }
        if (s.blocks.any { Instant.ofEpochMilli(it.startsAt).atZone(ZoneId.systemDefault()).toLocalDate() == today }) item {
            SectionTitle(stringResource(R.string.agenda))
            val blocks =
                s.blocks.filter {
                    Instant.ofEpochMilli(it.startsAt)
                        .atZone(ZoneId.systemDefault())
                        .toLocalDate() == today
                }
            if (blocks.isEmpty())
                Text(
                    stringResource(R.string.empty_agenda),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            blocks.forEach {
                EntryRow(
                    it.title,
                    "${timeLabel(it.startsAt)} • ${it.minutes} ${stringResource(R.string.minutes_unit)}",
                    it.color,
                    { nav("edit", "block", it.id) },
                )
            }
        }
        item { SectionTitle(stringResource(R.string.habits)) }
        items(s.habits.filter { !it.archived }.take(3), key = { "today-habit-${it.id}" }) { habit ->
            val day = StudyRules.today(habit)
            val checked = s.checks.any { it.habitId == habit.id && it.day == day.toString() }
            EntryRow(habit.title, color = habit.color, onClick = { nav("habit", habit.id, "") }) {
                Checkbox(checked, { action { repo.check(habit, day) } }, enabled = checked || StudyRules.scheduled(habit, day),
                    colors = CheckboxDefaults.colors(checkedColor = accent(habit.color), checkmarkColor = Color(0xFF1C1E21)))
            }
        }

    }
}

@Composable
fun TaskRow(
    task: Task,
    s: GardenState,
    repo: GardenRepository,
    action: (suspend () -> Unit) -> Unit,
    onClick: () -> Unit,
) {
    Surface(color = MaterialTheme.colorScheme.surface, shape = RoundedCornerShape(12.dp), modifier = Modifier.padding(bottom = 6.dp)) {
        Row(Modifier.fillMaxWidth().clickable(onClick = onClick).padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
            Checkbox(task.status == "done", { action { repo.save(task.copy(status = if (it) "done" else "todo")) } },
                colors = CheckboxDefaults.colors(checkedColor = accent("coral"), checkmarkColor = Color(0xFF1C1E21)))
            Column(Modifier.weight(1f).padding(vertical = 8.dp)) {
                Text(task.title, style = MaterialTheme.typography.titleMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
                val detail = listOfNotNull(s.courses.find { it.id == task.courseId }?.title, task.dueAt?.let(::dateLabel), if (task.priority == "high") stringResource(R.string.high) else null).joinToString(" · ") { android.text.BidiFormatter.getInstance().unicodeWrap(it) }
                if (detail.isNotBlank()) Text(detail, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                if (task.status == "progress") Text(stringResource(R.string.task_in_progress), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
            }
            IconButton(onClick = onClick) { Icon(Icons.Outlined.MoreHoriz, stringResource(R.string.edit)) }
        }
    }
}

@Composable
private fun StudyScreen(
    s: GardenState, p: PaddingValues, nav: (String, String, String) -> Unit,
    repo: GardenRepository, action: (suspend () -> Unit) -> Unit, initialTab: Int = 2,
) {
    var tab by rememberSaveable { mutableIntStateOf(initialTab.coerceIn(1, 4)) }
    var search by rememberSaveable { mutableStateOf("") }
    var selectedDay by rememberSaveable { mutableStateOf(LocalDate.now().toString()) }
    LazyColumn(contentPadding = pagePadding(p), modifier = Modifier.fillMaxSize()) {
        item {
            PageTitle(stringResource(R.string.study), stringResource(R.string.add), listOf("coral", "blue", "gold", "blue", "cyan")[tab]) {
                when (tab) {
                    1 -> nav("edit", "block", selectedDay)
                    2 -> nav("note", newId(), "")
                    3 -> nav("edit", "course", "")
                    else -> nav("edit", "lesson", "")
                }
            }
            Tabs(listOf(R.string.agenda, R.string.notes, R.string.courses, R.string.lessons), tab - 1) { tab = it + 1; search = "" }
            Spacer(Modifier.height(12.dp))
            if (tab != 1) SearchBox(search) { search = it }

        }
        when (tab) {
            1 -> {
                item { WeekPicker(LocalDate.parse(selectedDay)) { selectedDay = it.toString() } }
                val blocks = s.blocks.filter { Instant.ofEpochMilli(it.startsAt).atZone(ZoneId.systemDefault()).toLocalDate().toString() == selectedDay }.sortedBy { it.startsAt }
                if (blocks.isEmpty()) item { EmptyState(R.string.empty_agenda) }
                items(blocks, key = { it.id }) { block ->
                    TimelineBlock(block, s, { nav("edit", "block", block.id) }, { action { repo.save(block.copy(done = it)) } })
                }
            }
            2 -> {
                val notes = s.notes.filter { (it.title + it.preview).contains(search, true) }
                if (notes.isEmpty()) item { EmptyState(if (search.isBlank()) R.string.empty_notes else R.string.no_results) }
                items(notes, key = { it.id }) { note -> NoteTile(note, s) { nav("note", note.id, "") } }
            }
            3 -> {
                val courses = s.courses.filter { (it.title + it.code + it.term).contains(search, true) }
                if (courses.isEmpty()) item { EmptyState(if (search.isBlank()) R.string.empty_courses else R.string.no_results) }
                items(courses, key = { it.id }) { course -> CourseTile(course, s) { nav("edit", "course", course.id) } }
            }
            4 -> {
                val lessons = s.lessons.filter { (it.title + it.subject).contains(search, true) }
                if (lessons.isEmpty()) item { EmptyState(if (search.isBlank()) R.string.empty_lessons else R.string.no_results) }
                items(lessons, key = { it.id }) { lesson ->
                    EntryRow(lesson.title, lesson.dueAt?.let(::dateLabel) ?: stringResource(R.string.complete), lesson.color,
                        { nav("edit", "lesson", lesson.id) })
                }
            }
        }
    }
}

@Composable
fun CoursesScreen(
    s: GardenState,
    onBack: () -> Unit,
    onNavigate: (String, String, String) -> Unit,
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.courses)) },
                navigationIcon = { BackButton(onBack) },
                actions = {
                    IconButton(onClick = { onNavigate("edit", "course", "") }) {
                        Icon(Icons.Outlined.Add, stringResource(R.string.add))
                    }
                },
            )
        }
    ) { p ->
        LazyColumn(contentPadding = pagePadding(p)) {
            if (s.courses.isEmpty()) item { EmptyState(R.string.empty_courses) }
            items(s.courses, key = { it.id }, contentType = { "course" }) { c ->
                EntryRow(
                    c.title,
                    listOf(c.code, c.term).filter { it.isNotBlank() }.joinToString(" • "),
                    c.color,
                    { onNavigate("edit", "course", c.id) },
                )
            }
        }
    }
}

@Composable
fun BackButton(onBack: () -> Unit) {
    IconButton(onClick = onBack) {
        Icon(Icons.AutoMirrored.Outlined.ArrowBack, stringResource(R.string.close))
    }
}

@Composable
fun TrashScreen(
    s: GardenState,
    repo: GardenRepository,
    onBack: () -> Unit,
    action: (suspend () -> Unit) -> Unit,
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.trash)) },
                navigationIcon = { BackButton(onBack) },
            )
        }
    ) { p ->
        LazyColumn(contentPadding = pagePadding(p)) {
            if (s.trash.isEmpty()) item { EmptyState(R.string.no_results) }
            items(s.trash, key = { it.id }, contentType = { "trash" }) { r ->
                val title =
                    when (r) {
                        is Course -> r.title
                        is Task -> r.title
                        is Note -> r.title
                        is StudyBlock -> r.title
                        is Deck -> r.title
                        is Lesson -> r.title
                        is Habit -> r.title
                        is Flashcard -> r.answer
                        is Review -> r.rating
                        is LearningVideo -> r.title
                    }
                EntryRow(title, color = "slate", onClick = {}) {
                    TextButton(onClick = { action { repo.undoTrash(r.id) } }) {
                        Text(stringResource(R.string.restore))
                    }
                }
            }
        }
    }
}

@Composable
private fun TasksScreen(s: GardenState, p: PaddingValues, nav: (String, String, String) -> Unit,
    repo: GardenRepository, action: (suspend () -> Unit) -> Unit) {
    var search by rememberSaveable { mutableStateOf("") }
    var completed by rememberSaveable { mutableStateOf(false) }
    val tasks = s.tasks.filter { (it.status == "done") == completed && (it.title + it.detail).contains(search, true) }.sortedBy { it.dueAt ?: Long.MAX_VALUE }
    LazyColumn(contentPadding = pagePadding(p), modifier = Modifier.fillMaxSize()) {
        item {
            PageTitle(stringResource(R.string.tasks), stringResource(R.string.add), "coral") { nav("edit", "task", "") }
            SearchBox(search) { search = it }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(!completed, { completed = false }, { Text(stringResource(R.string.active)) })
                FilterChip(completed, { completed = true }, { Text(stringResource(R.string.done)) })
            }
        }
        if (tasks.isEmpty()) item { EmptyState(if (search.isBlank()) R.string.empty_tasks else R.string.no_results) }
        items(tasks, key = { it.id }) { task -> TaskRow(task, s, repo, action) { nav("edit", "task", task.id) } }
    }
}
