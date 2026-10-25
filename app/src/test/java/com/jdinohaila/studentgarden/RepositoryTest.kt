package com.jdinohaila.studentgarden

import androidx.test.core.app.ApplicationProvider
import com.jdinohaila.studentgarden.data.*
import com.jdinohaila.studentgarden.domain.*
import java.time.LocalDate
import kotlinx.coroutines.*
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = GardenApp::class)
class RepositoryTest {
    private lateinit var repo: GardenRepository

    @Before
    fun setup() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<GardenApp>()
        context.deleteDatabase("garden.db")
        repo = GardenRepository(context)
        repo.load()
    }

    @After
    fun close() {
        repo.database.close()
    }

    @Test
    fun editTrashUndoAndReloadKeepContent() = runBlocking {
        val note = Note(title = "Biology")
        repo.save(note, "<p><b>Cell</b></p>")
        repo.save(note.copy(title = "Cell biology"))
        repo.trash(note.id)
        assertTrue(repo.state.value.notes.isEmpty())
        assertEquals("<p><b>Cell</b></p>", repo.body(note.id))
        repo.undoTrash(note.id)
        repo.load()
        assertEquals("Cell biology", repo.state.value.notes.single().title)
    }

    @Test
    fun focusCompletionIsIdempotentUnderConcurrentCallbacks() = runBlocking {
        val timer = FocusTimer(minutes = 25, endsAt = System.currentTimeMillis() - 1000, boot = -1)
        repo.setTimer(timer)
        val results =
            awaitAll(async { repo.finishTimer(timer.id) }, async { repo.finishTimer(timer.id) })
        assertEquals(1, results.count { it })
        assertEquals(1, repo.state.value.blocks.size)
        assertTrue(repo.state.value.timer!!.finished)
    }

    @Test
    fun aStaleAlarmCannotFinishAPausedOrRunningSessionEarly() = runBlocking {
        val timer = FocusTimer(minutes = 10)
        repo.setTimer(timer)
        assertFalse(repo.finishTimer(timer.id))
        repo.setTimer(timer.copy(endsAt = System.currentTimeMillis() + 600000, boot = -1))
        assertFalse(repo.finishTimer(timer.id))
        assertTrue(repo.state.value.blocks.isEmpty())
    }

    @Test
    fun failedSaveDoesNotPartiallyReplaceNote() = runBlocking {
        val note = Note(title = "Before")
        repo.save(note, "old")
        assertTrue(
            runCatching { repo.save(note.copy(title = "After"), "x".repeat(1_000_001)) }.isFailure
        )
        repo.load()
        assertEquals("Before", repo.state.value.notes.single().title)
        assertEquals("old", repo.body(note.id))
    }

    @Test
    fun mergeDoesNotOverwriteCurrentRecords() = runBlocking {
        val task = Task(title = "My work")
        repo.save(task)
        repo.merge(
            listOf(task.copy(title = "Old backup"), Task(title = "New from backup")),
            emptyMap(),
            emptyList(),
        )
        assertEquals(2, repo.state.value.tasks.size)
        assertEquals("My work", repo.state.value.tasks.find { it.id == task.id }!!.title)
    }

    @Test
    fun habitToggleRejectsFutureAndPreservesHistory() = runBlocking {
        val h = Habit(title = "Read", startDay = LocalDate.now().minusDays(2).toString())
        repo.save(h)
        repo.check(h, LocalDate.now())
        repo.load()
        assertEquals(1, repo.state.value.checks.size)
        assertTrue(runCatching { repo.check(h, LocalDate.now().plusDays(1)) }.isFailure)
        repo.check(h, LocalDate.now())
        assertTrue(repo.state.value.checks.isEmpty())
    }

    @Test
    fun reviewAndSchedulePersistTogether() = runBlocking {
        val card = Flashcard(answer = "42", prompt = "Six times seven?", stage = 2)
        repo.save(card)
        repo.rate(card, "hard")
        repo.load()
        assertEquals(2, repo.state.value.cards.single().stage)
        assertEquals(1, repo.state.value.reviews.size)
    }

    @Test
    fun duplicateReviewTapsCannotSkipAnInterval() = runBlocking {
        val card = Flashcard(answer = "42", prompt = "Six times seven?")
        repo.save(card)
        repo.rate(card, "advance")
        assertTrue(runCatching { repo.rate(card, "advance") }.isFailure)
        assertEquals(1, repo.state.value.cards.single().stage)
        val lesson = Lesson(title = "Cell division")
        repo.save(lesson)
        repo.review(lesson)
        assertTrue(runCatching { repo.review(lesson) }.isFailure)
        assertEquals(1, repo.state.value.lessons.single().stage)
    }

    @Test fun editingAndMovingACardPreservesScheduleAndHistory() = runBlocking {
        val deck = Deck(title = "Revision")
        repo.save(deck)
        repo.save(Flashcard(prompt = "Question", answer = "Answer", stage = 2))
        repo.rate(repo.state.value.cards.single(), "good")
        val rated = repo.state.value.cards.single()
        repo.save(rated.copy(answer = "Better answer", deckId = deck.id))
        repo.load()
        val edited = repo.state.value.cards.single()
        assertEquals(rated.stage, edited.stage)
        assertEquals(rated.dueAt, edited.dueAt)
        assertEquals(1, repo.state.value.reviews.size)
    }

    @Test fun trashingDeckOrCourseRetainsRelatedWorkAndMediaReferences() = runBlocking {
        val deck = Deck(title = "Biology")
        val course = Course(title = "Biology 101")
        val card = Flashcard(deckId = deck.id, type = "image", imageId = "${newId()}.png", answer = "Cell")
        val task = Task(title = "Study", courseId = course.id)
        repo.save(deck); repo.save(course); repo.save(card); repo.save(task)
        repo.trash(deck.id); repo.trash(course.id); repo.load()
        assertEquals(card, repo.state.value.cards.single())
        assertEquals(task, repo.state.value.tasks.single())
        repo.undoTrash(deck.id); repo.undoTrash(course.id)
        assertEquals(deck.id, repo.state.value.cards.single().deckId)
    }

    @Test fun invalidImportedCardCannotEnterStorage() = runBlocking {
        val invalid = listOf(
            Flashcard(answer = "No question"),
            Flashcard(type = "image", answer = "Missing picture"),
            Flashcard(type = "audio", answer = "Missing recording"),
        )
        invalid.forEach { assertTrue(runCatching { repo.save(it) }.isFailure) }
        assertTrue(repo.state.value.cards.isEmpty())
    }

    @Test fun habitEditsAndArchiveKeepDatedHistory() = runBlocking {
        val habit = Habit(title = "Read", startDay = LocalDate.now().minusDays(10).toString())
        repo.save(habit)
        repo.check(habit, LocalDate.now().minusDays(2))
        val checks = repo.state.value.checks
        repo.save(habit.copy(color = "blue", days = 1, archived = true))
        repo.load()
        assertEquals(checks, repo.state.value.checks)
        assertTrue(repo.state.value.habits.single().archived)
    }
}
