// SPDX-License-Identifier: GPL-3.0-or-later
@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@file:androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
package com.jdinohaila.studentgarden.ui.screens

import android.content.pm.ActivityInfo
import android.content.res.Configuration
import android.view.SurfaceView
import android.os.SystemClock
import androidx.activity.compose.BackHandler
import androidx.activity.compose.LocalActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.PlaylistPlay
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.media3.ui.PlayerView
import coil3.compose.AsyncImage
import com.jdinohaila.studentgarden.GardenApp
import com.jdinohaila.studentgarden.R
import com.jdinohaila.studentgarden.data.GardenRepository
import com.jdinohaila.studentgarden.domain.GardenState
import com.jdinohaila.studentgarden.ui.*
import com.jdinohaila.studentgarden.video.*
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import com.jdinohaila.studentgarden.domain.Note

@Composable
fun VideosScreen(s: GardenState, padding: PaddingValues, onPlay: (String) -> Unit, onRemove: (String) -> Unit,
    onPlaylist: (String) -> Unit = {}, onChannel: (String) -> Unit = {}, onNotebook: (String) -> Unit = {}, onPlayInPlaylist: (String, String) -> Unit = { id, _ -> onPlay(id) }) {
    val app = LocalContext.current.applicationContext as GardenApp
    val scope = rememberCoroutineScope()
    val keyboard = LocalSoftwareKeyboardController.current
    var query by rememberSaveable { mutableStateOf("") }
    var submitted by rememberSaveable { mutableStateOf("") }
    var savedOnly by rememberSaveable { mutableStateOf(false) }
    var notebooksOnly by rememberSaveable { mutableStateOf(false) }
    var type by rememberSaveable { mutableStateOf(SearchType.ALL.name) }
    var duration by rememberSaveable { mutableStateOf(SearchDuration.ANY.name) }
    var date by rememberSaveable { mutableStateOf(SearchDate.ANY.name) }
    var sort by rememberSaveable { mutableStateOf(SearchSort.RELEVANCE.name) }
    val filters = SearchFilters(SearchType.valueOf(type), SearchDuration.valueOf(duration), SearchDate.valueOf(date), SearchSort.valueOf(sort))
    var showFilters by remember { mutableStateOf(false) }
    var retry by remember { mutableIntStateOf(0) }
    var results by remember { mutableStateOf<List<SearchHit>>(emptyList()) }
    var next by remember { mutableStateOf<CatalogCursor?>(null) }
    var suggestion by remember { mutableStateOf("") }
    var loading by remember { mutableStateOf(false) }
    var moreLoading by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf(false) }
    var moreError by remember { mutableStateOf(false) }
    var generation by remember { mutableIntStateOf(0) }
    fun search() {
        val text = query.trim()
        if (text.isEmpty()) return
        keyboard?.hide()
        ChannelLinks.url(if (text.startsWith("@")) "https://www.youtube.com/$text" else text)?.let { onChannel(it); return }
        val playlist = PlaylistLinks.id(text)
        val directVideo = text.contains("://") || text.startsWith("youtu") || text.startsWith("www.") ||
            (text.length == 11 && text.any { it.isDigit() || it == '-' || it == '_' })
        if (directVideo) VideoLinks.id(text)?.let { if (playlist != null) onPlayInPlaylist(it, playlist) else onPlay(it); return }
        if (playlist != null) { onPlaylist(playlist); return }
        savedOnly = false; submitted = text; retry++
    }
    LaunchedEffect(submitted, retry, filters) {
        generation++
        if (submitted.isBlank()) return@LaunchedEffect
        loading = true; error = false; results = emptyList(); next = null; moreError = false; moreLoading = false
        try {
            app.videoCatalog.searchUpdates(submitted, filters).collect { page ->
                results = page.items; next = page.next; suggestion = page.suggestion
            }
        } catch (e: Exception) { if (e is CancellationException) throw e; error = true }
        finally { loading = false }
    }
    fun loadMore() {
        val cursor = next ?: return
        val requestGeneration = generation
        moreLoading = true; moreError = false
        scope.launch {
            try {
                val page = app.videoCatalog.searchPage(submitted, filters, cursor)
                if (generation == requestGeneration) {
                    val combined = (results + page.items).distinctBy { it.key }
                    next = if (combined.size == results.size) null else page.next
                    results = combined
                }
            } catch (e: Exception) { if (e is CancellationException) throw e; if (generation == requestGeneration) moreError = true }
            finally { if (generation == requestGeneration) moreLoading = false }
        }
    }
    LazyColumn(contentPadding = pagePadding(padding), modifier = Modifier.fillMaxSize().imePadding()) {
        item(key = "search", contentType = "search") {
            OutlinedTextField(query, { query = it.take(300) }, Modifier.fillMaxWidth().testTag("video-query"),
                label = { Text(stringResource(if (notebooksOnly) R.string.search else R.string.video_search)) }, singleLine = true,
                leadingIcon = { Icon(Icons.Outlined.Search, null) },
                trailingIcon = { IconButton(onClick = { if (notebooksOnly) keyboard?.hide() else search() }, enabled = query.isNotBlank()) { Icon(Icons.Outlined.ArrowForward, stringResource(R.string.video_go)) } },
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search), keyboardActions = KeyboardActions(onSearch = { if (notebooksOnly) keyboard?.hide() else search() }),
                shape = RoundedCornerShape(12.dp))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(!savedOnly && !notebooksOnly, { savedOnly = false; notebooksOnly = false }, { Text(stringResource(R.string.video_find)) })
                FilterChip(savedOnly && !notebooksOnly, { savedOnly = true; notebooksOnly = false }, { Text(stringResource(R.string.video_saved)) })
                FilterChip(notebooksOnly, { notebooksOnly = true; savedOnly = false; query = "" }, { Text(stringResource(R.string.video_notebooks)) })
                if (!savedOnly && !notebooksOnly) AssistChip({ showFilters = true }, { Text(stringResource(R.string.search_filters)) }, leadingIcon = { Icon(Icons.Outlined.Tune, null) })
            }
            if (loading && !savedOnly && !notebooksOnly) LinearProgressIndicator(Modifier.fillMaxWidth())
        }
        if (notebooksOnly) {
            val notebooks = s.notes.filter { it.videoId != null && (it.title + it.preview).contains(query, true) }
            if (notebooks.isEmpty()) item(key = "empty-notebooks", contentType = "empty") { EmptyState(R.string.empty_video_notes) }
            items(notebooks, key = { it.id }, contentType = { "notebook" }) { note ->
                Row(Modifier.fillMaxWidth().clickable { onNotebook(note.id) }.padding(vertical = 16.dp),
                    verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    AsyncImage("https://i.ytimg.com/vi/${note.videoId}/mqdefault.jpg", null,
                        Modifier.width(104.dp).aspectRatio(16f/9f).clip(RoundedCornerShape(6.dp)), contentScale = ContentScale.Crop)
                    Column(Modifier.weight(1f)) {
                        Text(note.title, style = MaterialTheme.typography.titleMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
                        if (note.preview.isNotBlank()) Text(note.preview, style = MaterialTheme.typography.bodySmall, maxLines = 2, overflow = TextOverflow.Ellipsis,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        } else if (savedOnly) {
            if (s.videos.isEmpty()) item(key = "empty-saved", contentType = "empty") { EmptyState(R.string.video_empty_saved) }
            items(s.videos, key = { it.id }, contentType = { "video" }) {
                VideoRow(VideoItem(it.videoId, it.title, it.uploader, it.duration), it.positionMillis, onPlay, { onRemove(it.id) })
            }
        } else {
            if (error) item(key = "error", contentType = "error") { VideoError { retry++ } }
            else if (!loading && submitted.isNotBlank() && results.isEmpty()) item(key = "empty", contentType = "empty") { EmptyState(R.string.no_results) }
            if (!loading && suggestion.isNotBlank() && suggestion != submitted) item(key = "suggestion", contentType = "suggestion") {
                TextButton(onClick = { query = suggestion; search() }) { Text(stringResource(R.string.search_suggestion, suggestion)) }
            }
            items(results, key = { it.key }, contentType = { when (it) { is VideoHit -> "video"; is PlaylistHit -> "playlist"; is ChannelHit -> "channel" } }) { hit -> when (hit) {
                is VideoHit -> VideoRow(hit.item, s.videos.find { v -> v.videoId == hit.item.videoId }?.positionMillis ?: 0, onPlay)
                is PlaylistHit -> PlaylistRow(hit.item) { onPlaylist(hit.item.playlistId) }
                is ChannelHit -> ChannelRow(hit.item) { onChannel(hit.item.url) }
            } }
            if (!loading && next != null) item(contentType = "pagination", key = "search-next-${results.size}") {
                AutoLoadFooter(moreLoading, moreError, ::loadMore)
            }
        }
    }
    if (showFilters) SearchFilterDialog(filters, { showFilters = false }) {
        type = it.type.name; duration = it.duration.name; date = it.date.name; sort = it.sort.name; showFilters = false
    }
}

@Composable private fun typeLabel(value: SearchType) = stringResource(when (value) {
    SearchType.ALL -> R.string.search_all; SearchType.VIDEOS -> R.string.videos; SearchType.PLAYLISTS -> R.string.playlists; SearchType.CHANNELS -> R.string.channels
})
@Composable private fun durationLabel(value: SearchDuration) = stringResource(when (value) {
    SearchDuration.ANY -> R.string.duration_any; SearchDuration.SHORT -> R.string.duration_short; SearchDuration.MEDIUM -> R.string.duration_medium; SearchDuration.LONG -> R.string.duration_long
})
@Composable private fun dateLabel(value: SearchDate) = stringResource(when (value) {
    SearchDate.ANY -> R.string.date_any; SearchDate.HOUR -> R.string.date_hour; SearchDate.TODAY -> R.string.today; SearchDate.WEEK -> R.string.date_week; SearchDate.MONTH -> R.string.date_month; SearchDate.YEAR -> R.string.date_year
})
@Composable private fun sortLabel(value: SearchSort) = stringResource(when (value) {
    SearchSort.RELEVANCE -> R.string.sort_relevance; SearchSort.DATE -> R.string.sort_date; SearchSort.VIEWS -> R.string.sort_views; SearchSort.RATING -> R.string.sort_rating
})

@Composable
private fun SearchFilterDialog(value: SearchFilters, onDismiss: () -> Unit, onApply: (SearchFilters) -> Unit) {
    var draft by remember { mutableStateOf(value) }
    AlertDialog(onDismissRequest = onDismiss, title = { Text(stringResource(R.string.search_filters)) }, text = {
        Column(Modifier.heightIn(max = 420.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Choice(R.string.search_type, draft.type.name, SearchType.entries.map { it.name to typeLabel(it) }) {
                draft = draft.copy(type = SearchType.valueOf(it), duration = SearchDuration.ANY, date = SearchDate.ANY)
            }
            Choice(R.string.search_sort, draft.sort.name, SearchSort.entries.map { it.name to sortLabel(it) }) { draft = draft.copy(sort = SearchSort.valueOf(it)) }
            if (draft.type in setOf(SearchType.ALL, SearchType.VIDEOS)) {
                Choice(R.string.search_duration, draft.duration.name, SearchDuration.entries.map { it.name to durationLabel(it) }) { draft = draft.copy(duration = SearchDuration.valueOf(it), type = SearchType.VIDEOS) }
                Choice(R.string.search_date, draft.date.name, SearchDate.entries.map { it.name to dateLabel(it) }) { draft = draft.copy(date = SearchDate.valueOf(it), type = SearchType.VIDEOS) }
            }
            TextButton(onClick = { draft = SearchFilters() }) { Text(stringResource(R.string.reset_filters)) }
        }
    }, confirmButton = { TextButton(onClick = { onApply(draft) }) { Text(stringResource(R.string.apply_filters)) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } })
}

@Composable
internal fun AutoLoadFooter(loading: Boolean, error: Boolean, onLoad: () -> Unit) {
    LaunchedEffect(Unit) { if (!loading && !error) onLoad() }
    Box(Modifier.fillMaxWidth().padding(16.dp), contentAlignment = Alignment.Center) {
        if (error) TextButton(onClick = onLoad) { Text(stringResource(R.string.video_retry)) }
        else CircularProgressIndicator(Modifier.size(24.dp), strokeWidth = 2.dp)
    }
}

@Composable
private fun PlaylistRow(item: PlaylistItem, onClick: () -> Unit) {
    Row(Modifier.fillMaxWidth().clickable(onClick = onClick).padding(vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Box(Modifier.width(132.dp).aspectRatio(16f / 9f).clip(RoundedCornerShape(6.dp)).background(MaterialTheme.colorScheme.surfaceVariant)) {
            AsyncImage(item.thumbnail, null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
            Column(Modifier.align(Alignment.CenterEnd).fillMaxHeight().width(44.dp).background(Color.Black.copy(alpha = .72f)),
                horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                Icon(Icons.AutoMirrored.Outlined.PlaylistPlay, stringResource(R.string.playlists), tint = Color.White)
                if (item.count > 0) Text(item.count.toString(), color = Color.White, style = MaterialTheme.typography.labelMedium)
            }
        }
        Column(Modifier.weight(1f)) {
            Text(item.title, style = MaterialTheme.typography.titleMedium, maxLines = 3, overflow = TextOverflow.Ellipsis)
            Text(item.uploader, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun ChannelRow(item: ChannelItem, onClick: () -> Unit) {
    Row(Modifier.fillMaxWidth().clickable(onClick = onClick).padding(vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
        Box(Modifier.width(132.dp), contentAlignment = Alignment.Center) {
            AsyncImage(item.avatar, null, Modifier.size(72.dp).clip(CircleShape).background(MaterialTheme.colorScheme.surfaceVariant), contentScale = ContentScale.Crop)
        }
        Column(Modifier.weight(1f)) {
            Text(item.title, style = MaterialTheme.typography.titleMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
            Text(stringResource(R.string.channels), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            if (item.subscribers > 0) Text(stringResource(R.string.channel_subscribers, java.text.NumberFormat.getIntegerInstance().format(item.subscribers)), style = MaterialTheme.typography.bodySmall)
        }
    }
}

@Composable
fun PlaylistScreen(playlistId: String, onBack: () -> Unit, onPlay: (String, String) -> Unit) {
    Scaffold(topBar = { TopAppBar(title = { Text(stringResource(R.string.playlists)) }, navigationIcon = { BackButton(onBack) }) }) { padding ->
        Box(Modifier.fillMaxSize().padding(padding).padding(horizontal = 16.dp)) { PlaylistQueue(playlistId, "", embedded = false, onPlay = onPlay) }
    }
}

@Composable
private fun PlaylistQueue(playlistId: String, currentVideoId: String, embedded: Boolean = true, onPlay: (String, String) -> Unit) {
    val catalog = (LocalContext.current.applicationContext as GardenApp).videoCatalog
    val scope = rememberCoroutineScope()
    var page by remember(playlistId) { mutableStateOf<VideoPlaylist?>(null) }
    var loading by remember(playlistId) { mutableStateOf(false) }
    var error by remember(playlistId) { mutableStateOf(false) }
    var errorMessage by remember(playlistId) { mutableIntStateOf(R.string.playlist_error) }
    var requestedPage by remember(playlistId) { mutableStateOf<CatalogCursor?>(null) }
    var request by remember(playlistId) { mutableIntStateOf(0) }
    fun load(next: CatalogCursor?) { if (!loading) { requestedPage = next; request++ } }
    LaunchedEffect(playlistId, request) {
        val next = requestedPage
        loading = true; error = false
        try {
            val result = catalog.playlist(playlistId, next)
            val items = ((if (next != null) page?.items.orEmpty() else emptyList()) + result.items).distinctBy { it.videoId }
            page = result.copy(title = result.title.ifBlank { page?.title.orEmpty() }, items = items,
                next = if (next != null && items.size == page?.items?.size) null else result.next)
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            error = true
            errorMessage = when {
                generateSequence<Throwable>(e) { it.cause }.take(10).any { it is java.net.UnknownHostException || it is java.net.ConnectException || it is java.net.SocketTimeoutException } -> R.string.video_offline
                e is org.schabi.newpipe.extractor.exceptions.ContentNotAvailableException -> R.string.playlist_unavailable
                else -> R.string.playlist_error
            }
        } finally { loading = false }
    }
    Column((if (embedded) Modifier.fillMaxWidth().height(420.dp) else Modifier.fillMaxSize()).testTag("video-playlist")) {
        Text(page?.title.orEmpty(), style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(vertical = 12.dp))
        if (loading && page == null) LinearProgressIndicator(Modifier.fillMaxWidth())
        if (error && page == null) VideoError(errorMessage) { load(null) }
        LazyColumn(Modifier.weight(1f)) {
            items(page?.items.orEmpty(), key = { it.videoId }, contentType = { "video" }) { item ->
                Column {
                    if (item.videoId == currentVideoId) Text(stringResource(R.string.video_now_playing), color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.labelSmall)
                    VideoRow(item, 0, { onPlay(it, playlistId) })
                }
            }
            if (page?.next != null) item(contentType = "pagination", key = "playlist-next-${page?.items?.size}") { AutoLoadFooter(loading, error) { load(page!!.next) } }
            if (!loading && page?.items?.isEmpty() == true) item(key = "empty", contentType = "empty") { EmptyState(R.string.no_results) }
        }
    }
}

@Composable
fun ChannelScreen(url: String, onBack: () -> Unit, onPlay: (String) -> Unit) {
    val catalog = (LocalContext.current.applicationContext as GardenApp).videoCatalog
    val scope = rememberCoroutineScope()
    var page by remember(url) { mutableStateOf<VideoChannel?>(null) }
    var loading by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf(false) }
    fun load(next: CatalogCursor?) {
        if (loading) return
        loading = true; error = false
        scope.launch {
            try {
                val result = catalog.channel(url, next)
                val videos = ((if (next == null) emptyList() else page?.videos.orEmpty()) + result.videos).distinctBy { it.videoId }
                page = result.copy(videos = videos, next = if (next != null && videos.size == page?.videos?.size) null else result.next)
            } catch (e: Exception) { if (e is CancellationException) throw e; error = true }
            finally { loading = false }
        }
    }
    LaunchedEffect(url) { load(null) }
    Scaffold(topBar = { TopAppBar(title = { Text(page?.item?.title ?: stringResource(R.string.channels), maxLines = 1, overflow = TextOverflow.Ellipsis) }, navigationIcon = { BackButton(onBack) }) }) { padding ->
        LazyColumn(contentPadding = pagePadding(padding), modifier = Modifier.fillMaxSize()) {
            page?.item?.let { channel -> item(key = "channel", contentType = "channel") { ChannelRow(channel) {} } }
            if (loading && page == null) item(key = "loading", contentType = "loading") { LinearProgressIndicator(Modifier.fillMaxWidth()) }
            if (error && page == null) item(key = "error", contentType = "error") { VideoError { load(null) } }
            items(page?.videos.orEmpty(), key = { it.videoId }, contentType = { "video" }) { VideoRow(it, 0, onPlay) }
            if (page?.next != null) item(contentType = "pagination", key = "channel-next-${page?.videos?.size}") { AutoLoadFooter(loading, error) { load(page!!.next) } }
            if (!loading && page?.videos?.isEmpty() == true) item(key = "empty", contentType = "empty") { EmptyState(R.string.no_results) }
        }
    }
}

@Composable
private fun VideoRow(item: VideoItem, position: Long, onPlay: (String) -> Unit, onRemove: (() -> Unit)? = null) {
    Row(Modifier.fillMaxWidth().clickable { onPlay(item.videoId) }.padding(vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        AsyncImage("https://i.ytimg.com/vi/${item.videoId}/mqdefault.jpg", null,
            Modifier.width(132.dp).aspectRatio(16f / 9f).background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(8.dp)), contentScale = ContentScale.Crop)
        Column(Modifier.weight(1f)) {
            Text(item.title, style = MaterialTheme.typography.titleMedium, maxLines = 3, overflow = TextOverflow.Ellipsis)
            Text(item.uploader, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            if (item.duration > 0) Text(videoTime(item.duration), style = MaterialTheme.typography.labelSmall)
            if (position > 0) Text(stringResource(R.string.video_resume_at, videoTime(position / 1000)), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
        }
        if (onRemove != null) IconButton(onClick = onRemove) { Icon(Icons.Outlined.DeleteOutline, stringResource(R.string.delete)) }
    }
}

fun videoTime(seconds: Long): String = if (seconds >= 3600) "%d:%02d:%02d".format(seconds / 3600, seconds / 60 % 60, seconds % 60)
    else "%d:%02d".format(seconds / 60, seconds % 60)

@Composable
private fun VideoError(message: Int = R.string.video_error, retry: () -> Unit) {
    Column(Modifier.padding(24.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(stringResource(message))
        OutlinedButton(onClick = retry) { Text(stringResource(R.string.video_retry)) }
    }
}

/** Fullscreen is a root destination: HomeScaffold and its bar/rail are absent. */
@Composable
fun VideoScreen(videoId: String, repo: GardenRepository, onBack: () -> Unit,
    context: VideoContext = VideoContext(), onPlay: (String, String) -> Unit = { _, _ -> }, onNote: (String) -> Unit = {}) {
    val app = LocalContext.current.applicationContext as GardenApp
    val activity = LocalActivity.current
    val model: VideoPlayerModel = viewModel(key = "video-$videoId-${context.positionMillis}", factory = object : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T = VideoPlayerModel(app, repo, app.videoCatalog, videoId, context.positionMillis) as T
    })
    val state by repo.state.collectAsStateWithLifecycle()
    var nativePlayerView by remember { mutableStateOf<PlayerView?>(null) }
    var controlsVisible by remember { mutableStateOf(true) }
    val snackbar = remember { SnackbarHostState() }
    val noteMessage = model.noteMessage?.let { stringResource(it) }
    LaunchedEffect(noteMessage) {
        if (noteMessage != null) { snackbar.showSnackbar(noteMessage); model.noteMessage = null }
    }
    val landscape = LocalConfiguration.current.orientation == Configuration.ORIENTATION_LANDSCAPE
    var requestedFullscreen by rememberSaveable { mutableStateOf(false) }
    var forceInline by rememberSaveable { mutableStateOf(false) }
    val originalOrientation = rememberSaveable { activity?.requestedOrientation ?: ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED }
    val fullscreen = requestedFullscreen || (landscape && !forceInline)
    val view = LocalView.current
    val owner = LocalLifecycleOwner.current
    fun changeFullscreen(value: Boolean) {
        requestedFullscreen = value
        forceInline = !value
        activity?.requestedOrientation = if (value) ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE else ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
    }
    LaunchedEffect(landscape) { if (!landscape) forceInline = false }
    BackHandler(fullscreen) { changeFullscreen(false) }
    LaunchedEffect(model) { model.open() }
    LaunchedEffect(model.player) {
        while (model.player != null) { delay(15000); model.persist() }
    }
    DisposableEffect(owner, model) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_STOP && activity?.isChangingConfigurations != true) model.pause()
            if (event == Lifecycle.Event.ON_START) model.foreground()
        }
        owner.lifecycle.addObserver(observer)
        onDispose { owner.lifecycle.removeObserver(observer); if (activity?.isChangingConfigurations != true) model.close() }
    }
    DisposableEffect(fullscreen, activity) {
        val window = activity?.window
        val controller = window?.let { WindowCompat.getInsetsController(it, view) }
        if (fullscreen) {
            controller?.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            controller?.hide(WindowInsetsCompat.Type.systemBars())
        } else controller?.show(WindowInsetsCompat.Type.systemBars())
        onDispose { controller?.show(WindowInsetsCompat.Type.systemBars()) }
    }
    DisposableEffect(activity) {
        onDispose { if (activity?.isChangingConfigurations != true) activity?.requestedOrientation = originalOrientation }
    }
    val noteDraft = rememberSaveable(videoId, saver = VideoNoteDraft.Saver) { VideoNoteDraft() }
    var discardNote by remember { mutableStateOf(false) }
    var pendingVideo by remember { mutableStateOf<Pair<String, String>?>(null) }
    fun playNext(id: String, playlist: String) {
        activity?.requestedOrientation = originalOrientation
        onPlay(id, playlist)
    }
    fun leave() {
        if (fullscreen) changeFullscreen(false)
        else if (noteDraft.text.isNotBlank()) discardNote = true
        else { model.close(); onBack() }
    }
    BackHandler(!fullscreen && noteDraft.text.isNotBlank()) { leave() }
    val lesson = model.lesson
    VideoPlayerLayout(fullscreen, lesson?.item?.title ?: stringResource(R.string.videos),
        onBack = ::leave,
        showFullscreenControls = controlsVisible,
        fullscreenActions = {
            IconButton(onClick = {
                nativePlayerView?.hideController()
                (nativePlayerView?.videoSurfaceView as? SurfaceView)?.let(model::captureFrame)
            }, enabled = model.hasFrame && !model.capturing, modifier = Modifier.testTag("fullscreen-capture")) {
                Icon(Icons.Outlined.PhotoCamera, stringResource(R.string.capture_frame), tint = Color.White)
            }
        },
        playerContent = {
            val player = model.player
            if (player != null) AndroidView(factory = { context -> PlayerView(context).apply {
                this.player = player
                nativePlayerView = this
                keepScreenOn = true
                setShowBuffering(PlayerView.SHOW_BUFFERING_WHEN_PLAYING)
                setShowSubtitleButton(true)
                setFullscreenButtonClickListener { changeFullscreen(it) }
                setControllerVisibilityListener(PlayerView.ControllerVisibilityListener { controlsVisible = it == android.view.View.VISIBLE })
                // A new PlayerView may start hidden after rotation without sending
                // a visibility change. Synchronize before waiting for callbacks.
                controlsVisible = isControllerFullyVisible
            } }, onReset = null, onRelease = { if (nativePlayerView === it) nativePlayerView = null; it.setControllerVisibilityListener(null as PlayerView.ControllerVisibilityListener?); it.player = null; it.keepScreenOn = false },
                update = { it.player = player; it.setFullscreenButtonState(fullscreen) }, modifier = Modifier.fillMaxSize().testTag("video-native-player"))
            if (player == null && model.loading) AsyncImage("https://i.ytimg.com/vi/$videoId/hqdefault.jpg", null,
                Modifier.fillMaxSize(), contentScale = ContentScale.Fit)
            if (model.loading) CircularProgressIndicator(Modifier.align(Alignment.Center))
            if (model.failed) Surface(Modifier.align(Alignment.Center), color = MaterialTheme.colorScheme.surface) { VideoError(model.errorMessage) { model.reload() } }
        },
        details = {
            if (lesson != null) {
                Text(lesson.item.uploader, color = MaterialTheme.colorScheme.onSurfaceVariant)
                if (model.saveFailed) Text(stringResource(R.string.error), color = MaterialTheme.colorScheme.error)
                val saved = state.videos.any { it.videoId == videoId }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilledTonalButton(onClick = { model.save() }) {
                        Icon(if (saved) Icons.Outlined.BookmarkAdded else Icons.Outlined.BookmarkAdd, null)
                        Spacer(Modifier.width(8.dp)); Text(stringResource(if (saved) R.string.video_is_saved else R.string.video_save))
                    }
                    OutlinedButton(onClick = { changeFullscreen(true) }) { Icon(Icons.Outlined.Fullscreen, stringResource(R.string.video_fullscreen)) }
                }
                Choice(R.string.video_quality, model.trackIndex.toString(), lesson.tracks.mapIndexed { index, track -> index.toString() to track.label }) { model.selectTrack(it.toInt()) }
                val videoNotes = remember(state.notes, videoId) { state.notes.filter { it.videoId == videoId } }
                VideoNotes(noteDraft, videoId, lesson.item.title, videoNotes, repo,
                    { model.player?.currentPosition ?: 0L }, model::seekTo, onNote,
                    onCapture = { (nativePlayerView?.videoSurfaceView as? SurfaceView)?.let(model::captureFrame) },
                    canCapture = model.hasFrame && !model.capturing, capturing = model.capturing)
                if (model.pendingAttachment != null) TextButton(onClick = { model.retryAttachment() }) { Text(stringResource(R.string.video_retry)) }
                if (context.playlistId.isNotBlank()) PlaylistQueue(context.playlistId, videoId) { id, playlist ->
                    if (noteDraft.text.isNotBlank()) { pendingVideo = id to playlist; discardNote = true }
                    else playNext(id, playlist)
                }
            }
        }, snackbar = { SnackbarHost(snackbar) })
    if (discardNote) AlertDialog(onDismissRequest = { discardNote = false; pendingVideo = null }, title = { Text(stringResource(R.string.discard)) },
        confirmButton = { TextButton(onClick = {
            noteDraft.text = ""; discardNote = false
            val next = pendingVideo; pendingVideo = null
            if (next != null) playNext(next.first, next.second) else { model.close(); onBack() }
        }) { Text(stringResource(R.string.close)) } },
        dismissButton = { TextButton(onClick = { discardNote = false; pendingVideo = null }) { Text(stringResource(R.string.keep_editing)) } })
}

class VideoNoteDraft {
    var editing by mutableStateOf(false)
    var text by mutableStateOf("")
    var capturedAt by mutableLongStateOf(0)
    companion object {
        val Saver = listSaver<VideoNoteDraft, Any>(save = { listOf(it.editing, it.text, it.capturedAt) },
            restore = { VideoNoteDraft().apply { editing = it[0] as Boolean; text = it[1] as String; capturedAt = it[2] as Long } })
    }
}

@Composable
internal fun VideoNotes(draft: VideoNoteDraft, videoId: String, title: String, notes: List<Note>, repo: GardenRepository,
    position: () -> Long, onSeek: (Long) -> Unit, onEdit: (String) -> Unit,
    onCapture: () -> Unit = {}, canCapture: Boolean = false, capturing: Boolean = false) {
    val scope = rememberCoroutineScope()
    val media = (LocalContext.current.applicationContext as GardenApp).media
    var saving by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf(false) }
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        if (!draft.editing) OutlinedButton(onClick = {
            if (draft.text.isBlank()) draft.capturedAt = position().coerceAtLeast(0)
            draft.editing = true
        }) { Icon(Icons.Outlined.EditNote, null); Spacer(Modifier.width(8.dp)); Text(stringResource(R.string.video_take_note)) }
        OutlinedButton(onClick = onCapture, enabled = canCapture && !saving) {
            if (capturing) CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
            else Icon(Icons.Outlined.PhotoCamera, null)
            Spacer(Modifier.width(8.dp)); Text(stringResource(R.string.capture_frame))
        }
    }
    if (draft.editing) {
        TextButton(onClick = { onSeek(draft.capturedAt) }) { Text(stringResource(R.string.video_note_source, videoTime(draft.capturedAt / 1000))) }
        OutlinedTextField(draft.text, { draft.text = it.take(50000) }, Modifier.fillMaxWidth().testTag("video-note-text"),
            label = { Text(stringResource(R.string.video_note_prompt)) }, minLines = 3, maxLines = 8, enabled = !saving)
        if (error) Text(stringResource(R.string.error), color = MaterialTheme.colorScheme.error)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = {
                saving = true; error = false
                val content = draft.text.trim()
                val note = Note(title = title, preview = content.take(180), videoId = videoId, videoPositionMillis = draft.capturedAt)
                scope.launch {
                    try {
                        repo.appendVideoNote(note, "<p>" + android.text.Html.escapeHtml(content).replace("\n", "<br>") + "</p>")
                        draft.editing = false; draft.text = ""
                    } catch (e: Exception) { if (e is CancellationException) throw e; error = true }
                    finally { saving = false }
                }
            }, enabled = draft.text.isNotBlank() && !saving) { Text(stringResource(R.string.save)) }
            TextButton(onClick = { draft.editing = false }, enabled = !saving) { Text(stringResource(R.string.cancel)) }
        }
    }
    notes.forEach { note -> key(note.id) {
        val entries by produceState<List<NotebookEntry>>(emptyList(), note) {
            value = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) { VideoNotebook.entries(note, repo.body(note.id)) }
        }
        Column(Modifier.fillMaxWidth().testTag("video-notebook"), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(stringResource(R.string.video_notebook), Modifier.weight(1f), style = MaterialTheme.typography.titleMedium)
                NoteExportAction(media) { com.jdinohaila.studentgarden.export.ExportNote.from(note, repo.body(note.id)) }
                TextButton(onClick = { onEdit(note.id) }) { Text(stringResource(R.string.edit)) }
            }
            entries.takeLast(4).forEach { entry -> key(entry.id) {
                NotebookEntryView(entry, media, onSeek)
            } }
        }
    } }
}

/** Testable layout contract, also used while loading or showing an extraction error. */
@Composable
fun VideoPlayerLayout(fullscreen: Boolean, title: String, onBack: () -> Unit,
    playerContent: @Composable BoxScope.() -> Unit, details: @Composable ColumnScope.() -> Unit = {},
    footer: @Composable () -> Unit = {}, snackbar: @Composable () -> Unit = {},
    showFullscreenControls: Boolean = true, fullscreenActions: @Composable RowScope.() -> Unit = {}) {
    if (fullscreen) {
        Box(Modifier.fillMaxSize().background(Color.Black).testTag("video-fullscreen")) {
            Box(Modifier.fillMaxSize().safeDrawingPadding(), content = playerContent)
            if (showFullscreenControls) IconButton(onClick = onBack, modifier = Modifier.align(Alignment.TopStart).safeDrawingPadding().background(Color.Black.copy(alpha = .45f), RoundedCornerShape(24.dp))) {
                Icon(Icons.AutoMirrored.Outlined.ArrowBack, stringResource(R.string.video_exit_fullscreen), tint = Color.White)
            }
            if (showFullscreenControls) Row(Modifier.align(Alignment.TopEnd).safeDrawingPadding(), content = fullscreenActions)
            Box(Modifier.align(Alignment.BottomCenter).safeDrawingPadding()) { snackbar() }
        }
    } else Scaffold(topBar = { TopAppBar(title = { Text(title, maxLines = 1, overflow = TextOverflow.Ellipsis) }, navigationIcon = { BackButton(onBack) }) }, bottomBar = footer, snackbarHost = snackbar) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).consumeWindowInsets(padding).imePadding()) {
            Box(Modifier.fillMaxWidth().aspectRatio(16f / 9f).background(Color.Black), content = playerContent)
            LazyColumn(Modifier.fillMaxWidth().weight(1f).testTag("video-details")) {
                item(key = "details", contentType = "details") {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp), content = details)
                }
            }
        }
    }
}
