package com.jdinohaila.studentgarden

import com.jdinohaila.studentgarden.domain.*
import java.time.*
import org.junit.Assert.*
import org.junit.Test

class StudyRulesTest {
    private val utc = ZoneId.of("UTC")
    private val now = Instant.parse("2026-01-31T12:00:00Z").toEpochMilli()

    @Test
    fun shortPracticeKeepsLongIntervalProgress() {
        var card = Flashcard(answer = "Mitochondrion")
        for (stage in 0..3) {
            for (rating in listOf("again", "hard", "good")) {
                val reviewed = StudyRules.advance(card, rating, now, utc)
                assertEquals(stage, reviewed.stage)
                assertEquals(
                    now +
                        mapOf("again" to 30_000L, "hard" to 300_000L, "good" to 600_000L)
                            .getValue(rating),
                    reviewed.dueAt,
                )
            }
            card = StudyRules.advance(card, "advance", now, utc)
            assertEquals(stage + 1, card.stage)
        }
        assertFalse(StudyRules.due(card, Long.MAX_VALUE))
    }

    @Test
    fun monthClampsAtEndOfFebruary() {
        val card = StudyRules.advance(Flashcard(answer = "x", stage = 2), "advance", now, utc)
        assertEquals(Instant.parse("2026-02-28T12:00:00Z").toEpochMilli(), card.dueAt)
    }

    @Test
    fun lessonStartsAt48HoursThenWeekMonthComplete() {
        var lesson = Lesson(title = "Cell division", learnedAt = now)
        assertEquals(now + 48 * StudyRules.HOUR, lesson.dueAt)
        lesson = StudyRules.review(lesson, now, utc)
        assertEquals(now + 7 * 24 * StudyRules.HOUR, lesson.dueAt)
        lesson = StudyRules.review(lesson, now, utc)
        assertEquals(Instant.parse("2026-02-28T12:00:00Z").toEpochMilli(), lesson.dueAt)
        lesson = StudyRules.review(lesson, now, utc)
        assertEquals(3, lesson.stage)
        assertNull(lesson.dueAt)
    }

    @Test
    fun habitStreakSkipsUnscheduledDaysAndAllowsTodayUnfinished() {
        val habit = Habit(title = "Study", days = 1 + 4 + 16, startDay = "2026-10-05", zone = "UTC")
        val checks = setOf("2026-10-05", "2026-10-07", "2026-10-09", "2026-10-12", "2026-10-14")
        assertEquals(5, StudyRules.streak(habit, checks, LocalDate.parse("2026-10-16")))
        assertEquals(5, StudyRules.best(habit, checks, LocalDate.parse("2026-10-16")))
        assertEquals(0, StudyRules.streak(habit, checks, LocalDate.parse("2026-10-17")))
        assertFalse(StudyRules.scheduled(habit, LocalDate.parse("2026-10-04")))
    }

    @Test
    fun habitUsesItsOwnTimezone() {
        val h = Habit(title = "Read", zone = "Pacific/Honolulu")
        assertEquals(
            LocalDate.parse("2026-10-15"),
            StudyRules.today(h, Instant.parse("2026-10-16T01:00:00Z")),
        )
    }

    @Test
    fun timerUsesMonotonicClockAndSurvivesReboot() {
        val t = FocusTimer(minutes = 10, endsAt = 20000, elapsedEnd = 15000, boot = 7)
        assertEquals(10000, StudyRules.remaining(t, 1000000, 5000, 7))
        assertEquals(15000, StudyRules.remaining(t, 5000, 2000, 8))
        assertEquals(0, StudyRules.remaining(t, 30000, 2000, 8))
        assertEquals(600000, StudyRules.remaining(t.copy(endsAt = null), 30000, 2000, 8))
    }

    @Test(expected = IllegalArgumentException::class)
    fun rejectsUnknownRating() {
        StudyRules.advance(Flashcard(answer = "x"), "unknown", now)
    }
}
