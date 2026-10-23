// SPDX-License-Identifier: GPL-3.0-or-later
package com.jdinohaila.studentgarden.video

import java.net.URI
import java.io.IOException
import org.schabi.newpipe.extractor.InfoItem
import org.schabi.newpipe.extractor.Page
import org.schabi.newpipe.extractor.linkhandler.ListLinkHandler
import org.schabi.newpipe.extractor.channel.ChannelInfo
import org.schabi.newpipe.extractor.channel.ChannelInfoItem
import org.schabi.newpipe.extractor.channel.tabs.ChannelTabInfo
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import org.schabi.newpipe.extractor.playlist.PlaylistInfo
import org.schabi.newpipe.extractor.playlist.PlaylistInfoItem
import kotlinx.serialization.json.*
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.RequestBody.Companion.toRequestBody
import org.schabi.newpipe.extractor.NewPipe
import org.schabi.newpipe.extractor.ServiceList
import org.schabi.newpipe.extractor.downloader.Downloader
import org.schabi.newpipe.extractor.downloader.Request
import org.schabi.newpipe.extractor.downloader.Response
import org.schabi.newpipe.extractor.exceptions.ReCaptchaException
import org.schabi.newpipe.extractor.stream.DeliveryMethod
import org.schabi.newpipe.extractor.stream.StreamInfo
import org.schabi.newpipe.extractor.stream.StreamInfoItem
import org.schabi.newpipe.extractor.stream.StreamType

/** Only public YouTube video links are accepted; arbitrary URLs never reach the player. */
object VideoLinks {
    private val idPattern = Regex("[A-Za-z0-9_-]{11}")
    fun id(input: String): String? = runCatching {
        val text = input.trim()
        if (idPattern.matches(text)) return@runCatching text
        val uri = URI(if (text.startsWith("www.") || text.startsWith("youtu")) "https://$text" else text)
        if (uri.scheme !in setOf("https", "http") || uri.userInfo != null || uri.port != -1) return@runCatching null
        val host = uri.host?.lowercase() ?: return@runCatching null
        val parts = uri.path.orEmpty().trim('/').split('/')
        val id = when (host) {
            "youtu.be" -> parts.singleOrNull()
            "youtube.com", "www.youtube.com", "m.youtube.com", "music.youtube.com" -> when {
                parts.firstOrNull() == "watch" -> uri.rawQuery.orEmpty().split('&').firstOrNull { it.startsWith("v=") }?.substringAfter("v=")
                parts.size == 2 && parts[0] in setOf("shorts", "embed", "live") -> parts[1]
                else -> null
            }
            else -> null
        }
        id?.takeIf { idPattern.matches(it) }
    }.getOrNull()
    fun url(id: String): String {
        require(idPattern.matches(id))
        return "https://www.youtube.com/watch?v=$id"
    }
}

data class VideoItem(val videoId: String, val title: String, val uploader: String, val duration: Long)
data class VideoTrack(val label: String, val url: String, val audioUrl: String? = null, val hls: Boolean = false)
data class VideoCaption(val url: String, val mimeType: String, val language: String, val label: String)
data class VideoLesson(val item: VideoItem, val tracks: List<VideoTrack>, val captions: List<VideoCaption> = emptyList())

interface VideoCatalog {
    suspend fun search(query: String): List<VideoItem>
    suspend fun searchPage(query: String, filters: SearchFilters, next: CatalogCursor? = null): VideoSearchPage =
        VideoSearchPage(search(query).map(::VideoHit))
    fun searchUpdates(query: String, filters: SearchFilters): Flow<VideoSearchPage> = flow { emit(searchPage(query, filters)) }
    suspend fun channel(url: String, next: CatalogCursor? = null): VideoChannel = throw IOException("Channel unavailable")
    suspend fun playlist(playlistId: String, next: CatalogCursor? = null): VideoPlaylist = throw IOException("Playlist unavailable")
    suspend fun lesson(videoId: String): VideoLesson
    suspend fun refreshLesson(videoId: String): VideoLesson = lesson(videoId)
}

/** No accounts, API keys, recommendation feed, ads SDK or hosted proxy. */
class YoutubeVideoCatalog : VideoCatalog {
    companion object {
        const val USER_AGENT = "Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/130.0.0.0 Safari/537.36"
        private val extraction = ExtractionGate()
        private val lessons = LessonCache()
        private var initialized = false
        // NewPipe 0.26.5 exposes only content-type filters. All extraction is serialized by
        // extraction; override only the initial search body to pass YouTube's advanced filters.
        private var searchParams: String? = null
    }
    private fun initialize() {
        if (!initialized) {
            NewPipe.init(object : Downloader() {
                private val client = OkHttpClient.Builder().connectTimeout(10, TimeUnit.SECONDS)
                    .readTimeout(20, TimeUnit.SECONDS).callTimeout(30, TimeUnit.SECONDS).build()
                override fun execute(request: Request): Response {
                    require(URI(request.url()).scheme == "https")
                    val builder = okhttp3.Request.Builder().url(request.url()).header("User-Agent", USER_AGENT)
                    request.headers().forEach { (key, values) ->
                        builder.removeHeader(key)
                        values.forEach { builder.addHeader(key, it) }
                    }
                    val data = request.dataToSend()?.let { bytes ->
                        val params = searchParams
                        if (params != null && URI(request.url()).path.endsWith("/search")) {
                            val json = Json.parseToJsonElement(bytes.toString(Charsets.UTF_8)).jsonObject
                            if ("query" in json) JsonObject(json + ("params" to JsonPrimitive(params))).toString().toByteArray() else bytes
                        } else bytes
                    }
                    val body = data?.toRequestBody()
                        ?: if (request.httpMethod() in setOf("POST", "PUT", "PATCH")) ByteArray(0).toRequestBody() else null
                    return extraction.execute(client.newCall(builder.method(request.httpMethod(), body).build())) { response ->
                        if (response.code == 429) throw ReCaptchaException("YouTube temporarily blocked this request", request.url())
                        val source = response.body?.source()
                        source?.request(8_000_001)
                        if ((source?.buffer?.size ?: 0) > 8_000_000) throw IOException("Response too large")
                        Response(response.code, response.message, response.headers.toMultimap(), source?.readUtf8().orEmpty(), response.request.url.toString())
                    }
                }
            })
            initialized = true
        }
    }
    private data class Cursor(val page: Page) : CatalogCursor
    private data class ChannelCursor(val page: Page, val tab: ListLinkHandler, val item: ChannelItem) : CatalogCursor
    private fun thumbnail(item: InfoItem) = item.thumbnails.lastOrNull()?.url.orEmpty().takeIf { it.startsWith("https://") }.orEmpty()
    private fun cursor(page: Page?) = page?.takeIf { Page.isValid(it) }?.let(::Cursor)
    private fun video(item: StreamInfoItem): VideoItem? =
        if (item.streamType != StreamType.VIDEO_STREAM || item.isShortFormContent) null
        else VideoLinks.id(item.url)?.let { VideoItem(it, item.name, item.uploaderName.orEmpty(), item.duration.coerceAtLeast(0)) }
    private fun hits(items: List<InfoItem>): List<SearchHit> = items.mapNotNull {
        when (it) {
            is StreamInfoItem -> video(it)?.let(::VideoHit)
            is PlaylistInfoItem -> PlaylistLinks.id(it.url)?.let { id -> PlaylistHit(PlaylistItem(id, it.name, it.uploaderName.orEmpty(), it.streamCount.coerceAtLeast(0), thumbnail(it))) }
            is ChannelInfoItem -> ChannelLinks.url(it.url)?.let { url -> ChannelHit(ChannelItem(url, it.name, thumbnail(it), it.subscriberCount)) }
            else -> null
        }
    }.distinctBy { it.key }
    override suspend fun search(query: String): List<VideoItem> =
        searchPage(query, SearchFilters(type = SearchType.VIDEOS)).items.filterIsInstance<VideoHit>().map { it.item }

    override fun searchUpdates(query: String, filters: SearchFilters): Flow<VideoSearchPage> = flow {
        if (filters.type != SearchType.ALL) { emit(searchPage(query, filters)); return@flow }
        // Show the useful video results immediately; collections and creators follow.
        val primary = searchPage(query, filters.copy(type = SearchType.VIDEOS))
        emit(primary)
        val extras = mutableListOf<SearchHit>()
        val creator = primary.items.filterIsInstance<VideoHit>().firstOrNull()?.item?.uploader.orEmpty()
        for (type in listOf(SearchType.PLAYLISTS, SearchType.CHANNELS)) {
            try {
                val candidates = searchPage(if (type == SearchType.CHANNELS && creator.isNotBlank()) creator.take(300) else query, filters.copy(type = type)).items
                val extra = if (type == SearchType.CHANNELS) candidates.filterIsInstance<ChannelHit>().firstOrNull { it.item.title.equals(creator, ignoreCase = true) } ?: candidates.firstOrNull() else candidates.firstOrNull()
                if (extra != null) extras.add(extra)
                emit(primary.copy(items = mixedSearch(primary.items, extras)))
            } catch (e: Exception) { if (e is CancellationException) throw e }
        }
    }

    override suspend fun searchPage(query: String, filters: SearchFilters, next: CatalogCursor?): VideoSearchPage = withContext(Dispatchers.IO) {
        require(query.isNotBlank() && query.length <= 300)
        extraction.run {
            initialize()
            fun fetch(type: SearchType, page: Page? = null, searchQuery: String = query): VideoSearchPage {
                val selected = filters.copy(type = type)
                val extractor = ServiceList.YouTube.getSearchExtractor(searchQuery, listOf(type.name.lowercase()), "")
                searchParams = selected.params()
                try {
                    if (page == null) {
                        extractor.fetchPage()
                        val result = extractor.initialPage
                        return VideoSearchPage(hits(result.items), cursor(result.nextPage), runCatching { extractor.searchSuggestion }.getOrDefault(""))
                    }
                    val result = extractor.getPage(page)
                    return VideoSearchPage(hits(result.items), cursor(result.nextPage))
                } finally { searchParams = null }
            }
            val primary = fetch(if (filters.type == SearchType.ALL) SearchType.VIDEOS else filters.type, (next as? Cursor)?.page)
            if (filters.type != SearchType.ALL || next != null) primary else {
                val extras = listOf(SearchType.PLAYLISTS, SearchType.CHANNELS).mapNotNull { type ->
                    try {
                        val creator = primary.items.filterIsInstance<VideoHit>().firstOrNull()?.item?.uploader.orEmpty()
                        val candidates = fetch(type, searchQuery = if (type == SearchType.CHANNELS && creator.isNotBlank()) creator.take(300) else query).items
                        if (type == SearchType.CHANNELS) candidates.filterIsInstance<ChannelHit>().firstOrNull { it.item.title.equals(creator, ignoreCase = true) }
                            ?: candidates.firstOrNull()
                        else candidates.firstOrNull()
                    }
                    catch (e: Exception) { if (e is CancellationException) throw e; null }
                }
                primary.copy(items = mixedSearch(primary.items, extras))
            }
        }
    }
    override suspend fun channel(url: String, next: CatalogCursor?): VideoChannel = withContext(Dispatchers.IO) {
        extraction.run {
            initialize()
            require(ChannelLinks.url(url) != null)
            if (next is ChannelCursor) {
                val page = ChannelTabInfo.getMoreItems(ServiceList.YouTube, next.tab, next.page)
                VideoChannel(next.item, page.items.filterIsInstance<StreamInfoItem>().mapNotNull(::video),
                    page.nextPage?.takeIf { Page.isValid(it) }?.let { ChannelCursor(it, next.tab, next.item) })
            } else {
                val info = ChannelInfo.getInfo(ServiceList.YouTube, url)
                val item = ChannelItem(ChannelLinks.url(info.url) ?: url, info.name, info.avatars.lastOrNull()?.url.orEmpty(), info.subscriberCount)
                val tab = info.tabs.firstOrNull { "videos" in it.contentFilters } ?: info.tabs.firstOrNull()
                if (tab == null) VideoChannel(item, emptyList()) else {
                    val page = ChannelTabInfo.getInfo(ServiceList.YouTube, tab)
                    VideoChannel(item, page.relatedItems.filterIsInstance<StreamInfoItem>().mapNotNull(::video),
                        page.nextPage?.takeIf { Page.isValid(it) }?.let { ChannelCursor(it, tab, item) })
                }
            }
        }
    }
    override suspend fun playlist(playlistId: String, next: CatalogCursor?): VideoPlaylist = withContext(Dispatchers.IO) {
        extraction.run {
            initialize()
            val url = PlaylistLinks.url(playlistId)
            if (next == null) {
                val info = PlaylistInfo.getInfo(ServiceList.YouTube, url)
                VideoPlaylist(info.name, info.relatedItems.mapNotNull(::video).distinctBy { it.videoId }, cursor(info.nextPage))
            } else {
                val page = PlaylistInfo.getMoreItems(ServiceList.YouTube, url, (next as Cursor).page)
                VideoPlaylist("", page.items.mapNotNull(::video).distinctBy { it.videoId }, cursor(page.nextPage))
            }
        }
    }
    override suspend fun lesson(videoId: String): VideoLesson = withContext(Dispatchers.IO) {
        lessons.get(videoId)?.let { return@withContext it }
        extraction.run {
            lessons.get(videoId)?.let { return@run it }
            initialize()
            val info = StreamInfo.getInfo(ServiceList.YouTube, VideoLinks.url(videoId))
            require(info.streamType == StreamType.VIDEO_STREAM && info.ageLimit == 0)
            val audio = info.audioStreams.filter { it.isUrl && it.deliveryMethod == DeliveryMethod.PROGRESSIVE_HTTP && it.content.startsWith("https://") }
                .sortedWith(compareBy({ if (it.format?.mimeType == "audio/mp4") 0 else 1 },
                    { if (it.averageBitrate in 1..160) 0 else 1 }, { kotlin.math.abs(it.averageBitrate - 128) })).firstOrNull()
            val progressive = (info.videoStreams + info.videoOnlyStreams)
                .filter { it.isUrl && it.deliveryMethod == DeliveryMethod.PROGRESSIVE_HTTP && it.content.startsWith("https://") && (!it.isVideoOnly || audio != null) }
                .sortedWith(compareByDescending<org.schabi.newpipe.extractor.stream.VideoStream> { it.height }
                    .thenBy { if (it.codec.orEmpty().startsWith("avc")) 0 else 1 }
                    .thenBy { if (it.format?.mimeType == "video/mp4") 0 else 1 }
                    .thenBy { if (it.isVideoOnly) 1 else 0 }.thenBy { it.fps })
                .distinctBy { it.resolution }
                .map { VideoTrack(it.resolution, it.content, if (it.isVideoOnly) audio?.content else null) }
            val tracks = buildList {
                if (info.hlsUrl.startsWith("https://")) add(VideoTrack("Auto", info.hlsUrl, hls = true))
                addAll(progressive)
            }
            check(tracks.isNotEmpty())
            VideoLesson(VideoItem(videoId, info.name, info.uploaderName.orEmpty(), info.duration.coerceAtLeast(0)), tracks,
                info.subtitles.filter { it.isUrl && it.content.startsWith("https://") }.map {
                    VideoCaption(it.content, it.format?.mimeType ?: "text/vtt", it.languageTag, it.displayLanguageName)
                }).also { lessons.put(videoId, it) }
        }
    }
    override suspend fun refreshLesson(videoId: String): VideoLesson {
        lessons.invalidate(videoId)
        return lesson(videoId)
    }
}
