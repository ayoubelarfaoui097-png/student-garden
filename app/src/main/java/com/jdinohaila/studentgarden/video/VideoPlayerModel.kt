// SPDX-License-Identifier: GPL-3.0-or-later
@file:androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
package com.jdinohaila.studentgarden.video

import android.app.Application
import android.net.Uri
import android.view.SurfaceView
import android.os.SystemClock
import android.util.Log
import com.jdinohaila.studentgarden.GardenApp
import com.jdinohaila.studentgarden.R
import com.jdinohaila.studentgarden.domain.Note
import androidx.compose.runtime.*
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.HttpDataSource
import androidx.media3.datasource.okhttp.OkHttpDataSource
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.DefaultRenderersFactory
import okhttp3.OkHttpClient
import java.util.concurrent.TimeUnit
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.exoplayer.source.MergingMediaSource
import com.jdinohaila.studentgarden.data.GardenRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay

/** Activity-owned so rotation retains the actual player and its current position. */
class VideoPlayerModel(
    app: Application, private val repo: GardenRepository,
    private val catalog: VideoCatalog, private val videoId: String,
    initialPositionMillis: Long? = null,
) : AndroidViewModel(app) {
    var lesson by mutableStateOf<VideoLesson?>(null)
        private set
    var player by mutableStateOf<ExoPlayer?>(null)
        private set
    var loading by mutableStateOf(false)
        private set
    var failed by mutableStateOf(false)
        private set
    var trackIndex by mutableIntStateOf(0)
        private set
    var saveFailed by mutableStateOf(false)
        private set
    var hasFrame by mutableStateOf(false)
        private set
    var capturing by mutableStateOf(false)
        private set
    var noteMessage by mutableStateOf<Int?>(null)
    private var pendingAttachments by mutableStateOf<List<Note>>(emptyList())
    val pendingAttachment: Note? get() = pendingAttachments.firstOrNull()
    var errorMessage by mutableIntStateOf(R.string.video_error)
        private set
    private var foreground = true
    private var closed = false
    private var playIntent = true
    private var refreshUsed = false
    private var fallbackUsed = false
    private var bufferingJob: Job? = null
    private var healthyPlaybackJob: Job? = null
    private var loadGeneration = 0
    private var startedAt = 0L
    private var extractionMillis = 0L
    private var preparedAt = 0L
    internal var firstFrameMillis: Long? = null
        private set
    private val sourcePosition = initialPositionMillis?.coerceAtLeast(0)
    private var position = sourcePosition
        ?: repo.state.value.videos.find { it.videoId == videoId }?.positionMillis ?: 0L
    private var loadJob: Job? = null

    fun open() {
        if (player != null || loading) return
        position = sourcePosition ?: position
        closed = false; playIntent = true
        refreshUsed = false; fallbackUsed = false
        startedAt = SystemClock.elapsedRealtime(); firstFrameMillis = null
        loadLesson(false)
    }
    fun reload() {
        if (!failed) playIntent = player?.playWhenReady ?: playIntent
        refreshUsed = false; fallbackUsed = false
        startedAt = SystemClock.elapsedRealtime(); firstFrameMillis = null
        loadLesson(true)
    }
    private fun loadLesson(refresh: Boolean) {
        val generation = ++loadGeneration
        loadJob?.cancel(); bufferingJob?.cancel(); healthyPlaybackJob?.cancel()
        position = player?.currentPosition ?: position
        player?.stop()
        loading = true; failed = false; hasFrame = false
        val selectedLabel = lesson?.tracks?.getOrNull(trackIndex)?.label
        loadJob = viewModelScope.launch {
            try {
                val start = SystemClock.elapsedRealtime()
                val loaded = if (refresh) catalog.refreshLesson(videoId) else catalog.lesson(videoId)
                if (closed || generation != loadGeneration) return@launch
                extractionMillis = SystemClock.elapsedRealtime() - start
                lesson = loaded
                trackIndex = loaded.tracks.indexOfFirst { it.label == selectedLabel }.takeIf { it >= 0 }
                    ?: VideoPlaybackPolicy.initialTrack(loaded.tracks)
                prepare()
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                failed = true; errorMessage = R.string.video_error
            } finally { if (generation == loadGeneration) loading = false }
        }
    }
    fun selectTrack(index: Int) {
        if (index !in lesson?.tracks.orEmpty().indices || index == trackIndex) return
        position = player?.currentPosition ?: position
        playIntent = player?.playWhenReady ?: playIntent
        trackIndex = index
        fallbackUsed = false
        prepare()
    }
    fun seekTo(positionMillis: Long) {
        position = positionMillis.coerceAtLeast(0)
        player?.seekTo(position)
    }
    private fun recover(error: PlaybackException) {
        if (closed) return
        position = player?.currentPosition ?: position
        playIntent = player?.playWhenReady ?: playIntent
        val causes = generateSequence<Throwable>(error) { it.cause }.take(12).toList()
        val expired = causes.filterIsInstance<HttpDataSource.InvalidResponseCodeException>().any { it.responseCode in setOf(401, 403, 410) }
        if (expired && !refreshUsed) { refreshUsed = true; loadLesson(true); return }
        if (fallback()) return
        bufferingJob?.cancel()
        player?.pause()
        failed = true
        errorMessage = if (causes.any { it is java.net.UnknownHostException || it is java.net.ConnectException })
            R.string.video_offline else R.string.video_error
    }
    private fun fallback(): Boolean {
        if (fallbackUsed || closed) return false
        val tracks = lesson?.tracks ?: return false
        val next = VideoPlaybackPolicy.fallbackTrack(tracks, trackIndex) ?: return false
        fallbackUsed = true
        position = player?.currentPosition ?: position
        playIntent = player?.playWhenReady ?: playIntent
        trackIndex = next
        prepare()
        noteMessage = R.string.video_smoother_stream
        return true
    }
    private fun watchBuffering() {
        bufferingJob?.cancel()
        bufferingJob = viewModelScope.launch {
            val firstFrame = !hasFrame
            delay(if (firstFrame) 6_000 else 12_000)
            if (closed || !foreground || player?.playbackState != Player.STATE_BUFFERING || player?.playWhenReady != true) return@launch
            if (fallback()) return@launch
            // Never leave a permanently frozen player with an endless spinner.
            delay(if (firstFrame) 24_000 else 18_000)
            if (!closed && player?.playbackState == Player.STATE_BUFFERING) {
                player?.pause(); failed = true; errorMessage = R.string.video_connection_slow
            }
        }
    }
    private fun prepare() {
        val lesson = lesson ?: return
        val track = lesson.tracks.getOrNull(trackIndex) ?: run { failed = true; return }
        bufferingJob?.cancel(); healthyPlaybackJob?.cancel()
        failed = false; hasFrame = false
        val factory = DefaultMediaSourceFactory(DefaultDataSource.Factory(getApplication(), VideoPlaybackPolicy.http))
        val captions = lesson.captions.map {
            MediaItem.SubtitleConfiguration.Builder(Uri.parse(it.url)).setMimeType(it.mimeType)
                .setLanguage(it.language).setLabel(it.label).build()
        }
        val video = factory.createMediaSource(MediaItem.Builder().setUri(track.url)
            .setMimeType(if (track.hls) MimeTypes.APPLICATION_M3U8 else null)
            .setSubtitleConfigurations(captions).build())
        val source = track.audioUrl?.let { MergingMediaSource(video, factory.createMediaSource(MediaItem.fromUri(it))) } ?: video
        val current = player ?: ExoPlayer.Builder(getApplication<Application>(),
            DefaultRenderersFactory(getApplication()).setEnableDecoderFallback(true))
            .setLoadControl(VideoPlaybackPolicy.loadControl()).build().apply {
                setAudioAttributes(AudioAttributes.Builder().setUsage(C.USAGE_MEDIA).setContentType(C.AUDIO_CONTENT_TYPE_MOVIE).build(), true)
                setHandleAudioBecomingNoisy(true)
                addListener(object : Player.Listener {
                    override fun onPlayerError(error: PlaybackException) { recover(error) }
                    override fun onRenderedFirstFrame() {
                        hasFrame = true
                        Log.i("VideoPlayback", "render_ready_ms=${SystemClock.elapsedRealtime() - preparedAt}")
                        if (firstFrameMillis == null) {
                            firstFrameMillis = SystemClock.elapsedRealtime() - startedAt
                            // Local numeric diagnostics only: no video titles, IDs or URLs.
                            Log.i("VideoPlayback", "first_frame_ms=$firstFrameMillis extraction_ms=$extractionMillis")
                        }
                    }
                    override fun onPlayWhenReadyChanged(playWhenReady: Boolean, reason: Int) {
                        if (playWhenReady && player?.playbackState == Player.STATE_BUFFERING) watchBuffering()
                        else if (!playWhenReady) bufferingJob?.cancel()
                    }
                    override fun onPlaybackStateChanged(state: Int) {
                        if (state == Player.STATE_BUFFERING) watchBuffering() else bufferingJob?.cancel()
                        healthyPlaybackJob?.cancel()
                        if (state == Player.STATE_READY) healthyPlaybackJob = viewModelScope.launch {
                            delay(30_000)
                            if (!closed && player?.playbackState == Player.STATE_READY && player?.playWhenReady == true) {
                                refreshUsed = false; fallbackUsed = false
                            }
                        }
                    }
                })
            }.also { player = it }
        current.trackSelectionParameters = current.trackSelectionParameters.buildUpon()
            .setPreferredVideoMimeTypes(MimeTypes.VIDEO_H264)
            .setPreferredAudioMimeTypes(MimeTypes.AUDIO_AAC)
            .setMaxVideoSize(if (track.hls) 1280 else Int.MAX_VALUE, if (track.hls) 720 else Int.MAX_VALUE)
            .setMaxVideoFrameRate(if (track.hls) 30 else Int.MAX_VALUE)
            .setMaxVideoBitrate(if (track.hls) 3_000_000 else Int.MAX_VALUE).build()
        current.setMediaSource(source)
        current.seekTo(position)
        current.repeatMode = Player.REPEAT_MODE_OFF
        current.playWhenReady = foreground && playIntent
        preparedAt = SystemClock.elapsedRealtime()
        current.prepare()
    }
    suspend fun persist() {
        val current = player ?: return
        position = if (current.playbackState == Player.STATE_ENDED) 0 else current.currentPosition.coerceAtLeast(0)
        storePosition()
    }
    private suspend fun storePosition() {
        try {
            if (repo.state.value.videos.any { it.videoId == videoId }) repo.videoPosition(videoId, position)
        } catch (e: Exception) { if (e is CancellationException) throw e; saveFailed = true }
    }
    fun foreground() { foreground = true }
    fun pause() {
        foreground = false
        playIntent = false
        player?.pause()
        viewModelScope.launch { persist() }
    }
    fun save() = viewModelScope.launch {
        val item = lesson?.item ?: return@launch
        try {
            repo.saveVideo(item.videoId, item.title, item.uploader, item.duration, player?.currentPosition ?: position)
            saveFailed = false
        } catch (e: Exception) { if (e is CancellationException) throw e; saveFailed = true }
    }
    fun addAudioNote(audioId: String, at: Long) = storeAttachment(Note(
        title = lesson?.item?.title ?: getApplication<Application>().getString(R.string.notes),
        media = listOf(audioId), videoId = videoId, videoPositionMillis = at.coerceAtLeast(0)))

    fun retryAttachment() { pendingAttachments.forEach(::storeAttachment) }
    private fun storeAttachment(note: Note) = viewModelScope.launch {
        if (pendingAttachments.none { it.id == note.id }) pendingAttachments = pendingAttachments + note
        try {
            repo.appendVideoNote(note)
            pendingAttachments = pendingAttachments.filterNot { it.id == note.id }; noteMessage = R.string.video_note_saved
        } catch (e: Exception) { if (e is CancellationException) throw e; noteMessage = R.string.error }
    }
    fun captureFrame(surface: SurfaceView) {
        if (capturing) return
        val app = getApplication<GardenApp>()
        val at = player?.currentPosition?.coerceAtLeast(0) ?: position
        capturing = true
        viewModelScope.launch {
            try {
                val bitmap = app.frameCapture.capture(surface)
                val imageId = try { app.media.saveFrame(bitmap) } finally { bitmap.recycle() }
                storeAttachment(Note(title = lesson?.item?.title ?: app.getString(R.string.notes), images = listOf(imageId), videoId = videoId, videoPositionMillis = at))
            } catch (e: Exception) { if (e is CancellationException) throw e; noteMessage = R.string.frame_capture_error }
            finally { capturing = false }
        }
    }
    fun close() {
        closed = true; loadGeneration++
        loadJob?.cancel(); bufferingJob?.cancel(); healthyPlaybackJob?.cancel(); loading = false
        val current = player
        position = if (current?.playbackState == Player.STATE_ENDED) 0 else current?.currentPosition ?: position
        current?.release(); player = null
        viewModelScope.launch { storePosition() }
    }
    override fun onCleared() { closed = true; loadGeneration++; loadJob?.cancel(); bufferingJob?.cancel(); healthyPlaybackJob?.cancel(); player?.release() }
}

/** Adaptive startup balances bandwidth and decoding cost. Auto is bounded to
 * 720p/30fps; direct streams and higher manual qualities remain available. */
internal object VideoPlaybackPolicy {
    private val client = OkHttpClient.Builder().connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS).build()
    val http = OkHttpDataSource.Factory(client).setUserAgent(YoutubeVideoCatalog.USER_AGENT)
        .setDefaultRequestProperties(mapOf("Referer" to "https://www.youtube.com/", "Origin" to "https://www.youtube.com"))
    fun loadControl() = DefaultLoadControl.Builder().setBufferDurationsMs(15_000, 45_000, 750, 2_000)
        .setTargetBufferBytes(32 * 1024 * 1024).setPrioritizeTimeOverSizeThresholds(true)
        .setBackBuffer(10_000, true).build()
    private fun height(track: VideoTrack) = track.label.takeWhile(Char::isDigit).toIntOrNull() ?: 0
    fun initialTrack(tracks: List<VideoTrack>): Int = tracks.indexOfFirst { it.hls }.takeIf { it >= 0 }
        ?: tracks.indices.filter { height(tracks[it]) in 1..720 }.maxByOrNull { height(tracks[it]) }
        ?: tracks.indices.filter { height(tracks[it]) > 0 }.minByOrNull { height(tracks[it]) } ?: 0
    fun fallbackTrack(tracks: List<VideoTrack>, current: Int): Int? {
        val selected = tracks.getOrNull(current) ?: return null
        val limit = if (selected.hls) 480 else height(selected) - 1
        return tracks.indices.filter { it != current && !tracks[it].hls && height(tracks[it]) in 1..limit }
            .maxByOrNull { height(tracks[it]) }
            ?: if (selected.hls) tracks.indices.filter { !tracks[it].hls && height(tracks[it]) in 1..720 }.minByOrNull { height(tracks[it]) }
            else tracks.indices.firstOrNull { it != current && tracks[it].hls }
    }
}
