package com.spartan.ui

import android.app.Application
import android.content.Context
import android.content.pm.ActivityInfo
import androidx.activity.ComponentActivity
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isHeading
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.test.core.app.ApplicationProvider
import com.spartan.BuildConfig
import com.spartan.R
import com.spartan.data.local.UserProfileEntity
import com.spartan.domain.engine.PressureWindow
import com.spartan.domain.engine.SexAtBirth
import com.spartan.ui.screens.MainUiState
import com.spartan.ui.screens.PressureWindowsScreen
import com.spartan.ui.screens.YOU_PRIVACY_CARD_TAG
import com.spartan.ui.screens.YouScreen
import com.spartan.ui.theme.Spacing
import com.spartan.ui.theme.SpartanTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.ExternalResource
import org.junit.rules.RuleChain
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * The You list: a profile header, then five groups in order (Your plan, Data and devices, Display,
 * Privacy, About) with their rows; High-pressure windows and Healthy ranges each explained in their
 * subtitle, truthfully; switches that show the real setting; one About row instead of the inline
 * disclaimer and version; no badge or claim. The window is tall enough that the whole list composes.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class, qualifiers = "w360dp-h4000dp")
class YouScreenTest {

    private val composeRule = createComposeRule()

    /** As in LottieMomentsTest: release-variant unit tests have no ui-test-manifest activity entry. */
    @get:Rule
    val rules: RuleChain = RuleChain.outerRule(object : ExternalResource() {
        override fun before() {
            val app = ApplicationProvider.getApplicationContext<Context>()
            Shadows.shadowOf(app.packageManager).addOrUpdateActivity(
                ActivityInfo().apply {
                    name = ComponentActivity::class.java.name
                    packageName = app.packageName
                },
            )
        }
    }).around(composeRule)

    private val context: Context = ApplicationProvider.getApplicationContext()

    private fun show(
        state: MainUiState = MainUiState(),
        fontScale: Float? = null,
        onOpenAbout: () -> Unit = {},
        onOpenDiagnostics: (() -> Unit)? = {},
    ) {
        composeRule.setContent {
            val density = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(density.density, fontScale ?: density.fontScale)) {
                SpartanTheme {
                    YouScreen(
                        state = state,
                        onSaveDemographics = { _, _ -> },
                        onSaveGoal = { _, _, _ -> },
                        onOpenProgress = {},
                        onOpenTraining = {},
                        onOpenWindows = {},
                        onOpenRanges = {},
                        onOpenConnections = {},
                        onOpenReminders = {},
                        onOpenPrivacy = {},
                        onOpenDiagnostics = onOpenDiagnostics,
                        onOpenAbout = onOpenAbout,
                    )
                }
            }
        }
    }

    private fun SemanticsNode.text(): String =
        config.getOrNull(SemanticsProperties.Text)?.joinToString(" ") { it.text }.orEmpty()

    /** Every Text on screen, top to bottom, one entry per Text composable. */
    private fun allTexts(): List<String> =
        composeRule.onAllNodes(SemanticsMatcher.keyIsDefined(SemanticsProperties.Text), useUnmergedTree = true)
            .fetchSemanticsNodes()
            .map { it.text() }

    private fun assertAbsent(text: String) {
        val found = composeRule.onAllNodesWithText(text, substring = true, ignoreCase = true, useUnmergedTree = true).fetchSemanticsNodes()
        assertTrue("\"$text\" should not be on screen", found.isEmpty())
    }

    private val editsProfile = SemanticsMatcher("edits the profile") {
        it.config.getOrNull(SemanticsActions.OnClick)?.label == "Edit your profile"
    }

    /**
     * How much taller the Privacy card is than its own vertical padding plus the rows whose
     * subtitles are given, in dp: 0 with nothing else inside, 1 with one divider between rows.
     */
    private fun privacyCardExtraDp(vararg rowSubtitles: String): Float {
        val card = composeRule.onNodeWithTag(YOU_PRIVACY_CARD_TAG).fetchSemanticsNode()
        val rows = rowSubtitles.sumOf { subtitle ->
            composeRule.onNode(hasClickAction() and hasText(subtitle)).fetchSemanticsNode().boundsInRoot.height.toDouble()
        }
        return ((card.boundsInRoot.height - rows) / card.layoutInfo.density.density).toFloat() - 2 * Spacing.xs.value
    }

    @Test
    fun headings_areTheTitleThenTheFiveGroupsInOrder() {
        show()

        val headings = composeRule.onAllNodes(isHeading(), useUnmergedTree = true).fetchSemanticsNodes().map { it.text() }
        assertEquals(listOf("You", "Your plan", "Data and devices", "Display", "Privacy", "About"), headings)
    }

    @Test
    fun rows_sitInTheirGroupsInOrder() {
        show()

        val expected = listOf(
            "You", "Your profile",
            "Your plan", "Goal", "Weekly plan", "Plan settings", "Reminders", "High-pressure windows",
            "Data and devices", "Connections", "Healthy ranges",
            "Display", "Calmer theme", "Wallpaper colours",
            "Privacy", "Privacy", "Diagnostics",
            "About", "About Spartan",
        )
        assertEquals(expected, allTexts().filter { it in expected })
    }

    @Test
    fun goalRow_unset_saysNotSetAndPicksOneGoal() {
        show()

        composeRule.onNodeWithText("Not set · pick one focused goal").assertExists()
    }

    @Test
    fun windowsRow_noneSet_saysTheNudgeComesOnDaysYouOpenSpartan() {
        show()

        composeRule.onNodeWithText(
            "None set · on days you open Spartan, a breathing-reset nudge 5 minutes before a tense moment",
        ).assertExists()
    }

    @Test
    fun windowsRow_countsTheWindowsSet_oneAndMany() {
        val window = PressureWindow(id = "a", daysOfWeekMask = 0b10, startMinuteOfDay = 11 * 60, endMinuteOfDay = 12 * 60)
        show(MainUiState(pressureWindows = listOf(window, window.copy(id = "b"))))

        composeRule.onNodeWithText(
            "2 set · on days you open Spartan, a breathing-reset nudge 5 minutes before each",
        ).assertExists()
    }

    @Test
    fun windowsRow_oneWindow_readsInTheSingular() {
        val window = PressureWindow(id = "a", daysOfWeekMask = 0b10, startMinuteOfDay = 11 * 60, endMinuteOfDay = 12 * 60)
        show(MainUiState(pressureWindows = listOf(window)))

        composeRule.onNodeWithText(
            "1 set · on days you open Spartan, a breathing-reset nudge 5 minutes before it",
        ).assertExists()
    }

    @Test
    fun windowsLeadTime_isPluralSafe() {
        assertEquals("1 minute", context.resources.getQuantityString(R.plurals.you_windows_lead, 1, 1))
        assertEquals("5 minutes", context.resources.getQuantityString(R.plurals.you_windows_lead, 5, 5))
    }

    /** The page behind the row: the real lead time, the open-the-app condition, no raw placeholder. */
    @Test
    fun windowsPage_introStatesTheRealMinutesAndWhenTheNudgeComes() {
        composeRule.setContent {
            SpartanTheme { PressureWindowsScreen(MainUiState(), onAddWindow = { _, _, _, _ -> }, onRemoveWindow = {}, onBack = {}) }
        }

        composeRule.onNodeWithText(
            "On days you open Spartan, a notification 5 minutes before each window starts suggests a short breathing reset.",
            substring = true,
        ).assertExists()
        composeRule.onNodeWithText("Spartan sets them up when you open it, so one whose time has already passed is skipped.", substring = true)
            .assertExists()
        assertAbsent("%")
    }

    @Test
    fun rangesRow_withNoProfile_namesAdults_notYourAgeAndSex() {
        show()

        composeRule.onNodeWithText(
            "General ranges for adults, shown beside your own numbers for context · not targets, not a diagnosis",
        ).assertExists()
        assertAbsent("your age and sex")
    }

    @Test
    fun rangesRow_withAProfile_namesTheSameCohortAsThePage() {
        show(MainUiState(userAgeYears = 34, userSexAtBirth = SexAtBirth.MALE))

        composeRule.onNodeWithText(
            "General ranges for men aged 30–39, shown beside your own numbers for context · not targets, not a diagnosis",
        ).assertExists()
    }

    @Test
    fun switches_showTheRealSetting_onAndOff() {
        show(MainUiState(calmerTheme = true, wallpaperColours = false))

        composeRule.onNodeWithText("Calmer theme").assertIsOn()
        composeRule.onNodeWithText("Wallpaper colours").assertIsOff()
        composeRule.onNodeWithText("Colours from your wallpaper").assertExists()
    }

    @Test
    fun switches_showTheRealSetting_offAndOn() {
        show(MainUiState(calmerTheme = false, wallpaperColours = true))

        composeRule.onNodeWithText("Calmer theme").assertIsOff()
        composeRule.onNodeWithText("Wallpaper colours").assertIsOn()
        // With wallpaper colours on, the calmer theme promises only its motion half.
        composeRule.onNodeWithText("No celebration motion · colours come from your wallpaper").assertExists()
    }

    @Test
    fun debugBuild_privacyAndDiagnostics_areSplitByOneDivider() {
        show()

        composeRule.onNodeWithText("Diagnostics").assertExists()
        // The measure sees a divider: this is the positive control for the release case below.
        assertEquals(1f, privacyCardExtraDp("Export or delete your data on this device", "Debug builds only: operational log"), 0.5f)
    }

    @Test
    fun releaseBuild_hasNoDiagnosticsRow_andNoStrayDivider() {
        show(onOpenDiagnostics = null)

        assertAbsent("Diagnostics")
        assertAbsent("operational log")
        // The card holds its padding and the Privacy row, and nothing under it.
        assertEquals(0f, privacyCardExtraDp("Export or delete your data on this device"), 0.5f)
    }

    @Test
    fun aboutIsOneRow_inPlainWords_withNoVersionDisclaimerOrLicenceInTheList() {
        var opened = false
        show(onOpenAbout = { opened = true })

        composeRule.onNodeWithText("Version, disclaimer and licences").assertExists()
        assertAbsent(BuildConfig.VERSION_NAME)
        assertAbsent("general-wellness planner")
        assertAbsent(context.getString(R.string.today_in_context))
        assertAbsent(context.getString(R.string.about_privacy_line))
        assertAbsent("SIL Open Font License")

        composeRule.onNodeWithText("About Spartan").performClick()
        assertTrue(opened)
    }

    @Test
    fun noBadgeOrClaim() {
        show(MainUiState(profile = UserProfileEntity(displayName = "Mark")))

        assertAbsent("Premium")
        assertAbsent("Award")
    }

    @Test
    fun profileHeader_showsNameAndFacts_andEditsWithA48dpTarget() {
        show(MainUiState(profile = UserProfileEntity(displayName = "Mark"), userAgeYears = 41))

        composeRule.onNodeWithText("Mark").assertExists()
        composeRule.onNodeWithText("Age 41").assertExists()
        composeRule.onNode(editsProfile).assertHeightIsAtLeast(48.dp)
    }

    /** The avatar initial is decorative: TalkBack hears "Mark, Age 41", not "M, Mark, Age 41". */
    @Test
    fun profileHeader_announcesNameAndDetailsOnly_notTheAvatarInitial() {
        show(MainUiState(profile = UserProfileEntity(displayName = "Mark"), userAgeYears = 41))

        val announced = composeRule.onNode(editsProfile).fetchSemanticsNode().config[SemanticsProperties.Text].map { it.text }
        assertEquals(listOf("Mark", "Age 41"), announced)
    }

    @Test
    fun profileHeader_sexOnly_showsTheSexAndInvitesOnlyTheAge() {
        show(MainUiState(profile = UserProfileEntity(displayName = "Mark"), userSexAtBirth = SexAtBirth.FEMALE))

        composeRule.onNodeWithText("Female · add your age for tighter ranges").assertExists()
        assertAbsent("Add age and sex")
    }

    @Test
    fun profileHeader_neitherSet_invitesBoth() {
        show(MainUiState(profile = UserProfileEntity(displayName = "Mark")))

        composeRule.onNodeWithText("Add age and sex for tighter ranges and better video picks").assertExists()
    }

    @Test
    fun everyTarget_isAtLeast48dp() {
        show()

        val targets = composeRule.onAllNodes(hasClickAction()).fetchSemanticsNodes()
        assertTrue(targets.size >= 13)
        targets.forEach { node ->
            val heightDp = node.boundsInRoot.height / node.layoutInfo.density.density
            assertTrue("\"${node.config.getOrNull(SemanticsProperties.Text)}\" is ${heightDp}dp tall", heightDp >= 48f)
        }
    }

    /**
     * At 200% (Android 14's non-linear scale, as on a device) every Text wraps inside the screen:
     * no line or height is cut off, nothing runs past the right edge, and long subtitles take more
     * than one line instead of ellipsizing. Native graphics, so glyphs are really measured.
     */
    @Test
    @GraphicsMode(GraphicsMode.Mode.NATIVE)
    fun at200PercentFont_textWrapsAndNothingIsClipped() {
        show(MainUiState(profile = UserProfileEntity(displayName = "Mark"), userAgeYears = 41), fontScale = 2f)

        val rootWidth = composeRule.onRoot().fetchSemanticsNode().boundsInRoot.width
        val texts = composeRule.onAllNodes(SemanticsMatcher.keyIsDefined(SemanticsActions.GetTextLayoutResult), useUnmergedTree = true)
            .fetchSemanticsNodes()
        assertTrue(texts.size >= 30)
        val lines = texts.associate { node ->
            val layouts = mutableListOf<TextLayoutResult>()
            node.config[SemanticsActions.GetTextLayoutResult].action?.invoke(layouts)
            val layout = layouts.single()
            assertFalse("\"${node.text()}\" is cut off at 200%", layout.didOverflowHeight || layout.multiParagraph.didExceedMaxLines)
            assertTrue("\"${node.text()}\" runs off screen at 200%", node.boundsInRoot.right <= rootWidth)
            node.text() to layout.lineCount
        }
        assertTrue(lines.getValue("General ranges for adults aged 40–49, shown beside your own numbers for context · not targets, not a diagnosis") > 1)
        assertTrue(lines.getValue("Quieter colours and no celebration motion") > 1)
    }
}
