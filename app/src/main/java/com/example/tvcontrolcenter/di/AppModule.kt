package com.example.tvcontrolcenter.di

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.example.tvcontrolcenter.data.PreferencesFocusMemory
import com.example.tvcontrolcenter.data.PreferencesLayoutRepository
import com.example.tvcontrolcenter.data.SleepTimerRepositoryImpl
import com.example.tvcontrolcenter.domain.repository.FocusMemory
import com.example.tvcontrolcenter.domain.repository.LayoutRepository
import com.example.tvcontrolcenter.domain.repository.NotificationSource
import com.example.tvcontrolcenter.domain.repository.NowPlayingSource
import com.example.tvcontrolcenter.domain.repository.SleepTimerRepository
import com.example.tvcontrolcenter.domain.repository.SystemActions
import com.example.tvcontrolcenter.domain.repository.SystemStatusSource
import com.example.tvcontrolcenter.system.actions.AndroidSystemActions
import com.example.tvcontrolcenter.system.actions.AndroidSystemStatusSource
import com.example.tvcontrolcenter.system.media.MediaSessionNowPlayingSource
import com.example.tvcontrolcenter.system.notif.PanelNotificationsSource
import com.example.tvcontrolcenter.ui.panel.ControlCenterViewModel
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import javax.inject.Inject
import javax.inject.Provider
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class BindingsModule {
    @Binds abstract fun actions(impl: AndroidSystemActions): SystemActions
    @Binds abstract fun status(impl: AndroidSystemStatusSource): SystemStatusSource
    @Binds abstract fun nowPlaying(impl: MediaSessionNowPlayingSource): NowPlayingSource
    @Binds abstract fun notifications(impl: PanelNotificationsSource): NotificationSource
    @Binds abstract fun focusMemory(impl: PreferencesFocusMemory): FocusMemory
    @Binds abstract fun layouts(impl: PreferencesLayoutRepository): LayoutRepository
}

@Module
@InstallIn(SingletonComponent::class)
object ProvidersModule {
    @Provides @Singleton
    fun sleepTimer(actions: SystemActions): SleepTimerRepository =
        SleepTimerRepositoryImpl(
            scope = CoroutineScope(SupervisorJob() + Dispatchers.Default),
            actions = actions,
        )
}

/**
 * Builds the panel ViewModel for any owner: an Activity, or the overlay window,
 * which has no Hilt-aware owner. Both go through this factory.
 */
@Singleton
class ControlCenterViewModelFactory @Inject constructor(
    private val provider: Provider<ControlCenterViewModel>,
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T = provider.get() as T
}

@EntryPoint
@InstallIn(SingletonComponent::class)
interface ControlCenterEntryPoint {
    fun viewModelFactory(): ControlCenterViewModelFactory
    fun layoutRepository(): LayoutRepository

    companion object {
        fun from(context: Context): ControlCenterEntryPoint =
            EntryPointAccessors.fromApplication(context.applicationContext, ControlCenterEntryPoint::class.java)
    }
}
