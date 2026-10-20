@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.jdinohaila.studentgarden.ui.screens

import android.media.MediaPlayer
import androidx.compose.animation.AnimatedContent
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.*
import androidx.compose.foundation.lazy.grid.*
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.foundation.shape.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.*
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.semantics.*
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import coil3.compose.AsyncImage
import com.jdinohaila.studentgarden.R
import com.jdinohaila.studentgarden.data.GardenRepository
import com.jdinohaila.studentgarden.domain.*
import com.jdinohaila.studentgarden.platform.PrivateMedia
import com.jdinohaila.studentgarden.ui.*
import com.jdinohaila.studentgarden.ui.theme.*
import java.io.File
import java.time.*
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.Locale
import kotlinx.coroutines.delay

@Composable
fun AudioPlayer(file: File) {
    var player by remember { mutableStateOf<MediaPlayer?>(null) }
    var playing by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf(false) }
    fun stop() {
        player?.release()
        player = null
        playing = false
    }
    val owner = LocalLifecycleOwner.current
    DisposableEffect(file, owner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_STOP) stop()
        }
        owner.lifecycle.addObserver(observer)
        onDispose {
            owner.lifecycle.removeObserver(observer)
            stop()
        }
    }
    val context = LocalContext.current
    OutlinedButton(
        onClick = {
            if (playing) stop()
            else
                try {
                    val p = MediaPlayer()
                    player = p
                    p.setDataSource(file.absolutePath)
                    p.setOnPreparedListener {
                        it.start()
                        playing = true
                    }
                    p.setOnCompletionListener { stop() }
                    p.setOnErrorListener { _, _, _ ->
                        stop()
                        error = true
                        true
                    }
                    p.prepareAsync()
                } catch (e: Exception) {
                    stop()
                    error = true
                }
        }
    ) {
        Icon(if (playing) Icons.Outlined.Stop else Icons.Outlined.VolumeUp, null)
        Spacer(Modifier.width(8.dp))
        Text(stringResource(if (playing) R.string.stop_audio else R.string.play))
    }
    if (error) Text(stringResource(R.string.error), color = MaterialTheme.colorScheme.error)
}

@Composable
fun DeckScreen(
    id: String,
    s: GardenState,
    media: PrivateMedia,
    onBack: () -> Unit,
    onNavigate: (String, String, String) -> Unit,
) {
    var search by rememberSaveable { mutableStateOf("") }
    val cards =
        s.cards.filter {
            (it.deckId.orEmpty() == id ||
                (id.isBlank() && s.decks.none { d -> d.id == it.deckId })) &&
                (it.prompt + it.answer + it.subject).contains(search, true)
        }
    val visible = if (search.isBlank()) cards.take(9) else cards
    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(s.decks.find { it.id == id }?.title ?: stringResource(R.string.unfiled))
                },
                navigationIcon = { BackButton(onBack) },
                actions = {
                    if (id.isNotBlank())
                        IconButton(onClick = { onNavigate("edit", "deck", id) }) {
                            Icon(Icons.Outlined.Edit, stringResource(R.string.edit))
                        }
                },
            )
        }
    ) { p ->
        LazyVerticalGrid(columns = GridCells.Fixed(3), contentPadding = pagePadding(p), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxSize()) {
            item(span = { GridItemSpan(maxLineSpan) }) {
                Column {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        AccentButton(stringResource(R.string.review), "orange", { onNavigate("review", id, "") }, Modifier.weight(1f))
                        OutlinedButton(onClick = { onNavigate("review", id, "all") }, modifier = Modifier.weight(1f)) { Text(stringResource(R.string.practice)) }
                    }
                    Spacer(Modifier.height(8.dp))
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                        Text(pluralStringResource(R.plurals.card_count, cards.size, cards.size), style = MaterialTheme.typography.labelLarge)
                        TextButton(onClick = { onNavigate("edit", "card", id) }) { Icon(Icons.Outlined.Add, null); Text(stringResource(R.string.new_card)) }
                    }
                    SearchBox(search) { search = it }
                    if (search.isBlank() && cards.size > 9) Text(stringResource(R.string.showing9), style = MaterialTheme.typography.bodySmall)
                }
            }
            items(visible, key = { it.id }, contentType = { "card" }) { card ->
                Surface(onClick = { onNavigate("edit", "card", card.id) }, shape = RoundedCornerShape(12.dp), color = MaterialTheme.colorScheme.surface, border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)) {
                    Column(Modifier.heightIn(min = 140.dp).padding(10.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                        if (card.type == "image" && card.imageId != null) AsyncImage(media.file(card.imageId), null, Modifier.fillMaxWidth().height(76.dp).clip(RoundedCornerShape(8.dp)), contentScale = ContentScale.Fit)
                        else Icon(if (card.type == "audio") Icons.Outlined.VolumeUp else Icons.Outlined.Notes, null, Modifier.size(32.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                        Spacer(Modifier.height(8.dp))
                        Text(card.prompt.ifBlank { card.answer }, style = MaterialTheme.typography.bodyMedium, maxLines = 3, overflow = TextOverflow.Ellipsis)
                    }
                }
            }
            if (visible.isEmpty()) item(span = { GridItemSpan(maxLineSpan) }) { EmptyState(R.string.no_results) }
        }
    }
}

@Composable
fun CardReview(
    deckId: String,
    all: Boolean,
    s: GardenState,
    repo: GardenRepository,
    media: PrivateMedia,
    onBack: () -> Unit,
    onAction: (suspend () -> Unit) -> Unit,
) {
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    LaunchedEffect(lifecycle) {
        lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
            while (true) {
                delay(1000)
                now = System.currentTimeMillis()
            }
        }
    }
    val eligible =
        s.cards
            .filter {
                (it.deckId.orEmpty() == deckId ||
                    (deckId.isBlank() && s.decks.none { d -> d.id == it.deckId })) &&
                    (all || StudyRules.due(it, now))
            }
    var seen by rememberSaveable(deckId, all) { mutableStateOf(listOf<String>()) }
    val available = eligible.filter { !all || it.id !in seen }
    val availableIds = available.map { it.id }
    var order by rememberSaveable(deckId, all) { mutableStateOf(reviewOrder(emptyList(), availableIds)) }
    LaunchedEffect(availableIds, s.loaded) {
        if (s.loaded) order = reviewOrder(order, availableIds)
    }
    val byId = available.associateBy { it.id }
    val card = order.firstNotNullOfOrNull { byId[it] }
    var revealed by rememberSaveable(card?.id) { mutableStateOf(false) }
    var busy by remember { mutableStateOf(false) }
    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(s.decks.find { it.id == deckId }?.title ?: stringResource(R.string.review))
                },
                navigationIcon = { BackButton(onBack) },
            )
        }
    ) { p ->
        if (!s.loaded || (card == null && available.isNotEmpty()))
            Box(Modifier.fillMaxSize().padding(p), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
        else if (card == null)
            Column(
                Modifier.fillMaxSize().padding(p).padding(24.dp),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Icon(
                    Icons.Outlined.CheckCircleOutline,
                    null,
                    Modifier.size(48.dp),
                    tint = MaterialTheme.colorScheme.primary,
                )
                Spacer(Modifier.height(20.dp))
                val next =
                    s.cards
                        .filter {
                            (it.deckId.orEmpty() == deckId ||
                                (deckId.isBlank() && s.decks.none { d -> d.id == it.deckId })) &&
                                it.stage < 4 &&
                                it.dueAt > now
                        }
                        .minByOrNull { it.dueAt }
                Text(
                    stringResource(
                        if (next != null && next.dueAt - now <= 600000) R.string.next_up
                        else R.string.no_due
                    ),
                    style = MaterialTheme.typography.headlineMedium,
                )
                if (next != null) Text("${dateLabel(next.dueAt)} • ${timeLabel(next.dueAt)}")
                TextButton(onClick = onBack) { Text(stringResource(R.string.close)) }
            }
        else
            Column(
                Modifier.fillMaxSize().padding(p).consumeWindowInsets(p).padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                LinearProgressIndicator(
                    progress = {
                        val pending = eligible.count { it.id !in seen }
                        seen.distinct().size.toFloat() / (seen.distinct().size + pending).coerceAtLeast(1)
                    },
                    modifier = Modifier.fillMaxWidth(),
                    color = accent("orange"),
                )
                Surface(
                    onClick = { revealed = !revealed },
                    modifier = Modifier.fillMaxWidth().weight(1f).testTag("review-card"),
                    shape = RoundedCornerShape(20.dp),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                    color = MaterialTheme.colorScheme.surface,
                ) {
                    BoxWithConstraints(Modifier.fillMaxSize()) {
                        val viewportHeight = maxHeight
                        AnimatedContent(revealed, modifier = Modifier.fillMaxSize(), label = "card reveal") { back ->
                            Column(
                                Modifier.fillMaxWidth()
                                    .verticalScroll(rememberScrollState())
                                    .heightIn(min = viewportHeight)
                                    .padding(24.dp),
                                verticalArrangement = Arrangement.Center,
                                horizontalAlignment = Alignment.CenterHorizontally,
                            ) {
                                Column(Modifier.fillMaxWidth().testTag("review-content"), horizontalAlignment = Alignment.CenterHorizontally) {
                                    val hasQuestion = card.prompt.isNotBlank()
                                    val hasImage = card.type == "image" && card.imageId != null
                                    if (hasQuestion) Text(card.prompt, style = MaterialTheme.typography.headlineMedium, modifier = Modifier.fillMaxWidth().testTag("review-question"))
                                    if (back) {
                                        if (hasQuestion) {
                                            Spacer(Modifier.height(24.dp))
                                            HorizontalDivider(Modifier.testTag("review-divider"))
                                            Spacer(Modifier.height(20.dp))
                                        }
                                        Text(stringResource(R.string.answer), color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.fillMaxWidth())
                                        Spacer(Modifier.height(12.dp))
                                        Text(card.answer, style = MaterialTheme.typography.headlineSmall, modifier = Modifier.fillMaxWidth().testTag("review-answer"))
                                    }
                                    if (hasImage) {
                                        if (hasQuestion || back) Spacer(Modifier.height(24.dp))
                                        AsyncImage(media.file(card.imageId), card.prompt.ifBlank { null }, Modifier.fillMaxWidth().heightIn(max = 320.dp).testTag("review-image"), contentScale = ContentScale.Fit)
                                    }
                                    if (card.audioId != null) {
                                        if (hasQuestion || back || hasImage) Spacer(Modifier.height(24.dp))
                                        AudioPlayer(media.file(card.audioId))
                                    }
                                }
                            }
                        }
                    }
                }
                if (revealed) {
                    fun rate(rating: String) {
                        if (!busy) {
                            busy = true
                            onAction {
                                try {
                                    repo.rate(card, rating)
                                    order = order.filterNot { it == card.id }
                                    seen = seen + card.id
                                    revealed = false
                                } finally {
                                    busy = false
                                }
                            }
                        }
                    }
                    val advanceLabel = stringResource(when (card.stage) { 0 -> R.string.hours48; 1 -> R.string.days7; 2 -> R.string.month1; else -> R.string.complete })
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            ReviewRating(stringResource(R.string.again), "<1m", "coral", Modifier.weight(1f), !busy) { rate("again") }
                            ReviewRating(stringResource(R.string.hard), "<6m", "orange", Modifier.weight(1f), !busy) { rate("hard") }
                        }
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            ReviewRating(stringResource(R.string.good), "10m", "teal", Modifier.weight(1f), !busy) { rate("good") }
                            AccentButton(advanceLabel, "orange", { rate("advance") }, Modifier.weight(1f).heightIn(min = 68.dp), !busy)
                        }
                    }
                } else AccentButton(stringResource(R.string.reveal), "orange", { revealed = true }, Modifier.fillMaxWidth().heightIn(min = 56.dp))
            }
    }
}

@Composable
fun HabitsScreen(
    s: GardenState,
    p: PaddingValues,
    nav: (String, String, String) -> Unit,
    repo: GardenRepository,
    action: (suspend () -> Unit) -> Unit,
) {
    var archived by rememberSaveable { mutableStateOf(false) }
    val habits = s.habits.filter { it.archived == archived }
    LazyColumn(contentPadding = pagePadding(p), modifier = Modifier.fillMaxSize()) {
        item {
            PageTitle(stringResource(R.string.habits), stringResource(R.string.add), "green") {
                nav("edit", "habit", "")
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(
                    !archived,
                    { archived = false },
                    { Text(stringResource(R.string.active)) },
                )
                FilterChip(
                    archived,
                    { archived = true },
                    { Text(stringResource(R.string.archived)) },
                )
            }
        }
        if (habits.isEmpty()) item { EmptyState(R.string.empty_habits) }
        items(habits, key = { it.id }, contentType = { "habit" }) { h ->
            val contextDone = stringResource(R.string.done)
            val contextActive = stringResource(R.string.active)
            val today = StudyRules.today(h)
            val checks = s.checks.filter { it.habitId == h.id }.map { it.day }.toSet()
            Column(Modifier.fillMaxWidth().padding(vertical = 8.dp).background(MaterialTheme.colorScheme.surface, RoundedCornerShape(16.dp)).padding(12.dp)) {
                Row(
                    Modifier.fillMaxWidth()
                        .clickable { nav("habit", h.id, "") }
                        .heightIn(min = 48.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(
                        Modifier.width(5.dp)
                            .height(28.dp)
                            .background(accent(h.color), RoundedCornerShape(3.dp))
                    )
                    Text(
                        h.title,
                        Modifier.weight(1f).padding(horizontal = 12.dp),
                        style = MaterialTheme.typography.titleLarge,
                    )
                    Icon(
                        Icons.Outlined.BarChart,
                        stringResource(R.string.history),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                BoxWithConstraints(Modifier.fillMaxWidth()) {
                    val dayCount = (maxWidth.value / 48).toInt().coerceIn(1, 7)
                    Row(
                        Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        (dayCount - 1 downTo 0).forEach { n ->
                            val d = today.minusDays(n.toLong())
                            val checked = d.toString() in checks
                            val enabled = !h.archived && (checked || StudyRules.scheduled(h, d))
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text(
                                    d.dayOfWeek.getDisplayName(
                                        TextStyle.NARROW,
                                        Locale.getDefault(),
                                    ),
                                    style = MaterialTheme.typography.bodySmall,
                                )
                                IconButton(
                                    onClick = { action { repo.check(h, d) } },
                                    enabled = enabled,
                                    modifier =
                                        Modifier.size(48.dp)
                                            .semantics {
                                                contentDescription =
                                                    "${h.title}, ${d.format(DateTimeFormatter.ofLocalizedDate(java.time.format.FormatStyle.FULL))}"
                                                stateDescription =
                                                    if (checked) contextDone else contextActive
                                            }
                                            .background(
                                                if (checked) accent(h.color) else Color.Transparent,
                                                CircleShape,
                                            ),
                                ) {
                                    if (checked)
                                        Icon(Icons.Outlined.Check, null, tint = Color(0xFF202A3B))
                                    else
                                        Text(
                                            d.dayOfMonth.toString(),
                                            color =
                                                if (enabled)
                                                    MaterialTheme.colorScheme.onSurfaceVariant
                                                else MaterialTheme.colorScheme.outlineVariant,
                                        )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun HabitDetail(
    id: String,
    s: GardenState,
    repo: GardenRepository,
    onBack: () -> Unit,
    onNavigate: (String, String, String) -> Unit,
    onAction: (suspend () -> Unit) -> Unit,
) {
    val h = s.habits.find { it.id == id } ?: return
    val today = StudyRules.today(h)
    var month by rememberSaveable { mutableStateOf(YearMonth.from(today).toString()) }
    val yearMonth = YearMonth.parse(month)
    val checks = s.checks.filter { it.habitId == h.id }.map { it.day }.toSet()
    val rate = StudyRules.rate(h, checks, today)
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(h.title, maxLines = 1) },
                navigationIcon = { BackButton(onBack) },
                actions = {
                    IconButton(onClick = { onNavigate("edit", "habit", h.id) }) {
                        Icon(Icons.Outlined.Edit, stringResource(R.string.edit))
                    }
                },
            )
        }
    ) { p ->
        LazyColumn(contentPadding = pagePadding(p), modifier = Modifier.fillMaxSize()) {
            item {
                if (h.cue.isNotBlank())
                    Text(h.cue, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Row(
                    Modifier.fillMaxWidth().padding(vertical = 24.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    listOf(
                            R.string.streak to StudyRules.streak(h, checks, today).toString(),
                            R.string.best_streak to StudyRules.best(h, checks, today).toString(),
                            R.string.last30 to
                                if (rate.second == 0) "—" else "${rate.first*100/rate.second}%",
                        )
                        .forEach { (label, value) ->
                            Column(Modifier.weight(1f)) {
                                Text(
                                    value,
                                    style = MaterialTheme.typography.headlineLarge,
                                    color = MaterialTheme.colorScheme.onSurface,
                                )
                                Text(
                                    stringResource(label),
                                    style = MaterialTheme.typography.bodySmall,
                                )
                            }
                        }
                }
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = { month = yearMonth.minusMonths(1).toString() }) {
                        Icon(Icons.Outlined.ChevronLeft, stringResource(R.string.history))
                    }
                    Text(
                        yearMonth.format(DateTimeFormatter.ofPattern("MMMM yyyy")),
                        Modifier.weight(1f),
                        style = MaterialTheme.typography.titleLarge,
                    )
                    IconButton(
                        onClick = { month = yearMonth.plusMonths(1).toString() },
                        enabled = yearMonth < YearMonth.from(today),
                    ) {
                        Icon(Icons.Outlined.ChevronRight, stringResource(R.string.history))
                    }
                }
                Row {
                    (1..7).forEach {
                        Text(
                            DayOfWeek.of(it).getDisplayName(TextStyle.NARROW, Locale.getDefault()),
                            Modifier.weight(1f).padding(12.dp),
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                }
                val offset = yearMonth.atDay(1).dayOfWeek.value - 1
                val cells = ((offset + yearMonth.lengthOfMonth() + 6) / 7) * 7
                (0 until cells step 7).forEach { row ->
                    Row(Modifier.fillMaxWidth()) {
                        (row until row + 7).forEach { n ->
                            val day = n - offset + 1
                            Box(Modifier.weight(1f), contentAlignment = Alignment.Center) {
                                if (day in 1..yearMonth.lengthOfMonth()) {
                                    val d = yearMonth.atDay(day)
                                    val checked = d.toString() in checks
                                    val enabled =
                                        !h.archived &&
                                            d <= today &&
                                            d >= LocalDate.parse(h.startDay) &&
                                            (checked || StudyRules.scheduled(h, d))
                                    TextButton(
                                        onClick = { onAction { repo.check(h, d) } },
                                        enabled = enabled,
                                        modifier =
                                            Modifier.sizeIn(minWidth = 40.dp, minHeight = 48.dp)
                                                .background(
                                                    if (checked) accent(h.color)
                                                    else Color.Transparent,
                                                    RoundedCornerShape(10.dp),
                                                ),
                                        contentPadding = PaddingValues(0.dp),
                                    ) {
                                        Text(
                                            day.toString(),
                                            color =
                                                if (checked) Color(0xFF202A3B)
                                                else if (enabled)
                                                    MaterialTheme.colorScheme.onSurface
                                                else MaterialTheme.colorScheme.outlineVariant,
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
                SectionTitle(stringResource(R.string.history))
                Row(
                    Modifier.fillMaxWidth().height(116.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.Bottom,
                ) {
                    (7 downTo 0).forEach { n ->
                        val start = StudyRules.week(today).minusWeeks(n.toLong())
                        val count = (0L..6L).count { start.plusDays(it).toString() in checks }
                        Column(
                            Modifier.weight(1f),
                            horizontalAlignment = Alignment.CenterHorizontally,
                        ) {
                            Text(count.toString(), style = MaterialTheme.typography.bodySmall)
                            Box(
                                Modifier.fillMaxWidth()
                                    .height((count * 10 + 3).dp)
                                    .background(
                                        accent(h.color),
                                        RoundedCornerShape(topStart = 4.dp, topEnd = 4.dp),
                                    )
                            )
                            Text(
                                start.format(DateTimeFormatter.ofPattern("d/M")),
                                style = MaterialTheme.typography.labelSmall,
                            )
                        }
                    }
                }
                Spacer(Modifier.height(24.dp))
                OutlinedButton(
                    onClick = { onAction { repo.save(h.copy(archived = !h.archived)) } }
                ) {
                    Text(stringResource(if (h.archived) R.string.restore else R.string.archive))
                }
            }
        }
    }
}

@Composable
private fun ReviewRating(label: String, interval: String, color: String, modifier: Modifier, enabled: Boolean, onClick: () -> Unit) {
    OutlinedButton(onClick = onClick, enabled = enabled, modifier = modifier.heightIn(min = 68.dp), shape = RoundedCornerShape(12.dp), contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp)) {
        Box(Modifier.size(12.dp).background(accent(color), CircleShape))
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(label, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurface)
            Text(interval, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
