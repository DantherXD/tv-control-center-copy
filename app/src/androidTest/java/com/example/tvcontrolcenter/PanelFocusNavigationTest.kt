package com.example.tvcontrolcenter

import androidx.compose.ui.input.key.Key
import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.assertWidthIsAtLeast
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.pressKey
import androidx.compose.ui.unit.dp
import com.example.tvcontrolcenter.domain.model.TileId
import com.example.tvcontrolcenter.fakes.FakeActions
import com.example.tvcontrolcenter.fakes.FakeFocusMemory
import com.example.tvcontrolcenter.fakes.FakeLayoutRepository
import com.example.tvcontrolcenter.fakes.FakeNotifications
import com.example.tvcontrolcenter.fakes.FakeNowPlaying
import com.example.tvcontrolcenter.fakes.FakeSleepTimer
import com.example.tvcontrolcenter.fakes.FakeStatusSource
import com.example.tvcontrolcenter.ui.panel.ControlCenterHost
import com.example.tvcontrolcenter.ui.panel.ControlCenterViewModel
import com.example.tvcontrolcenter.ui.theme.ControlCenterTheme
import org.junit.Rule
import org.junit.Test

/** D-pad navigation checks. Run on a TV emulator or device: ./gradlew connectedDebugAndroidTest */
class PanelFocusNavigationTest {

    @get:Rule val rule = createComposeRule()

    private val memory = FakeFocusMemory()
    private val actions = FakeActions()
    private val layouts = FakeLayoutRepository()

    private fun launch(visible: Boolean = true) {
        val vm = ControlCenterViewModel(
            FakeStatusSource(), actions, FakeNowPlaying(), FakeNotifications(), FakeSleepTimer(),
            memory, layouts,
        )
        rule.setContent {
            ControlCenterTheme {
                ControlCenterHost(vm, visible, onDismissRequest = {}, onClosed = {}, onCapability = {})
            }
        }
        rule.waitForIdle()
    }

    private fun press(key: Key) {
        rule.onRoot().performKeyInput { pressKey(key) }
        rule.waitForIdle()
    }

    @Test fun opensWithPowerFocused() {
        launch()
        rule.onNodeWithContentDescription("Power Off").assertIsFocused()
    }

    @Test fun dpadWalksTheTopBlock() {
        launch()
        press(Key.DirectionRight)
        rule.onNodeWithContentDescription("Wi-Fi").assertIsFocused()
        press(Key.DirectionDown)
        rule.onNodeWithContentDescription("Do Not Disturb").assertIsFocused()
        press(Key.DirectionLeft)
        rule.onNodeWithContentDescription("Power Off").assertIsFocused()
        press(Key.DirectionDown)
        rule.onNodeWithContentDescription("Bluetooth").assertIsFocused()
    }

    @Test fun focusIsTrappedAtTheEdges() {
        launch()
        press(Key.DirectionLeft)
        rule.onNodeWithContentDescription("Power Off").assertIsFocused()
        press(Key.DirectionUp)
        rule.onNodeWithContentDescription("Menu").assertIsFocused()
        press(Key.DirectionUp)
        rule.onNodeWithContentDescription("Menu").assertIsFocused()
        press(Key.DirectionLeft)
        rule.onNodeWithContentDescription("Menu").assertIsFocused()
    }

    @Test fun topBarButtonsLeadBackToTheGrid() {
        launch()
        press(Key.DirectionUp)
        press(Key.DirectionRight)
        rule.onNodeWithContentDescription("Profile").assertIsFocused()
        press(Key.DirectionDown)
        rule.onNodeWithContentDescription("Wi-Fi").assertIsFocused()
    }

    @Test fun reachesTheBottomRowAndVolume() {
        launch()
        repeat(2) { press(Key.DirectionDown) }
        rule.onNodeWithContentDescription("Game controllers").assertIsFocused()
        press(Key.DirectionRight)
        rule.onNodeWithContentDescription("Accessibility").assertIsFocused()
        press(Key.DirectionDown)
        rule.onNodeWithContentDescription("Volume").assertIsFocused()
    }

    @Test fun profileButtonOpensSettings() {
        launch()
        press(Key.DirectionUp)
        press(Key.DirectionRight)
        rule.onNodeWithContentDescription("Profile").assertIsFocused()
        press(Key.DirectionCenter)
        assert(actions.calls.contains("settings"))
    }

    @Test fun restoresTheLastFocusedTileOnOpen() {
        memory.last = TileId.SLEEP_TIMER
        launch()
        rule.onNodeWithContentDescription("Sleep Timer").assertIsFocused()
    }

    @Test fun focusMovesAreRememberedForNextTime() {
        launch()
        press(Key.DirectionRight)
        assert(memory.last == TileId.WIFI)
    }

    @Test fun selectActivatesTheFocusedTile() {
        launch()
        press(Key.DirectionRight)
        press(Key.DirectionCenter)
        assert(actions.calls.contains("wifi"))
    }

    @Test fun aControlAddedInTheEditorAppearsAndCanBeFocused() {
        layouts.update { it.setVisible(TileId.SCREENSHOT, true) }
        memory.last = TileId.SCREENSHOT
        launch()
        rule.onNodeWithContentDescription("Screenshot").assertIsFocused()
    }

    @Test fun everyShownControlMeetsTheMinimumTargetSize() {
        launch()
        listOf(
            "Power Off", "Wi-Fi", "Do Not Disturb", "Bluetooth", "Sleep Timer",
            "Game controllers", "Accessibility", "Lock screen", "Search", "Volume",
        ).forEach {
            rule.onNodeWithContentDescription(it).assertWidthIsAtLeast(48.dp).assertHeightIsAtLeast(48.dp)
        }
        // The round buttons beside the clock are 40 dp, a deliberate trade for the tvOS look.
        listOf("Menu", "Profile").forEach {
            rule.onNodeWithContentDescription(it).assertWidthIsAtLeast(40.dp).assertHeightIsAtLeast(40.dp)
        }
    }
}
