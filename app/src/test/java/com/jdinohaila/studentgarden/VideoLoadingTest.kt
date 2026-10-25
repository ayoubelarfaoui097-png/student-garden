package com.jdinohaila.studentgarden

import com.jdinohaila.studentgarden.video.*
import java.io.IOException
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.*
import okhttp3.*
import okhttp3.ResponseBody.Companion.toResponseBody
import okio.Timeout
import org.junit.*
import org.junit.Assert.*

class VideoLoadingTest {
    private fun lesson(id: String) = VideoLesson(VideoItem(id, "Lesson", "Teacher", 300), emptyList())
    @Test fun streamCacheExpiresEvictsAndSupportsFreshRetries() {
        var now = 0L
        val cache = LessonCache({ now }, ttlMillis = 1000, capacity = 2)
        val a = lesson("CMiPYHNNg28"); val b = lesson("dQw4w9WgXcQ"); val c = lesson("abcdefghijk")
        cache.put(a.item.videoId, a); now = 500
        assertSame(a, cache.get(a.item.videoId))
        cache.put(b.item.videoId, b); cache.get(a.item.videoId); cache.put(c.item.videoId, c)
        assertNull(cache.get(b.item.videoId)); assertSame(c, cache.get(c.item.videoId))
        now = 1000; assertNull(cache.get(a.item.videoId))
        cache.invalidate(c.item.videoId); assertNull(cache.get(c.item.videoId))
    }
    @Test fun cancelledSearchReleasesTheSocketAndTheExtractionQueuePromptly() = runBlocking {
        val gate = ExtractionGate(); val call = BlockingCall()
        val search = launch { gate.run { gate.execute(call) { it.code } } }
        withContext(Dispatchers.IO) { assertTrue(call.started.await(2, TimeUnit.SECONDS)) }
        val next = async { gate.run { "Playback can start" } }
        search.cancel()
        assertEquals("Playback can start", withTimeout(2000) { next.await() })
        search.join(); assertTrue(call.isCanceled())
    }
    private class BlockingCall : Call {
        val started = CountDownLatch(1)
        private val released = CountDownLatch(1)
        private val cancelled = AtomicBoolean(false)
        private var executed = false
        override fun request() = Request.Builder().url("https://example.test/search").build()
        override fun execute(): Response {
            executed = true; started.countDown()
            if (!released.await(5, TimeUnit.SECONDS)) error("Cancellation did not reach socket")
            if (isCanceled()) throw IOException("Canceled")
            return Response.Builder().request(request()).protocol(Protocol.HTTP_1_1).code(200).message("OK").body("ok".toResponseBody()).build()
        }
        override fun cancel() { cancelled.set(true); released.countDown() }
        override fun isCanceled() = cancelled.get()
        override fun isExecuted() = executed
        override fun timeout() = Timeout()
        override fun enqueue(responseCallback: Callback) = error("Not used")
        override fun clone(): Call = BlockingCall()
    }
}
