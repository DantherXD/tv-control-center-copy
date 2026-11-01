package com.example.tvcontrolcenter.system.actions

import android.accessibilityservice.AccessibilityService
import android.app.NotificationManager
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.media.AudioManager
import android.os.Build
import android.os.Handler
import android.os.Looper
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
    private val handler = Handler(Looper.getMainLooper())

    override fun setVolumePercent(percent: Int) {
        val max = audio.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
        val target = (percent.coerceIn(0, 100) * max / 100f).toInt()
        // On HDMI-CEC / fixed-volume boxes this can be a no-op. See README.
        runCatching { audio.setStreamVolume(AudioManager.STREAM_MUSIC, target, 0) }
    }

    override fun toggleMute(): ActionResult = try {
        audio.adjustStreamVolume(AudioManager.STREAM_MUSIC, AudioManager.ADJUST_TOGGLE_MUTE, 0)
        ActionResult.Done
    } catch (_: SecurityException) {
        ActionResult.Unavailable
    }

    override fun openWifiSettings() = launchSettings(Settings.ACTION_WIFI_SETTINGS)

    override fun openBluetoothSettings() = launchSettings(Settings.ACTION_BLUETOOTH_SETTINGS)

    override fun openGameControllerSettings() =
        launchSettings("android.settings.BLUETOOTH_SETTINGS")

    override fun openAccessibilitySettings() = launchSettings(Settings.ACTION_ACCESSIBILITY_SETTINGS)

    override fun openSettings() = launchSettings(Settings.ACTION_SETTINGS)

    override fun openDisplaySettings() = launchSettings(Settings.ACTION_DISPLAY_SETTINGS)

    override fun openSoundSettings() = launchSettings(Settings.ACTION_SOUND_SETTINGS)

    override fun openCaptionSettings() = launchSettings(Settings.ACTION_CAPTIONING_SETTINGS)

    override fun openScreensaverSettings() = launchSettings(Settings.ACTION_DREAM_SETTINGS)

    override fun openAppsSettings() = launchSettings(Settings.ACTION_APPLICATION_SETTINGS)

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

    override fun goHome(): ActionResult {
        if (ControlCenterAccessibilityService.instance != null) {
            return global(AccessibilityService.GLOBAL_ACTION_HOME)
        }
        // No accessibility service: the plain HOME intent still works from the panel's window.
        val home = Intent(Intent.ACTION_MAIN)
            .addCategory(Intent.CATEGORY_HOME)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        return try {
            context.startActivity(home)
            ActionResult.Done
        } catch (_: ActivityNotFoundException) {
            ActionResult.Unavailable
        }
    }

    override fun showRecents() = global(AccessibilityService.GLOBAL_ACTION_RECENTS)

    override fun takeScreenshot() =
        global(AccessibilityService.GLOBAL_ACTION_TAKE_SCREENSHOT, minSdk = Build.VERSION_CODES.P)

    override fun showNotifications() = global(AccessibilityService.GLOBAL_ACTION_NOTIFICATIONS)

    /** Runs an accessibility global action once the panel has had time to close. */
    private fun global(action: Int, minSdk: Int = 0): ActionResult {
        val service = ControlCenterAccessibilityService.instance
            ?: return ActionResult.NeedsCapability(Capability.ACCESSIBILITY_SERVICE)
        if (Build.VERSION.SDK_INT < minSdk) return ActionResult.Unavailable
        handler.postDelayed({ service.performGlobalAction(action) }, PANEL_CLOSE_DELAY_MILLIS)
        return ActionResult.Done
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

    private companion object {
        /** Slightly longer than the panel's close spring, so it is gone before the action runs. */
        const val PANEL_CLOSE_DELAY_MILLIS = 600L
    }
}
