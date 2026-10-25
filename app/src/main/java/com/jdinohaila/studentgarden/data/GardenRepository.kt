package com.jdinohaila.studentgarden.data

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import com.jdinohaila.studentgarden.domain.*
import com.jdinohaila.studentgarden.video.NotebookEntry
import com.jdinohaila.studentgarden.video.VideoNotebook
import java.time.LocalDate
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

class GardenDatabase(context: Context) : SQLiteOpenHelper(context, "garden.db", null, 1) {
    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL(
            "CREATE TABLE records(id TEXT PRIMARY KEY, kind TEXT NOT NULL, data TEXT NOT NULL, updated_at INTEGER NOT NULL, trashed_at INTEGER)"
        )
        db.execSQL("CREATE INDEX records_kind ON records(kind,trashed_at)")
        db.execSQL("CREATE TABLE note_contents(id TEXT PRIMARY KEY, html TEXT NOT NULL)")
        db.execSQL(
            "CREATE TABLE habit_checks(habit_id TEXT NOT NULL, day TEXT NOT NULL, checked_at INTEGER NOT NULL, PRIMARY KEY(habit_id,day))"
        )
        db.execSQL("CREATE TABLE timer(id INTEGER PRIMARY KEY CHECK(id=1), data TEXT NOT NULL)")
    }

    override fun onUpgrade(db: SQLiteDatabase, old: Int, new: Int) {
        error("Unsupported database upgrade")
    }

    override fun onConfigure(db: SQLiteDatabase) {
        db.setForeignKeyConstraintsEnabled(true)
        db.enableWriteAheadLogging()
    }
}

class GardenRepository(private val context: Context) {
    val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
        classDiscriminator = "recordType"
    }
    val database = GardenDatabase(context.applicationContext)
    private val mutex = Mutex()
    private val _state = MutableStateFlow(GardenState())
    val state = _state.asStateFlow()

    suspend fun load() = mutate { consolidateVideoNotes() }

    private fun kind(record: GardenRecord) =
        when (record) {
            is Course -> "course"
            is Task -> "task"
            is Note -> "note"
            is StudyBlock -> "block"
            is Deck -> "deck"
            is Flashcard -> "card"
            is Lesson -> "lesson"
            is Habit -> "habit"
            is Review -> "review"
            is LearningVideo -> "video"
        }

    private fun write(record: GardenRecord) {
        validate(record)
        database.writableDatabase.insertWithOnConflict(
            "records",
            null,
            ContentValues().apply {
                put("id", record.id)
                put("kind", kind(record))
                put("data", json.encodeToString<GardenRecord>(record))
                put("updated_at", System.currentTimeMillis())
                putNull("trashed_at")
            },
            SQLiteDatabase.CONFLICT_REPLACE,
        )
    }

    private fun writeBody(id: String, html: String) {
        require(html.length <= 1_000_000) { "Note is too large" }
        database.writableDatabase.insertWithOnConflict("note_contents", null,
            ContentValues().apply { put("id", id); put("html", html) }, SQLiteDatabase.CONFLICT_REPLACE)
    }

    private fun readBody(id: String): String = database.readableDatabase
        .rawQuery("SELECT html FROM note_contents WHERE id=?", arrayOf(id))
        .use { if (it.moveToFirst()) it.getString(0) else "" }

    suspend fun save(record: GardenRecord, html: String? = null) = mutate {
        if (record is Note && record.videoId != null) {
            val current = _state.value.notes.find { it.id == record.id }
            require(current == null || record.videoMoments.map { it.id }.containsAll(current.videoMoments.map { it.id })) {
                context.getString(com.jdinohaila.studentgarden.R.string.video_note_changed)
            }
        }
        write(record)
        if (record is Note && html != null) writeBody(record.id, html)
        if (record is Note && record.videoId != null) consolidateVideoNotes()
    }

    /** Serialized read/append/write: voice, frame and text saves cannot replace each other.
     * The incoming UUID also makes retrying a completed save idempotent. */
    suspend fun appendVideoNote(entry: Note, html: String = "") = mutate {
        require(entry.videoId != null)
        consolidateVideoNotes()
        val current = readRecords().filterIsInstance<Note>().firstOrNull { it.videoId == entry.videoId }
        if (current != null && (current.id == entry.id || current.videoMoments.any { it.id == entry.id })) return@mutate
        val previous = current?.let(::moments).orEmpty()
        val moment = VideoMoment(entry.id, entry.videoPositionMillis, entry.media, entry.images)
        val merged = (current ?: entry).copy(
            media = (current?.media.orEmpty() + entry.media).distinct(),
            images = (current?.images.orEmpty() + entry.images).distinct(),
            preview = (current?.preview.orEmpty() + " " + entry.preview).trim().take(180),
            videoMoments = previous + moment,
        )
        val body = current?.let { readBody(it.id) }.orEmpty() + timedBody(entry, html)
        validate(merged)
        writeBody(merged.id, body)
        write(merged)
    }

    private fun moments(note: Note) = note.videoMoments.ifEmpty {
        listOf(VideoMoment(note.id, note.videoPositionMillis, note.media, note.images))
    }

    suspend fun editVideoEntry(noteId: String, original: NotebookEntry, text: String) = mutate {
        val note = readRecords().filterIsInstance<Note>().first { it.id == noteId && it.videoId != null }
        val entries = VideoNotebook.entries(note, readBody(noteId))
        require(entries.find { it.id == original.id } == original) {
            context.getString(com.jdinohaila.studentgarden.R.string.video_note_changed)
        }
        require(text.isNotBlank() && text.length <= 50000)
        val html = "<p>" + android.text.Html.escapeHtml(text.trim()).replace("\n", "<br>") + "</p>"
        writeNotebook(note, entries.map { if (it.id == original.id) it.copy(html = html) else it })
    }

    suspend fun removeVideoEntry(noteId: String, original: NotebookEntry) = mutate {
        val note = readRecords().filterIsInstance<Note>().first { it.id == noteId && it.videoId != null }
        val entries = VideoNotebook.entries(note, readBody(noteId))
        require(entries.find { it.id == original.id } == original) {
            context.getString(com.jdinohaila.studentgarden.R.string.video_note_changed)
        }
        writeNotebook(note, entries.filterNot { it.id == original.id })
    }

    suspend fun restoreVideoEntry(noteId: String, entry: NotebookEntry) = mutate {
        val note = readRecords().filterIsInstance<Note>().first { it.id == noteId && it.videoId != null }
        val entries = VideoNotebook.entries(note, readBody(noteId))
        val current = entries.find { it.id == entry.id }
        val restored = current?.copy(images = (current.images + entry.images).distinct(), audio = (current.audio + entry.audio).distinct()) ?: entry
        writeNotebook(note, (entries.filterNot { it.id == entry.id } + restored).sortedBy { it.positionMillis })
    }

    suspend fun removeVideoImage(noteId: String, original: NotebookEntry, imageId: String) = mutate {
        val note = readRecords().filterIsInstance<Note>().first { it.id == noteId && it.videoId != null }
        val entries = VideoNotebook.entries(note, readBody(noteId))
        require(entries.find { it.id == original.id } == original) {
            context.getString(com.jdinohaila.studentgarden.R.string.video_note_changed)
        }
        writeNotebook(note, entries.map { if (it.id == original.id) it.copy(images = it.images - imageId) else it }
            .filter { it.html.isNotBlank() || it.images.isNotEmpty() || it.audio.isNotEmpty() })
    }

    private fun writeNotebook(note: Note, entries: List<NotebookEntry>) {
        val html = VideoNotebook.body(note.videoId!!, entries)
        val updated = note.copy(
            media = entries.flatMap { it.audio }.distinct(), images = entries.flatMap { it.images }.distinct(),
            videoMoments = entries.map { VideoMoment(it.id, it.positionMillis, it.audio, it.images) },
            preview = android.text.Html.fromHtml(html, android.text.Html.FROM_HTML_MODE_LEGACY).toString().trim().take(180))
        writeBody(note.id, html)
        write(updated)
    }

    private fun timedBody(note: Note, html: String): String {
        if (html.isBlank()) return ""
        val seconds = note.videoPositionMillis / 1000
        val time = if (seconds >= 3600) "%d:%02d:%02d".format(seconds / 3600, seconds / 60 % 60, seconds % 60)
            else "%d:%02d".format(seconds / 60, seconds % 60)
        return "<p><a href=\"https://www.youtube.com/watch?v=${note.videoId}&amp;t=${seconds}s\">$time</a></p>" + html
    }

    /** Runs on upgrade and backup import. Preserve exact rich HTML and all media before
     * removing redundant records, in the same database transaction. */
    private fun consolidateVideoNotes() {
        readRecords().filterIsInstance<Note>().filter { it.videoId != null }.groupBy { it.videoId }.values
            .filter { it.size > 1 }.forEach { group ->
                val ordered = group.sortedWith(compareBy<Note> { if (it.videoMoments.isNotEmpty()) 0 else 1 }.thenBy { it.createdAt }.thenBy { it.id })
                val first = ordered.first()
                val seen = mutableSetOf<String>()
                val allMoments = mutableListOf<VideoMoment>()
                val bodies = StringBuilder()
                val previews = mutableListOf<String>()
                val audio = mutableListOf<String>(); val images = mutableListOf<String>()
                ordered.forEach { note ->
                    val fresh = moments(note).filter { seen.add(it.id) }
                    if (fresh.isNotEmpty()) {
                        allMoments.addAll(fresh)
                        if (note.preview.isNotBlank()) previews.add(note.preview)
                        audio.addAll(note.media); images.addAll(note.images)
                        val html = readBody(note.id).ifBlank {
                            if (note.preview.isBlank()) "" else "<p>" + android.text.Html.escapeHtml(note.preview) + "</p>"
                        }
                        bodies.append(if (note.videoMoments.isEmpty()) timedBody(note, html) else html)
                    }
                }
                val merged = first.copy(media = audio.distinct(), images = images.distinct(), videoMoments = allMoments,
                    preview = previews.joinToString(" ").take(180))
                // Oversized legacy groups stay intact rather than lose any content.
                if (bodies.length > 1_000_000 || runCatching { validate(merged) }.isFailure) return@forEach
                writeBody(first.id, bodies.toString()); write(merged)
                ordered.drop(1).forEach { note ->
                    database.writableDatabase.delete("records", "id=?", arrayOf(note.id))
                    database.writableDatabase.delete("note_contents", "id=?", arrayOf(note.id))
                }
            }
    }

    suspend fun trash(id: String) = mutate {
        database.writableDatabase.execSQL(
            "UPDATE records SET trashed_at=? WHERE id=?",
            arrayOf<Any>(System.currentTimeMillis(), id),
        )
    }

    suspend fun undoTrash(id: String) = mutate {
        database.writableDatabase.execSQL(
            "UPDATE records SET trashed_at=NULL WHERE id=?",
            arrayOf(id),
        )
        consolidateVideoNotes()
    }

    suspend fun body(id: String): String = withContext(Dispatchers.IO) { readBody(id) }

    suspend fun rate(card: Flashcard, rating: String) = mutate {
        val current = _state.value.cards.find { it.id == card.id } ?: error("Card no longer exists")
        require(current.stage == card.stage && current.dueAt == card.dueAt) {
            "This card has already been reviewed"
        }
        val now = System.currentTimeMillis()
        write(StudyRules.advance(current, rating, now))
        write(Review(itemId = card.id, kind = "card", rating = rating, createdAt = now))
    }

    suspend fun review(lesson: Lesson) = mutate {
        val current =
            _state.value.lessons.find { it.id == lesson.id } ?: error("Lesson no longer exists")
        require(current.stage == lesson.stage && current.dueAt == lesson.dueAt) {
            "This lesson has already been reviewed"
        }
        write(StudyRules.review(current, System.currentTimeMillis()))
        write(Review(itemId = lesson.id, kind = "lesson", rating = "review"))
    }

    suspend fun check(habit: Habit, day: LocalDate) = mutate {
        require(day <= StudyRules.today(habit) && day >= LocalDate.parse(habit.startDay))
        val db = database.writableDatabase
        val existing =
            db.delete("habit_checks", "habit_id=? AND day=?", arrayOf(habit.id, day.toString()))
        if (existing == 0) {
            require(StudyRules.scheduled(habit, day))
            db.insertOrThrow(
                "habit_checks",
                null,
                ContentValues().apply {
                    put("habit_id", habit.id)
                    put("day", day.toString())
                    put("checked_at", System.currentTimeMillis())
                },
            )
        }
    }

    suspend fun setTimer(timer: FocusTimer?) = mutate { writeTimer(timer) }

    suspend fun saveVideo(videoId: String, title: String, uploader: String, duration: Long, position: Long) = mutate {
        val previous = _state.value.videos.find { it.videoId == videoId }
        write((previous ?: LearningVideo(videoId = videoId, title = title)).copy(
            title = title, uploader = uploader, duration = duration,
            positionMillis = position.coerceAtLeast(0),
        ))
    }

    suspend fun videoPosition(videoId: String, position: Long) = mutate {
        _state.value.videos.find { it.videoId == videoId }?.let {
            write(it.copy(positionMillis = position.coerceAtLeast(0)))
        }
    }

    private fun writeTimer(timer: FocusTimer?) {
        database.writableDatabase.delete("timer", null, null)
        if (timer != null)
            database.writableDatabase.insertOrThrow(
                "timer",
                null,
                ContentValues().apply {
                    put("id", 1)
                    put("data", json.encodeToString(timer))
                },
            )
    }

    suspend fun finishTimer(id: String): Boolean =
        withContext(Dispatchers.IO) {
            mutex.withLock {
                val timer = readTimer() ?: return@withLock false
                val boot =
                    android.provider.Settings.Global.getInt(
                        context.contentResolver,
                        android.provider.Settings.Global.BOOT_COUNT,
                        0,
                    )
                if (
                    timer.id != id ||
                        timer.finished ||
                        timer.endsAt == null ||
                        StudyRules.remaining(
                            timer,
                            System.currentTimeMillis(),
                            android.os.SystemClock.elapsedRealtime(),
                            boot,
                        ) > 0
                )
                    return@withLock false
                val db = database.writableDatabase
                db.beginTransaction()
                try {
                    writeTimer(
                        timer.copy(finished = true, remaining = 0, endsAt = null, elapsedEnd = null)
                    )
                    write(
                        StudyBlock(
                            id = timer.id,
                            title = "Focus",
                            courseId = timer.courseId,
                            startsAt = timer.startedAt,
                            minutes = timer.minutes,
                            done = true,
                            source = "focus",
                        )
                    )
                    db.setTransactionSuccessful()
                } finally {
                    db.endTransaction()
                }
                refresh()
                true
            }
        }

    private fun readTimer(): FocusTimer? =
        database.readableDatabase.rawQuery("SELECT data FROM timer WHERE id=1", null).use {
            if (it.moveToFirst()) json.decodeFromString(it.getString(0)) else null
        }

    suspend fun allRecords(): List<GardenRecord> =
        withContext(Dispatchers.IO) { mutex.withLock { readRecords() } }

    suspend fun snapshot(): Triple<List<GardenRecord>, Map<String, String>, List<HabitCheck>> =
        withContext(Dispatchers.IO) {
            mutex.withLock {
                val records = readRecords()
                val bodies =
                    records.filterIsInstance<Note>().associate { note ->
                        note.id to
                            database.readableDatabase
                                .rawQuery(
                                    "SELECT html FROM note_contents WHERE id=?",
                                    arrayOf(note.id),
                                )
                                .use { if (it.moveToFirst()) it.getString(0) else "" }
                    }
                val checks =
                    database.readableDatabase
                        .rawQuery("SELECT habit_id,day,checked_at FROM habit_checks", null)
                        .use { c ->
                            buildList {
                                while (c.moveToNext()) add(
                                    HabitCheck(c.getString(0), c.getString(1), c.getLong(2))
                                )
                            }
                        }
                        .filter { c -> records.any { it is Habit && it.id == c.habitId } }
                Triple(records, bodies, checks)
            }
        }

    private fun readRecords(): List<GardenRecord> =
        database.readableDatabase
            .rawQuery(
                "SELECT data FROM records WHERE trashed_at IS NULL ORDER BY updated_at DESC",
                null,
            )
            .use { c ->
                buildList {
                    while (c.moveToNext()) add(json.decodeFromString<GardenRecord>(c.getString(0)))
                }
            }

    suspend fun merge(
        records: List<GardenRecord>,
        notes: Map<String, String>,
        checks: List<HabitCheck>,
    ) = mutate {
        records.forEach { r ->
            validate(r)
            database.readableDatabase
                .rawQuery("SELECT id FROM records WHERE id=?", arrayOf(r.id))
                .use { if (!it.moveToFirst()) write(r) }
        }
        notes.forEach { (id, html) ->
            require(html.length <= 1_000_000)
            database.writableDatabase.insertWithOnConflict(
                "note_contents",
                null,
                ContentValues().apply {
                    put("id", id)
                    put("html", html)
                },
                SQLiteDatabase.CONFLICT_IGNORE,
            )
        }
        consolidateVideoNotes()
        checks.forEach { c ->
            LocalDate.parse(c.day)
            database.writableDatabase.insertWithOnConflict(
                "habit_checks",
                null,
                ContentValues().apply {
                    put("habit_id", c.habitId)
                    put("day", c.day)
                    put("checked_at", c.checkedAt)
                },
                SQLiteDatabase.CONFLICT_IGNORE,
            )
        }
    }

    private suspend fun mutate(block: () -> Unit) =
        withContext(Dispatchers.IO) {
            mutex.withLock {
                val db = database.writableDatabase
                db.beginTransaction()
                try {
                    block()
                    db.setTransactionSuccessful()
                } finally {
                    db.endTransaction()
                }
                refresh()
            }
        }

    private fun refresh() {
        val records = readRecords()
        val checks =
            database.readableDatabase
                .rawQuery("SELECT habit_id,day,checked_at FROM habit_checks", null)
                .use { c ->
                    buildList {
                        while (c.moveToNext()) add(
                            HabitCheck(c.getString(0), c.getString(1), c.getLong(2))
                        )
                    }
                }
        _state.value =
            GardenState(
                records.filterIsInstance<Course>(),
                records.filterIsInstance<Task>(),
                records.filterIsInstance<Note>(),
                records.filterIsInstance<StudyBlock>(),
                records.filterIsInstance<Deck>(),
                records.filterIsInstance<Flashcard>(),
                records.filterIsInstance<Lesson>(),
                records.filterIsInstance<Habit>(),
                records.filterIsInstance<Review>(),
                checks,
                readTimer(),
                true,
                database.readableDatabase
                    .rawQuery(
                        "SELECT data FROM records WHERE trashed_at IS NOT NULL ORDER BY trashed_at DESC",
                        null,
                    )
                    .use { c ->
                        buildList {
                            while (c.moveToNext()) add(
                                json.decodeFromString<GardenRecord>(c.getString(0))
                            )
                        }
                },
                videos = records.filterIsInstance<LearningVideo>(),
            )
        com.jdinohaila.studentgarden.platform.LessonReminder.schedule(context, _state.value.lessons)
        com.jdinohaila.studentgarden.platform.StudyReminders.schedule(context, _state.value)
    }

    fun validate(r: GardenRecord) {
        require(runCatching { java.util.UUID.fromString(r.id) }.isSuccess) { "Invalid record ID" }
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
        require(title.isNotBlank() && title.length <= 10_000) { "Add a title or answer" }
        when (r) {
            is Note -> {
                require(r.media.size <= (if (r.videoId == null) 20 else 500) && r.media.distinct().size == r.media.size)
                r.media.forEach { require(it.matches(Regex("[a-f0-9-]{36}\\.(m4a|mp3|ogg|wav|flac|webm)"))) }
                require(r.images.size <= (if (r.videoId == null) 20 else 500) && r.images.distinct().size == r.images.size)
                r.images.forEach { require(it.matches(Regex("[a-f0-9-]{36}\\.(jpg|png|webp)"))) }
                require(r.videoId == null || r.videoId.matches(Regex("[A-Za-z0-9_-]{11}")))
                require(r.videoPositionMillis >= 0)
                require(r.videoMoments.size <= 2000 && r.videoMoments.distinctBy { it.id }.size == r.videoMoments.size)
                require(r.videoId != null || r.videoMoments.isEmpty())
                r.videoMoments.forEach { moment ->
                    require(runCatching { java.util.UUID.fromString(moment.id) }.isSuccess && moment.positionMillis >= 0)
                    require(moment.media.distinct().size == moment.media.size && moment.images.distinct().size == moment.images.size)
                    require(r.media.containsAll(moment.media) && r.images.containsAll(moment.images))
                }
            }
            is Habit -> {
                require(r.days in 1..127)
                java.time.ZoneId.of(r.zone)
                require(
                    LocalDate.parse(r.startDay) in LocalDate.of(2000, 1, 1)..StudyRules.today(r)
                )
            }
            is Flashcard -> {
                require(r.stage in 0..4 && r.type in setOf("text", "image", "audio"))
                require(r.type != "text" || r.prompt.isNotBlank()) { "Add a question or cue" }
                require(r.type != "image" || r.imageId != null) { "Attach an image" }
                require(r.type != "audio" || r.audioId != null) { "Attach audio" }
                listOfNotNull(r.imageId, r.audioId).forEach {
                    require(it.matches(Regex("[a-f0-9-]{36}\\.[a-z0-9]{1,5}")))
                }
            }
            is Lesson -> {
                require(r.stage in 0..3 && (r.stage == 3) == (r.dueAt == null))
                require(r.color in setOf("green", "blue", "gold", "coral", "teal", "cyan", "orange", "rose") || r.color.matches(Regex("#[0-9A-Fa-f]{6}")))
            }
            is LearningVideo -> {
                require(r.videoId.matches(Regex("[A-Za-z0-9_-]{11}")))
                require(r.positionMillis >= 0 && r.duration >= 0 && r.uploader.length <= 10000)
            }
            is Task -> require(r.minutes in 1..1440 && r.status in setOf("todo", "progress", "done") && r.priority in setOf("normal", "high"))
            is StudyBlock -> require(r.minutes in 1..1440)
            else -> Unit
        }
    }
}
