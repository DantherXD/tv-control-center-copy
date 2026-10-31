package com.example.tvcontrolcenter.data

import android.content.Context
import com.example.tvcontrolcenter.domain.model.SleepTimerPresets
import com.example.tvcontrolcenter.domain.model.TileId
import com.example.tvcontrolcenter.domain.repository.FocusMemory
import com.example.tvcontrolcenter.domain.repository.SleepTimerRepository
import com.example.tvcontrolcenter.domain.repository.SystemActions
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

/** Persists the last focused tile across panel openings and process restarts. */
@Singleton
class PreferencesFocusMemory @Inject constructor(
    @ApplicationContext context: Context,
) : FocusMemory {
    private val prefs = context.getSharedPreferences("control_center", Context.MODE_PRIVATE)

    override fun get(): TileId =
        prefs.getString(KEY, null)?.let { name -> TileId.entries.firstOrNull { it.name == name } }
            ?: TileId.POWER

    override fun set(id: TileId) {
        prefs.edit().putString(KEY, id.name).apply()
    }

    private companion object {
        const val KEY = "last_focused_tile"
    }
}

/**
 * Counts down in whole minutes and puts the screen to sleep at zero.
 * The scope and tick length are injectable so tests can run on virtual time.
 */
class SleepTimerRepositoryImpl(
    private val scope: CoroutineScope,
    private val actions: SystemActions,
    private val tickMillis: Long = 60_000L,
) : SleepTimerRepository {

    private val _remaining = MutableStateFlow<Int?>(null)
    override val remainingMinutes: StateFlow<Int?> = _remaining.asStateFlow()
    private var job: Job? = null
    private var presetIndex = 0

    override fun cycle() {
        presetIndex = (presetIndex + 1) % SleepTimerPresets.size
        val minutes = SleepTimerPresets[presetIndex]
        if (minutes == 0) cancel() else start(minutes)
    }

    override fun cancel() {
        job?.cancel()
        job = null
        presetIndex = 0
        _remaining.value = null
    }

    private fun start(minutes: Int) {
        job?.cancel()
        _remaining.value = minutes
        job = scope.launch {
            var left = minutes
            while (left > 0) {
                delay(tickMillis)
                left -= 1
                _remaining.value = left
            }
            actions.sleepScreen()
            _remaining.value = null
        }
    }
}
