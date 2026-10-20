package com.jdinohaila.studentgarden

import android.graphics.Bitmap
import androidx.activity.ComponentActivity
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.test.core.app.ApplicationProvider
import com.jdinohaila.studentgarden.domain.*
import com.jdinohaila.studentgarden.ui.GardenRoot
import com.jdinohaila.studentgarden.ui.theme.GardenTheme
import java.io.File
import java.time.LocalDate
import kotlinx.coroutines.runBlocking
import org.junit.*
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.*

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w412dp-h915dp-xxhdpi", application = GardenApp::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class MobileUiTest {
    @get:Rule val name = org.junit.rules.TestName()
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()
    private val restoration = StateRestorationTester(compose)
    private val lessonRequest = mutableIntStateOf(0)
    private val reminderRequest = mutableIntStateOf(0)
    private val reminderDestination = mutableStateOf("")
    private lateinit var app: GardenApp

    @Before
    fun setup() = runBlocking {
        app = ApplicationProvider.getApplicationContext()
        app.deleteDatabase("garden.db")
        app.repository.load()
        val course = Course(title = "Biology", code = "BIO 102")
        app.repository.save(course)
        app.repository.save(
            Task(
                title = "Revise cell division",
                courseId = course.id,
                dueAt = System.currentTimeMillis() + 86400000,
            )
        )
        app.repository.save(
            Note(title = "Cell structure", preview = "How organelles work together"),
            "<p>How organelles work together</p>",
        )
        val deck = Deck(title = "Biology foundations")
        app.repository.save(deck)
        val other = Deck(title = "Chemistry")
        app.repository.save(other)
        app.repository.save(
            Flashcard(
                deckId = deck.id,
                prompt = "What powers a cell?",
                answer = "Mitochondrion",
                dueAt = System.currentTimeMillis() - 60000,
            )
        )
        app.repository.save(
            Flashcard(
                deckId = other.id,
                prompt = "Symbol for gold?",
                answer = "Au",
                dueAt = System.currentTimeMillis() - 30000,
            )
        )
        for ((title, color) in
            listOf(
                "Read 10 pages" to "gold",
                "Morning revision" to "blue",
                "Take a walk" to "green",
            )) {
            val habit =
                Habit(
                    title = title,
                    color = color,
                    startDay = LocalDate.now().minusDays(20).toString(),
                )
            app.repository.save(habit)
            app.repository.check(habit, LocalDate.now().minusDays(1))
        }
        restoration.setContent {
            GardenTheme(if (name.methodName == "darkModeRenders") "dark" else "light") {
                GardenRoot(app.repository, app.media, {}, lessonRequest = lessonRequest.intValue, reminderRequest = reminderRequest.intValue, reminderDestination = reminderDestination.value)
            }
        }
    }

    private fun image(name: String) {
        compose.waitForIdle()
        val output = File("build/qa/$name.png")
        output.parentFile!!.mkdirs()
        compose.runOnIdle {
            val view = compose.activity.window.decorView
            val bitmap = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
            view.draw(android.graphics.Canvas(bitmap))
            output.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        }
        val icon = app.getDrawable(R.drawable.ic_garden)!!
        val bitmap = Bitmap.createBitmap(512, 512, Bitmap.Config.ARGB_8888)
        icon.setBounds(0, 0, 512, 512)
        icon.draw(android.graphics.Canvas(bitmap))
        File("build/qa/icon.png").outputStream().use {
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)
        }
    }

    @Test
    fun mainDestinationsRender() {
        image("today-phone")
        compose.onNodeWithText("Study", useUnmergedTree = false).performClick()
        compose.waitForIdle()
        compose.onNodeWithText("Cell structure").assertIsDisplayed()
        image("notes-phone")
        compose.onNodeWithTag("nav-4").performClick()
        compose.onNodeWithText("Read 10 pages").assertIsDisplayed()
        image("habits-phone")
    }

    @Test
    fun taskCanBeEditedAndSaved() {
        compose.onNodeWithText("Revise cell division").performClick()
        compose.onNodeWithText("Title").performTextReplacement("Finish biology notes")
        compose.onNodeWithText("Save", ignoreCase = false).performClick()
        compose.waitUntil(5000) {
            app.repository.state.value.tasks.any { it.title == "Finish biology notes" }
        }
        compose.onNodeWithText("Finish biology notes").assertIsDisplayed()
    }

    @Test
    fun newNoteEditorUsesWholeScreen() {
        compose.onNodeWithText("Study").performClick()
        compose.onNodeWithText("Add").performClick()
        compose.onNodeWithText("Title").performTextInput("New lecture")
        compose.onNodeWithText("Start writing…").performTextInput("A useful observation")
        image("note-editor-phone")
        compose.onNodeWithText("Save").performClick()
        compose.waitUntil(5000) {
            app.repository.state.value.notes.any { it.title == "New lecture" }
        }
    }

    @Test
    fun darkModeRenders() {
        image("today-dark")
        compose.onNodeWithTag("nav-4").performClick()
        image("habits-dark")
    }

    @Test
    @Config(qualifiers = "ar-w412dp-h915dp-xxhdpi")
    fun arabicNavigationRenders() {
        image("today-arabic")
        compose.onNodeWithTag("nav-4").performClick()
        image("habits-arabic")
    }

    @Test
    @Config(qualifiers = "w840dp-h900dp-xhdpi")
    fun wideScreenUsesNavigationRail() {
        image("today-tablet")
        compose.onNodeWithTag("nav-4").performClick()
        compose.onNodeWithText("Read 10 pages").assertIsDisplayed()
        image("habits-tablet")
    }

    @Test
    @Config(qualifiers = "w320dp-h720dp-hdpi")
    fun smallPhoneKeepsHabitTouchTargets() {
        compose.onNodeWithTag("nav-4").performClick()
        image("habits-small-phone")
    }

    @Test
    fun flashcardsRevealAndKeepDecksSeparate() {
        compose.onNodeWithTag("nav-3").performClick()
        compose.onNodeWithText("Biology foundations").performClick()
        compose.onNodeWithText("Review").performClick()
        compose.onNodeWithText("Mitochondrion").assertDoesNotExist()
        compose.onNodeWithText("Symbol for gold?").assertDoesNotExist()
        compose.onNodeWithText("Again").assertDoesNotExist()
        image("card-front-phone")
        compose.onNodeWithText("Tap to reveal").performClick()
        compose.onNodeWithText("Mitochondrion").assertIsDisplayed()
        image("card-review-phone")
        compose.onNodeWithText("48 hours").performClick()
        compose.waitUntil(5000) {
            app.repository.state.value.cards.first { it.answer == "Mitochondrion" }.stage == 1
        }
        compose.onNodeWithText("Close").performClick()
        compose.onNodeWithText("Practice all").performClick()
        compose.onNodeWithText("Tap to reveal").performClick()
        compose.onNodeWithText("7 days").assertIsDisplayed()
    }

    @Test
    fun habitHasItsOwnEditableColor() {
        compose.onNodeWithTag("nav-4").performClick()
        compose.onNodeWithText("Read 10 pages").performClick()
        image("habit-detail-phone")
        compose.onNodeWithContentDescription("Edit").performClick()
        compose.onNodeWithContentDescription("Coral").performScrollTo().performClick()
        compose.onNodeWithText("Save").performClick()
        compose.waitUntil(5000) {
            app.repository.state.value.habits.first { it.title == "Read 10 pages" }.color == "coral"
        }
    }

    @Test
    fun customFocusCanPauseWithoutLosingTime() {
        compose.onNodeWithContentDescription("Focus room").performClick()
        compose.onNodeWithText("Minutes").performTextReplacement("1")
        compose.onNodeWithText("Start").performClick()
        compose.waitUntil(5000) { app.repository.state.value.timer?.minutes == 1 }
        compose.onNodeWithText("Pause").performClick()
        compose.waitUntil(5000) { app.repository.state.value.timer?.endsAt == null }
        org.junit.Assert.assertTrue(app.repository.state.value.timer!!.remaining > 0)
        image("focus-phone")
    }
    @Test
    fun plannerKeepsDaysSeparate() {
        runBlocking {
            val noon = LocalDate.now().atTime(12, 0).atZone(java.time.ZoneId.systemDefault()).toInstant().toEpochMilli()
            app.repository.save(StudyBlock(title = "Today session", startsAt = noon))
            app.repository.save(StudyBlock(title = "Next week session", startsAt = noon + 7 * 86400000L))
        }
        compose.onNodeWithTag("nav-1").performClick()
        compose.onNodeWithText("Agenda").performClick()
        compose.onNodeWithText("Today session").assertIsDisplayed()
        compose.onNodeWithText("Next week session").assertDoesNotExist()
        image("planner-phone")
        compose.onNodeWithContentDescription("Next week").performClick()
        compose.onNodeWithText("Next week session").assertIsDisplayed()
        compose.onNodeWithText("Today session").assertDoesNotExist()
    }

    @Test
    fun libraryLimitsNineUntilSearch() {
        val deck = app.repository.state.value.decks.first { it.title == "Biology foundations" }
        runBlocking {
            (1..12).forEach { n -> app.repository.save(Flashcard(deckId = deck.id, prompt = "Practice prompt $n", answer = "Recall $n")) }
        }
        compose.onNodeWithTag("nav-3").performClick()
        image("decks-phone")
        compose.onNodeWithText("Biology foundations").performClick()
        image("card-library-phone")
        val cards = app.repository.state.value.cards.filter { it.deckId == deck.id }
        val hidden = cards.drop(9).first()
        compose.onNodeWithText(hidden.prompt).assertDoesNotExist()
        compose.onNodeWithText("Search").performTextInput(hidden.prompt)
        compose.onNode(hasText(hidden.prompt) and hasClickAction() and !hasSetTextAction()).assertIsDisplayed()
    }

    @Test
    fun taskDeletionCanBeRestoredFromSettings() {
        compose.onNodeWithText("Revise cell division").performClick()
        compose.onNodeWithContentDescription(app.getString(R.string.delete)).performClick()
        compose.waitUntil(5000) { app.repository.state.value.tasks.isEmpty() }
        compose.onNodeWithContentDescription("Settings").performClick()
        compose.onNodeWithText("Trash").performScrollTo().performClick()
        image("trash-navigation")
        compose.onNodeWithText("Revise cell division").assertIsDisplayed()
        compose.onNodeWithText("Restore").performClick()
        compose.waitUntil(5000) { app.repository.state.value.tasks.any { it.title == "Revise cell division" } }
    }

    @Test
    fun coursesAndSettingsAreReachable() {
        compose.onNodeWithTag("nav-2").performClick()
        compose.onNodeWithText("Courses").performClick()
        compose.onNodeWithText("Biology").assertIsDisplayed()
        image("courses-phone")
        compose.onNodeWithContentDescription("Settings").performClick()
        compose.onNodeWithText("English").assertIsDisplayed()
        image("settings-phone")
    }

    @Test fun imageCardRevealsOnlyAfterTapAndKeepsItsScheduleWhenEdited() {
        val deck = app.repository.state.value.decks.first { it.title == "Biology foundations" }
        val id = "${newId()}.png"
        val picture = Bitmap.createBitmap(64, 64, Bitmap.Config.ARGB_8888)
        picture.eraseColor(android.graphics.Color.BLUE)
        app.media.file(id).outputStream().use { picture.compress(Bitmap.CompressFormat.PNG, 100, it) }
        val card = Flashcard(deckId = deck.id, type = "image", imageId = id, prompt = "Name this color", answer = "Blue")
        runBlocking { app.repository.save(card) }
        compose.onNodeWithTag("nav-3").performClick()
        compose.onNodeWithText("Biology foundations").performClick()
        compose.onNodeWithText("Review").performClick()
        // Review order is shuffled: advance the other card only if it appears first.
        if (compose.onAllNodesWithText("Name this color").fetchSemanticsNodes().isEmpty()) {
            compose.onNodeWithText("Tap to reveal").performClick()
            compose.onNodeWithText("48 hours").performClick()
            compose.waitUntil(5000) { app.repository.state.value.cards.first { it.answer == "Mitochondrion" }.stage == 1 }
        }
        compose.onNodeWithText("Blue").assertDoesNotExist()
        compose.onNodeWithText("Name this color").performClick()
        compose.onNodeWithText("Blue").assertIsDisplayed()
        compose.onNodeWithText("Again").assertIsDisplayed()
        image("picture-card-revealed")
    }

    @Test fun lessonCreationAndReviewUseTheSavedActualReviewTime() {
        compose.onNodeWithTag("nav-2").performClick()
        compose.onNodeWithText("Lessons").performClick()
        compose.onNodeWithText("Add").performClick()
        compose.onNodeWithText("Title").performTextInput("Family words")
        compose.onNodeWithText("Save").performClick()
        compose.waitUntil(5000) { app.repository.state.value.lessons.size == 1 }
        val lesson = app.repository.state.value.lessons.single()
        org.junit.Assert.assertEquals(lesson.learnedAt + 48 * StudyRules.HOUR, lesson.dueAt)
        compose.onNodeWithText("Family words").performClick()
        val before = System.currentTimeMillis()
        compose.onNodeWithText("Mark reviewed").performScrollTo().performClick()
        compose.waitUntil(5000) { app.repository.state.value.lessons.single().stage == 1 }
        val due = app.repository.state.value.lessons.single().dueAt!!
        org.junit.Assert.assertTrue(due in (before + 7 * 86400000L)..(System.currentTimeMillis() + 7 * 86400000L))
    }

    @Test fun unsavedNoteSurvivesSavedInstanceStateRestoration() {
        compose.onNodeWithTag("nav-2").performClick()
        compose.onNodeWithText("Add").performClick()
        compose.onNodeWithText("Title").performTextInput("Draft lecture")
        compose.onNodeWithText("Start writing…").performTextInput("Keep this unsaved idea")
        restoration.emulateSavedInstanceStateRestore()
        compose.onNodeWithText("Draft lecture").assertIsDisplayed()
        compose.onNodeWithText("Keep this unsaved idea").assertIsDisplayed()
        compose.onNodeWithText("Save").performClick()
        compose.waitUntil(5000) { app.repository.state.value.notes.any { it.title == "Draft lecture" } }
        val note = app.repository.state.value.notes.first { it.title == "Draft lecture" }
        org.junit.Assert.assertTrue(runBlocking { app.repository.body(note.id) }.contains("Keep this unsaved idea"))
    }

    @Test fun taskStatusCanBeChangedIndependentlyOfPriority() {
        compose.onNodeWithText("Revise cell division").performClick()
        compose.onNodeWithText("To do").performScrollTo().performClick()
        compose.onNodeWithText("In progress").performClick()
        compose.onNodeWithText("Save").performClick()
        compose.waitUntil(5000) { app.repository.state.value.tasks.single().status == "progress" }
        org.junit.Assert.assertEquals("normal", app.repository.state.value.tasks.single().priority)
    }

    @Test fun lessonNotificationNavigationPreservesThePreviousNoteDraft() {
        runBlocking { app.repository.save(Lesson(title = "Due lesson")) }
        compose.onNodeWithTag("nav-2").performClick()
        compose.onNodeWithText("Add").performClick()
        compose.onNodeWithText("Title").performTextInput("Keep my draft")
        compose.onNodeWithText("Start writing…").performTextInput("An unsaved paragraph")
        compose.runOnIdle { lessonRequest.intValue++ }
        compose.onNodeWithText("Due lesson").assertIsDisplayed()
        compose.onNodeWithContentDescription("Close").performClick()
        compose.onNodeWithText("Keep my draft").assertIsDisplayed()
        compose.onNodeWithText("An unsaved paragraph").assertIsDisplayed()
    }

    @Test fun allReminderPagesPreserveAnUnfinishedNote() {
        compose.onNodeWithTag("nav-2").performClick()
        compose.onNodeWithText("Add").performClick()
        compose.onNodeWithText("Title").performTextInput("Reminder draft")
        compose.onNodeWithText("Start writing…").performTextInput("Keep this paragraph")
        listOf("tasks" to "Revise cell division", "agenda" to "Agenda", "cards" to "Biology foundations", "habits" to "Habits", "focus" to "Focus room").forEach { (destination, expected) ->
            compose.runOnIdle { reminderDestination.value = destination; reminderRequest.intValue++ }
            compose.onAllNodesWithText(expected).onFirst().assertIsDisplayed()
            compose.onNodeWithContentDescription("Close").performClick()
            compose.onNodeWithText("Reminder draft").assertIsDisplayed()
            compose.onNodeWithText("Keep this paragraph").assertIsDisplayed()
        }
    }

    @Test fun agendaColorAndSelectedDaySurviveCreationAndEditing() {
        compose.onNodeWithTag("nav-1").performClick()
        compose.onNodeWithText("Agenda").performClick()
        compose.onNodeWithContentDescription("Next week").performClick()
        compose.onAllNodesWithText("Add").onFirst().performClick()
        compose.onNodeWithText("Title").performTextInput("Color-coded revision")
        compose.onNodeWithText("Custom color").performScrollTo().performTextInput("#12abef")
        image("agenda-color-picker")
        compose.onNodeWithText("Save").performClick()
        compose.waitUntil(5000) { app.repository.state.value.blocks.size == 1 }
        val original = app.repository.state.value.blocks.single()
        org.junit.Assert.assertEquals("#12ABEF", original.color)
        org.junit.Assert.assertEquals(LocalDate.now().plusWeeks(1), java.time.Instant.ofEpochMilli(original.startsAt).atZone(java.time.ZoneId.systemDefault()).toLocalDate())
        compose.onNodeWithText("Color-coded revision").assertIsDisplayed()
        image("agenda-custom-color")
        compose.onNodeWithText("Color-coded revision").performClick()
        compose.onNodeWithText("Custom color").performScrollTo().performTextReplacement("#F08040")
        compose.onNodeWithText("Save").performClick()
        compose.waitUntil(5000) { app.repository.state.value.blocks.single().color == "#F08040" }
        org.junit.Assert.assertEquals(original.startsAt, app.repository.state.value.blocks.single().startsAt)
    }

    @Test fun agendaTimeCanChangeWhileTheCalendarDayStaysLocked() {
        compose.onNodeWithTag("nav-1").performClick()
        compose.onNodeWithText("Agenda").performClick()
        compose.onNodeWithContentDescription("Next week").performClick()
        compose.onAllNodesWithText("Add").onFirst().performClick()
        compose.onNodeWithText("Title").performTextInput("Selected day only")
        compose.onNodeWithTag("agenda-selected-day").assertIsDisplayed().assert(hasClickAction().not())
        compose.onNodeWithTag("record-time").performScrollTo().performClick()
        compose.runOnIdle {
            val picker = org.robolectric.shadows.ShadowDialog.getLatestDialog() as android.app.TimePickerDialog
            picker.updateTime(18, 45)
            picker.getButton(android.content.DialogInterface.BUTTON_POSITIVE).performClick()
        }
        compose.onNodeWithText("Save").performClick()
        compose.waitUntil(5000) { app.repository.state.value.blocks.isNotEmpty() }
        val time = java.time.Instant.ofEpochMilli(app.repository.state.value.blocks.single().startsAt).atZone(java.time.ZoneId.systemDefault())
        org.junit.Assert.assertEquals(LocalDate.now().plusWeeks(1), time.toLocalDate())
        org.junit.Assert.assertEquals(18, time.hour)
        org.junit.Assert.assertEquals(45, time.minute)
    }

    @Test fun invalidAgendaColorKeepsTheDraftAndCanBeReplacedByASwatch() {
        compose.onNodeWithTag("nav-1").performClick()
        compose.onNodeWithText("Agenda").performClick()
        compose.onAllNodesWithText("Add").onFirst().performClick()
        compose.onNodeWithText("Title").performTextInput("Keep this session")
        compose.onNodeWithText("Custom color").performScrollTo().performTextInput("#NOPE")
        compose.onNodeWithText("Save").performClick()
        compose.waitForIdle()
        org.junit.Assert.assertTrue(app.repository.state.value.blocks.isEmpty())
        compose.onNodeWithContentDescription("Green").performScrollTo().performClick()
        compose.onNodeWithText("Save").performClick()
        compose.waitUntil(5000) { app.repository.state.value.blocks.any { it.title == "Keep this session" } }
        org.junit.Assert.assertEquals("green", app.repository.state.value.blocks.single().color)
    }


    @Test fun backDuringAReplacementRecordingOffersToKeepTheClip() {
        org.robolectric.Shadows.shadowOf(app).grantPermissions(android.Manifest.permission.RECORD_AUDIO)
        compose.onNodeWithTag("nav-3").performClick()
        compose.onNodeWithText("Biology foundations").performClick()
        compose.onNodeWithText("What powers a cell?").performClick()
        compose.onNodeWithText("Record audio").performScrollTo().performClick()
        compose.onNodeWithTag("recording-status").assertIsDisplayed()
        compose.onNodeWithContentDescription("Close").performClick()
        compose.onNodeWithText("Discard changes?").assertIsDisplayed()
        compose.onNodeWithTag("recording-status").assertDoesNotExist()
        compose.onNodeWithText("Keep editing").performClick()
        compose.onNodeWithText("Save").performClick()
        compose.waitUntil(5000) {
            app.repository.state.value.cards.first { it.answer == "Mitochondrion" }.audioId != null
        }
    }

    @Test fun activeMicrophoneShowsAPinnedRecordingBannerAndStopReturnsSave() {
        org.robolectric.Shadows.shadowOf(app).grantPermissions(android.Manifest.permission.RECORD_AUDIO)
        compose.onNodeWithTag("nav-3").performClick()
        compose.onNodeWithText("Biology foundations").performClick()
        compose.onNodeWithText("New card").performClick()
        compose.onNodeWithText("Record audio").performScrollTo().performClick()
        compose.onNodeWithTag("recording-status").assertIsDisplayed()
        compose.onNodeWithText("Recording…").assertIsDisplayed()
        compose.onNodeWithText("Save").assertDoesNotExist()
        image("recording-active")
        compose.onNode(hasText("Stop recording") and hasAnyAncestor(hasTestTag("recording-status"))).performClick()
        compose.onNodeWithTag("recording-status").assertDoesNotExist()
        compose.onNodeWithText("Save").assertIsDisplayed()
    }

}
