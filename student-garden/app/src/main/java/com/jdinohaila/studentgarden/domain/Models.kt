package com.jdinohaila.studentgarden.domain

import java.time.*
import java.util.UUID
import kotlinx.serialization.Serializable

fun newId(): String = UUID.randomUUID().toString()

@Serializable
sealed interface GardenRecord {
    val id: String
    val createdAt: Long
}

@Serializable
data class Course(
    override val id: String = newId(),
    val title: String,
    val code: String = "",
    val color: String = "slate",
    val term: String = "",
    val weeklyGoal: Int = 3,
    override val createdAt: Long = System.currentTimeMillis(),
) : GardenRecord

@Serializable
data class Task(
    override val id: String = newId(),
    val title: String,
    val detail: String = "",
    val courseId: String? = null,
    val dueAt: Long? = null,
    val status: String = "todo",
    val priority: String = "normal",
    val kind: String = "assignment",
    val minutes: Int = 30,
    override val createdAt: Long = System.currentTimeMillis(),
) : GardenRecord

@Serializable
data class Note(
    override val id: String = newId(),
    val title: String,
    val courseId: String? = null,
    val preview: String = "",
    val media: List<String> = emptyList(),
    override val createdAt: Long = System.currentTimeMillis(),
    val videoId: String? = null,
    val videoPositionMillis: Long = 0,
    val images: List<String> = emptyList(),
    val videoMoments: List<VideoMoment> = emptyList(),
) : GardenRecord

/** Attachment references stay in Note.media/images for portable backups. */
@Serializable
data class VideoMoment(
    val id: String = newId(),
    val positionMillis: Long,
    val media: List<String> = emptyList(),
    val images: List<String> = emptyList(),
)

@Serializable
data class StudyBlock(
    override val id: String = newId(),
    val title: String,
    val courseId: String? = null,
    val taskId: String? = null,
    val startsAt: Long,
    val minutes: Int = 30,
    val done: Boolean = false,
    val source: String = "planner",
    override val createdAt: Long = System.currentTimeMillis(),
    val color: String = "blue",
) : GardenRecord

@Serializable
data class Deck(
    override val id: String = newId(),
    val title: String,
    val color: String = "orange",
    override val createdAt: Long = System.currentTimeMillis(),
) : GardenRecord

@Serializable
data class Flashcard(
    override val id: String = newId(),
    val deckId: String? = null,
    val type: String = "text",
    val prompt: String = "",
    val answer: String,
    val subject: String = "",
    val imageId: String? = null,
    val audioId: String? = null,
    val dueAt: Long = System.currentTimeMillis(),
    val stage: Int = 0,
    override val createdAt: Long = System.currentTimeMillis(),
) : GardenRecord

@Serializable
data class Lesson(
    override val id: String = newId(),
    val title: String,
    val subject: String = "",
    val detail: String = "",
    val learnedAt: Long = System.currentTimeMillis(),
    val dueAt: Long? = learnedAt + 48 * 3_600_000L,
    val stage: Int = 0,
    override val createdAt: Long = System.currentTimeMillis(),
    val color: String = "cyan",
) : GardenRecord

@Serializable
data class LearningVideo(
    override val id: String = newId(),
    val videoId: String,
    val title: String,
    val uploader: String = "",
    val duration: Long = 0,
    val positionMillis: Long = 0,
    override val createdAt: Long = System.currentTimeMillis(),
) : GardenRecord

@Serializable
data class Habit(
    override val id: String = newId(),
    val title: String,
    val cue: String = "",
    val color: String = "green",
    val days: Int = 127,
    val zone: String = ZoneId.systemDefault().id,
    val startDay: String = LocalDate.now(ZoneId.of(zone)).toString(),
    val archived: Boolean = false,
    override val createdAt: Long = System.currentTimeMillis(),
) : GardenRecord

@Serializable
data class Review(
    override val id: String = newId(),
    val itemId: String,
    val kind: String,
    val rating: String,
    override val createdAt: Long = System.currentTimeMillis(),
) : GardenRecord

@Serializable
data class HabitCheck(
    val habitId: String,
    val day: String,
    val checkedAt: Long = System.currentTimeMillis(),
)

@Serializable
data class FocusTimer(
    val id: String = newId(),
    val minutes: Int,
    val mode: String = "focus",
    val courseId: String? = null,
    val startedAt: Long = System.currentTimeMillis(),
    val endsAt: Long? = null,
    val elapsedEnd: Long? = null,
    val boot: Int = 0,
    val remaining: Long = minutes * 60_000L,
    val finished: Boolean = false,
)

data class GardenState(
    val courses: List<Course> = emptyList(),
    val tasks: List<Task> = emptyList(),
    val notes: List<Note> = emptyList(),
    val blocks: List<StudyBlock> = emptyList(),
    val decks: List<Deck> = emptyList(),
    val cards: List<Flashcard> = emptyList(),
    val lessons: List<Lesson> = emptyList(),
    val habits: List<Habit> = emptyList(),
    val reviews: List<Review> = emptyList(),
    val checks: List<HabitCheck> = emptyList(),
    val timer: FocusTimer? = null,
    val loaded: Boolean = false,
    val trash: List<GardenRecord> = emptyList(),
    // A fresh snapshot also refreshes dates and due counters after activity resume.
    val refreshedAt: Long = System.currentTimeMillis(),
    val videos: List<LearningVideo> = emptyList(),
)

object StudyRules {
    const val MINUTE = 60_000L
    const val HOUR = 60 * MINUTE

    fun advance(
        card: Flashcard,
        rating: String,
        now: Long,
        zone: ZoneId = ZoneId.systemDefault(),
    ): Flashcard {
        require(rating in setOf("again", "hard", "good", "advance"))
        val due =
            when (rating) {
                "again" -> now + 30_000L
                "hard" -> now + 5 * MINUTE
                "good" -> now + 10 * MINUTE
                else ->
                    when (card.stage) {
                        0 -> now + 48 * HOUR
                        1 -> now + 7 * 24 * HOUR
                        2 ->
                            Instant.ofEpochMilli(now)
                                .atZone(zone)
                                .plusMonths(1)
                                .toInstant()
                                .toEpochMilli()
                        else -> now
                    }
            }
        return card.copy(
            dueAt = due,
            stage = if (rating == "advance") (card.stage + 1).coerceAtMost(4) else card.stage,
        )
    }

    fun review(lesson: Lesson, now: Long, zone: ZoneId = ZoneId.systemDefault()): Lesson =
        lesson.copy(
            stage = (lesson.stage + 1).coerceAtMost(3),
            dueAt =
                when (lesson.stage) {
                    0 -> now + 7 * 24 * HOUR
                    1 ->
                        Instant.ofEpochMilli(now)
                            .atZone(zone)
                            .plusMonths(1)
                            .toInstant()
                            .toEpochMilli()
                    else -> null
                },
        )

    fun due(card: Flashcard, now: Long) = card.stage < 4 && card.dueAt <= now

    fun scheduled(habit: Habit, date: LocalDate) =
        date >= LocalDate.parse(habit.startDay) &&
            habit.days and (1 shl (date.dayOfWeek.value - 1)) != 0

    fun today(habit: Habit, now: Instant = Instant.now()): LocalDate =
        now.atZone(ZoneId.of(habit.zone)).toLocalDate()

    fun streak(habit: Habit, checks: Set<String>, today: LocalDate): Int {
        var day = today
        if (!checks.contains(day.toString())) day = day.minusDays(1)
        var count = 0
        val start = LocalDate.parse(habit.startDay)
        while (day >= start) {
            if (scheduled(habit, day)) {
                if (!checks.contains(day.toString())) break
                count++
            }
            day = day.minusDays(1)
        }
        return count
    }

    fun best(habit: Habit, checks: Set<String>, today: LocalDate): Int {
        var day = LocalDate.parse(habit.startDay)
        var current = 0
        var best = 0
        while (day <= today) {
            if (scheduled(habit, day)) {
                if (day.toString() in checks) {
                    current++
                    best = maxOf(best, current)
                } else if (day < today) current = 0
            }
            day = day.plusDays(1)
        }
        return best
    }

    fun rate(habit: Habit, checks: Set<String>, today: LocalDate): Pair<Int, Int> {
        val days = (1L..30L).map { today.minusDays(it) }.filter { scheduled(habit, it) }
        return days.count { it.toString() in checks } to days.size
    }

    fun week(date: LocalDate): LocalDate = date.minusDays((date.dayOfWeek.value - 1).toLong())

    fun remaining(timer: FocusTimer, wallNow: Long, elapsedNow: Long, boot: Int): Long =
        if (timer.finished) 0
        else if (timer.endsAt == null) timer.remaining
        else
            ((if (timer.boot == boot && timer.elapsedEnd != null) timer.elapsedEnd - elapsedNow
                else timer.endsAt - wallNow)
                .coerceAtLeast(0))
}
