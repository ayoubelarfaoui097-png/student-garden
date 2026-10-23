// SPDX-License-Identifier: GPL-3.0-or-later
package com.jdinohaila.studentgarden.video

import java.io.IOException
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference
import kotlinx.coroutines.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import okhttp3.Call
import okhttp3.Response

/** NewPipe has shared parser state. Keep extraction serialized, but cancel its actual
 * socket as soon as a search screen is left, so playback can claim the parser promptly. */
internal class ExtractionGate {
    private val mutex = Mutex()
    private val session = ThreadLocal<Session>()
    private class Session {
        val cancelled = AtomicBoolean(false)
        val call = AtomicReference<Call?>()
        fun cancel() { cancelled.set(true); call.get()?.cancel() }
    }
    suspend fun <T> run(block: () -> T): T = coroutineScope {
        val request = Session()
        val cancellation = launch(Dispatchers.Default, start = CoroutineStart.UNDISPATCHED) {
            try { awaitCancellation() } finally { request.cancel() }
        }
        try {
            withContext(Dispatchers.IO + session.asContextElement(request)) {
                mutex.withLock { ensureActive(); block().also { ensureActive() } }
            }
        } catch (error: Exception) {
            // A cancelled OkHttp socket throws IOException; keep coroutine cancellation
            // from becoming a visible search error or cancelling a sibling request.
            ensureActive()
            throw error
        } finally { cancellation.cancel() }
    }
    fun <T> execute(call: Call, read: (Response) -> T): T {
        val request = session.get() ?: error("Extraction outside its request scope")
        request.call.set(call)
        try {
            if (request.cancelled.get()) { call.cancel(); throw IOException("Extraction cancelled") }
            return call.execute().use(read)
        } finally { request.call.compareAndSet(call, null) }
    }
}

/** Only extracted URLs/metadata are cached in memory; no video files are downloaded.
 * A short lifetime avoids retaining signed YouTube URLs after they expire. */
internal class LessonCache(
    private val clock: () -> Long = android.os.SystemClock::elapsedRealtime,
    private val ttlMillis: Long = 180_000,
    private val capacity: Int = 12,
) {
    private data class Entry(val lesson: VideoLesson, val at: Long)
    private val entries = LinkedHashMap<String, Entry>(16, .75f, true)
    @Synchronized fun get(id: String): VideoLesson? {
        val entry = entries[id] ?: return null
        if (clock() - entry.at !in 0 until ttlMillis) { entries.remove(id); return null }
        return entry.lesson
    }
    @Synchronized fun put(id: String, lesson: VideoLesson) {
        entries[id] = Entry(lesson, clock())
        while (entries.size > capacity) entries.remove(entries.keys.first())
    }
    @Synchronized fun invalidate(id: String) { entries.remove(id) }
}
