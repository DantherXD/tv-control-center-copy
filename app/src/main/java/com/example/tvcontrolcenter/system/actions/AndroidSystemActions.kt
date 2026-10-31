package com.example.tvcontrolcenter.system.actions

import android.app.NotificationManager
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.media.AudioManager
import android.provider.Settings
import com.example.tvcontrolcenter.domain.model.ActionResult
import com.example.tvcontrolcenter.domain.model.Capability
import com.example.tvcontrolcenter.domain.repository.SystemActions
import com.example.tvcontrolcenter.system.trigger.ControlCenterAccessibilityService
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/** Real system actions where Android allows them. Anything else returns a typed result. */
@Singleton
class AndroidSystemActions @Inject constructor(
    @ApplicationContext private val context: Context,
) : SystemActions {

    private val audio get() = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager

    override fun setVolumePercent(percent: Int) {
        val max = audio.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
        val target = (percent.coerceIn(0, 100) * max / 100f).toInt()
        // On HDMI-CEC / fixed-volume boxes this can be a no-op. See README.
        runCatching { audio.setStreamVolume(AudioManager.STREAM_MUSIC, target, 0) }
    }

    override fun openWifiSettings() = launchSettings(Settings.ACTION_WIFI_SETTINGS)

    override fun openBluetoothSettings() = launchSettings(Settings.ACTION_BLUETOOTH_SETTINGS)

    override fun openGameControllerSettings() =
        launchSettings("android.settings.BLUETOOTH_SETTINGS")

    override fun openAccessibilitySettings() = launchSettings(Settings.ACTION_ACCESSIBILITY_SETTINGS)

    override fun openSearch(): ActionResult {
        val assist = Intent(Intent.ACTION_ASSIST).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        return try {
            context.startActivity(assist)
            ActionResult.Done
        } catch (_: ActivityNotFoundException) {
            ActionResult.Unavailable
        }
    }

    override fun toggleDoNotDisturb(currentlyOn: Boolean): ActionResult {
        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (!nm.isNotificationPolicyAccessGranted) {
            return ActionResult.NeedsCapability(Capability.NOTIFICATION_POLICY)
        }
        nm.setInterruptionFilter(
            if (currentlyOn) NotificationManager.INTERRUPTION_FILTER_ALL
            else NotificationManager.INTERRUPTION_FILTER_NONE,
        )
        return ActionResult.Done
    }

    override fun powerOff(): ActionResult {
        val service = ControlCenterAccessibilityService.instance
            ?: return ActionResult.NeedsCapability(Capability.ACCESSIBILITY_SERVICE)
        return if (service.showPowerDialog()) ActionResult.Done else sleepScreen()
    }

    override fun sleepScreen(): ActionResult {
        val service = ControlCenterAccessibilityService.instance
            ?: return ActionResult.NeedsCapability(Capability.ACCESSIBILITY_SERVICE)
        return if (service.lockScreen()) ActionResult.Done else ActionResult.Unavailable
    }

    private fun launchSettings(action: String): ActionResult = try {
        context.startActivity(Intent(action).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        ActionResult.Done
    } catch (_: ActivityNotFoundException) {
        // Some TV builds strip individual settings screens. Fall back to the root screen.
        try {
            context.startActivity(Intent(Settings.ACTION_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            ActionResult.Done
        } catch (_: ActivityNotFoundException) {
            ActionResult.Unavailable
        }
    }
}
