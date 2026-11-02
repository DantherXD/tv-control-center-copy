package com.example.tvcontrolcenter.ui.panel

import app.cash.turbine.test
import kotlinx.coroutines.Job
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runCurrent
import com.example.tvcontrolcenter.domain.model.ActionResult
import com.example.tvcontrolcenter.domain.model.Capability
import com.example.tvcontrolcenter.domain.model.NetworkKind
import com.example.tvcontrolcenter.domain.model.NowPlaying
import com.example.tvcontrolcenter.domain.model.PanelEvent
import com.example.tvcontrolcenter.domain.model.SystemStatus
import com.example.tvcontrolcenter.domain.model.TileId
import com.example.tvcontrolcenter.domain.model.TileSize
import com.example.tvcontrolcenter.fakes.FakeActions
import com.example.tvcontrolcenter.fakes.FakeFocusMemory
import com.example.tvcontrolcenter.fakes.FakeLayoutRepository
import com.example.tvcontrolcenter.fakes.FakeNowPlaying
import com.example.tvcontrolcenter.fakes.FakeSleepTimer
import com.example.tvcontrolcenter.fakes.FakeStatusSource
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ControlCenterViewModelTest {

    private val status = FakeStatusSource()
    private val actions = FakeActions()
    private val nowPlaying = FakeNowPlaying()
    private val timer = FakeSleepTimer()
    private val memory = FakeFocusMemory()
    private val layouts = FakeLayoutRepository()

    private fun viewModel() = ControlCenterViewModel(status, actions, nowPlaying, timer, memory, layouts)

    @Before fun setUp() { Dispatchers.setMain(UnconfinedTestDispatcher()) }

    @After fun tearDown() { Dispatchers.resetMain() }

    private fun TestScope.collecting(vm: ControlCenterViewModel): Job {
        val job = vm.uiState.onEach { }.launchIn(backgroundScope)
        runCurrent()
        return job
    }

    @Test
    fun `tiles reflect live system status`() = runTest {
        val vm = viewModel()
        collecting(vm)
        status.state.value = SystemStatus(network = NetworkKind.WIFI, dndEnabled = true, bluetoothEnabled = true)
        val s = vm.uiState.value
        assertTrue(s.tiles.getValue(TileId.WIFI).active)
        assertTrue(s.tiles.getValue(TileId.DND).active)
        assertTrue(s.tiles.getValue(TileId.BLUETOOTH).active)
    }

    @Test
    fun `music page without listener access asks for it and stays closed`() = runTest {
        nowPlaying.access = false
        val vm = viewModel()
        vm.events.test {
            assertFalse(vm.onMusicPageRequested())
            assertEquals(PanelEvent.NeedsCapability(Capability.NOTIFICATION_LISTENER), awaitItem())
        }
    }

    @Test
    fun `music page opens and play pause reaches the player when access is granted`() = runTest {
        val vm = viewModel()
        assertTrue(vm.onMusicPageRequested())
        vm.onPlayPause()
        assertEquals(listOf("toggle"), nowPlaying.calls)
    }

    @Test
    fun `gap and inverted focus are settings with defaults`() = runTest {
        val vm = viewModel()
        collecting(vm)
        assertEquals(8, vm.uiState.value.settings.headerGap)
        assertTrue(vm.uiState.value.settings.invertFocus)
        vm.updateSettings { it.copy(headerGap = 0, invertFocus = false) }
        assertEquals(0, vm.uiState.value.settings.headerGap)
        assertFalse(vm.uiState.value.settings.invertFocus)
    }

    @Test
    fun `wifi tile is inactive without a network`() = runTest {
        val vm = viewModel()
        collecting(vm)
        assertFalse(vm.uiState.value.tiles.getValue(TileId.WIFI).active)
    }

    @Test
    fun `activating wifi opens settings and dismisses the panel`() = runTest {
        val vm = viewModel()
        vm.events.test {
            vm.onTileActivated(TileId.WIFI)
            assertEquals(PanelEvent.Dismiss, awaitItem())
        }
        assertEquals(listOf("wifi"), actions.calls)
    }

    @Test
    fun `do not disturb without policy access asks for the capability`() = runTest {
        actions.dndResult = ActionResult.NeedsCapability(Capability.NOTIFICATION_POLICY)
        val vm = viewModel()
        vm.events.test {
            vm.onTileActivated(TileId.DND)
            assertEquals(PanelEvent.NeedsCapability(Capability.NOTIFICATION_POLICY), awaitItem())
        }
    }

    @Test
    fun `do not disturb toggles without dismissing when it works`() = runTest {
        val vm = viewModel()
        vm.events.test {
            vm.onTileActivated(TileId.DND)
            expectNoEvents()
        }
        assertEquals(listOf("dnd:false"), actions.calls)
    }

    @Test
    fun `unavailable settings screen reports the tile`() = runTest {
        actions.result = ActionResult.Unavailable
        val vm = viewModel()
        vm.events.test {
            vm.onTileActivated(TileId.GAMEPAD)
            assertEquals(PanelEvent.Unavailable(TileId.GAMEPAD), awaitItem())
        }
    }

    @Test
    fun `power without the accessibility service asks for it`() = runTest {
        actions.result = ActionResult.NeedsCapability(Capability.ACCESSIBILITY_SERVICE)
        val vm = viewModel()
        vm.events.test {
            vm.onTileActivated(TileId.POWER)
            assertEquals(PanelEvent.NeedsCapability(Capability.ACCESSIBILITY_SERVICE), awaitItem())
        }
    }

    @Test
    fun `volume steps clamp to 0 and 100 and reach the system`() = runTest {
        status.state.value = SystemStatus(volumePercent = 98)
        val vm = viewModel()
        val job = collecting(vm)
        vm.onVolumeStep(5)
        assertEquals(100, vm.uiState.value.volumePercent)
        assertEquals(100, actions.lastVolume)
        vm.onVolumeStep(-150)
        assertEquals(0, vm.uiState.value.volumePercent)
        job.cancel()
    }

    @Test
    fun `local volume override expires and polling takes over`() = runTest {
        // StandardTestDispatcher so the 2.5 s override timer runs on virtual time.
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        status.state.value = SystemStatus(volumePercent = 40)
        val vm = viewModel()
        val job = collecting(vm)
        vm.onVolumeStep(10)
        testScheduler.runCurrent()
        assertEquals(50, vm.uiState.value.volumePercent)
        advanceTimeBy(3_000)
        testScheduler.runCurrent()
        assertEquals(40, vm.uiState.value.volumePercent)
        job.cancel()
    }

    @Test
    fun `focus is remembered and restored`() {
        val vm = viewModel()
        vm.onFocused(TileId.SLEEP_TIMER)
        assertEquals(TileId.SLEEP_TIMER, memory.last)
        assertEquals(TileId.SLEEP_TIMER, vm.rememberedFocus())
    }

    @Test
    fun `edit tile asks the host to open the editor`() = runTest {
        val vm = viewModel()
        vm.events.test {
            vm.onTileActivated(TileId.EDIT)
            assertEquals(PanelEvent.OpenEditor, awaitItem())
        }
    }

    @Test
    fun `layout edits reach the state and the repository`() = runTest {
        val vm = viewModel()
        collecting(vm)
        vm.setTileSize(TileId.WIFI, TileSize.LARGE)
        assertEquals(TileSize.LARGE, vm.uiState.value.layout.item(TileId.WIFI)?.size)
        vm.setTileVisible(TileId.DND, false)
        assertFalse(vm.uiState.value.layout.item(TileId.DND)?.visible == true)
        vm.setTileVisible(TileId.SCREENSHOT, true)
        assertEquals(TileId.SCREENSHOT, vm.uiState.value.layout.items.last().id)
        vm.moveTile(TileId.POWER, 1)
        assertEquals(TileId.WIFI, vm.uiState.value.layout.items.first().id)
        vm.resizeTileBy(TileId.HOME, 1) // Small steps to Wide
        assertEquals(TileSize.WIDE, vm.uiState.value.layout.item(TileId.HOME)?.size)
        vm.resetLayout()
        assertEquals(com.example.tvcontrolcenter.domain.model.PanelLayout.Default, vm.uiState.value.layout)
    }

    @Test
    fun `panel settings flow into the state`() = runTest {
        val vm = viewModel()
        collecting(vm)
        vm.setUiScale(1.5f)
        vm.setBlurBehind(false)
        vm.updateSettings { it.copy(topOffset = 96, blurLevel = 4) }
        assertEquals(1.5f, vm.uiState.value.settings.uiScale)
        assertFalse(vm.uiState.value.settings.blurBehind)
        assertEquals(96, vm.uiState.value.settings.topOffset)
        assertEquals(4, vm.uiState.value.settings.blurLevel)
    }

    @Test
    fun `now playing tile without listener access asks for it`() = runTest {
        nowPlaying.access = false
        val vm = viewModel()
        vm.events.test {
            vm.onTileActivated(TileId.NOW_PLAYING)
            assertEquals(PanelEvent.NeedsCapability(Capability.NOTIFICATION_LISTENER), awaitItem())
        }
        assertTrue(nowPlaying.calls.none { it == "toggle" })
    }

    @Test
    fun `now playing tile toggles playback when access is granted`() {
        nowPlaying.state.value = NowPlaying("Song", "Artist", "pkg", isPlaying = true)
        val vm = viewModel()
        vm.onTileActivated(TileId.NOW_PLAYING)
        vm.onSkipNext()
        vm.onSkipPrevious()
        assertEquals(listOf("toggle", "next", "previous"), nowPlaying.calls)
    }

    @Test
    fun `sleep timer tile cycles the timer`() {
        val vm = viewModel()
        vm.onTileActivated(TileId.SLEEP_TIMER)
        vm.onTileActivated(TileId.SLEEP_TIMER)
        assertEquals(2, timer.cycles)
    }

    @Test
    fun `opening the panel refreshes now playing`() {
        viewModel().onOpened()
        assertEquals(listOf("refresh"), nowPlaying.calls)
    }

    @Test
    fun `every quick action calls its system action and dismisses the panel`() = runTest {
        val expected = mapOf(
            TileId.SETTINGS to "settings", TileId.DISPLAY to "display", TileId.SOUND to "sound",
            TileId.CAPTIONS to "captions", TileId.SCREENSAVER to "screensaver", TileId.APPS to "apps",
            TileId.HOME to "home", TileId.RECENTS to "recents", TileId.SCREENSHOT to "screenshot",
            TileId.NOTIFICATIONS to "notifications", TileId.PROFILE to "settings",
        )
        val vm = viewModel()
        vm.events.test {
            for ((tile, call) in expected) {
                vm.onTileActivated(tile)
                assertEquals(PanelEvent.Dismiss, awaitItem())
                assertEquals(call, actions.calls.last())
            }
        }
    }

    @Test
    fun `global actions without the accessibility service ask for it`() = runTest {
        actions.result = ActionResult.NeedsCapability(Capability.ACCESSIBILITY_SERVICE)
        val vm = viewModel()
        vm.events.test {
            for (tile in listOf(TileId.HOME, TileId.RECENTS, TileId.SCREENSHOT, TileId.NOTIFICATIONS)) {
                vm.onTileActivated(tile)
                assertEquals(PanelEvent.NeedsCapability(Capability.ACCESSIBILITY_SERVICE), awaitItem())
            }
        }
    }

    @Test
    fun `volume tile toggles mute and shows it at once`() = runTest {
        val vm = viewModel()
        collecting(vm)
        assertFalse(vm.uiState.value.muted)
        vm.onTileActivated(TileId.VOLUME)
        assertTrue(vm.uiState.value.muted)
        assertEquals(listOf("mute"), actions.calls)
    }

    @Test
    fun `mute state comes from the system when there is no local change`() = runTest {
        status.state.value = SystemStatus(muted = true)
        val vm = viewModel()
        collecting(vm)
        assertTrue(vm.uiState.value.muted)
    }

    @Test
    fun `a failed mute does not flip the display`() = runTest {
        actions.result = ActionResult.Unavailable
        val vm = viewModel()
        collecting(vm)
        vm.events.test {
            vm.onTileActivated(TileId.VOLUME)
            assertEquals(PanelEvent.Unavailable(TileId.VOLUME), awaitItem())
        }
        assertFalse(vm.uiState.value.muted)
    }

    @Test
    fun `raising the volume while muted unmutes`() = runTest {
        status.state.value = SystemStatus(volumePercent = 20, muted = true)
        val vm = viewModel()
        collecting(vm)
        vm.onVolumeStep(5)
        assertFalse(vm.uiState.value.muted)
        assertEquals(25, vm.uiState.value.volumePercent)
    }
}
