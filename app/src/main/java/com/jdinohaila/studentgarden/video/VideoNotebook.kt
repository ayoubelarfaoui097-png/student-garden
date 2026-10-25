// SPDX-License-Identifier: GPL-3.0-or-later
package com.jdinohaila.studentgarden.video

import com.jdinohaila.studentgarden.domain.Note
import com.jdinohaila.studentgarden.domain.VideoMoment
import java.util.UUID

data class NotebookEntry(
    val id: String,
    val positionMillis: Long,
    val html: String = "",
    val images: List<String> = emptyList(),
    val audio: List<String> = emptyList(),
)

/** A projection of the existing portable body and timestamp references. No storage
 * migration or copying media is required; legacy rich HTML remains byte-for-byte. */
object VideoNotebook {
    private val marker = Regex("""<p\b[^>]*>\s*<a\b[^>]*href=["']https?://(?:www\.)?youtube\.com/watch\?v=[A-Za-z0-9_-]{11}(?:&amp;|&)t=(\d+)s["'][^>]*>.*?</a>\s*</p>""",
        setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL))

    fun entries(note: Note, body: String): List<NotebookEntry> {
        val moments = note.videoMoments.ifEmpty {
            listOf(VideoMoment(note.id, note.videoPositionMillis, note.media, note.images))
        }
        val result = moments.map { NotebookEntry(it.id, it.positionMillis, images = it.images, audio = it.media) }.toMutableList()
        val matches = marker.findAll(body).toList()
        val fragments = buildList {
            if (matches.isEmpty()) { if (body.isNotBlank()) add(note.videoPositionMillis to body) }
            else {
                val prefix = body.substring(0, matches.first().range.first)
                if (prefix.isNotBlank()) add(note.videoPositionMillis to prefix)
                matches.forEachIndexed { i, match ->
                    val html = body.substring(match.range.last + 1, matches.getOrNull(i + 1)?.range?.first ?: body.length)
                    if (html.isNotBlank()) add(match.groupValues[1].toLong() * 1000 to html)
                }
            }
        }
        fragments.forEachIndexed { index, (at, html) ->
            val matching = result.indices.filter { result[it].html.isEmpty() && result[it].positionMillis / 1000 == at / 1000 }
            val target = matching.firstOrNull { result[it].images.isEmpty() && result[it].audio.isEmpty() } ?: matching.firstOrNull()
            if (target != null) result[target] = result[target].copy(html = html)
            else result += NotebookEntry(UUID.nameUUIDFromBytes("${note.id}:text:$index".toByteArray()).toString(), at, html)
        }
        // Very early notes stored media only in their flat lists. Never hide it.
        val missingImages = note.images.filter { id -> result.none { id in it.images } }
        val missingAudio = note.media.filter { id -> result.none { id in it.audio } }
        if (missingImages.isNotEmpty() || missingAudio.isNotEmpty()) result += NotebookEntry(
            UUID.nameUUIDFromBytes("${note.id}:attachments".toByteArray()).toString(), note.videoPositionMillis,
            images = missingImages, audio = missingAudio)
        return result.filter { it.html.isNotBlank() || it.images.isNotEmpty() || it.audio.isNotEmpty() }.sortedBy { it.positionMillis }
    }

    fun body(videoId: String, entries: List<NotebookEntry>): String = entries.joinToString("") {
        if (it.html.isBlank()) "" else "<p><a href=\"https://www.youtube.com/watch?v=$videoId&amp;t=${it.positionMillis / 1000}s\">${time(it.positionMillis)}</a></p>" + it.html
    }

    fun time(millis: Long): String {
        val seconds = millis.coerceAtLeast(0) / 1000
        return if (seconds >= 3600) "%d:%02d:%02d".format(java.util.Locale.ROOT, seconds / 3600, seconds / 60 % 60, seconds % 60)
        else "%d:%02d".format(java.util.Locale.ROOT, seconds / 60, seconds % 60)
    }
}
