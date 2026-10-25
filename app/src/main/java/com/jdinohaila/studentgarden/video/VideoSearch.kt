// SPDX-License-Identifier: GPL-3.0-or-later
package com.jdinohaila.studentgarden.video

import java.net.URI
import java.net.URLEncoder
import java.util.Base64
import kotlinx.serialization.Serializable

/** Values follow YouTube's search protocol; relevance keeps the server's original order. */
enum class SearchType { ALL, VIDEOS, PLAYLISTS, CHANNELS }
enum class SearchDuration(val code: Int) { ANY(0), SHORT(1), MEDIUM(3), LONG(2) }
enum class SearchDate(val code: Int) { ANY(0), HOUR(1), TODAY(2), WEEK(3), MONTH(4), YEAR(5) }
enum class SearchSort(val code: Int) { RELEVANCE(0), DATE(2), VIEWS(3), RATING(1) }
data class SearchFilters(
    val type: SearchType = SearchType.ALL,
    val duration: SearchDuration = SearchDuration.ANY,
    val date: SearchDate = SearchDate.ANY,
    val sort: SearchSort = SearchSort.RELEVANCE,
) {
    fun params(): String {
        val filters = buildList<Int> {
            if (date != SearchDate.ANY) { add(8); add(date.code) }
            when (type) { SearchType.VIDEOS -> { add(16); add(1) }; SearchType.PLAYLISTS -> { add(16); add(3) }; SearchType.CHANNELS -> { add(16); add(2) }; else -> Unit }
            if (duration != SearchDuration.ANY) { add(24); add(duration.code) }
        }
        val bytes = buildList<Int> {
            if (sort != SearchSort.RELEVANCE) { add(8); add(sort.code) }
            if (filters.isNotEmpty()) { add(18); add(filters.size); addAll(filters) }
            // Request only search results, matching NewPipe's default parameter.
            addAll(listOf(240, 1, 1))
        }.map { it.toByte() }.toByteArray()
        return URLEncoder.encode(Base64.getUrlEncoder().encodeToString(bytes), "UTF-8")
    }
}

interface CatalogCursor
sealed interface SearchHit { val key: String }
data class VideoHit(val item: VideoItem) : SearchHit { override val key get() = "v:${item.videoId}" }
data class PlaylistItem(val playlistId: String, val title: String, val uploader: String, val count: Long, val thumbnail: String = "")
data class PlaylistHit(val item: PlaylistItem) : SearchHit { override val key get() = "p:${item.playlistId}" }
data class ChannelItem(val url: String, val title: String, val avatar: String = "", val subscribers: Long = -1)
data class ChannelHit(val item: ChannelItem) : SearchHit { override val key get() = "c:${item.url}" }
data class VideoChannel(val item: ChannelItem, val videos: List<VideoItem>, val next: CatalogCursor? = null)

/** Keep video relevance order and insert a small number of relevant collections/creators. */
fun mixedSearch(videos: List<SearchHit>, extras: List<SearchHit>): List<SearchHit> {
    val additions = extras.filter { it !is VideoHit }.distinctBy { it.key }.take(2)
    val result = videos.filterIsInstance<VideoHit>().toMutableList<SearchHit>()
    additions.forEachIndexed { index, hit -> result.add(minOf(4 + index * 5, result.size), hit) }
    return result.distinctBy { it.key }
}

data class VideoSearchPage(val items: List<SearchHit>, val next: CatalogCursor? = null, val suggestion: String = "")
data class VideoPlaylist(val title: String, val items: List<VideoItem>, val next: CatalogCursor? = null)

@Serializable
data class VideoContext(val playlistId: String = "", val positionMillis: Long? = null)

object PlaylistLinks {
    private val pattern = Regex("[A-Za-z0-9_-]{12,100}")
    fun id(input: String): String? = runCatching {
        val text = input.trim()
        if (pattern.matches(text) && text.length >= 24 &&
            listOf("PL", "UU", "LL", "FL", "RD", "OLAK5uy_").any { text.startsWith(it) }) return@runCatching text
        val uri = URI(if (text.startsWith("www.") || text.startsWith("youtu")) "https://$text" else text)
        if (uri.scheme !in setOf("https", "http") || uri.userInfo != null || uri.port != -1 ||
            uri.host?.lowercase() !in setOf("youtube.com", "www.youtube.com", "m.youtube.com", "music.youtube.com", "youtu.be")) return@runCatching null
        uri.rawQuery.orEmpty().split('&').firstOrNull { it.startsWith("list=") }?.substringAfter("list=")?.takeIf { pattern.matches(it) }
    }.getOrNull()
    fun url(id: String): String { require(pattern.matches(id)); return "https://www.youtube.com/playlist?list=$id" }
}

object ChannelLinks {
    fun url(input: String): String? = runCatching {
        val uri = URI(input.trim())
        if (uri.scheme != "https" || uri.userInfo != null || uri.port != -1 ||
            uri.host?.lowercase() !in setOf("www.youtube.com", "youtube.com", "m.youtube.com")) return@runCatching null
        val parts = uri.path.orEmpty().trim('/').split('/')
        val valid = (parts.size == 1 && parts[0].startsWith("@") && parts[0].length > 1) ||
            (parts.size == 2 && parts[0] in setOf("channel", "c", "user") && parts[1].isNotBlank())
        if (!valid || parts.any { it == "." || it == ".." }) null else "https://www.youtube.com/" + uri.rawPath.trim('/')
    }.getOrNull()
}
