package com.example.tvcontrolcenter.system.actions

import android.app.NotificationManager
import android.bluetooth.BluetoothManager
import android.content.Context
import android.media.AudioDeviceInfo
import android.media.AudioManager
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import com.example.tvcontrolcenter.domain.model.NetworkKind
import com.example.tvcontrolcenter.domain.model.SystemStatus
import com.example.tvcontrolcenter.domain.repository.SystemStatusSource
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Polls cheap system getters every two seconds while the panel is collecting.
 * Polling keeps the code small and needs no extra permissions; see README.
 */
@Singleton
class AndroidSystemStatusSource @Inject constructor(
    @ApplicationContext private val context: Context,
) : SystemStatusSource {

    override val status: Flow<SystemStatus> = flow {
        while (true) {
            emit(read())
            delay(POLL_MILLIS)
        }
    }.distinctUntilChanged().flowOn(Dispatchers.Default)

    override suspend fun read(): SystemStatus = withContext(Dispatchers.Default) {
        val audio = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
        val max = audio.getStreamMaxVolume(AudioManager.STREAM_MUSIC).coerceAtLeast(1)
        val volume = audio.getStreamVolume(AudioManager.STREAM_MUSIC) * 100 / max

        val btAdapter = (context.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager)?.adapter
        // Reading the output device list needs no Bluetooth permission, unlike bonded devices.
        val btOutput = audio.getDevices(AudioManager.GET_DEVICES_OUTPUTS).firstOrNull {
            it.type == AudioDeviceInfo.TYPE_BLUETOOTH_A2DP || it.type == AudioDeviceInfo.TYPE_BLE_HEADSET
        }

        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        SystemStatus(
            network = currentNetwork(),
            bluetoothEnabled = runCatching { btAdapter?.isEnabled == true }.getOrDefault(false),
            audioDeviceName = btOutput?.productName?.toString()?.takeIf { it.isNotBlank() },
            dndEnabled = nm.currentInterruptionFilter == NotificationManager.INTERRUPTION_FILTER_NONE ||
                nm.currentInterruptionFilter == NotificationManager.INTERRUPTION_FILTER_ALARMS,
            volumePercent = volume,
            muted = audio.isStreamMute(AudioManager.STREAM_MUSIC),
        )
    }

    private fun currentNetwork(): NetworkKind {
        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val caps = cm.getNetworkCapabilities(cm.activeNetwork) ?: return NetworkKind.NONE
        return when {
            caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) -> NetworkKind.WIFI
            caps.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) -> NetworkKind.ETHERNET
            else -> NetworkKind.NONE
        }
    }

    private companion object {
        const val POLL_MILLIS = 2_000L
    }
}
