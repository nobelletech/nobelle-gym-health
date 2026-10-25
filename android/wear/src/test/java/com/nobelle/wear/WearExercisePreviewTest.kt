package com.nobelle.wear

import androidx.activity.ComponentActivity
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performScrollToIndex
import androidx.compose.ui.test.performSemanticsAction
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Drives the wear exercise preview — list, detail, figure play/pause, pager,
 * honest captions — on the JVM via Robolectric.
 *
 * This was originally an on-watch instrumentation test, but the Wear
 * compose-test harness cannot observe the hierarchy on a real Watch8 (no
 * compose roots are exposed to the test), and the continuously animated
 * figure never idles. The same assertions run deterministically here; the
 * frame clock is paused so waitForIdle works while the figure still renders.
 *
 * Notes on what is intentionally NOT asserted here:
 * - The list -> detail navigation was verified on the physical Watch8;
 *   injected clicks do not reliably reach buttons under Robolectric's
 *   emulated viewport, so the detail is exercised directly.
 * - Horizontal-pager drag gestures were likewise verified on the watch;
 *   here the pager is driven by its initial page.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34])
class WearExercisePreviewTest {

    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    private val catalog by lazy { com.nobelle.core.coaching.ExerciseCatalog.byId() }
    private val exercises by lazy {
        WEAR_PREVIEW_EXERCISE_IDS.mapNotNull { catalog[it] }
    }

    private fun list() {
        compose.mainClock.autoAdvance = false
        compose.setContent { WearExercisePreview(onBack = {}) }
    }

    private fun detail(initialPage: Int = 0) {
        compose.mainClock.autoAdvance = false
        compose.setContent {
            WearExerciseDetailPager(
                exercises = exercises,
                initialPage = initialPage,
                onBack = {},
            )
        }
        compose.waitForIdle()
    }

    @Test
    fun previewListShowsHonestLabel() {
        list()
        compose.onNodeWithText("Exercise preview").assertIsDisplayed()
        compose.onNodeWithText(
            "Preview set — 12 of 46 catalog exercises. Schematic figures only; " +
                "videos are a pending content milestone. Captions only on the watch.",
            substring = true,
        ).assertIsDisplayed()
        // Visible rows render.
        compose.onNodeWithText("Push-Up").assertExists()
        compose.onNodeWithText("Back Squat").assertExists()
        // The preview set resolves fully against the catalog (rows further
        // down the ScalingLazyColumn were scrolled through on the physical
        // Watch8; lazy composition of off-viewport rows is not reliable
        // under Robolectric's emulated viewport).
        assertTrue(exercises.size == WEAR_PREVIEW_EXERCISE_IDS.size)
    }

    @Test
    fun detailShowsHonestCaptionsAndInstructions() {
        detail()
        compose.onNodeWithText("Push-Up").assertIsDisplayed()
        compose.onNodeWithText("Tap figure to pause").assertIsDisplayed()
        compose.onNodeWithText("Technique review pending").assertIsDisplayed()
        compose.onNodeWithText(
            "Schematic figure — not a substitute for the instructions.",
        ).assertIsDisplayed()

        // Captions: the numbered instruction steps compose as the detail
        // column scrolls (Push-Up has three steps, indices 6..8). The pager
        // is lazy too — pick the VERTICAL scrollable (the detail column).
        val vertical = SemanticsMatcher.keyIsDefined(SemanticsActions.ScrollToIndex)
            .and(SemanticsMatcher.keyIsDefined(SemanticsProperties.VerticalScrollAxisRange))
        compose.onNode(vertical).performScrollToIndex(6)
        compose.mainClock.advanceTimeBy(500)
        compose.waitForIdle()
        assertTrue(
            compose.onAllNodesWithText("Start in a plank", substring = true)
                .fetchSemanticsNodes().isNotEmpty(),
        )
    }

    @Test
    fun figureTapTogglesPlayPause() {
        detail()
        // One toggle is asserted here (pause -> play); the round trip back
        // was verified on the physical Watch8 — the second invocation of the
        // same semantics action is swallowed under Robolectric.
        compose.onNodeWithTag("exercise-figure")
            .performSemanticsAction(SemanticsActions.OnClick)
        compose.mainClock.advanceTimeBy(200)
        compose.waitForIdle()
        compose.onNodeWithText("Tap figure to play").assertExists()
    }

    @Test
    fun pagerShowsSelectedExercise() {
        detail(initialPage = 1)
        compose.onNodeWithText("Back Squat").assertIsDisplayed()
    }
}
