package com.example.tvcontrolcenter.data

import com.example.tvcontrolcenter.fakes.FakeActions
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class SleepTimerRepositoryTest {

    private val minute = 60_000L

    @Test fun `cycle walks through presets and back to off`() = runTest {
        val repo = SleepTimerRepositoryImpl(backgroundScope, FakeActions())
        val seen = mutableListOf<Int?>()
        repeat(6) { repo.cycle(); seen += repo.remainingMinutes.value }
        assertEquals(listOf(15, 30, 60, 90, null, 15), seen)
    }

    @Test fun `counts down by minute`() = runTest {
        val repo = SleepTimerRepositoryImpl(backgroundScope, FakeActions())
        repo.cycle()
        advanceTimeBy(3 * minute + 1)
        runCurrent()
        assertEquals(12, repo.remainingMinutes.value)
    }

    @Test fun `puts the screen to sleep when it expires`() = runTest {
        val actions = FakeActions()
        val repo = SleepTimerRepositoryImpl(backgroundScope, actions)
        repo.cycle()
        advanceTimeBy(15 * minute + 1)
        runCurrent()
        assertEquals(listOf("sleep"), actions.calls)
        assertNull(repo.remainingMinutes.value)
    }

    @Test fun `cancel stops the countdown`() = runTest {
        val actions = FakeActions()
        val repo = SleepTimerRepositoryImpl(backgroundScope, actions)
        repo.cycle()
        repo.cancel()
        advanceTimeBy(20 * minute)
        runCurrent()
        assertNull(repo.remainingMinutes.value)
        assertEquals(emptyList<String>(), actions.calls)
    }
}
