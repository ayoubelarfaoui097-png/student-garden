package com.jdinohaila.studentgarden

import android.graphics.Bitmap
import androidx.activity.ComponentActivity
import androidx.compose.runtime.*
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.core.app.ApplicationProvider
import com.jdinohaila.studentgarden.domain.*
import com.jdinohaila.studentgarden.ui.screens.CardReview
import com.jdinohaila.studentgarden.ui.theme.GardenTheme
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.*
import kotlin.math.abs

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w360dp-h800dp-xhdpi", application = GardenApp::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@OptIn(coil3.annotation.DelicateCoilApi::class)
class CardReviewUiTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()
    private val restoration = StateRestorationTester(compose)
    private lateinit var app: GardenApp
    private val deck = Deck(title = "German numbers")

    @Before fun setup() = runBlocking {
        app = ApplicationProvider.getApplicationContext()
        // Robolectric's ImageDecoder cannot decode Coil file sources. Keep real PNG
        // decoding through BitmapFactory for these layout checks; device QA uses
        // the application's default ImageDecoder without this test override.
        coil3.SingletonImageLoader.setUnsafe(coil3.ImageLoader.Builder(app)
            .components { add(coil3.decode.BitmapFactoryDecoder.Factory()) }.build())
        app.deleteDatabase("garden.db")
        app.repository.load()
        app.repository.save(deck)
    }

    @After fun resetImageLoader() { coil3.SingletonImageLoader.reset() }

    private fun open(cards: List<Flashcard>, practice: Boolean = false) {
        runBlocking { cards.forEach { app.repository.save(it) } }
        restoration.setContent { GardenTheme {
            val state by app.repository.state.collectAsState()
            val scope = rememberCoroutineScope()
            CardReview(deck.id, practice, state, app.repository, app.media, {}, { action -> scope.launch { action() } })
        } }
    }
    private fun picture() = "${newId()}.png".also { id ->
        val bitmap = Bitmap.createBitmap(80, 80, Bitmap.Config.ARGB_8888)
        bitmap.eraseColor(android.graphics.Color.rgb(0, 82, 204))
        app.media.file(id).outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }
    private fun centered() {
        compose.waitForIdle()
        val frame = compose.onNodeWithTag("review-card", true).fetchSemanticsNode().boundsInRoot
        val content = compose.onNodeWithTag("review-content", true).fetchSemanticsNode().boundsInRoot
        assertTrue("Content must fit for this centering check", content.height < frame.height)
        assertTrue("Content center ${content.center.y}, frame center ${frame.center.y}", abs(content.center.y - frame.center.y) <= 2f)
    }
    private fun image(name: String) {
        compose.waitForIdle()
        compose.runOnIdle {
            val view = compose.activity.window.decorView
            val bitmap = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
            view.draw(android.graphics.Canvas(bitmap))
            val file = java.io.File("build/qa/$name.png")
            file.parentFile!!.mkdirs()
            file.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        }
    }
    private fun question() = compose.onNodeWithTag("review-question", true).fetchSemanticsNode().config[SemanticsProperties.Text].joinToString("") { it.text }

    @Test fun blankQuestionHasNoDividerAndThePictureAndAnswerAreCentered() {
        open(listOf(Flashcard(deckId = deck.id, type = "image", prompt = " \n\t", answer = "einhundert", imageId = picture())))
        compose.onNodeWithTag("review-question", true).assertDoesNotExist()
        compose.onNodeWithTag("review-divider", true).assertDoesNotExist()
        compose.waitUntil(5000) { compose.onAllNodesWithTag("review-image", true).fetchSemanticsNodes().singleOrNull()?.boundsInRoot?.height?.let { it > 0f } == true }
        compose.onNodeWithTag("review-image", true).assertIsDisplayed()
        compose.onNodeWithText("einhundert").assertDoesNotExist()
        centered()
        image("picture-without-question-front")
        compose.onNodeWithText("Tap to reveal").performClick()
        compose.onNodeWithTag("review-question", true).assertDoesNotExist()
        compose.onNodeWithTag("review-divider", true).assertDoesNotExist()
        compose.onNodeWithText("einhundert").assertIsDisplayed()
        centered()
        image("picture-without-question-answer")
    }

    @Test fun aRealQuestionKeepsItsDividerAndTapRevealBehavior() {
        open(listOf(Flashcard(deckId = deck.id, prompt = "Which number?", answer = "eins")))
        compose.onNodeWithText("Which number?").assertIsDisplayed()
        compose.onNodeWithText("eins").assertDoesNotExist()
        centered()
        compose.onNodeWithText("Tap to reveal").performClick()
        compose.onNodeWithTag("review-divider", true).assertIsDisplayed()
        compose.onNodeWithText("eins").assertIsDisplayed()
        centered()
    }

    @Test fun aNewlyDueCardDoesNotInterruptTheRevealedCard() {
        val first = Flashcard(deckId = deck.id, prompt = "Number 1", answer = "eins")
        val later = Flashcard(deckId = deck.id, prompt = "Number 2", answer = "zwei", dueAt = System.currentTimeMillis() + 3600000)
        open(listOf(first, later))
        compose.onNodeWithText("Tap to reveal").performClick()
        runBlocking { app.repository.save(later.copy(dueAt = System.currentTimeMillis() - 1)) }
        compose.onNodeWithText("Number 1").assertIsDisplayed()
        compose.onNodeWithText("eins").assertIsDisplayed()
        compose.onNodeWithText("48 hours").performClick()
        compose.waitUntil(5000) { app.repository.state.value.cards.first { it.id == first.id }.stage == 1 }
        compose.waitUntil(5000) { compose.onAllNodesWithText("Tap to reveal").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText("Number 2").assertIsDisplayed()
        compose.onNodeWithText("zwei").assertDoesNotExist()
        assertEquals(1, app.repository.state.value.cards.first { it.id == first.id }.stage)
    }

    @Test fun sessionOrderAndRevealSurviveRecreationAndPracticeVisitsEachCardOnce() {
        val cards = (1..12).map { Flashcard(deckId = deck.id, prompt = "Number $it", answer = "Answer $it", dueAt = System.currentTimeMillis() - it * 60000) }
        open(cards, practice = true)
        val first = question()
        compose.mainClock.advanceTimeBy(2500)
        assertEquals(first, question())
        compose.onNodeWithText("Tap to reveal").performClick()
        restoration.emulateSavedInstanceStateRestore()
        assertEquals(first, question())
        compose.onNodeWithText("Again").assertIsDisplayed()
        val visited = mutableListOf<String>()
        repeat(cards.size) { index ->
            visited += question()
            if (index > 0) compose.onNodeWithText("Tap to reveal").performClick()
            compose.onNodeWithText("48 hours").performClick()
            compose.waitUntil(5000) { app.repository.state.value.cards.count { it.stage == 1 } == index + 1 }
            if (index < cards.lastIndex) compose.waitUntil(5000) { compose.onAllNodesWithText("Tap to reveal").fetchSemanticsNodes().isNotEmpty() }
        }
        assertEquals(cards.map { it.prompt }.toSet(), visited.toSet())
        assertEquals(cards.size, visited.size)
        compose.onNodeWithText("Close").assertIsDisplayed()
        assertTrue(app.repository.state.value.cards.all { it.stage == 1 })
    }

    @Test @Config(qualifiers = "w320dp-h640dp-hdpi")
    fun longContentStillScrollsOnSmallPhonesAndRatingControlsStayReachable() {
        open(listOf(Flashcard(deckId = deck.id, prompt = "A longer question. ".repeat(40), answer = "A longer answer. ".repeat(40))))
        compose.onNodeWithText("Tap to reveal").assertIsDisplayed().performClick()
        compose.onNodeWithTag("review-answer", true).performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Again").assertIsDisplayed()
        compose.onNodeWithText("48 hours").assertIsDisplayed()
        image("review-long-content-small-phone")
    }
}
